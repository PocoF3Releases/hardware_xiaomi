/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

/** Pure selection policy reconstructed from stock DeviceId / DolbyEffectController. */
internal object DolbyEndpointPolicy {
    const val VOLUME_TUNING_PROPERTY = "vendor.audio.dolby.control.tunning.by.volume.support"
    enum class Route { SPEAKER, WIRED, BLUETOOTH_A2DP, USB, NATIVE }
    data class Tuning(val port: Int, val id: String)
    enum class SpeakerTuning(val key: String, val tuningId: String?) {
        AUTOMATIC("automatic", null),
        PORTRAIT("portrait", "speaker_portrait"),
        LANDSCAPE("landscape", "speaker_landscape");

        companion object {
            fun fromKey(key: String?): SpeakerTuning =
                entries.firstOrNull { it.key == key } ?: AUTOMATIC
        }
    }

    fun select(
        route: Route,
        spatializerActive: Boolean,
        volumeTuning: Boolean,
        volume: Int,
        maxVolume: Int,
        speakerTuning: SpeakerTuning = SpeakerTuning.AUTOMATIC
    ): Tuning? = when (route) {
        // Rotation-specific speaker spatializer selection is not reconstructed from
        // the incomplete decompilation. Do not overwrite the native spatializer endpoint.
        Route.SPEAKER -> if (spatializerActive) null else {
            val id = speakerTuning.tuningId ?: if (!volumeTuning || maxVolume <= 0 || volume < 0) {
                "default_internal_speaker"
            } else if (volume > maxVolume.toLong() * 10 / 15) {
                "speaker_volume_high"
            } else {
                "speaker_volume_low"
            }
            Tuning(0, id)
        }
        Route.WIRED -> Tuning(3, if (spatializerActive) "headphone_spatializer" else "default_headphone")
        Route.BLUETOOTH_A2DP -> Tuning(4, if (spatializerActive) "bluetooth_spatializer" else "default_bluetooth")
        Route.USB -> Tuning(5, if (spatializerActive) "headphone_spatializer" else "default_usb")
        // Passthrough, remote submix, SCO/communication and unknown/new transports
        // belong to native route policy. Never substitute a speaker tuning ID.
        Route.NATIVE -> null
    }

    fun fallback(tuning: Tuning): Tuning? = when (tuning.id) {
        "speaker_volume_high", "speaker_volume_low" -> Tuning(0, "default_internal_speaker")
        else -> null // Do not replace spatializer tuning with an unrelated media endpoint.
    }
}
