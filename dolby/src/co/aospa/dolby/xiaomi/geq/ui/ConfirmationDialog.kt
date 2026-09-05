/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import co.aospa.dolby.xiaomi.ui.BackdropBlur
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Composable
fun ConfirmationDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDialog by remember { mutableStateOf(true) }
    if (!showDialog) {
        onDismiss()
        return
    }

    BackdropBlur()
    AlertDialog(
        shape = RoundedCornerShape(28.dp),
        onDismissRequest = { showDialog = false },
        confirmButton = {
            TextButton(
                onClick = {
                    showDialog = false
                    onConfirm()
                }
            ) {
                Text(
                    stringResource(id = android.R.string.ok)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showDialog = false }
            ) {
                Text(
                    stringResource(id = android.R.string.cancel)
                )
            }
        },
        text = {
            Text(text)
        }
    )
}
