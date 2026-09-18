/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import co.aospa.dolby.xiaomi.ui.ExpressiveChoice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.spa.framework.theme.settingsBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel, modifier: Modifier = Modifier, expanded: Boolean = false) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("equalizer_ui", android.content.Context.MODE_PRIVATE) }
    var sliders by remember { mutableStateOf(preferences.getBoolean("sliders", false)) }
    val onView: (Boolean) -> Unit = {
        sliders = it
        preferences.edit().putBoolean("sliders", it).apply()
    }
    val error by viewModel.error.collectAsState()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.settingsBackground) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
            val selectorWidth = if (maxWidth >= 840.dp) 300.dp else 240.dp
            val trackHeight = (maxHeight * .45f).coerceIn(120.dp, 240.dp)
            val editor: @Composable (Modifier) -> Unit = { editorModifier ->
                Column(editorModifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqualizerBands(viewModel, graphHeight = trackHeight, sliders = sliders,
                        connectedAbove = !expanded, scrollTracks = expanded)
                    if (error != null) Text(stringResource(R.string.dolby_setting_failed),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (expanded) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(selectorWidth)
                        .fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EqualizerProfile(viewModel, sliders, onView)
                    }
                    editor(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    EqualizerProfile(viewModel, sliders, onView, connectedBelow = true)
                    editor(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerProfile(viewModel: EqualizerViewModel, sliders: Boolean, onView: (Boolean) -> Unit, connectedBelow: Boolean = false) {
    val profileName by viewModel.profileName.collectAsState()
    EqualizerPanel(Modifier.fillMaxWidth(), connectedBelow = connectedBelow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dolby_geq_profile_heading, profileName),
                style = MaterialTheme.typography.titleSmall
            )
            PresetSelector(viewModel)
            ExpressiveChoice(
                listOf(stringResource(R.string.dolby_view_curve), stringResource(R.string.dolby_view_sliders)),
                if (sliders) 1 else 0, { onView(it == 1) }
            )
        }
    }
}
