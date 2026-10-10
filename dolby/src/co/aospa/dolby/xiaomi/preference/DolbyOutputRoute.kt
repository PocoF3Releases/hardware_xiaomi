/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.preference

import android.media.AudioDeviceInfo

object DolbyOutputRoute {
    data class Visibility(val speaker: Boolean, val headphones: Boolean)

    fun visibility(types: List<Int>): Visibility = Visibility(
        speaker = types.any { it == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            it == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE },
        headphones = types.any { it in headphoneTypes }
    )

    // A2DP always uses headphone virtualization, including Bluetooth loudspeakers.
    // Speaker virtualization is reserved for the device built-in speakers.
    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID
    )
}
