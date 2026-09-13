/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.*
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.geq.ui.EqualizerPanel
import co.aospa.dolby.xiaomi.preference.DolbyOutputRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(controller: DolbyController, modifier: Modifier) {
    val state by controller.activeState.collectAsState()
    val context = LocalContext.current
    var failure by remember { mutableStateOf(false) }
    var manageVqe by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(false) }
    var ieq by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(DolbyOutputRoute.Visibility(false, false)) }
    DisposableEffect(context) {
        val audio = context.getSystemService(AudioManager::class.java)
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val listener = AudioManager.OnDevicesForAttributesChangedListener { _, devices ->
            route = DolbyOutputRoute.visibility(devices.orEmpty().map { it.type })
        }
        var registered = false
        try {
            route = DolbyOutputRoute.visibility(audio.getDevicesForAttributes(media).map { it.type })
            audio.addOnDevicesForAttributesChangedListener(media, context.mainExecutor, listener)
            registered = true
        } catch (_: RuntimeException) { route = DolbyOutputRoute.Visibility(false, false) }
        onDispose {
            if (registered) try { audio.removeOnDevicesForAttributesChangedListener(listener) } catch (_: RuntimeException) {}
        }
    }
    val scope = rememberCoroutineScope()
    fun apply(key: String, value: Any) { scope.launch {
        try { controller.updateSetting(key, value); failure = false }
        catch (_: RuntimeException) { failure = true }
    } }
    fun toggle(key: String) { scope.launch {
        try { controller.toggleSetting(key); failure = false }
        catch (_: RuntimeException) { failure = true }
    } }
    fun checked(key: String) = state.settings[key] as? Boolean ?: false
    if (manageVqe) VqeAppsDialog(controller) { manageVqe = false }
    val enabled = state.enabled && state.loaded
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dolby_enable)) },
                modifier = Modifier.toggleable(state.enabled, role = Role.Switch) {
                    scope.launch {
                        try { controller.toggleEnabled(); failure = false }
                        catch (_: RuntimeException) { failure = true }
                    }
                },
                trailingContent = {
                    Switch(checked = state.enabled, onCheckedChange = null, thumbContent = {
                        Icon(if (state.enabled) Icons.Default.Check else Icons.Default.Close,
                            null, Modifier.size(16.dp))
                    })
                },
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer)
            )
        }

        EqualizerPanel(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dolby_profile_title)) },
                supportingContent = { Text(state.name) },
                modifier = Modifier.clickable { profiles = true },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
        Text(stringResource(R.string.dolby_category_settings), style = MaterialTheme.typography.titleMedium)
        val rows = mutableListOf<@Composable () -> Unit>()
        fun toggle(key: String, title: Int, summary: Int) {
            rows += {
                ListItem(
                    headlineContent = { Text(stringResource(title)) },
                    supportingContent = { Text(stringResource(summary)) },
                    modifier = Modifier.toggleable(checked(key), enabled = enabled, role = Role.Switch) { toggle(key) },
                    trailingContent = {
                        Switch(checked(key), onCheckedChange = null, enabled = enabled,
                            thumbContent = { Icon(if (checked(key)) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(16.dp)) })
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
        toggle("dolby_vqe", R.string.dolby_vqe_title,
            if (checked("dolby_vqe_failed")) R.string.dolby_vqe_failed else R.string.dolby_vqe_summary)
        rows += {
            ListItem(headlineContent = { Text(stringResource(R.string.dolby_vqe_manage)) },
                supportingContent = { Text(stringResource(R.string.dolby_vqe_apps_help)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable { manageVqe = true })
        }
        toggle(PREF_BASS, R.string.dolby_bass_enhancer, R.string.dolby_bass_summary)
        if (context.resources.getBoolean(R.bool.dolby_volume_leveler_supported))
            toggle(PREF_VOLUME, R.string.dolby_volume_leveler, R.string.dolby_volume_summary)
        rows += {
            ListItem(headlineContent = { Text(stringResource(R.string.dolby_ieq)) },
                leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                supportingContent = {
                    val index = (state.settings[PREF_IEQ] as? Int ?: 0).coerceIn(0, 3)
                    Text(context.resources.getStringArray(R.array.dolby_ieq_entries)[index])
                },
                modifier = Modifier.clickable(enabled = enabled) { ieq = true },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
        }
        if (route.speaker) toggle(PREF_SPK_VIRTUALIZER, R.string.dolby_spk_virtualizer, R.string.dolby_speaker_summary)
        if (route.headphones) toggle(PREF_HP_VIRTUALIZER, R.string.dolby_hp_virtualizer, R.string.dolby_headphone_summary)
        toggle(PREF_DIALOGUE, R.string.dolby_dialogue_enhancer, R.string.dolby_dialogue_summary)
        if (checked(PREF_DIALOGUE)) rows += {
            val amount = state.settings[PREF_DIALOGUE_AMOUNT] as? Int ?: 1
            var value by remember(state.key, amount) { mutableFloatStateOf(amount.toFloat().coerceIn(1f, 12f)) }
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dolby_dialogue_strength_title))
                ExpressiveValueSlider(value, { value = it }, { apply(PREF_DIALOGUE_AMOUNT, value.toInt()) },
                    1f..12f, enabled, { it.toInt().toString() }, Modifier.fillMaxWidth())
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, row ->
                EqualizerPanel(Modifier.fillMaxWidth(), connectedAbove = index > 0,
                    connectedBelow = index < rows.lastIndex, content = row)
            }
        }
        if (failure || state.error != null) Text(stringResource(R.string.dolby_setting_failed), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.dolby_tuning_help), style = MaterialTheme.typography.bodySmall)
    }
    if (profiles || ieq) {
        val choosingProfiles = profiles
        val entries = state.profiles
        SelectionSheet(
            title = stringResource(if (choosingProfiles) R.string.dolby_profile_title else R.string.dolby_ieq),
            labels = if (choosingProfiles) entries.map { it.name }
                else context.resources.getStringArray(R.array.dolby_ieq_entries).toList(),
            selected = if (choosingProfiles) entries.indexOfFirst { it.key == state.key }
                else state.settings[PREF_IEQ] as? Int ?: 0,
            onSelect = { index ->
                if (choosingProfiles) {
                    scope.launch {
                        try { controller.selectProfile(entries[index].key); failure = false }
                        catch (_: RuntimeException) { failure = true }
                    }
                } else apply(PREF_IEQ, index)
            },
            onDismiss = { profiles = false; ieq = false }
        )
    }
}

private data class VqeAppEntry(val packageName: String, val label: String)

@Composable
internal fun VqeAppsDialog(controller: DolbyController, dismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var entries by remember { mutableStateOf<List<VqeAppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            selected = controller.vqeApps()
            val saved = selected
            entries = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.applicationInfo }
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                    .associateBy { it.packageName }
                (apps.keys + saved).map { pkg ->
                    VqeAppEntry(pkg, apps[pkg]?.loadLabel(pm)?.toString() ?: pkg)
                }.sortedWith(compareBy<VqeAppEntry> { it.label.lowercase() }.thenBy { it.packageName })
            }
        } catch (_: RuntimeException) { failed = true }
        finally { busy = false }
    }
    fun change(app: String, enabled: Boolean) {
        busy = true
        scope.launch {
            try {
                controller.setVqeApp(app, enabled)
                selected = controller.vqeApps()
                failed = false
            } catch (_: RuntimeException) { failed = true }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dolby_vqe_manage), Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall)
                    TextButton(enabled = !busy, onClick = dismiss) { Text(stringResource(android.R.string.ok)) }
                }
                Text(stringResource(R.string.dolby_vqe_apps_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.dolby_vqe_search)) }, singleLine = true)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.dolby_setting_failed), color = MaterialTheme.colorScheme.error)
                val visible = entries.filter {
                    it.label.contains(query, true) || it.packageName.contains(query, true)
                }
                if (!busy && visible.isEmpty()) Text(stringResource(R.string.dolby_vqe_no_apps))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        val checked = app.packageName in selected
                        EqualizerPanel(Modifier.fillMaxWidth(),
                            connectedAbove = app != visible.first(), connectedBelow = app != visible.last()) {
                            ListItem(headlineContent = { Text(app.label) },
                                supportingContent = { Text(app.packageName) },
                                trailingContent = { Checkbox(checked, onCheckedChange = null, enabled = !busy) },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                                modifier = Modifier.toggleable(checked, enabled = !busy, role = Role.Checkbox) {
                                    change(app.packageName, it)
                                })
                        }
                    }
                }
            }
        }
    }
}
