/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import co.aospa.dolby.xiaomi.ui.ExpressiveActions
import co.aospa.dolby.xiaomi.ui.ExpressiveValueSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.data.BandGain
import kotlin.math.roundToInt

@Composable
fun BandGainSlider(bandGain: BandGain, enabled: Boolean = true, onValueChangeFinished: (Int) -> Unit) {
    var position by remember(bandGain.band, bandGain.gain) {
        mutableFloatStateOf(bandGain.gain.toFloat())
    }
    val frequencies = LocalContext.current.resources.getIntArray(R.array.dolby_geq_frequencies)
    val bandLabel = stringResource(
        R.string.dolby_geq_band_frequency, bandGain.band, frequencies[bandGain.band - 1]
    )
    fun applyGain(gain: Int) {
        position = gain.coerceIn(-100, 100).toFloat()
        onValueChangeFinished(position.toInt())
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(bandLabel, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text(stringResource(R.string.dolby_geq_gain_db, position / 10f))
        }
        ExpressiveValueSlider(
            value = position, enabled = enabled, range = -100f..100f,
            onValueChange = { position = it },
            onFinished = { applyGain(position.roundToInt()) },
            label = { java.lang.String.format(java.util.Locale.getDefault(), "%+.1f", it / 10f) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = bandLabel }
        )
    }
}
