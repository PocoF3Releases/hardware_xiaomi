/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import android.media.AudioAttributes
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.*
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants
import kotlin.math.roundToInt
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_DUCKING
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_GEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_REVERB_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_DISTANCE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_REVERB
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VIRTUAL_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.ui.EqualizerPanel
import co.aospa.dolby.xiaomi.preference.DolbyOutputRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(controller: DolbyController, modifier: Modifier) {
    val state by controller.activeState.collectAsState()
    val context = LocalContext.current
    var failure by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(false) }
    var ieq by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(DolbyOutputRoute.Visibility(false, false)) }
    DisposableEffect(context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val listener =
            AudioManager.OnDevicesForAttributesChangedListener { _, devices ->
                route = DolbyOutputRoute.visibility(devices.orEmpty().map { it.type })
            }
        var registered = false
        try {
            route =
                DolbyOutputRoute.visibility(audio.getDevicesForAttributes(media).map { it.type })
            audio.addOnDevicesForAttributesChangedListener(media, context.mainExecutor, listener)
            registered = true
        } catch (_: RuntimeException) {
            route = DolbyOutputRoute.Visibility(false, false)
        }
        onDispose {
            if (registered)
                try {
                    audio.removeOnDevicesForAttributesChangedListener(listener)
                } catch (_: RuntimeException) {}
        }
    }
    val scope = rememberCoroutineScope()
    fun apply(key: String, value: Any) {
        scope.launch {
            try {
                controller.updateSetting(key, value)
                failure = false
            } catch (_: RuntimeException) {
                failure = true
            }
        }
    }
    fun toggle(key: String) {
        scope.launch {
            try {
                controller.toggleSetting(key)
                failure = false
            } catch (_: RuntimeException) {
                failure = true
            }
        }
    }
    fun checked(key: String) = state.settings[key] as? Boolean ?: false
    val enabled = state.enabled && state.loaded
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DossierHeader()
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape =
                if (LocalDossierTheme.current) MaterialTheme.shapes.large
                else RoundedCornerShape(28.dp),
        ) {
            ListItem(
                headlineContent = { DolbyHeadline(stringResource(R.string.dolby_enable)) },
                modifier =
                    Modifier.toggleable(state.enabled, role = Role.Switch) {
                        scope.launch {
                            try {
                                controller.toggleEnabled()
                                failure = false
                            } catch (_: RuntimeException) {
                                failure = true
                            }
                        }
                    },
                trailingContent = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = null,
                        thumbContent = {
                            Icon(
                                if (state.enabled) Icons.Default.Check else Icons.Default.Close,
                                null,
                                Modifier.size(16.dp),
                            )
                        },
                    )
                },
                colors =
                    ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }

        DolbyRuntimePanel(controller, state)

        EqualizerPanel(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { DolbyHeadline(stringResource(R.string.dolby_profile_title)) },
                supportingContent = {
                    Text(
                        profileLabel(state.key, state.name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                leadingContent = { SelectionIcon(profileIcon(state.base), true) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable { profiles = true },
                colors =
                    ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
            )
        }
        val tone = mutableListOf<@Composable () -> Unit>()
        val voice = mutableListOf<@Composable () -> Unit>()
        val surround = mutableListOf<@Composable () -> Unit>()
        fun toggle(rows: MutableList<@Composable () -> Unit>, key: String, title: Int, summary: Int) {
            rows += {
                ListItem(
                    headlineContent = { DolbyHeadline(stringResource(title)) },
                    supportingContent = { Text(stringResource(summary)) },
                    modifier =
                        Modifier.toggleable(checked(key), enabled = enabled, role = Role.Switch) {
                            toggle(key)
                        },
                    trailingContent = {
                        Switch(
                            checked(key),
                            onCheckedChange = null,
                            enabled = enabled,
                            thumbContent = {
                                Icon(
                                    if (checked(key)) Icons.Default.Check else Icons.Default.Close,
                                    null,
                                    Modifier.size(16.dp),
                                )
                            },
                        )
                    },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                )
            }
        }
        fun amount(
            rows: MutableList<@Composable () -> Unit>,
            key: String,
            title: Int,
            range: IntRange,
        ) {
            rows += {
                val saved = (state.settings[key] as? Int ?: range.first).coerceIn(range)
                var value by remember(state.key, saved) { mutableFloatStateOf(saved.toFloat()) }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(title),
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            value.roundToInt().toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExpressiveValueSlider(
                        value = value,
                        onValueChange = { value = it },
                        onFinished = { apply(key, value.roundToInt()) },
                        range = range.first.toFloat()..range.last.toFloat(),
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        tone += {
            ListItem(
                headlineContent = { DolbyHeadline(stringResource(R.string.dolby_ieq)) },
                leadingContent = {
                    SelectionIcon(ieqIcon(state.settings[PREF_IEQ] as? Int ?: 0), true, enabled)
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                supportingContent = {
                    val index = (state.settings[PREF_IEQ] as? Int ?: 0).coerceIn(0, 3)
                    Text(context.resources.getStringArray(R.array.dolby_ieq_entries)[index])
                },
                modifier = Modifier.clickable(enabled = enabled) { ieq = true },
                colors =
                    ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
            )
        }
        if ((state.settings[PREF_IEQ] as? Int ?: 0) != 0)
            amount(tone, PREF_IEQ_AMOUNT, R.string.dolby_ieq_amount, DolbyConstants.IEQ_AMOUNT_RANGE)
        toggle(tone, PREF_GEQ, R.string.dolby_geq_enable, R.string.dolby_geq_enable_summary)
        toggle(tone, PREF_BASS, R.string.dolby_bass_enhancer, R.string.dolby_bass_summary_compact)
        // Virtual bass is tuned for the small built-in speakers only.
        if (route.speaker)
            toggle(
                tone,
                PREF_VIRTUAL_BASS,
                R.string.dolby_virtual_bass,
                R.string.dolby_virtual_bass_summary,
            )
        toggle(voice, PREF_DIALOGUE, R.string.dolby_dialogue_enhancer, R.string.dolby_dialogue_summary)
        if (checked(PREF_DIALOGUE)) {
            amount(voice, PREF_DIALOGUE_AMOUNT, R.string.dolby_dialogue_strength_title, 1..12)
            amount(
                voice,
                PREF_DIALOGUE_DUCKING,
                R.string.dolby_dialogue_ducking,
                DolbyConstants.DIALOGUE_DUCKING_RANGE,
            )
        }
        toggle(voice, PREF_REVERB, R.string.dolby_reverb_suppression, R.string.dolby_reverb_summary)
        if (checked(PREF_REVERB))
            amount(
                voice,
                PREF_REVERB_AMOUNT,
                R.string.dolby_reverb_amount,
                DolbyConstants.REVERB_AMOUNT_RANGE,
            )
        if (context.resources.getBoolean(R.bool.dolby_volume_leveler_supported)) {
            toggle(
                voice,
                PREF_VOLUME,
                R.string.dolby_volume_leveler,
                R.string.dolby_volume_summary_compact,
            )
            if (checked(PREF_VOLUME))
                amount(
                    voice,
                    PREF_VOLUME_AMOUNT,
                    R.string.dolby_volume_amount,
                    DolbyConstants.VOLUME_AMOUNT_RANGE,
                )
        }
        if (route.speaker)
            toggle(
                surround,
                PREF_SPK_VIRTUALIZER,
                R.string.dolby_spk_virtualizer,
                R.string.dolby_speaker_summary,
            )
        if (route.headphones) {
            toggle(
                surround,
                PREF_HP_VIRTUALIZER,
                R.string.dolby_hp_virtualizer,
                R.string.dolby_headphone_summary,
            )
            if (checked(PREF_HP_VIRTUALIZER))
                amount(
                    surround,
                    PREF_HP_DISTANCE,
                    R.string.dolby_hp_distance,
                    DolbyConstants.HP_DISTANCE_RANGE,
                )
        }
        listOf(
                R.string.dolby_category_tone to tone,
                R.string.dolby_category_voice to voice,
                R.string.dolby_category_surround to surround,
            )
            .filter { it.second.isNotEmpty() }
            .forEachIndexed { section, (title, rows) ->
                DossierSection("%02d".format(section + 1), stringResource(title))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEachIndexed { index, row ->
                        EqualizerPanel(
                            Modifier.fillMaxWidth(),
                            connectedAbove = index > 0,
                            connectedBelow = index < rows.lastIndex,
                            content = row,
                        )
                    }
                }
            }
        if (failure || state.error != null)
            Text(
                stringResource(R.string.dolby_setting_failed),
                color = MaterialTheme.colorScheme.error,
            )
        Text(
            stringResource(R.string.dolby_tuning_help),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (profiles || ieq) {
        val choosingProfiles = profiles
        val entries = state.profiles
        SelectionSheet(
            title =
                stringResource(
                    if (choosingProfiles) R.string.dolby_profile_title else R.string.dolby_ieq
                ),
            labels =
                if (choosingProfiles) entries.map { profileLabel(it.key, it.name) }
                else context.resources.getStringArray(R.array.dolby_ieq_entries).toList(),
            selected =
                if (choosingProfiles) entries.indexOfFirst { it.key == state.key }
                else state.settings[PREF_IEQ] as? Int ?: 0,
            icon = { index ->
                if (choosingProfiles) profileIcon(entries[index].base) else ieqIcon(index)
            },
            onSelect = { index ->
                if (choosingProfiles) {
                    scope.launch {
                        try {
                            controller.selectProfile(entries[index].key)
                            failure = false
                        } catch (_: RuntimeException) {
                            failure = true
                        }
                    }
                } else apply(PREF_IEQ, index)
            },
            onDismiss = {
                profiles = false
                ieq = false
            },
        )
    }
}
