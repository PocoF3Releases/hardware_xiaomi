/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.os.SystemProperties

/** Product capabilities, not user preferences or proof of successful DSP processing. */
internal object DolbyCapabilities {
    const val GAME_EFFECT_PROPERTY = "ro.vendor.audio.game.effect"
    const val GAME_EFFECT_BACKEND_PROPERTY = "ro.vendor.audio.game.effect.backend"

    val gameBackend: GameEffectBackend
        get() = if (SystemProperties.getBoolean(GAME_EFFECT_PROPERTY, false)) {
            GameEffectBackend.fromKey(SystemProperties.get(GAME_EFFECT_BACKEND_PROPERTY, "none"))
        } else GameEffectBackend.NONE

    const val SPATIALIZER_PROPERTY = "ro.vendor.audio.dolby.spatializer.support"
    const val SPEAKER_TUNING_PROPERTY = "ro.vendor.audio.dolby.speaker_tuning.support"

    // The audited legacy Qualcomm stack has no platform spatial-audio integration.
    // Products must opt in only with a matching spatializer and verified endpoint IDs.
    val spatializerSupported: Boolean
        get() = SystemProperties.getBoolean(SPATIALIZER_PROPERTY, false)
    val speakerTuningSupported: Boolean
        get() = SystemProperties.getBoolean(SPEAKER_TUNING_PROPERTY, false)
}
