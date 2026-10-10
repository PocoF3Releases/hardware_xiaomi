/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import co.aospa.dolby.xiaomi.ui.BackdropBlur
import co.aospa.dolby.xiaomi.ui.DolbyDialog

@Composable
fun ConfirmationDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var showDialog by remember { mutableStateOf(true) }
    if (!showDialog) {
        onDismiss()
        return
    }

    BackdropBlur()
    DolbyDialog(
        shape = RoundedCornerShape(28.dp),
        onDismissRequest = { showDialog = false },
        confirmButton = {
            TextButton(
                onClick = {
                    showDialog = false
                    onConfirm()
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
        text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
    )
}
