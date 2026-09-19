/*
 * Copyright (C) 2023-25 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.os.SystemProperties
import java.util.concurrent.atomic.AtomicLong
import android.media.AudioAttributes
import android.media.AudioRecordingConfiguration
import android.media.AudioManager.AudioRecordingCallback
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyConstants.DsParam
import co.aospa.dolby.xiaomi.geq.data.EqualizerGains
import co.aospa.dolby.xiaomi.profiles.DolbyProfiles
import co.aospa.dolby.xiaomi.profiles.ActiveProfileState
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Synchronous engine implementation, exclusively owned by DolbyController's mutex. */
internal class DolbyEngine(
    private val context: Context,
    val profiles: DolbyProfiles,
    private val _activeState: MutableStateFlow<ActiveProfileState>,
    private val requestRestore: () -> Unit
) {
    private enum class ProcessingState {
        MEDIA,
        COMMUNICATION,
        MEDIA_RESTORE
    }

    val activeState = _activeState.asStateFlow()
    private var initialized = false

    val activeProfileKey: String
        get() = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(DolbyConstants.PREF_PROFILE, "0")
            ?.takeIf { profiles.find(it) != null } ?: "0"

    private var requestedEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DolbyConstants.PREF_ENABLE, true)
    private var requestedSpeakerTuning = DolbyEndpointPolicy.SpeakerTuning.fromKey(
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SPEAKER_TUNING, null))
        .takeIf { DolbyCapabilities.speakerTuningSupported }
        ?: DolbyEndpointPolicy.SpeakerTuning.AUTOMATIC
    private var appliedTuning: DolbyEndpointPolicy.Tuning? = null
    private val rejectedTunings = mutableSetOf<DolbyEndpointPolicy.Tuning>()
    private var tuningCommandSupported: Boolean? = null
    private val endpointRevision = AtomicLong()
    private var appliedEndpointRevision = -1L
    private var appliedRoute: Pair<Int, Int>? = null
    private val controlRevision = AtomicLong()
    private var appliedControlRevision = -1L
    private val serverRevision = AtomicLong()
    private var appliedServerRevision = -1L
    @Volatile private var audioServerAvailable = true
    private var needsBootstrap = true
    private var appliedProfileKey: String? = null
    private var nativeEffect: DolbyAudioEffect? = null
    private val dolbyEffect: DolbyAudioEffect
        get() = checkNotNull(nativeEffect) { "Dolby effect has not been connected" }
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val handler = Handler(context.mainLooper)
    private val serverMonitor = DolbyAudioServerMonitor.get(context)
    @Volatile
    private var lastAudioStateInvalidationMs = SystemClock.elapsedRealtime()
    private var processingState = ProcessingState.MEDIA
    private val volumeLevelerSupported =
        context.getResources().getBoolean(R.bool.dolby_volume_leveler_supported)

    // Both start and stop matter: the last voice track can end without a mode change.
    // Query fresh state inside the serialized transaction instead of retaining callback lists.
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) {
            invalidateAudioState("playback")
        }
    }

    private val recordingCallback = object : AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) {
            invalidateAudioState("recording")
        }
    }

    // Restore current profile on audio device change
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            endpointRevision.incrementAndGet()
            invalidateAudioState("device-added")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            endpointRevision.incrementAndGet()
            invalidateAudioState("device-removed")
        }
    }

    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).build()
    private val mediaRouteListener = AudioManager.OnDevicesForAttributesChangedListener { _, _ ->
        endpointRevision.incrementAndGet()
        invalidateAudioState("media-route")
    }

    // Mode changes need their own callback: starting VoIP need not add/remove a device.
    // Native effect access stays on the controller's serialized transaction path.
    private val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
        dlog(TAG, "onModeChanged: $mode")
        invalidateAudioState("mode")
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.VOLUME_CHANGED_ACTION &&
                intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC &&
                SystemProperties.getBoolean(DolbyEndpointPolicy.VOLUME_TUNING_PROPERTY, false)) {
                // Only crossing the stock LOW/HIGH boundary changes the selected ID.
                // A volume step is not a route transition and must not extend the quiet window.
                // Products without these IDs use native pregain; they need no app
                // profile readback on each volume-key repeat.
                requestRestore()
            }
        }
    }

    private val spatializerListener = object : Spatializer.OnSpatializerStateChangedListener {
        override fun onSpatializerEnabledChanged(spatializer: Spatializer, enabled: Boolean) {
            endpointRevision.incrementAndGet()
            invalidateAudioState("spatializer-enabled")
        }
        override fun onSpatializerAvailableChanged(spatializer: Spatializer, available: Boolean) {
            endpointRevision.incrementAndGet()
            invalidateAudioState("spatializer-available")
        }
    }

    private val serverCallback = object : AudioManager.AudioServerStateCallback() {
        override fun onAudioServerDown() {
            audioServerAvailable = false
            serverRevision.incrementAndGet()
            invalidateAudioState("audioserver-down")
        }
        override fun onAudioServerUp() {
            audioServerAvailable = true
            endpointRevision.incrementAndGet()
            invalidateAudioState("audioserver-up")
        }
    }

    private fun invalidateAudioState(reason: String) {
        lastAudioStateInvalidationMs = SystemClock.elapsedRealtime()
        dlog(TAG, "invalidateAudioState: $reason")
        requestRestore()
    }

    private val mediaMode: Boolean
        get() = !audioManager.isDolbyCommunicationActive()

    private fun applyEnabledState(force: Boolean = false): Boolean {
        checkEffect()
        val enabled = requestedEnabled && mediaMode && processingState == ProcessingState.MEDIA
        if (enabled) {
            // Endpoint/profile setup must precede activation. A failed restore stays bypassed.
            try {
                setCurrentProfile()
            } catch (failure: RuntimeException) {
                runCatching { dolbyEffect.dsOn = false }.exceptionOrNull()?.let {
                    if (it !== failure) failure.addSuppressed(it)
                }
                throw failure
            }
            // AudioService may change while the synchronous native transaction runs.
            if (!mediaMode) {
                processingState = ProcessingState.COMMUNICATION
                dolbyEffect.dsOn = false
                requestRestore()
                return false
            }
        }
        if (force || dolbyEffect.dsOn != enabled || dolbyEffect.enabled != enabled) {
            dlog(TAG, "DAP requested=$requestedEnabled state=$processingState applied=$enabled")
            dolbyEffect.dsOn = enabled
            if (!enabled) {
                appliedTuning = null
                appliedProfileKey = null
            }
        }
        if (enabled) {
            // A call can begin during SET/readback, not only during profile
            // restoration. Do not publish media-active after that transition.
            val stillMedia = try {
                mediaMode
            } catch (failure: RuntimeException) {
                try {
                    dolbyEffect.dsOn = false
                } catch (cleanup: RuntimeException) {
                    if (cleanup !== failure) failure.addSuppressed(cleanup)
                } finally {
                    appliedTuning = null
                    appliedProfileKey = null
                    requestRestore()
                }
                throw failure
            }
            if (!stillMedia) {
                processingState = ProcessingState.COMMUNICATION
                try {
                    dolbyEffect.dsOn = false
                } finally {
                    appliedTuning = null
                    appliedProfileKey = null
                    requestRestore()
                }
                return false
            }
        }
        return enabled
    }

    /**
     * Reconcile DAP with the live AudioService state.
     *
     * Communication entry is fail-closed and immediate. On exit, keep DAP off until
     * mode/playback/recording/device callbacks have been quiet long enough for the
     * legacy HAL to finish rebuilding its media route. The controller schedules the
     * returned delay without cancelling any native transaction already in progress.
     */
    fun restoreForAudioState(): Long? {
        val now = SystemClock.elapsedRealtime()
        if (!mediaMode) {
            val enteringCommunication = processingState != ProcessingState.COMMUNICATION
            if (enteringCommunication) {
                Log.i(TAG, "Entering communication bypass: mode=${audioManager.mode}, " +
                    "requested=$requestedEnabled")
            }
            processingState = ProcessingState.COMMUNICATION
            applyEnabledState(force = enteringCommunication)
            refreshActiveState()
            return null
        }

        if (processingState != ProcessingState.MEDIA) {
            val startingRestore = processingState != ProcessingState.MEDIA_RESTORE
            processingState = ProcessingState.MEDIA_RESTORE
            if (startingRestore) {
                Log.i(TAG, "Communication ended; waiting for the media route: " +
                    "mode=${audioManager.mode}, requested=$requestedEnabled")
                // Reassert both effect gates off after communication. This also repairs a
                // partially failed disable once the route has started returning to media.
                applyEnabledState(force = true)
            }

            val quietFor = (now - lastAudioStateInvalidationMs).coerceAtLeast(0L)
            if (quietFor < MEDIA_RESTORE_SETTLE_MS) {
                val retryAfter = MEDIA_RESTORE_SETTLE_MS - quietFor
                dlog(TAG, "Waiting ${retryAfter}ms for media route to settle")
                refreshActiveState()
                return retryAfter
            }

            Log.i(TAG, "Media route settled: requested=$requestedEnabled")
            processingState = ProcessingState.MEDIA
            applyEnabledState(force = true)
            refreshActiveState()
            return null
        }

        applyEnabledState()
        refreshActiveState()
        return null
    }

    private var callbacksRegistered = false

    private fun setCallbacksRegistered(value: Boolean) {
        if (callbacksRegistered == value) return
        if (value) {
            var route = false
            var playback = false
            var recording = false
            var devices = false
            var mode = false
            var volume = false
            var spatializer = false
            var server = false
            try {
                audioManager.addOnDevicesForAttributesChangedListener(
                    mediaAttributes, context.mainExecutor, mediaRouteListener)
                route = true
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                playback = true
                audioManager.registerAudioRecordingCallback(recordingCallback, handler)
                recording = true
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
                devices = true
                audioManager.addOnModeChangedListener(context.mainExecutor, modeChangedListener)
                mode = true
                context.registerReceiver(volumeReceiver,
                    IntentFilter(AudioManager.VOLUME_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED)
                volume = true
                if (DolbyCapabilities.spatializerSupported) {
                    audioManager.spatializer.addOnSpatializerStateChangedListener(
                        context.mainExecutor, spatializerListener)
                    spatializer = true
                }
                serverMonitor.subscribe(serverCallback)
                server = true
                callbacksRegistered = true
            } catch (failure: RuntimeException) {
                if (server) runCatching { serverMonitor.unsubscribe(serverCallback) }
                if (spatializer) runCatching {
                    audioManager.spatializer.removeOnSpatializerStateChangedListener(spatializerListener)
                }
                if (volume) runCatching { context.unregisterReceiver(volumeReceiver) }
                if (mode) runCatching { audioManager.removeOnModeChangedListener(modeChangedListener) }
                if (devices) runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
                if (recording) runCatching { audioManager.unregisterAudioRecordingCallback(recordingCallback) }
                if (playback) runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
                if (route) runCatching {
                    audioManager.removeOnDevicesForAttributesChangedListener(mediaRouteListener)
                }
                throw failure
            }
            return
        }

        var failure: RuntimeException? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: RuntimeException) {
                val first = failure
                if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        attempt { serverMonitor.unsubscribe(serverCallback) }
        if (DolbyCapabilities.spatializerSupported) {
            attempt { audioManager.spatializer.removeOnSpatializerStateChangedListener(spatializerListener) }
        }
        attempt { context.unregisterReceiver(volumeReceiver) }
        attempt { audioManager.removeOnModeChangedListener(modeChangedListener) }
        attempt { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        attempt { audioManager.unregisterAudioRecordingCallback(recordingCallback) }
        attempt { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
        attempt { audioManager.removeOnDevicesForAttributesChangedListener(mediaRouteListener) }
        callbacksRegistered = false
        failure?.let { throw it }
    }

    // Expose the user's choice, not the temporary call-mode bypass, to UI/persistence.
    var dsOn: Boolean
        get() = requestedEnabled
        set(value) {
            val previous = requestedEnabled
            requestedEnabled = value
            try {
                // Equality is not evidence that the retained vendor engine is disabled.
                if (!value) applyEnabledState(force = true) else restoreForAudioState()
            } catch (failure: RuntimeException) {
                // An unsuccessful enable can roll back; an Off request must not
                // resurrect the previous On state during recovery.
                if (value) requestedEnabled = previous
                requestRestore()
                throw failure
            }
            // The returned quiet-window delay must be scheduled even for a UI-triggered enable.
            requestRestore()
        }

    var profile: Int
        get() =
            dolbyEffect.profile.also {
                dlog(TAG, "getProfile: $it")
            }
        set(value) {
            dlog(TAG, "setProfile: $value")
            checkEffect()
            dolbyEffect.profile = value
        }

    init {
        // Constructing the owner performs no native I/O. A temporarily unavailable
        // audioserver must not permanently fail DolbyController's Deferred.
        profiles.onChanged = { refreshActiveState() }
    }

    fun ensureInitialized() {
        // Keep lifecycle observers while disabled too: the policy-owned effect may
        // be recreated by audioserver independently of the UI's saved enable flag.
        setCallbacksRegistered(true)
        checkEffect()
        // Bootstrap establishes bypass only. Automatic profile/tuning writes
        // belong to applyEnabledState after the media restore window, never to
        // a read, a saved Off request, or repeated communication callbacks.
        if (!initialized) {
            initialized = true
            requestRestore()
        }
    }

    fun onBootCompleted () {
        dlog(TAG, "onBootCompleted()")

        // Migrate presets from credential protected storage if needed
        maybeMigratePresets()
    }

    private fun restoreSettings(profile: Int, key: String = profile.toString()) {
        dlog(TAG, "restoreSettings(profile=$profile)")
        val prefs = profiles.preferences(key)
        prefs.getString(DolbyConstants.PREF_PRESET, null)?.let { preset ->
            try {
                EqualizerGains.parse(preset)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Ignoring invalid saved equalizer for profile $profile", exception)
                prefs.edit().remove(DolbyConstants.PREF_PRESET).apply()
                return@let
            }
            setPreset(preset, profile)
        }
        // Unset preferences belong to the OEM endpoint-specific tuning, not the app.
        if (prefs.contains(DolbyConstants.PREF_BASS)) {
            setBassEnhancerEnabled(prefs.getBoolean(DolbyConstants.PREF_BASS, false), profile)
        }
        if (volumeLevelerSupported && prefs.contains(DolbyConstants.PREF_VOLUME)) {
            setVolumeLevelerEnabled(prefs.getBoolean(DolbyConstants.PREF_VOLUME, false), profile)
        }
        prefs.getString(DolbyConstants.PREF_IEQ, null)?.toIntOrNull()?.let {
            if (it in 0..3) setIeqPreset(it, profile)
        }
        if (prefs.contains(DolbyConstants.PREF_HP_VIRTUALIZER)) {
            setHeadphoneVirtEnabled(prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, false), profile)
        }
        if (prefs.contains(DolbyConstants.PREF_SPK_VIRTUALIZER)) {
            setSpeakerVirtEnabled(prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, false), profile)
        }
        // Retired strength overrides are intentionally not restored: parameter 113
        // is not verified as a headphone surround strength control.
        if (prefs.contains(DolbyConstants.PREF_DIALOGUE)) {
            setDialogueEnhancerEnabled(prefs.getBoolean(DolbyConstants.PREF_DIALOGUE, false), profile)
        }
        if (prefs.contains(DolbyConstants.PREF_DIALOGUE_AMOUNT)) {
            val amount = prefs.getInt(DolbyConstants.PREF_DIALOGUE_AMOUNT, 4)
            if (amount in 1..12) {
                setDialogueEnhancerAmount(amount, profile)
            } else {
                Log.w(TAG, "Ignoring invalid saved dialogue amount for profile $profile")
                prefs.edit().remove(DolbyConstants.PREF_DIALOGUE_AMOUNT).apply()
            }
        }
    }

    private fun maybeMigratePresets() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_KEY_PRESETS_MIGRATED, false)) {
            return
        }
        val ceContext = context.createCredentialProtectedStorageContext()
        val cePrefs = ceContext.getSharedPreferences(PREF_PRESETS, Context.MODE_PRIVATE)
        if (cePrefs.all.isEmpty()) {
            dlog(TAG, "no presets to migrate")
            return
        }
        if (context.moveSharedPreferencesFrom(ceContext, PREF_PRESETS)) {
            prefs.edit().putBoolean(PREF_KEY_PRESETS_MIGRATED, true).apply()
            dlog(TAG, "presets migrated successfully")
        } else {
            Log.w(TAG, "failed to migrate presets")
        }
    }

    private fun updateEndpointTuning(): Boolean {
        if (!requestedEnabled || !mediaMode || processingState != ProcessingState.MEDIA) return false
        val outputs = audioManager.getAudioDevicesForAttributes(mediaAttributes)
        val device = outputs.singleOrNull()
        val route = when (device?.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> DolbyEndpointPolicy.Route.SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> DolbyEndpointPolicy.Route.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> DolbyEndpointPolicy.Route.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> DolbyEndpointPolicy.Route.USB
            else -> DolbyEndpointPolicy.Route.NATIVE
        }
        val spatializerActive = DolbyCapabilities.spatializerSupported &&
            audioManager.spatializer.let { it.isEnabled && it.isAvailable }
        val selected = DolbyEndpointPolicy.select(route, spatializerActive,
            SystemProperties.getBoolean(DolbyEndpointPolicy.VOLUME_TUNING_PROPERTY, false),
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), requestedSpeakerTuning)
        val routeIdentity = device?.let { it.id to it.type }
        val revision = endpointRevision.get()
        if (selected == null || tuningCommandSupported == false) {
            val changed = appliedRoute != routeIdentity || appliedEndpointRevision != revision
            appliedTuning = null
            appliedRoute = routeIdentity
            appliedEndpointRevision = revision
            return changed
        }
        val tuning = if (selected in rejectedTunings) DolbyEndpointPolicy.fallback(selected) else selected
        if (tuning == null || tuning in rejectedTunings) return false
        if (tuning == appliedTuning && appliedRoute == routeIdentity && appliedEndpointRevision == revision) {
            return false
        }
        try {
            dolbyEffect.setSelectedTuningDevice(tuning.port, tuning.id)
            tuningCommandSupported = true
            appliedTuning = tuning
            appliedRoute = routeIdentity
            appliedEndpointRevision = revision
            dlog(TAG, "DAP endpoint acknowledged: $tuning")
            return true
        } catch (error: DolbyHalException) {
            // A dead engine or control handoff is never a capability verdict.
            if (error.status != AudioEffect.ERROR_BAD_VALUE &&
                error.status != AudioEffect.ERROR_INVALID_OPERATION) throw error
            dolbyEffect.requireControl()
            if (error.status == AudioEffect.ERROR_INVALID_OPERATION) {
                tuningCommandSupported = false
            } else {
                rejectedTunings += tuning
                if (DolbyEndpointPolicy.fallback(tuning) != null) return updateEndpointTuning()
            }
            appliedTuning = null
            Log.w(TAG, "DAP rejected optional endpoint $tuning; keeping native routing", error)
            return false
        }
    }

    /** A saved speaker choice applies only to the speaker, never a headset/voice route. */
    fun setSpeakerTuning(tuning: DolbyEndpointPolicy.SpeakerTuning) {
        require(DolbyCapabilities.speakerTuningSupported ||
            tuning == DolbyEndpointPolicy.SpeakerTuning.AUTOMATIC) {
            "Speaker tuning selection is not supported by this product"
        }
        val previous = requestedSpeakerTuning
        requestedSpeakerTuning = tuning
        appliedEndpointRevision = -1
        try {
            if (requestedEnabled && mediaMode && processingState == ProcessingState.MEDIA) {
                setCurrentProfile()
                // A rejected active-speaker request must not be saved as successful.
                val outputType = audioManager.getAudioDevicesForAttributes(mediaAttributes)
                    .singleOrNull()?.type
                if (outputType in setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)) {
                    val validIds = tuning.tuningId?.let { setOf(it) } ?: setOf(
                        "default_internal_speaker", "speaker_volume_low", "speaker_volume_high")
                    check(appliedTuning?.id in validIds) { "Speaker tuning was not acknowledged" }
                }
            }
            refreshActiveState()
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_SPEAKER_TUNING, tuning.key).apply()
        } catch (failure: RuntimeException) {
            requestedSpeakerTuning = previous
            appliedEndpointRevision = -1
            requestRestore()
            throw failure
        }
    }

    private fun checkEffect() {
        if (!audioServerAvailable) throw DolbyHalException(AudioEffect.ERROR_DEAD_OBJECT, "audioserver")
        val server = serverRevision.get()
        val old = nativeEffect
        if (old == null || old.isDead || appliedServerRevision != server) {
            runCatching { old?.close() }
            nativeEffect = null
            val effect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
            try {
                effect.setControlStatusListener { _, granted ->
                    if (granted) controlRevision.incrementAndGet()
                    requestRestore()
                }
                effect.setEnableStatusListener { _, _ -> requestRestore() }
            } catch (failure: RuntimeException) {
                runCatching { effect.close() }
                throw failure
            }
            nativeEffect = effect
            appliedServerRevision = server
            needsBootstrap = true
            tuningCommandSupported = null
            rejectedTunings.clear()
        }
        dolbyEffect.requireControl()
        val control = controlRevision.get()
        if (appliedControlRevision != control) {
            appliedControlRevision = control
            needsBootstrap = true
        }
        if (needsBootstrap) {
            // Restore intent after server death/control return, without resetting every
            // DMS profile slot just because the app has acquired a new Java handle.
            dolbyEffect.dsOn = false
            appliedTuning = null
            appliedProfileKey = null
            appliedRoute = null
            appliedEndpointRevision = -1
            needsBootstrap = false
            processingState = if (mediaMode) ProcessingState.MEDIA_RESTORE else ProcessingState.COMMUNICATION
            lastAudioStateInvalidationMs = SystemClock.elapsedRealtime()
            requestRestore()
        }
    }

    private fun setCurrentProfile() {
        // Failures propagate to the retry scheduler. A failed restore is not success.
        // The enclosing reconciliation publishes one final readback after all
        // route/profile/gate work; do not expose an intermediate snapshot here.
        selectProfile(activeProfileKey, publishState = false)
    }

    /** Reset the reused native slot before restoring a variant, so siblings never leak tuning. */
    fun selectProfile(key: String, publishState: Boolean = true) {
        val target = profiles.requireProfile(key)
        val previous = profiles.requireProfile(activeProfileKey)
        checkEffect()
        val endpointChanged = updateEndpointTuning()
        if (appliedProfileKey == key) {
            if (!endpointChanged && dolbyEffect.profile == target.base) {
                if (publishState) refreshActiveState()
                return
            }
            // Routine playback and routing notifications must not repeatedly reset the DSP.
            // A failed write may leave the native slot partially restored. Retire
            // its cache before any mutation so recovery cannot take the fast path.
            appliedProfileKey = null
            profile = target.base
            // Selecting a native base can reload OEM tuning, even for the same ID.
            // Reapply the app-owned variant after playback/route callbacks too.
            restoreSettings(target.base, target.key)
            appliedProfileKey = key
            if (publishState) refreshActiveState()
            return
        }
        try {
            profile = target.base
            dolbyEffect.resetProfileSpecificSettings(target.base)
            restoreSettings(target.base, target.key)
            appliedProfileKey = key
        } catch (error: RuntimeException) {
            try {
                profile = previous.base
                dolbyEffect.resetProfileSpecificSettings(previous.base)
                restoreSettings(previous.base, previous.key)
                appliedProfileKey = previous.key
            } catch (rollback: RuntimeException) {
                appliedProfileKey = null
                error.addSuppressed(rollback)
            }
            throw error
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DolbyConstants.PREF_PROFILE, key).apply()
        if (publishState) refreshActiveState()
    }

    fun deleteNamedProfile(key: String) {
        val target = profiles.requireProfile(key)
        require(target.custom)
        if (activeProfileKey == key) selectProfile(target.base.toString())
        profiles.delete(key)
    }

    /** A departing equalizer may finish saving after navigation; never alter another variant. */
    fun saveEqualizer(key: String, value: String) {
        EqualizerGains.parse(value)
        val target = profiles.find(key) ?: return
        if (activeProfileKey == key) setPreset(value, target.base)
        profiles.preferences(key).edit().putString(DolbyConstants.PREF_PRESET, value).apply()
        refreshActiveState()
    }

    fun setDsOnAndPersist(dsOn: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (!dsOn) {
            // Save shutdown intent before observer registration, HAL connection,
            // or any readback can fail. This is requested state, not a claim
            // that either processing gate has already acknowledged shutdown.
            requestedEnabled = false
            preferences.edit().putBoolean(DolbyConstants.PREF_ENABLE, false).apply()
            _activeState.value = _activeState.value.copy(enabled = false)
        }
        ensureInitialized()
        this.dsOn = dsOn
        if (dsOn) {
            preferences.edit().putBoolean(DolbyConstants.PREF_ENABLE, true).apply()
        }
        refreshActiveState()
    }

    fun getProfileName(): String? = profiles.find(activeProfileKey)?.name

    fun resetProfileSpecificSettings(profile: Int = profiles.requireProfile(activeProfileKey).base) {
        checkEffect()
        val key = if (profile == this.profile) activeProfileKey else profile.toString()
        // RESET may reach the vendor before a failed reply. Recovery must not
        // trust the old profile ACK and skip replaying the persisted overrides.
        appliedProfileKey = null
        dolbyEffect.resetProfileSpecificSettings(profile)
        profiles.preferences(key).edit().clear().apply()
        refreshActiveState()
    }

    fun resetAllProfiles() {
        checkEffect()
        // Retire the active-slot ACK before clearing preferences or resetting
        // any native slot, including when a later RESET fails partway through.
        appliedProfileKey = null
        // Keep variant names and named EQ presets; reset all independently saved tuning.
        for (entry in profiles.all) profiles.preferences(entry.key).edit().clear().apply()
        for (entry in profiles.builtIn) dolbyEffect.resetProfileSpecificSettings(entry.base)
        selectProfile("0")
    }

    /** Persisted overrides are authoritative; DSP readback may lag a profile change. */
    fun getSavedPreset(key: String): String = profiles.preferences(key)
        .getString(DolbyConstants.PREF_PRESET, null)
        ?.takeIf { runCatching { EqualizerGains.parse(it) }.isSuccess }
        ?: getPreset(profiles.requireProfile(key).base)

    fun getPreset(profile: Int = profiles.requireProfile(activeProfileKey).base): String {
        val gains = dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        return gains.joinToString(separator = ",").also {
            dlog(TAG, "getPreset: $it")
        }
    }

    fun setPreset(value: String, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setPreset: $value")
        checkEffect()
        val gains = EqualizerGains.parse(value)
        dolbyEffect.setDapParameter(DsParam.GEQ_BAND_GAINS, gains, profile)
    }

    fun getPresetName(): String {
        val preset = getPreset()
        val presets = context.resources.getStringArray(R.array.dolby_preset_values)
        val userPresets = context.getSharedPreferences(PREF_PRESETS, Context.MODE_PRIVATE)
        return if (presets.contains(preset)) {
            context.resources.getStringArray(R.array.dolby_preset_entries)[presets.indexOf(preset)]
        } else {
            userPresets.all.entries.firstOrNull { it.value == preset }?.key
                ?: context.getString(R.string.dolby_preset_custom)
        }
    }

    fun getHeadphoneVirtEnabled(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile).also {
            dlog(TAG, "getHeadphoneVirtEnabled: $it")
        }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile)
    }

    fun getSpeakerVirtEnabled(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile).also {
            dlog(TAG, "getSpeakerVirtEnabled: $it")
        }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile)
    }

    fun getBassEnhancerEnabled(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getBassEnhancerEnabled: $it")
        }

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setBassEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile)
    }

    fun getVolumeLevelerEnabled(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile).also {
            dlog(TAG, "getVolumeLevelerEnabled: $it")
        }

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setVolumeLevelerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile)
    }

    fun getDialogueEnhancerEnabled(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getDialogueEnhancerEnabled: $it")
        }

    fun setDialogueEnhancerEnabled(value: Boolean, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setDialogueEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value, profile)
    }

    fun getDialogueEnhancerAmount(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile).also {
            dlog(TAG, "getDialogueEnhancerAmount: $it")
        }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setDialogueEnhancerAmount: $value")
        checkEffect()
        require(value in 1..12) { "Dialogue amount outside the supported app range" }
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, profile)
    }

    fun getIeqPreset(profile: Int = profiles.requireProfile(activeProfileKey).base) =
        dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile).also {
            dlog(TAG, "getIeqPreset: $it")
        }

    fun setIeqPreset(value: Int, profile: Int = profiles.requireProfile(activeProfileKey).base) {
        dlog(TAG, "setIeqPreset: $value")
        checkEffect()
        require(value in 0..3) { "Unknown intelligent EQ preset" }
        dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, value, profile)
    }

    fun refreshActiveState() {
        if (!initialized) return
        val target = profiles.requireProfile(activeProfileKey)
        try {
            // Preferences are restore requests; only native readback describes the
            // active endpoint after routing, profile changes or vendor normalization.
            val tuning = mapOf<String, Any>(
                PREF_BASS to getBassEnhancerEnabled(target.base),
                PREF_VOLUME to (volumeLevelerSupported && getVolumeLevelerEnabled(target.base)),
                PREF_HP_VIRTUALIZER to getHeadphoneVirtEnabled(target.base),
                PREF_SPK_VIRTUALIZER to getSpeakerVirtEnabled(target.base),
                PREF_DIALOGUE to getDialogueEnhancerEnabled(target.base),
                PREF_DIALOGUE_AMOUNT to getDialogueEnhancerAmount(target.base),
                PREF_IEQ to getIeqPreset(target.base)
            )
            val runtime = DolbyRuntimeState(processingState.name, dolbyEffect.dsOn,
                dolbyEffect.enabled, dolbyEffect.hasControl(), appliedTuning?.id,
                tuningCommandSupported, appliedServerRevision,
                routedDeviceType = appliedRoute?.second,
                speakerTuning = requestedSpeakerTuning.key,
                speakerTuningSupported = DolbyCapabilities.speakerTuningSupported)
            val previous = _activeState.value.runtime
            if (runtime.phase != previous.phase || runtime.nativeEnabled != previous.nativeEnabled ||
                runtime.frameworkEnabled != previous.frameworkEnabled ||
                runtime.hasControl != previous.hasControl) {
                // One record per observed transition, not per playback callback.
                // No package/phone details and no claim about measured DSP output.
                Log.i(TAG, "DAP control state: phase=${runtime.phase}, requested=$dsOn, " +
                    "native=${runtime.nativeEnabled}, framework=${runtime.frameworkEnabled}, " +
                    "control=${runtime.hasControl}, mode=${audioManager.mode}")
            }
            _activeState.value = ActiveProfileState(target.key, target.name, target.base,
                profiles.all, dsOn, EqualizerGains.decode(getSavedPreset(target.key)), tuning, true,
                runtime = runtime)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot read active configuration", error)
            _activeState.value = _activeState.value.copy(key = target.key, name = target.name,
                base = target.base, profiles = profiles.all, loaded = false,
                error = context.getString(R.string.dolby_setting_failed))
            throw error
        }
    }

    fun updateSetting(key: String, value: Any) {
        when (key) {
            PREF_BASS -> setBassEnhancerEnabled(value as Boolean)
            PREF_VOLUME -> setVolumeLevelerEnabled(value as Boolean)
            PREF_HP_VIRTUALIZER -> setHeadphoneVirtEnabled(value as Boolean)
            PREF_SPK_VIRTUALIZER -> setSpeakerVirtEnabled(value as Boolean)
            PREF_DIALOGUE -> setDialogueEnhancerEnabled(value as Boolean)
            PREF_DIALOGUE_AMOUNT -> setDialogueEnhancerAmount(value as Int)
            PREF_IEQ -> setIeqPreset(value as Int)
            else -> error("Unsupported setting")
        }
        refreshActiveState()
        check(activeState.value.loaded && activeState.value.settings[key] == value) {
            "Dolby did not confirm the requested setting: $key"
        }
        val editor = profiles.preferences(activeProfileKey).edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> if (key == PREF_IEQ) editor.putString(key, value.toString()) else editor.putInt(key, value)
        }
        // Persistence does not change the acknowledged native values. Keep the
        // checked snapshot rather than repeating the same full HAL query.
        editor.apply()
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val MEDIA_RESTORE_SETTLE_MS = 500L
        private const val PREF_SPEAKER_TUNING = "speaker_tuning"
        private const val PREF_PRESETS = "presets"
        private const val PREF_KEY_PRESETS_MIGRATED = "presets_migrated"

    }
}