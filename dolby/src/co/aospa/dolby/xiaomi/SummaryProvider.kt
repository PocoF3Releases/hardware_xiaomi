/*
 * Copyright (C) 2019 The Android Open Source Project
 *           (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY

private const val KEY_DOLBY = "dolby"

/** Provide preference summary for injected items. */
class SummaryProvider : ContentProvider() {

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?
    ): Bundle? {
        val summary = when (method) {
            KEY_DOLBY -> getDolbySummary()
            else -> return null
        }
        return Bundle().apply {
            putString(META_DATA_PREFERENCE_SUMMARY, summary)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    private fun getDolbySummary(): String {
        val context = requireNotNull(context)
        val controller = DolbyController.getInstance(context)
        val state = controller.activeState.value
        // Until the engine has published its first snapshot (or after a failed
        // transaction) the live state holds defaults. Settings shows the saved
        // user choice instead of a transient "Off" or an empty profile name.
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = if (state.loaded) state.enabled
            else prefs.getBoolean(DolbyConstants.PREF_ENABLE, true)
        if (!enabled) {
            return context.getString(R.string.dolby_off)
        }
        val name = state.name.takeIf { state.loaded && it.isNotEmpty() }
            ?: controller.profiles.find(prefs.getString(DolbyConstants.PREF_PROFILE, "0") ?: "0")?.name
        return name?.takeIf { it.isNotEmpty() }?.let {
            context.getString(R.string.dolby_on_with_profile, it)
        } ?: context.getString(R.string.dolby_on)
    }

}
