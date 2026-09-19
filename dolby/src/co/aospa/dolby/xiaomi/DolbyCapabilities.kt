/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.os.SystemProperties

/** Product capabilities, not user preferences or proof of successful DSP processing. */
internal object DolbyCapabilities {
    const val SPATIALIZER_PROPERTY = "ro.vendor.audio.dolby.spatializer.support"
    const val SPEAKER_TUNING_PROPERTY = "ro.vendor.audio.dolby.speaker_tuning.support"

    // Alioth's Audio HAL 6.0 has no platform spatial-audio integration. Products
    // must opt in only with a matching spatializer and verified endpoint IDs.
    val spatializerSupported: Boolean
        get() = SystemProperties.getBoolean(SPATIALIZER_PROPERTY, false)
    val speakerTuningSupported: Boolean
        get() = SystemProperties.getBoolean(SPEAKER_TUNING_PROPERTY, false)
}
