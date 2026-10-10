/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.profiles

import android.content.Context
import android.content.SharedPreferences
import co.aospa.dolby.xiaomi.DolbyConstants
import co.aospa.dolby.xiaomi.R
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DolbyProfile(val key: String, val name: String, val base: Int, val custom: Boolean)

/** App-owned variants use native base IDs only at the engine boundary. */
internal class DolbyProfiles(private val context: Context) {
    private val catalog = context.getSharedPreferences("named_profiles", Context.MODE_PRIVATE)
    val builtIn: List<DolbyProfile>
        get() {
            val names = context.resources.getStringArray(R.array.dolby_profile_entries)
            return context.resources.getStringArray(R.array.dolby_profile_values).mapIndexed { i, id ->
                DolbyProfile(id, names[i], id.toInt(), false)
            }
        }
    private fun loadProfiles(): List<DolbyProfile> = catalog.all.mapNotNull { (key, value) ->
            runCatching {
                require(key.matches(Regex("user:[0-9a-f-]{36}")))
                val json = JSONObject(value as String)
                val base = json.getInt("base")
                require(builtIn.any { it.base == base })
                DolbyProfile(key, json.getString("name"), base, true)
            }.getOrNull()
        }.sortedBy { it.name.lowercase() } + builtIn

    private val _state = MutableStateFlow(loadProfiles())
    val state = _state.asStateFlow()
    val all: List<DolbyProfile> get() = _state.value
    var onChanged: () -> Unit = {}
    private fun publish() {
        _state.value = loadProfiles()
        onChanged()
    }

    fun find(key: String) = all.firstOrNull { it.key == key }
    fun requireProfile(key: String) = requireNotNull(find(key)) { "Unknown Dolby profile" }
    fun preferences(key: String): SharedPreferences {
        val profile = requireProfile(key)
        val file = if (profile.custom) "variant_${key.removePrefix("user:")}" else "profile_${profile.base}"
        return context.getSharedPreferences(file, Context.MODE_PRIVATE)
    }

    private fun validateName(name: String, excluding: String? = null): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 50) {
            context.getString(R.string.dolby_profile_name_invalid)
        }
        require(all.none { it.key != excluding && it.name.equals(trimmed, ignoreCase = true) }) {
            context.getString(R.string.dolby_profile_name_exists)
        }
        return trimmed
    }

    fun create(name: String, base: Int): DolbyProfile {
        val valid = validateName(name)
        require(builtIn.any { it.base == base })
        val key = "user:" + UUID.randomUUID().toString()
        // Copy only explicit base overrides, leaving endpoint-specific factory values inherited.
        val editor = context.getSharedPreferences("variant_${key.removePrefix("user:")}", Context.MODE_PRIVATE).edit()
        for ((setting, value) in preferences(base.toString()).all) {
            if (setting !in DolbyConstants.PROFILE_SPECIFIC_PREFS) continue
            when (value) {
                is String -> editor.putString(setting, value)
                is Int -> editor.putInt(setting, value)
                is Boolean -> editor.putBoolean(setting, value)
            }
        }
        editor.apply()
        catalog.edit().putString(key, JSONObject().put("name", valid).put("base", base).toString()).apply()
        publish()
        return requireProfile(key)
    }

    fun rename(key: String, name: String) {
        val profile = requireProfile(key)
        require(profile.custom)
        val valid = validateName(name, key)
        catalog.edit().putString(key, JSONObject().put("name", valid).put("base", profile.base).toString()).apply()
        publish()
    }

    fun delete(key: String) {
        require(requireProfile(key).custom)
        catalog.edit().remove(key).apply()
        context.deleteSharedPreferences("variant_${key.removePrefix("user:")}")
        publish()
    }
}
