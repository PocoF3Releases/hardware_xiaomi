/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.aospa.dolby.xiaomi.GameEffectController
import co.aospa.dolby.xiaomi.GameEffectTuning
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.ui.EqualizerPanel
import com.android.settingslib.spa.framework.theme.settingsBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GameAppEntry(val packageName: String, val label: String)

@Composable
internal fun GameEffectSettings(controller: GameEffectController) {
    val state by controller.state.collectAsState()
    var manage by remember { mutableStateOf(false) }

    if (manage) GameAppsDialog(controller) { manage = false }

    EqualizerPanel {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { DolbyHeadline(stringResource(R.string.game_effect_title)) },
                supportingContent = {
                    Text(
                        when {
                            !state.supported -> stringResource(R.string.game_effect_not_supported)
                            state.error -> stringResource(R.string.game_effect_failed)
                            state.activePackage != null ->
                                stringResource(R.string.game_effect_active, state.activePackage!!)
                            else -> stringResource(R.string.dolby_game_summary_compact)
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = null,
                        enabled = state.supported
                    )
                },
                modifier = Modifier.toggleable(
                    value = state.enabled,
                    enabled = state.supported,
                    role = Role.Switch
                ) { controller.setEnabled(it) }
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { DolbyHeadline(stringResource(R.string.game_effect_manage)) },
                supportingContent = {
                    Text(stringResource(R.string.game_effect_manage_summary))
                },
                leadingContent = { Icon(Icons.Default.Tune, null) },
                modifier = Modifier.clickable(enabled = state.supported) { manage = true }
            )
        }
    }
}

@Composable
private fun GameAppsDialog(controller: GameEffectController, dismiss: () -> Unit) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    var entries by remember { mutableStateOf<List<GameAppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var tuningPackage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(state.games) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.applicationInfo }
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .associateBy { it.packageName }
            (apps.keys + state.games + controller.stockGames()).distinct().map { pkg ->
                GameAppEntry(pkg, apps[pkg]?.loadLabel(pm)?.toString() ?: pkg)
            }.sortedWith(compareBy<GameAppEntry> { it.label.lowercase() }.thenBy { it.packageName })
        }
        loading = false
    }

    tuningPackage?.let { pkg ->
        val entry = entries.firstOrNull { it.packageName == pkg }
        GameTuningDialog(
            packageName = pkg,
            label = entry?.label ?: pkg,
            current = controller.tuning(pkg),
            defaults = controller.defaultTuning(pkg),
            onDismiss = { tuningPackage = null },
            onSave = {
                controller.setTuning(pkg, it)
                tuningPackage = null
            }
        )
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.game_effect_manage),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = dismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
                Text(
                    stringResource(R.string.game_effect_help),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.game_effect_search)) },
                    singleLine = true
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.error) {
                    Text(
                        stringResource(R.string.game_effect_failed),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val visible = entries.filter {
                    it.label.contains(query, true) || it.packageName.contains(query, true)
                }
                if (!loading && visible.isEmpty()) {
                    Text(stringResource(R.string.game_effect_no_apps))
                }

                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        val selected = app.packageName in state.games
                        EqualizerPanel(
                            Modifier.fillMaxWidth(),
                            connectedAbove = app != visible.first(),
                            connectedBelow = app != visible.last()
                        ) {
                            ListItem(
                                headlineContent = { DolbyHeadline(app.label) },
                                supportingContent = { Text(app.packageName) },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selected) {
                                            IconButton(onClick = {
                                                tuningPackage = app.packageName
                                            }) {
                                                Icon(
                                                    Icons.Default.Tune,
                                                    stringResource(R.string.game_effect_tune)
                                                )
                                            }
                                        }
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = {
                                                controller.setGameEnabled(app.packageName, it)
                                            }
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTuningDialog(
    packageName: String,
    label: String,
    current: GameEffectTuning,
    defaults: GameEffectTuning,
    onDismiss: () -> Unit,
    onSave: (GameEffectTuning) -> Unit
) {
    var low by remember(packageName, current) {
        mutableFloatStateOf(current.lowFrequency.toFloat())
    }
    var vocal by remember(packageName, current) {
        mutableFloatStateOf(current.vocal.toFloat())
    }
    var footstep by remember(packageName, current) {
        mutableFloatStateOf(current.footstep.toFloat())
    }
    var soundField by remember(packageName, current) {
        mutableFloatStateOf(current.soundField.toFloat())
    }

    fun valueLabel(value: Float) = value.toInt().toString()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_effect_tuning_title, label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TuningSlider(
                    R.string.game_effect_low_freq,
                    low,
                    { low = it },
                    { valueLabel(it) }
                )
                TuningSlider(
                    R.string.game_effect_vocal,
                    vocal,
                    { vocal = it },
                    { valueLabel(it) }
                )
                TuningSlider(
                    R.string.game_effect_footstep,
                    footstep,
                    { footstep = it },
                    { valueLabel(it) }
                )
                TuningSlider(
                    R.string.game_effect_sound_field,
                    soundField,
                    { soundField = it },
                    { valueLabel(it) }
                )
                TextButton(onClick = {
                    low = defaults.lowFrequency.toFloat()
                    vocal = defaults.vocal.toFloat()
                    footstep = defaults.footstep.toFloat()
                    soundField = defaults.soundField.toFloat()
                }) {
                    Text(stringResource(R.string.game_effect_reset_defaults))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    GameEffectTuning(
                        low.toInt(),
                        vocal.toInt(),
                        footstep.toInt(),
                        soundField.toInt()
                    )
                )
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun TuningSlider(
    title: Int,
    value: Float,
    onValue: (Float) -> Unit,
    label: (Float) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
            Text(
                label(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ExpressiveValueSlider(
            value = value,
            onValueChange = onValue,
            onFinished = {},
            range = 0f..100f,
            enabled = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
