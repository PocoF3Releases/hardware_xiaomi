/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One tonal surface and selection model for profile, IEQ and graphic preset pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionSheet(title: String, labels: List<String>, selected: Int,
    onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    BackdropBlur()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
        LazyColumn(Modifier.fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(labels) { index, label ->
                val chosen = index == selected
                Surface(shape = RoundedCornerShape(
                    topStart = if (index == 0) 20.dp else 4.dp,
                    topEnd = if (index == 0) 20.dp else 4.dp,
                    bottomStart = if (index == labels.lastIndex) 20.dp else 4.dp,
                    bottomEnd = if (index == labels.lastIndex) 20.dp else 4.dp),
                    color = if (chosen) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer) {
                    ListItem(headlineContent = { Text(label) },
                        trailingContent = { RadioButton(chosen, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.selectable(chosen, enabled = !closing, role = Role.RadioButton) {
                            closing = true
                            onSelect(index)
                            scope.launch { sheet.hide(); onDismiss() }
                        })
                }
            }
        }
    }
}
