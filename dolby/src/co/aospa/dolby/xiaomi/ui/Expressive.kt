/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.WeakHashMap
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DolbyTheme(content: @Composable () -> Unit) {
    SettingsTheme {
        MaterialExpressiveTheme(
            colorScheme = MaterialTheme.colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = Shapes(extraLarge = RoundedCornerShape(28.dp)),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveChoice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(28.dp))) {
        val width = maxWidth / labels.size
        val offset by animateDpAsState(width * selected,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "tonal thumb")
        Box(Modifier.offset(x = offset, y = 4.dp).width(width).height(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp)))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = {
                        if (selected != index) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                    border = BorderStroke(0.dp, Color.Transparent),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.Transparent, inactiveContainerColor = Color.Transparent
                    )
                ) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveActions(labels: List<String>, enabled: List<Boolean>, actions: List<() -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, text ->
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val outer by animateDpAsState(if (pressed) 12.dp else 28.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "action outer corner")
            val inner by animateDpAsState(if (pressed) 12.dp else 4.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "action inner corner")
            val start = if (index == 0) outer else inner
            val end = if (index == labels.lastIndex) outer else inner
            FilledTonalButton(
                onClick = actions[index], enabled = enabled[index], interactionSource = source,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(start, end, end, start),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) { Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
internal fun rememberTuningHaptics(min: Int = -100, max: Int = 100): (Int, Int) -> Unit {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var previous by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    return remember(haptic, view, scope, min, max) {
        { band, gain ->
            val next = band to gain
            if (next != previous) {
                if (gain == 0 || gain == min || gain == max) {
                    scope.launch {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        delay(48)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                } else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                previous = next
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveValueSlider(
    value: Float, onValueChange: (Float) -> Unit, onFinished: () -> Unit,
    range: ClosedFloatingPointRange<Float>, enabled: Boolean, label: (Float) -> String,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dragged by source.collectIsDraggedAsState()
    val height by animateDpAsState(if (pressed || dragged) 12.dp else 20.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "pressed track")
    val haptic = rememberTuningHaptics(range.start.toInt(), range.endInclusive.toInt())
    Slider(
        value = value, enabled = enabled, valueRange = range,
        steps = (range.endInclusive - range.start).toInt().minus(1).coerceAtLeast(0),
        interactionSource = source,
        onValueChange = {
            haptic(0, it.roundToInt())
            onValueChange(it.roundToInt().toFloat())
        },
        onValueChangeFinished = onFinished,
        thumb = {
            Surface(shape = RoundedCornerShape(16.dp),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(label(value), modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        },
        track = { state ->
            SliderDefaults.Track(state, modifier = Modifier.height(height),
                thumbTrackGapSize = 0.dp, drawStopIndicator = null,
                colors = SliderDefaults.colors(activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent, disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent))
        },
        modifier = modifier.heightIn(min = 48.dp)
    )
}

private val blurOwners = WeakHashMap<View, Int>()

/** Blur the live host window; the overlay renders in its own unblurred window. */
@Composable
internal fun BackdropBlur(active: Boolean = true) {
    val host = LocalView.current
    DisposableEffect(host, active) {
        // Resolve the current window, including a dialog hosting a nested dropdown.
        val content = host.rootView.findViewById<View>(android.R.id.content) ?: host
        if (active) {
            blurOwners[content] = (blurOwners[content] ?: 0) + 1
            content.setRenderEffect(RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP))
        }
        onDispose {
            if (active) {
                val remaining = (blurOwners[content] ?: 1) - 1
                if (remaining <= 0) { blurOwners.remove(content); content.setRenderEffect(null) }
                else blurOwners[content] = remaining
            }
        }
    }
}
