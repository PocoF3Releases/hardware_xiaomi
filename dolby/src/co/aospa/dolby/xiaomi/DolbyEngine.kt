/*
 * Copyright (C) 2023-25 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
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
    val activeState = _activeState.asStateFlow()
    private var initialized = false

    val activeProfileKey: String
        get() = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(DolbyConstants.PREF_PROFILE, "0")
            ?.takeIf { profiles.find(it) != null } ?: "0"

    private var requestedEnabled = false
    private var appliedProfileKey: String? = null
    private var dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val handler = Handler(context.mainLooper)
    private val volumeLevelerSupported =
        context.getResources().getBoolean(R.bool.dolby_volume_leveler_supported)

    // Restore current profile on every media session
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) {
            val isPlaying = configs.orEmpty().any {
                it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
            }
            dlog(TAG, "onPlaybackConfigChanged: isPlaying=$isPlaying")
            if (isPlaying)
                requestRestore()
        }
    }

    // Restore current profile on audio device change
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            requestRestore()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            requestRestore()
        }
    }

    // Mode changes need their own callback: starting VoIP need not add/remove a device.
    // Native effect access stays on the controller's serialized transaction path.
    private val modeChangedListener = AudioManager.OnModeChangedListener { mode ->
        dlog(TAG, "onModeChanged: $mode")
        requestRestore()
    }

    private val mediaMode: Boolean
        get() = when (audioManager.mode) {
            AudioManager.MODE_NORMAL, AudioManager.MODE_RINGTONE -> true
            else -> false // Calls, VoIP, call screening and redirected call modes.
        }

    private fun applyEnabledState() {
        checkEffect()
        val enabled = requestedEnabled && mediaMode
        if (dolbyEffect.dsOn != enabled || dolbyEffect.enabled != enabled) {
            dlog(TAG, "applyEnabledState: requested=$requestedEnabled mode=${audioManager.mode} enabled=$enabled")
            dolbyEffect.dsOn = enabled
            appliedProfileKey = null
        }
    }

    fun restoreForAudioState() {
        applyEnabledState()
        if (requestedEnabled && mediaMode) setCurrentProfile()
        refreshActiveState()
    }

    private var registerCallbacks = false
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setRegisterCallbacks($value)")
            if (value) {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
                audioManager.addOnModeChangedListener(context.mainExecutor, modeChangedListener)
            } else {
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
                audioManager.removeOnModeChangedListener(modeChangedListener)
            }
        }

    // Expose the user's choice, not the temporary call-mode bypass, to UI/persistence.
    var dsOn: Boolean
        get() = requestedEnabled
        set(value) {
            dlog(TAG, "setDsOn: $value")
            requestedEnabled = value
            applyEnabledState()
            registerCallbacks = value
            if (value && mediaMode) setCurrentProfile()
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
        // Restore our main settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        dsOn = prefs.getBoolean(DolbyConstants.PREF_ENABLE, true)

        context.resources.getStringArray(R.array.dolby_profile_values)
                .map { it.toInt() }
                .forEach { profile ->
                    // Reset dolby first to prevent it from loading bad settings
                    dolbyEffect.resetProfileSpecificSettings(profile)
                    // Now restore our profile-specific settings
                    restoreSettings(profile)
                }

        // Base-slot initialization may have replaced a selected variant.
        appliedProfileKey = null
        setCurrentProfile()

        initialized = true
        profiles.onChanged = { refreshActiveState() }
        refreshActiveState()
        dlog(TAG, "initialized")
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

    private fun checkEffect() {
        if (!dolbyEffect.hasControl()) {
            Log.w(TAG, "lost control, recreating effect")
            dolbyEffect.release()
            dolbyEffect = DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0)
            appliedProfileKey = null
        }
    }

    private fun setCurrentProfile() {
        try {
            selectProfile(activeProfileKey)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot restore profile after audio callback", error)
            refreshActiveState()
        }
    }

    /** Reset the reused native slot before restoring a variant, so siblings never leak tuning. */
    fun selectProfile(key: String) {
        val target = profiles.requireProfile(key)
        val previous = profiles.requireProfile(activeProfileKey)
        checkEffect()
        if (appliedProfileKey == key) {
            // Routine playback and routing notifications must not repeatedly reset the DSP.
            profile = target.base
            // Selecting a native base can reload OEM tuning, even for the same ID.
            // Reapply the app-owned variant after playback/route callbacks too.
            restoreSettings(target.base, target.key)
            refreshActiveState()
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
        refreshActiveState()
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
        this.dsOn = dsOn
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(DolbyConstants.PREF_ENABLE, dsOn)
            .apply()
        refreshActiveState()
    }

    fun getProfileName(): String? = profiles.find(activeProfileKey)?.name

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        checkEffect()
        val key = if (profile == this.profile) activeProfileKey else profile.toString()
        dolbyEffect.resetProfileSpecificSettings(profile)
        profiles.preferences(key).edit().clear().apply()
        refreshActiveState()
    }

    fun resetAllProfiles() {
        checkEffect()
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

    fun getPreset(profile: Int = this.profile): String {
        val gains = dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        return gains.joinToString(separator = ",").also {
            dlog(TAG, "getPreset: $it")
        }
    }

    fun setPreset(value: String, profile: Int = this.profile) {
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

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile).also {
            dlog(TAG, "getHeadphoneVirtEnabled: $it")
        }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile)
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile).also {
            dlog(TAG, "getSpeakerVirtEnabled: $it")
        }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile)
    }

    fun getBassEnhancerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getBassEnhancerEnabled: $it")
        }

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setBassEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile)
    }

    fun getVolumeLevelerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile).also {
            dlog(TAG, "getVolumeLevelerEnabled: $it")
        }

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setVolumeLevelerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile)
    }

    fun getDialogueEnhancerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getDialogueEnhancerEnabled: $it")
        }

    fun setDialogueEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value, profile)
    }

    fun getDialogueEnhancerAmount(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile).also {
            dlog(TAG, "getDialogueEnhancerAmount: $it")
        }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerAmount: $value")
        checkEffect()
        require(value in 1..12) { "Dialogue amount outside the supported app range" }
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, profile)
    }

    fun getIeqPreset(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile).also {
            dlog(TAG, "getIeqPreset: $it")
        }

    fun setIeqPreset(value: Int, profile: Int = this.profile) {
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
            _activeState.value = ActiveProfileState(target.key, target.name, target.base,
                profiles.all, dsOn, EqualizerGains.decode(getSavedPreset(target.key)), tuning, true)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Cannot read active configuration", error)
            _activeState.value = _activeState.value.copy(key = target.key, name = target.name,
                base = target.base, profiles = profiles.all, loaded = false,
                error = context.getString(R.string.dolby_setting_failed))
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
        editor.apply()
        refreshActiveState()
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val PREF_PRESETS = "presets"
        private const val PREF_KEY_PRESETS_MIGRATED = "presets_migrated"

    }
}
