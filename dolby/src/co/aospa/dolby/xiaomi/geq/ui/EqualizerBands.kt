/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import co.aospa.dolby.xiaomi.ui.rememberTuningHaptics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlin.math.abs
import kotlin.math.roundToInt
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.data.EqualizerGraphScale as Scale

@OptIn(ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)
@Composable
fun EqualizerBands(
    viewModel: EqualizerViewModel,
    graphHeight: Dp,
    wide: Boolean = false,
    modifier: Modifier = Modifier,
    sliders: Boolean = false,
    connectedAbove: Boolean = true,
    scrollTracks: Boolean = false
) {
    val preset by viewModel.preset.collectAsState()
    val profileKey by viewModel.profileKey.collectAsState()
    val ready by viewModel.ready.collectAsState()
    val context = LocalContext.current
    val frequencies = remember(context) { context.resources.getIntArray(R.array.dolby_geq_frequencies) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // A drag previews locally; release sends one complete edit to the existing engine path.
    var preview by remember(preset, profileKey, sliders) { mutableStateOf<Pair<Int, Int>?>(null) }
    val gains by rememberUpdatedState(preset.bandGains)
    fun applyGain(band: Int, gain: Int) {
        if (viewModel.profileKey.value == profileKey) viewModel.setGain(band, gain)
    }
    val haptic = rememberTuningHaptics()
    // Coalesce complete preview arrays without sending intermediate IPC writes.
    // A 16 ms sampling window also stays fluid with 90/120 Hz pointer input.
    // Completion uses the raw final value and never waits for this visual preview.
    val displayedPreview by produceState<List<co.aospa.dolby.xiaomi.geq.data.BandGain>?>(
        null, profileKey, sliders, preset
    ) {
        snapshotFlow {
            preview?.let { (band, gain) ->
                gains.mapIndexed { index, entry -> if (index == band) entry.copy(gain = gain) else entry }
            }
        }.sample(16L).collect { value = it }
    }
    val animatedGains = (displayedPreview ?: gains).mapIndexed { index, band ->
        animateFloatAsState(
            band.gain.toFloat(),
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "curve band $index"
        ).value.coerceIn(-100f, 100f)
    }
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textSize = with(density) { 10.sp.toPx() }
    val axisPaint = remember(textSize, colors.onSurfaceVariant) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onSurfaceVariant.toArgb()
            this.textSize = textSize
        }
    }
    val left = maxOf(with(density) { 38.dp.toPx() }, axisPaint.measureText("+10") + with(density) { 8.dp.toPx() })
    val right = maxOf(with(density) { 16.dp.toPx() }, axisPaint.measureText(frequencies.last().toString()) / 2 + with(density) { 4.dp.toPx() })
    val top = maxOf(with(density) { 14.dp.toPx() }, textSize + with(density) { 4.dp.toPx() })
    val bottom = maxOf(with(density) { 28.dp.toPx() }, textSize + with(density) { 12.dp.toPx() })
    val description = stringResource(R.string.dolby_geq_graph_description)
    val previous = stringResource(R.string.dolby_geq_previous_band)
    val next = stringResource(R.string.dolby_geq_next_band)

    val graph: @Composable (Modifier) -> Unit = { modifier ->
        EqualizerPanel(modifier, connectedAbove = connectedAbove, connectedBelow = !wide && !sliders) {
            if (sliders) {
                EqualizerSliders(displayedPreview ?: gains, frequencies, ready, profileKey, graphHeight,
                    scrollTracks, { selected = it }, { preview = it; it?.let { value -> haptic(value.first, value.second) } }, ::applyGain)
            } else Column {
                Text(
                    stringResource(R.string.dolby_geq_graph_hint),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Canvas(Modifier.fillMaxWidth().height(graphHeight)
                    .semantics { contentDescription = description }
                    .pointerInput(frequencies, ready, profileKey, preset, left, right, top, bottom) {
                        if (!ready) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val gestureProfile = profileKey
                            val band = Scale.nearestBand(
                                (down.position.x - left) / (size.width - left - right).coerceAtLeast(1f),
                                frequencies.size
                            )
                            selected = band
                            val initialGain = gains[band].gain
                            val height = (size.height - top - bottom).coerceAtLeast(1f)
                            var dragging = false
                            // Own the gesture from down through selection, preview and release.
                            down.consume()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    val deltaY = change.position.y - down.position.y
                                    if (abs(deltaY) > viewConfiguration.touchSlop) dragging = true
                                    if (dragging) {
                                        // Relative movement avoids jumping when the finger misses the dot.
                                        preview = band to (initialGain - deltaY * 200f / height)
                                            .roundToInt().coerceIn(-100, 100)
                                        preview?.let { haptic(it.first, it.second) }
                                    }
                                    change.consume()
                                    if (!change.pressed) {
                                        preview?.let { if (viewModel.profileKey.value == gestureProfile) viewModel.setGain(it.first, it.second) }
                                        break
                                    }
                                }
                            } finally {
                                // Cancellation drops the preview without changing the engine.
                                preview = null
                            }
                        }
                    }
                ) {
                    val width = (size.width - left - right).coerceAtLeast(1f)
                    val height = (size.height - top - bottom).coerceAtLeast(1f)
                    val paint = axisPaint
                    for (gain in listOf(-100, -50, 0, 50, 100)) {
                        val y = top + Scale.y(gain) * height
                        drawLine(colors.outline.copy(alpha = if (gain == 0) .6f else .2f),
                            Offset(left, y), Offset(left + width, y), 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(
                            if (gain > 0) "+${gain / 10}" else "${gain / 10}", 4.dp.toPx(), y + textSize / 3, paint
                        )
                    }
                    val points = animatedGains.mapIndexed { index, gain ->
                        val x = left + Scale.x(index, frequencies.size) * width
                        Offset(x, top + (100f - gain) / 200f * height)
                    }
                    val line = Path().apply {
                        points.forEachIndexed { index, p ->
                            if (index == 0) moveTo(p.x, p.y) else {
                                val previousPoint = points[index - 1]
                                val middle = (previousPoint.x + p.x) / 2
                                cubicTo(middle, previousPoint.y, middle, p.y, p.x, p.y)
                            }
                        }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(points.last().x, top + height)
                        lineTo(points.first().x, top + height)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(colors.primary.copy(alpha = .22f), colors.primary.copy(alpha = 0f))))
                    points.forEachIndexed { index, point ->
                        drawLine(colors.outline.copy(alpha = .15f), Offset(point.x, top), Offset(point.x, top + height))
                        if (index == selected) drawLine(colors.primary.copy(alpha = .4f),
                            Offset(point.x, top), Offset(point.x, top + height), 1.dp.toPx())
                    }
                    drawPath(line, colors.primary, style = Stroke(width = 2.dp.toPx()))
                    points.forEachIndexed { index, point ->
                        if (index == selected) drawCircle(colors.primary.copy(alpha = .2f), 12.dp.toPx(), point)
                        drawCircle(if (index == selected) colors.primary else colors.onSurfaceVariant,
                            if (index == selected) 5.dp.toPx() else 2.5.dp.toPx(), point)
                    }
                    for (index in listOf(0, 6, 12, frequencies.lastIndex)) {
                        val text = frequencies[index].toString()
                        val x = left + Scale.x(index, frequencies.size) * width
                        drawContext.canvas.nativeCanvas.drawText(text, x - paint.measureText(text) / 2,
                            size.height - 7.dp.toPx(), paint)
                    }
                }
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        EqualizerPanel(modifier, connectedAbove = !wide) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { selected = selected - 1 }, enabled = ready && selected > 0,
                        modifier = Modifier.semantics { contentDescription = previous }) { Text("‹") }
                    Text(stringResource(R.string.dolby_geq_band_counter, selected + 1, frequencies.size),
                        style = MaterialTheme.typography.labelLarge)
                    FilledTonalIconButton(onClick = { selected = selected + 1 }, enabled = ready && selected < frequencies.lastIndex,
                        modifier = Modifier.semantics { contentDescription = next }) { Text("›") }
                }
                val selectedBand = gains[selected].let { band ->
                    preview?.takeIf { it.first == selected }?.let { band.copy(gain = it.second) } ?: band
                }
                BandGainSlider(selectedBand, enabled = ready) { applyGain(selected, it) }
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (sliders) {
            graph(Modifier.fillMaxWidth())
        } else if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                graph(Modifier.weight(1f))
                controls(Modifier.width(280.dp))
            }
        } else {
            graph(Modifier.fillMaxWidth())
            controls(Modifier.fillMaxWidth())
        }
    }
}
