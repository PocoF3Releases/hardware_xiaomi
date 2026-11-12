/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.util.Log

class DolbyConstants {

    enum class DsParam(val id: Int, val length: Int = 1) {
        HEADPHONE_VIRTUALIZER(101),
        SPEAKER_VIRTUALIZER(102),
        VOLUME_LEVELER_ENABLE(103),
        IEQ_PRESET(104),
        DIALOGUE_ENHANCER_ENABLE(105),
        GEQ_ENABLE(106),
        IEQ_AMOUNT(107),
        DIALOGUE_ENHANCER_AMOUNT(108),
        DIALOGUE_ENHANCER_DUCKING(109),
        GEQ_BAND_GAINS(110, 20),
        BASS_ENHANCER_ENABLE(111),
        VIRTUAL_BASS_ENABLE(112),
        HEADPHONE_VIRTUALIZER_DISTANCE(113),
        REVERB_SUPPRESSION_ENABLE(114),
        REVERB_SUPPRESSION_AMOUNT(115),
        VOLUME_LEVELER_AMOUNT(116);

        override fun toString(): String {
            return "${name}(${id})"
        }
    }

    companion object {
        const val TAG = "XiaomiDolby"
        const val PREF_ENABLE = "dolby_enable"
        const val PREF_PROFILE = "dolby_profile"
        const val PREF_PRESET = "dolby_preset"
        const val PREF_IEQ = "dolby_ieq"
        const val PREF_HP_VIRTUALIZER = "dolby_virtualizer"
        const val PREF_SPK_VIRTUALIZER = "dolby_spk_virtualizer"
        const val PREF_DIALOGUE = "dolby_dialogue_enabled"
        const val PREF_DIALOGUE_AMOUNT = "dolby_dialogue_amount"
        const val PREF_BASS = "dolby_bass"
        const val PREF_VOLUME = "dolby_volume"
        const val PREF_VIRTUAL_BASS = "dolby_virtual_bass"
        const val PREF_REVERB = "dolby_reverb_suppression"
        const val PREF_IEQ_AMOUNT = "dolby_ieq_amount"
        const val PREF_DIALOGUE_DUCKING = "dolby_dialogue_ducking"
        const val PREF_HP_DISTANCE = "dolby_hp_virtualizer_distance"
        const val PREF_GEQ = "dolby_geq_enabled"
        const val PREF_REVERB_AMOUNT = "dolby_reverb_amount"
        const val PREF_VOLUME_AMOUNT = "dolby_volume_amount"

        // Clamps of the matching dap_cpdp_*_set functions in the shipped libswdap
        // and the QDSP capi_v2_dap_cpdp module.
        val IEQ_AMOUNT_RANGE = 0..16
        // Alioth DMS ParameterAdapter and the matching DAP setter both clamp 0..16.
        val DIALOGUE_AMOUNT_RANGE = 0..16
        // The QDSP clamp is 0..16, but the DAX service keeps values above 9 at 9.
        val REVERB_AMOUNT_RANGE = 0..9
        val VOLUME_AMOUNT_RANGE = 0..10
        val DIALOGUE_DUCKING_RANGE = 0..16
        val HP_DISTANCE_RANGE = 4..64
        const val PREF_RESET = "dolby_reset"

        val PROFILE_SPECIFIC_PREFS = setOf(
            PREF_PRESET,
            PREF_IEQ,
            PREF_HP_VIRTUALIZER,
            PREF_SPK_VIRTUALIZER,
            PREF_DIALOGUE,
            PREF_DIALOGUE_AMOUNT,
            PREF_BASS,
            PREF_VOLUME,
            PREF_VIRTUAL_BASS,
            PREF_REVERB,
            PREF_IEQ_AMOUNT,
            PREF_DIALOGUE_DUCKING,
            PREF_HP_DISTANCE,
            PREF_GEQ,
            PREF_REVERB_AMOUNT,
            PREF_VOLUME_AMOUNT
        )

        fun dlog(tag: String, msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG) || Log.isLoggable(tag, Log.DEBUG)) {
                Log.d("$TAG-$tag", msg)
            }
        }
    }
}
