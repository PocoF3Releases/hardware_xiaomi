/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.profiles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.ui.EqualizerPanel
import co.aospa.dolby.xiaomi.ui.*
import co.aospa.dolby.xiaomi.ui.DolbyTheme
import com.android.settingslib.spa.framework.theme.settingsBackground

/** Compatibility entry point; all pages share the main activity and active state. */
class ProfileSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigate(this, DolbyPage.SETTINGS)
    }
}

@Composable
internal fun ProfileManager(controller: DolbyController, modifier: Modifier, onActivate: () -> Unit) {
    val scope = rememberCoroutineScope()
    val active by controller.activeState.collectAsState()
    val profiles by controller.profiles.state.collectAsState()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val failure = stringResource(R.string.dolby_setting_failed)
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            EqualizerPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.dolby_profiles_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.dolby_profiles_help), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dolby_profile_create))
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (profiles.none { it.custom }) {
            item {
                EqualizerPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(40.dp))
                        Text(stringResource(R.string.dolby_profile_empty), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dolby_profiles_help), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = { creating = true }) { Text(stringResource(R.string.dolby_profile_create)) }
                    }
                }
            }
        }
        items(profiles.filter { it.custom }, key = { it.key }) { profile ->
            EqualizerPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.dolby_profile_based_on,
                            profiles.first { !it.custom && it.base == profile.base }.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { scope.launch {
                                try {
                                    controller.selectProfile(profile.key)
                                    onActivate()
                                } catch (_: RuntimeException) { error = failure }
                            } },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(if (active.key == profile.key)
                            R.string.dolby_profile_active else R.string.dolby_profile_activate)) }
                        IconButton(onClick = { renaming = profile.key }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.dolby_profile_rename))
                        }
                        IconButton(onClick = { deleting = profile.key }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.dolby_profile_delete))
                        }
                    }
                }
            }
        }
    }
    BackdropBlur(creating || renaming != null || deleting != null)
    if (creating || renaming != null) {
        val existing = profiles.firstOrNull { it.key == renaming }
        ProfileEditor(
            existing = existing,
            bases = profiles.filter { !it.custom },
            initialBase = active.base,
            onDismiss = { creating = false; renaming = null },
            onSave = { name, base ->
                try {
                    if (existing == null) controller.createNamedProfile(name, base)
                    else controller.renameNamedProfile(existing.key, name)
                    creating = false
                    renaming = null
                    null
                } catch (e: IllegalArgumentException) { e.message ?: failure }
                  catch (_: RuntimeException) { failure }
            }
        )
    }
    profiles.firstOrNull { it.key == deleting }?.let { profile ->
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dolby_profile_delete)) },
            text = { Text(stringResource(R.string.dolby_profile_delete_message, profile.name)) },
            confirmButton = {
                TextButton(onClick = { scope.launch {
                    try {
                        controller.deleteNamedProfile(profile.key)
                        deleting = null
                    } catch (_: RuntimeException) { error = failure; deleting = null }
                } }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    existing: DolbyProfile?,
    bases: List<DolbyProfile>,
    initialBase: Int,
    onDismiss: () -> Unit,
    onSave: suspend (String, Int) -> String?
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var name by rememberSaveable(existing?.key) { mutableStateOf(existing?.name.orEmpty()) }
    var base by rememberSaveable(existing?.key) { mutableIntStateOf(existing?.base ?: initialBase) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        shape = RoundedCornerShape(28.dp),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.dolby_profile_create else R.string.dolby_profile_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.dolby_profile_name)) },
                    singleLine = true, isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing == null) {
                    BackdropBlur(expanded)
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = bases.first { it.base == base }.name, onValueChange = {},
                            readOnly = true, label = { Text(stringResource(R.string.dolby_profile_base)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            bases.forEach { entry ->
                                DropdownMenuItem(text = { Text(entry.name) },
                                    onClick = { base = entry.base; expanded = false })
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch {
                saving = true
                try { error = onSave(name, base) } finally { saving = false }
            } }, enabled = name.isNotBlank() && !saving) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
