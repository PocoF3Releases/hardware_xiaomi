/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants
import co.aospa.dolby.xiaomi.profiles.DolbyProfiles

class DolbyPreferenceStore(
    private val context: Context
) : PreferenceDataStore() {

    var suppressWrites = false

    private val defaultSharedPrefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var profileSharedPrefs: SharedPreferences =
        context.getSharedPreferences("profile_0", Context.MODE_PRIVATE)

    var profile = "0"
        set(value) {
            profileSharedPrefs = DolbyProfiles(context).preferences(value)
            field = value
        }

    private fun getSharedPreferences(key: String) =
        if (DolbyConstants.PROFILE_SPECIFIC_PREFS.contains(key)) {
            profileSharedPrefs
        } else {
            defaultSharedPrefs
        }

    override fun putBoolean(key: String, value: Boolean) {
        if (!suppressWrites) {
            getSharedPreferences(key).edit().putBoolean(key, value).apply()
        }
    }

    override fun getBoolean(key: String, defValue: Boolean) =
        getSharedPreferences(key).getBoolean(key, defValue)

    override fun putInt(key: String, value: Int) {
        if (!suppressWrites) {
            getSharedPreferences(key).edit().putInt(key, value).apply()
        }
    }

    override fun getInt(key: String, defValue: Int) =
        getSharedPreferences(key).getInt(key, defValue)

    override fun putString(key: String, value: String?) {
        if (!suppressWrites) {
            getSharedPreferences(key).edit().putString(key, value).apply()
        }
    }

    override fun getString(key: String, defValue: String?) =
        getSharedPreferences(key).getString(key, defValue)
}
