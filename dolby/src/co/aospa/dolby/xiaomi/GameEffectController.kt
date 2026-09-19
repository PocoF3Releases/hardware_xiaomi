/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.app.ActivityTaskManager
import android.app.TaskStackListener
import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.media.AudioSystem
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import android.view.Display
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class GameEffectTuning(
    val lowFrequency: Int,
    val vocal: Int,
    val footstep: Int,
    val soundField: Int
) {
    init {
        require(listOf(lowFrequency, vocal, footstep, soundField).all { it in 0..100 }) {
            "Game effect values must be in the HyperOS 0..100 range"
        }
    }

    fun encode(): String = listOf(lowFrequency, vocal, footstep, soundField).joinToString(",")

    companion object {
        fun decode(value: String?): GameEffectTuning? {
            val parts = value?.split(",") ?: return null
            if (parts.size != 4) return null
            val values = parts.map { it.toIntOrNull() ?: return null }
            if (values.any { it !in 0..100 }) return null
            return GameEffectTuning(values[0], values[1], values[2], values[3])
        }
    }
}

internal data class GameEffectState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val games: Set<String> = emptySet(),
    val activePackage: String? = null,
    val error: Boolean = false
)

internal class GameEffectController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)!!
    private val serverMonitor = DolbyAudioServerMonitor.get(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val supported = SystemProperties.getBoolean(PROP_GAME_EFFECT, false)

    private val worker = HandlerThread("XiaomiGameEffect").apply { start() }
    private val handler = Handler(worker.looper)
    private val started = AtomicBoolean(false)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private val _state = MutableStateFlow(GameEffectState())
    val state = _state.asStateFlow()

    private var appliedPackage: String? = null
    private var appliedTuning: GameEffectTuning? = null
    private var pendingClear = false
    private var communicationActive: Boolean? = null
    private val retry = DolbyRetryPolicy()
    private val reconcileRevision = AtomicLong()
    private var handledRevision = -1L

    private val taskListener = object : TaskStackListener() {
        override fun onTaskStackChanged() = scheduleReconcile(SETTLE_DELAY_MS)
        override fun onTaskMovedToFront(taskInfo: android.app.ActivityManager.RunningTaskInfo) =
            scheduleReconcile(SETTLE_DELAY_MS)
        override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {
            if (focused) scheduleReconcile(SETTLE_DELAY_MS)
        }
    }

    private val routeListener = AudioManager.OnDevicesForAttributesChangedListener { _, _ ->
        invalidateTuning(SETTLE_DELAY_MS)
    }

    private val modeListener = AudioManager.OnModeChangedListener { _ ->
        // The HAL may rebuild its processing path without changing package or tuning values.
        invalidateTuning(SETTLE_DELAY_MS)
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) =
            reconcileCommunicationState()
    }
    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) =
            reconcileCommunicationState()
    }

    private fun reconcileCommunicationState() {
        handler.post {
            try {
                val active = audioManager.isDolbyCommunicationActive()
                if (communicationActive != active) {
                    communicationActive = active
                    appliedTuning = null
                    scheduleReconcile(SETTLE_DELAY_MS)
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "Cannot query communication lifecycle", error)
                publish(error = true)
            }
        }
    }

    private val spatialAudioObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            invalidateTuning(0)
        }
    }

    private fun invalidateTuning(delayMs: Long) {
        handler.post {
            appliedTuning = null
            scheduleReconcile(delayMs)
        }
    }

    private val audioServerCallback = object : AudioManager.AudioServerStateCallback() {
        override fun onAudioServerDown() {
            handler.post {
                appliedPackage = null
                appliedTuning = null
                pendingClear = false
                handler.removeCallbacks(reconcileRunnable)
                publish(error = false)
            }
        }

        override fun onAudioServerUp() {
            invalidateTuning(AUDIO_SERVER_RECOVERY_DELAY_MS)
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        handler.post {
            publish(error = false)
            if (!supported) return@post

            try {
                ActivityTaskManager.getService().registerTaskStackListener(taskListener)
            } catch (error: Exception) {
                started.set(false)
                Log.e(TAG, "Cannot register Android 17 task listener", error)
                publish(error = true)
                return@post
            }

            try {
                audioManager.addOnDevicesForAttributesChangedListener(
                    mediaAttributes, appContext.mainExecutor, routeListener)
            } catch (error: RuntimeException) {
                started.set(false)
                Log.e(TAG, "Cannot monitor routed media devices", error)
                runCatching {
                    ActivityTaskManager.getService().unregisterTaskStackListener(taskListener)
                }
                publish(error = true)
                return@post
            }

            var mode = false
            var spatial = false
            var playback = false
            var recording = false
            try {
                audioManager.addOnModeChangedListener(appContext.mainExecutor, modeListener)
                mode = true
                if (DolbyCapabilities.spatializerSupported) {
                    appContext.contentResolver.registerContentObserver(
                        Settings.Global.getUriFor(SPATIAL_AUDIO_FEATURE_ENABLE), false, spatialAudioObserver)
                    spatial = true
                }
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                playback = true
                audioManager.registerAudioRecordingCallback(recordingCallback, handler)
                recording = true
                serverMonitor.subscribe(audioServerCallback)
            } catch (error: RuntimeException) {
                if (recording) runCatching { audioManager.unregisterAudioRecordingCallback(recordingCallback) }
                if (playback) runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
                if (spatial) runCatching { appContext.contentResolver.unregisterContentObserver(spatialAudioObserver) }
                if (mode) runCatching { audioManager.removeOnModeChangedListener(modeListener) }
                runCatching { audioManager.removeOnDevicesForAttributesChangedListener(routeListener) }
                runCatching { ActivityTaskManager.getService().unregisterTaskStackListener(taskListener) }
                started.set(false)
                Log.w(TAG, "Cannot monitor game-audio lifecycle", error)
                publish(error = true)
                return@post
            }

            scheduleReconcile(SETTLE_DELAY_MS)
        }
    }

    fun stockGames(): Set<String> = STOCK_TUNINGS.keys

    fun games(): Set<String> =
        prefs.getStringSet(KEY_GAMES, null)?.toSet() ?: STOCK_TUNINGS.keys

    fun isEnabled(): Boolean = supported && prefs.getBoolean(KEY_ENABLED, true)

    fun tuning(packageName: String): GameEffectTuning =
        GameEffectTuning.decode(prefs.getString(tuningKey(packageName), null))
            ?: STOCK_TUNINGS[packageName]
            ?: DEFAULT_GAME_TUNING

    fun defaultTuning(packageName: String): GameEffectTuning =
        STOCK_TUNINGS[packageName] ?: DEFAULT_GAME_TUNING

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        handler.post {
            if (!supported) {
                publish(error = false)
                return@post
            }
            if (!value) {
                handler.removeCallbacks(reconcileRunnable)
                retry.reset()
                clearEffect(force = true)
            } else {
                scheduleReconcile(0)
            }
            publish(error = false)
        }
    }

    fun setGameEnabled(packageName: String, value: Boolean) {
        require(PACKAGE_PATTERN.matches(packageName)) { "Invalid package name" }
        val games = games().toMutableSet()
        if (value) games.add(packageName) else games.remove(packageName)
        prefs.edit().putStringSet(KEY_GAMES, games).apply()
        scheduleReconcile(0)
        handler.post { publish(error = false) }
    }

    fun setTuning(packageName: String, tuning: GameEffectTuning) {
        require(PACKAGE_PATTERN.matches(packageName)) { "Invalid package name" }
        prefs.edit().putString(tuningKey(packageName), tuning.encode()).apply()
        scheduleReconcile(0)
        handler.post { publish(error = false) }
    }

    fun resetTuning(packageName: String) {
        prefs.edit().remove(tuningKey(packageName)).apply()
        scheduleReconcile(0)
        handler.post { publish(error = false) }
    }

    private fun scheduleReconcile(delayMs: Long, newEvent: Boolean = true) {
        if (!supported) return
        if (newEvent) reconcileRevision.incrementAndGet()
        handler.removeCallbacks(reconcileRunnable)
        handler.postDelayed(reconcileRunnable, delayMs)
    }

    private val reconcileRunnable = Runnable {
        val revision = reconcileRevision.get()
        if (handledRevision != revision) {
            handledRevision = revision
            retry.reset()
        }
        val foreground = foregroundPackage()
        val target = foreground?.takeIf {
            isEnabled() && it in games() && isSupportedRoute() && !isSpatialAudioEnabled()
        }
        if (target == null) {
            clearEffect(force = appliedPackage != null)
            publish(error = false)
            return@Runnable
        }

        val tuning = tuning(target)
        if (appliedPackage == target && appliedTuning == tuning) {
            publish(error = false)
            return@Runnable
        }

        try {
            applyTuning(tuning)
            appliedPackage = target
            appliedTuning = tuning
            pendingClear = false
            retry.reset()
            publish(error = false)
            Log.i(TAG, "Applied HyperOS game audio for $target: $tuning")
        } catch (error: RuntimeException) {
            Log.e(TAG, "Cannot apply HyperOS game audio for $target", error)
            clearEffect(force = true, resetRetryOnSuccess = false)
            if (!pendingClear) retry.nextDelay()?.let { scheduleReconcile(it, newEvent = false) }
            publish(error = true)
        }
    }

    private fun foregroundPackage(): String? = try {
        ActivityTaskManager.getService()
            .getTasks(1, false, false, Display.DEFAULT_DISPLAY)
            .firstOrNull()
            ?.let { it.topActivity ?: it.baseActivity }
            ?.packageName
    } catch (error: Exception) {
        Log.w(TAG, "Cannot resolve foreground task", error)
        null
    }

    private fun isSpatialAudioEnabled(): Boolean =
        DolbyCapabilities.spatializerSupported && Settings.Global.getInt(
            appContext.contentResolver, SPATIAL_AUDIO_FEATURE_ENABLE, 0
        ) == 1

    private fun isSupportedRoute(): Boolean {
        val devices = try {
            audioManager.getAudioDevicesForAttributes(mediaAttributes)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot query routed media output", error)
            return false
        }
        // FPSOP is a global HAL parameter set. Do not guess a single endpoint for duplicated routes.
        return devices.singleOrNull()?.type in SUPPORTED_DEVICE_TYPES
    }

    private fun applyTuning(tuning: GameEffectTuning) {
        val values = intArrayOf(
            tuning.lowFrequency, tuning.vocal, tuning.footstep, tuning.soundField)
        try {
            PARAMETER_NAMES.forEachIndexed { index, key ->
                writeParameter(key, values[index])
            }
        } catch (failure: RuntimeException) {
            try {
                clearParameters()
            } catch (cleanup: RuntimeException) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    private fun clearEffect(force: Boolean, resetRetryOnSuccess: Boolean = true) {
        if (!force && !pendingClear && appliedPackage == null && appliedTuning == null) return
        try {
            clearParameters()
            pendingClear = false
            if (resetRetryOnSuccess) retry.reset()
        } catch (error: RuntimeException) {
            pendingClear = true
            // Keep the dirty state until a complete four-parameter clear is acknowledged.
            // The retry re-reads current foreground/route/enable state, never an old snapshot.
            retry.nextDelay()?.let { scheduleReconcile(it, newEvent = false) }
            Log.w(TAG, "Cannot clear all HyperOS game-audio parameters", error)
        } finally {
            appliedPackage = null
            appliedTuning = null
        }
    }

    private fun clearParameters() {
        var failure: RuntimeException? = null
        for (key in PARAMETER_NAMES) {
            try {
                writeParameter(key, 0)
            } catch (error: RuntimeException) {
                val first = failure
                if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun writeParameter(key: String, value: Int) {
        require(value in 0..100)
        val command = "AURISYS_SET_PARAM,HAL,ALL,FPSOP,KEY_VALUE,$key,$value=SET"
        val status = AudioSystem.setParameters(command)
        check(status == AudioSystem.AUDIO_STATUS_OK) {
            "AudioSystem rejected $key=$value with status $status"
        }
    }

    private fun publish(error: Boolean) {
        _state.value = GameEffectState(
            supported = supported,
            enabled = isEnabled(),
            games = games(),
            activePackage = appliedPackage,
            error = error || pendingClear
        )
    }

    private fun tuningKey(packageName: String) = "tuning_$packageName"

    companion object {
        private const val TAG = "XiaomiGameEffect"
        private const val PROP_GAME_EFFECT = "ro.vendor.audio.game.effect"
        private const val PREFS = "game_effect"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_GAMES = "games"
        private const val SPATIAL_AUDIO_FEATURE_ENABLE = "spatial_audio_feature_enable"
        private const val SETTLE_DELAY_MS = 2_000L
        private const val AUDIO_SERVER_RECOVERY_DELAY_MS = 1_000L

        private val PACKAGE_PATTERN =
            Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        private val PARAMETER_NAMES = arrayOf(
            "FpsOpLowFreqBoost",
            "FpsOpVocalBoost",
            "FpsOpFootStepBoost",
            "FpsOpSoundFieldBoost"
        )
        private val SUPPORTED_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )

        private val DEFAULT_GAME_TUNING = GameEffectTuning(100, 0, 100, 100)
        private val STOCK_TUNINGS = linkedMapOf(
            "com.tencent.tmgp.pubgmhd" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.tmgp.cf" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.mf.uam" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.toaa" to GameEffectTuning(100, 0, 100, 100),
            "com.tencent.tmgp.cod" to GameEffectTuning(100, 0, 100, 100),
            "com.miHoYo.ys.mi" to GameEffectTuning(100, 100, 0, 0),
            "com.tencent.tmgp.sgame" to GameEffectTuning(100, 100, 0, 0)
        )

        @Volatile private var instance: GameEffectController? = null

        fun getInstance(context: Context): GameEffectController =
            instance ?: synchronized(this) {
                instance ?: GameEffectController(context).also { instance = it }
            }
    }
}
