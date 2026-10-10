/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.ui.BackdropBlur
import co.aospa.dolby.xiaomi.ui.MusicIcons
import co.aospa.dolby.xiaomi.ui.SelectionIcon
import co.aospa.dolby.xiaomi.ui.SelectionSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSelector(viewModel: EqualizerViewModel) {
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.preset.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    val ready by viewModel.ready.collectAsState()
    var showNewPresetDialog by remember { mutableStateOf(false) }
    var showRenamePresetDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val names =
        androidx.compose.ui.platform.LocalContext.current.resources
            .getStringArray(R.array.dolby_preset_entries)
            .toList()
    val symbols =
        listOf(
            MusicIcons.sliders_vertical,
            MusicIcons.electric_guitar,
            MusicIcons.saxophone,
            MusicIcons.disc_3,
            MusicIcons.piano,
            MusicIcons.mic_vocal,
            MusicIcons.harmonica,
            MusicIcons.audio_lines,
            MusicIcons.hand_metal,
        )
    fun presetIcon(name: String?, userDefined: Boolean) =
        symbols.getOrNull(if (userDefined) -1 else names.indexOf(name))
            ?: MusicIcons.sliders_vertical

    BackdropBlur(actionsExpanded)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = ready,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).padding(end = 8.dp).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SelectionIcon(presetIcon(currentPreset.name, currentPreset.isUserDefined), true, ready)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dolby_geq_preset),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    currentPreset.name ?: stringResource(R.string.dolby_preset_custom),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }

        Box {
            FilledTonalIconButton(onClick = { actionsExpanded = true }, enabled = ready) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.dolby_geq_preset_actions))
            }
            DropdownMenu(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dolby_geq_new_preset)) },
                    onClick = {
                        actionsExpanded = false
                        showNewPresetDialog = true
                    },
                )
                if (currentPreset.isUserDefined) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dolby_geq_rename_preset)) },
                        onClick = {
                            actionsExpanded = false
                            showRenamePresetDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dolby_geq_delete_preset)) },
                        onClick = {
                            actionsExpanded = false
                            showDeleteConfirmDialog = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dolby_geq_reset_gains)) },
                    onClick = {
                        actionsExpanded = false
                        showResetConfirmDialog = true
                    },
                )
            }
        }
    }

    if (expanded) {
        SelectionSheet(
            stringResource(R.string.dolby_geq_preset),
            presets.map { it.name.orEmpty() },
            presets.indexOfFirst {
                it.name == currentPreset.name && it.isUserDefined == currentPreset.isUserDefined
            },
            onSelect = { viewModel.setPreset(presets[it]) },
            onDismiss = { expanded = false },
            icon = { index ->
                val preset = presets[index]
                presetIcon(preset.name, preset.isUserDefined)
            },
        )
    }

    // Dialogs

    if (showNewPresetDialog) {
        PresetNameDialog(
            title = stringResource(id = R.string.dolby_geq_new_preset),
            onPresetNameSet = {
                return@PresetNameDialog viewModel.createNewPreset(name = it)
            },
            onDismissDialog = { showNewPresetDialog = false },
        )
    }

    if (showRenamePresetDialog) {
        PresetNameDialog(
            title = stringResource(id = R.string.dolby_geq_rename_preset),
            presetName = currentPreset.name!!,
            onPresetNameSet = {
                return@PresetNameDialog viewModel.renamePreset(preset = currentPreset, name = it)
            },
            onDismissDialog = { showRenamePresetDialog = false },
        )
    }

    if (showDeleteConfirmDialog) {
        ConfirmationDialog(
            text = stringResource(id = R.string.dolby_geq_delete_preset_prompt),
            onConfirm = { viewModel.deletePreset(currentPreset) },
            onDismiss = { showDeleteConfirmDialog = false },
        )
    }

    if (showResetConfirmDialog) {
        ConfirmationDialog(
            text = stringResource(id = R.string.dolby_geq_reset_gains_prompt),
            onConfirm = { viewModel.reset() },
            onDismiss = { showResetConfirmDialog = false },
        )
    }
}
