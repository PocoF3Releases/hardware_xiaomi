/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.ui.BackdropBlur
import co.aospa.dolby.xiaomi.ui.DolbyDialog

@Composable
fun PresetNameDialog(
    title: String,
    presetName: String = "",
    onPresetNameSet: (String) -> PresetNameValidationError?,
    onDismissDialog: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(true) }
    if (!showDialog) {
        onDismissDialog()
        return
    }
    var text by remember { mutableStateOf(presetName) }
    var error by remember { mutableStateOf<PresetNameValidationError?>(null) }

    BackdropBlur()
    DolbyDialog(
        shape = RoundedCornerShape(28.dp),
        onDismissRequest = { showDialog = false },
        confirmButton = {
            TextButton(
                onClick = {
                    onPresetNameSet(text)?.let {
                        // validation failed
                        error = it
                        return@TextButton
                    }
                    // succeeded
                    showDialog = false
                    error = null
                }
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(id = R.string.dolby_geq_preset_name)) },
                    isError = error != null,
                    singleLine = true,
                )
                error?.let {
                    Text(
                        text = it.toErrorMessage(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}
