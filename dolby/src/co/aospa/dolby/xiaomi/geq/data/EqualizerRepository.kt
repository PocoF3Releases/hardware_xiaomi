/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PRESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class EqualizerRepository(
    private val context: Context
) {

    private val dolbyController by lazy { DolbyController.getInstance(context) }

    // Preset is saved as a string of comma separated gains in SharedPreferences
    // and is unique to each profile ID
    internal val activeState = dolbyController.activeState

    private val presetsSharedPrefs by lazy {
        context.getSharedPreferences(
            "presets",
            Context.MODE_PRIVATE
        )
    }

    val builtInPresets: List<Preset> by lazy {
        val names = context.resources.getStringArray(
            R.array.dolby_preset_entries
        )
        val presets = context.resources.getStringArray(
            R.array.dolby_preset_values
        )
        List(names.size) { index ->
            Preset(
                name = names[index],
                bandGains = deserializeGains(presets[index]),
            )
        }
    }

    val defaultPreset by lazy { builtInPresets[0] } // Flat

    // User defined presets are stored in a SharedPreferences as
    // key - preset name
    // value - comma separated string of gains
    val userPresets: Flow<List<Preset>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            dlog(TAG, "presetsSharedPrefs changed")
            trySend(
                presetsSharedPrefs.all.map { (key, value) ->
                    Preset(
                        name = key,
                        bandGains = deserializeGains(value.toString()),
                        isUserDefined = true
                    )
                }
            )
        }

        presetsSharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        dlog(TAG, "presetsSharedPrefs registered listener")
        // trigger an initial emission
        listener.onSharedPreferenceChanged(presetsSharedPrefs, null)

        awaitClose {
            presetsSharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            dlog(TAG, "presetsSharedPrefs unregistered listener")
        }
    }

    fun beginEdit() = dolbyController.beginEqualizerEdit()

    suspend fun editBandGains(key: String, transform: (List<BandGain>) -> List<BandGain>) =
        dolbyController.editEqualizer(key, transform)

    suspend fun setBandGains(key: String, bandGains: List<BandGain>) = withContext(Dispatchers.IO) {
        dolbyController.saveEqualizer(key, serializeGains(bandGains))
    }

    suspend fun addPreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "addPreset($preset)")
        presetsSharedPrefs.edit()
            .putString(preset.name, serializeGains(preset.bandGains))
            .apply()
    }

    suspend fun removePreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "removePreset($preset)")
        presetsSharedPrefs.edit()
            .remove(preset.name)
            .apply()
    }

    private companion object {
        const val TAG = "EqRepository"

        fun deserializeGains(bandGains: String): List<BandGain> =
            try {
                EqualizerGains.decode(bandGains)
            } catch (exception: IllegalArgumentException) {
                Log.e(TAG, "Failed to parse preset", exception)
                List(EqualizerGains.BAND_COUNT) { BandGain(it + 1, 0) }
            }

        fun serializeGains(bandGains: List<BandGain>): String =
            EqualizerGains.encode(bandGains)
    }
}
