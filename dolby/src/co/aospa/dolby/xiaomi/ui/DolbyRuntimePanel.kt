/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.DolbyEndpointPolicy
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.ui.EqualizerPanel
import co.aospa.dolby.xiaomi.profiles.ActiveProfileState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Exposes requested settings and observed control state without claiming acoustic measurement. */
@Composable
internal fun DolbyRuntimePanel(controller: DolbyController, state: ActiveProfileState) {
    val runtime = state.runtime
    val scope = rememberCoroutineScope()
    var choosingSpeaker by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val status = when {
        !state.loaded || state.error != null -> R.string.dolby_status_unavailable
        !state.enabled -> R.string.dolby_status_off
        runtime.phase == "COMMUNICATION" -> R.string.dolby_status_communication
        runtime.phase == "MEDIA_RESTORE" -> R.string.dolby_status_restoring
        !runtime.hasControl -> R.string.dolby_status_control
        runtime.nativeEnabled == true && runtime.frameworkEnabled == true -> R.string.dolby_status_media
        else -> R.string.dolby_status_bypassed
    }
    EqualizerPanel(Modifier.fillMaxWidth()) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dolby_status_title)) },
                supportingContent = { Text(stringResource(status)) },
                leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Text(stringResource(R.string.dolby_status_details), style = MaterialTheme.typography.labelMedium) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { details = !details }
            )
            if (runtime.speakerTuningSupported) {
                val tuning = DolbyEndpointPolicy.SpeakerTuning.fromKey(runtime.speakerTuning)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dolby_speaker_tuning_title)) },
                    leadingContent = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    supportingContent = { Text(stringResource(speakerTuningLabel(tuning))) },
                    modifier = Modifier.clickable(enabled = state.loaded && runtime.hasControl) {
                        choosingSpeaker = true
                    }
                )
            }
            if (failed) Text(stringResource(R.string.dolby_setting_failed),
                modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text(stringResource(R.string.dolby_status_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dolby_output_route, stringResource(routeLabel(runtime.routedDeviceType))),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.dolby_tuning_ack,
                        runtime.acknowledgedTuning ?: stringResource(R.string.dolby_tuning_native)),
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.dolby_control_gates,
                        stringResource(gateLabel(runtime.nativeEnabled)),
                        stringResource(gateLabel(runtime.frameworkEnabled))),
                        style = MaterialTheme.typography.bodySmall)
                    if (runtime.speakerTuningSupported) Text(stringResource(R.string.dolby_speaker_tuning_help),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.dolby_status_help), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.dolby_status_readback_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

            },
            confirmButton = {
                TextButton(onClick = { details = false }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { controller.requestRefresh() }) {
                    Text(stringResource(R.string.dolby_status_refresh))
                }
            }
        )
    }
    if (choosingSpeaker) {
        val choices = DolbyEndpointPolicy.SpeakerTuning.entries
        SelectionSheet(
            title = stringResource(R.string.dolby_speaker_tuning_title),
            labels = choices.map { stringResource(speakerTuningLabel(it)) },
            selected = choices.indexOf(DolbyEndpointPolicy.SpeakerTuning.fromKey(runtime.speakerTuning)),
            onSelect = { index ->
                scope.launch {
                    try { controller.setSpeakerTuning(choices[index]); failed = false }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: RuntimeException) { failed = true }
                }
            },
            onDismiss = { choosingSpeaker = false }
        )
    }
}

private fun speakerTuningLabel(tuning: DolbyEndpointPolicy.SpeakerTuning): Int = when (tuning) {
    DolbyEndpointPolicy.SpeakerTuning.AUTOMATIC -> R.string.dolby_speaker_tuning_auto
    DolbyEndpointPolicy.SpeakerTuning.PORTRAIT -> R.string.dolby_speaker_tuning_portrait
    DolbyEndpointPolicy.SpeakerTuning.LANDSCAPE -> R.string.dolby_speaker_tuning_landscape
}

private fun gateLabel(enabled: Boolean?): Int = when (enabled) {
    true -> R.string.dolby_gate_on
    false -> R.string.dolby_gate_off
    null -> R.string.dolby_gate_unknown
}

private fun routeLabel(type: Int?): Int = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> R.string.dolby_route_speaker
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> R.string.dolby_route_wired
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> R.string.dolby_route_bluetooth
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.dolby_route_usb
    else -> R.string.dolby_route_native
}
