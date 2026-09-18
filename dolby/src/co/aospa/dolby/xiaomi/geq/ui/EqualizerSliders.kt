/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.data.BandGain
import co.aospa.dolby.xiaomi.geq.data.EqualizerGains
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EqualizerSliders(
    gains: List<BandGain>, frequencies: IntArray, enabled: Boolean,
    profileKey: String, sliderHeight: Dp,
    scrollTracks: Boolean,
    onSelect: (Int) -> Unit, onPreview: (Pair<Int, Int>?) -> Unit,
    onCommit: (Int, Int) -> Unit
) {
    val count = EqualizerGains.BAND_COUNT
    val colors = MaterialTheme.colorScheme
    var touch by remember(profileKey) { mutableStateOf<Pair<Int, Int>?>(null) }
    val latestProfile by rememberUpdatedState(profileKey)
    val latestCommit by rememberUpdatedState(onCommit)
    val latestPreview by rememberUpdatedState(onPreview)
    val latestSelect by rememberUpdatedState(onSelect)
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val trackAreaWidth = if (scrollTracks) maxOf(maxWidth, (count * 36).dp) else maxWidth
            Row(Modifier.horizontalScroll(rememberScrollState(), enabled = scrollTracks)) {
                Row(Modifier.width(trackAreaWidth).padding(top = 8.dp)
                    .pointerInput(profileKey, count, enabled, sliderHeight) {
                        if (!enabled) return@pointerInput
                        val height = sliderHeight.coerceIn(120.dp, 240.dp).toPx()
                        val halfThumb = 20.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            if (down.position.y !in 0f..height) return@awaitEachGesture
                            val band = (down.position.x / size.width * count).toInt().coerceIn(0, count - 1)
                            fun gain(y: Float) = (100f - (y - halfThumb) * 200f /
                                (height - 2 * halfThumb)).roundToInt().coerceIn(-100, 100)
                            var value = gain(down.position.y)
                            var vertical = false
                            down.consume()
                            latestSelect(band)
                            touch = band to value
                            latestPreview(touch)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.isConsumed) break
                                    val dx = abs(change.position.x - down.position.x)
                                    val dy = abs(change.position.y - down.position.y)
                                    // A horizontal gesture remains available to the wide-layout scroller.
                                    if (!vertical && dx > viewConfiguration.touchSlop && dx > dy) break
                                    if (dy > viewConfiguration.touchSlop) vertical = true
                                    value = gain(change.position.y)
                                    touch = band to value
                                    latestPreview(touch)
                                    if (vertical || !change.pressed) change.consume()
                                    if (!change.pressed) {
                                        if (latestProfile == profileKey) latestCommit(band, value)
                                        break
                                    }
                                }
                            } finally {
                                touch = null
                                latestPreview(null)
                            }
                        }
                    }) {
                    repeat(count) { band ->
                        key(profileKey, count, band) {
                            val source = remember { MutableInteractionSource() }
                            val dragged by source.collectIsDraggedAsState()
                            val pressed by source.collectIsPressedAsState()
                            val interacting = dragged || pressed || touch?.first == band
                            val state = remember { SliderState(gains[band].gain.toFloat(), steps = 199, valueRange = -100f..100f) }
                            state.onValueChangeFinished = {
                                if (latestProfile == profileKey) {
                                    latestSelect(band)
                                    latestCommit(band, state.value.roundToInt().coerceIn(-100, 100))
                                }
                                latestPreview(null)
                            }
                            LaunchedEffect(gains[band].gain, interacting, touch) {
                                val pointer = touch?.takeIf { it.first == band }
                                if (pointer != null) state.value = pointer.second.toFloat()
                                else if (!interacting) state.value = gains[band].gain.toFloat()
                            }
                            LaunchedEffect(state, interacting) {
                                if (dragged || pressed) snapshotFlow { state.value.roundToInt() }.collect {
                                    latestSelect(band)
                                    latestPreview(band to it.coerceIn(-100, 100))
                                }
                            }
                            DisposableEffect(state) { onDispose { latestPreview(null) } }
                            val trackWidth by animateDpAsState(if (interacting) 8.dp else 12.dp,
                                MaterialTheme.motionScheme.fastSpatialSpec(), label = "vertical track")
                            val handleFraction by androidx.compose.animation.core.animateFloatAsState(
                                if (interacting) .75f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "vertical handle")
                            val cellWidth = trackAreaWidth / count
                            val label = stringResource(R.string.dolby_geq_band_frequency, band + 1, frequencies[band])
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                // Pointer ownership is resolved once by the row, before child hit targets.
                                // Material semantics and keyboard adjustment remain available per band.
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    VerticalSlider(state = state, reverseDirection = true, enabled = enabled,
                                        interactionSource = source,
                                        modifier = Modifier.width(cellWidth).height(sliderHeight.coerceIn(120.dp, 240.dp))
                                            .semantics { contentDescription = label },
                                        thumb = {
                                            Canvas(Modifier.width(cellWidth).height(40.dp)) {
                                                val fill = if (enabled) colors.primary else colors.surfaceContainerHighest
                                                val handleWidth = minOf(18.dp.toPx(), size.width - 4.dp.toPx()) * handleFraction.coerceIn(.5f, 1f)
                                                drawRoundRect(fill, topLeft = Offset((size.width - handleWidth) / 2, 0f),
                                                    size = Size(handleWidth, size.height), cornerRadius = CornerRadius(8.dp.toPx()))
                                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                    color = if (enabled) colors.onPrimary.toArgb() else colors.onSurfaceVariant.toArgb()
                                                    textSize = 9.sp.toPx()
                                                    textAlign = Paint.Align.CENTER
                                                }
                                                val text = String.format(java.util.Locale.getDefault(), "%+.1f", state.value / 10f)
                                                val available = size.height - 4.dp.toPx()
                                                if (paint.measureText(text) > available) paint.textSize *= available / paint.measureText(text)
                                                val canvas = drawContext.canvas.nativeCanvas
                                                val x = size.width / 2; val y = size.height / 2
                                                canvas.save()
                                                canvas.rotate(-90f, x, y)
                                                canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint)
                                                canvas.restore()
                                            }
                                        },
                                        track = { slider ->
                                            // Both slots use the full cell width; paint around one shared axis.
                                            // Shrinking the thumb changes its drawing, never its measured origin.
                                            Canvas(Modifier.width(cellWidth).fillMaxHeight()) {
                                                val width = minOf(trackWidth.toPx(), size.width - 4.dp.toPx()).coerceAtLeast(1f)
                                                val x = (size.width - width) / 2
                                                val y = size.height * (100f - slider.value) / 200f
                                                val radius = CornerRadius(width / 2)
                                                drawRoundRect(colors.surfaceContainerHighest, Offset(x, 0f),
                                                    Size(width, size.height), radius)
                                                drawRoundRect(if (enabled) colors.primary else colors.onSurface.copy(alpha = .38f),
                                                    Offset(x, y), Size(width, size.height - y), radius)
                                            }
                                        })
                                }
                                val frequency = if (frequencies[band] < 1000) frequencies[band].toString()
                                    else String.format(java.util.Locale.getDefault(), "%.1fk", frequencies[band] / 1000f)
                                SliderLabel(frequency)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderLabel(text: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val density = androidx.compose.ui.platform.LocalDensity.current
    val paint = remember(density, color) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = Paint.Align.CENTER
        }
    }
    val vertical = true
    val labelHeight = with(density) {
        if (vertical) (paint.measureText("19.7k") + 12.dp.toPx()).toDp()
        else (paint.descent() - paint.ascent() + 8.dp.toPx()).toDp()
    }
    // Every cell reserves the same height; rotation happens around the track center,
    // never around an oversized Text layout that extends into neighboring bands.
    Canvas(Modifier.fillMaxWidth().height(if (vertical) maxOf(52.dp, labelHeight) else labelHeight)) {
        val x = size.width / 2
        val y = size.height / 2
        val canvas = drawContext.canvas.nativeCanvas
        val labelPaint = Paint(paint)
        val availableCross = if (vertical) size.width - 2.dp.toPx() else size.height
        val fontHeight = labelPaint.descent() - labelPaint.ascent()
        if (fontHeight > availableCross) labelPaint.textSize *= availableCross / fontHeight
        if (!vertical && labelPaint.measureText(text) > size.width - 2.dp.toPx()) {
            labelPaint.textSize *= (size.width - 2.dp.toPx()) / labelPaint.measureText(text)
        }
        canvas.save()
        if (vertical) canvas.rotate(-90f, x, y)
        canvas.drawText(text, x, y - (labelPaint.ascent() + labelPaint.descent()) / 2, labelPaint)
        canvas.restore()
    }
}
