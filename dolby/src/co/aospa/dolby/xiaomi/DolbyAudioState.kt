/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioPlaybackConfiguration
import android.media.MediaRecorder

/** Query live state inside each owner's serialized transaction; callback lists are invalidations. */
internal fun AudioManager.isDolbyCommunicationActive(): Boolean {
    val currentMode = mode
    if (currentMode != AudioManager.MODE_NORMAL && currentMode != AudioManager.MODE_RINGTONE) return true
    if (activePlaybackConfigurations.any {
            it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED &&
                it.audioAttributes.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
        }) return true
    return activeRecordingConfigurations.any {
        !it.isClientSilenced && it.clientAudioSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION
    }
}
