/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

internal val LocalDossierTheme = staticCompositionLocalOf { false }
internal const val THEME_KEY = "dossier_theme"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DolbyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("dolby_appearance", Context.MODE_PRIVATE)
    }
    var dossier by remember { mutableStateOf(preferences.getBoolean(THEME_KEY, false)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == THEME_KEY) dossier = preferences.getBoolean(THEME_KEY, false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    SettingsTheme {
        val dark = isSystemInDarkTheme()
        val colors = if (!dossier) MaterialTheme.colorScheme else if (dark) {
            darkColorScheme(
                primary = Color(0xFFFFB4A8), onPrimary = Color(0xFF520C05),
                primaryContainer = Color(0xFF8B241C), onPrimaryContainer = Color(0xFFFFDAD3),
                secondary = Color(0xFFE4C4BC), secondaryContainer = Color(0xFF40312F),
                onSecondaryContainer = Color(0xFFF8E9DF),
                background = Color(0xFF101112), surface = Color(0xFF101112),
                surfaceContainer = Color(0xFF202123), surfaceContainerLow = Color(0xFF191A1C),
                surfaceContainerHigh = Color(0xFF292A2D), surfaceContainerHighest = Color(0xFF333437),
                onSurface = Color(0xFFF4EEE5), onSurfaceVariant = Color(0xFFCFC3BA),
                outlineVariant = Color(0xFF574945)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFA52C21), onPrimary = Color.White,
                primaryContainer = Color(0xFFFFDAD3), onPrimaryContainer = Color(0xFF400500),
                secondaryContainer = Color(0xFFEBDCD4), onSecondaryContainer = Color(0xFF302521),
                background = Color(0xFFF4EEE5), surface = Color(0xFFF4EEE5),
                surfaceContainer = Color(0xFFE8E1D7), surfaceContainerLow = Color(0xFFF0E9DF),
                onSurface = Color(0xFF1E1B19), onSurfaceVariant = Color(0xFF54443E),
                outlineVariant = Color(0xFFBFAFA4)
            )
        }
        val headingFont = remember {
            FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
        }
        val type = MaterialTheme.typography
        CompositionLocalProvider(LocalDossierTheme provides dossier) {
            MaterialExpressiveTheme(
                colorScheme = colors,
                typography = if (dossier) type.copy(
                    headlineSmall = type.headlineSmall.copy(fontFamily = headingFont, fontWeight = FontWeight.Black),
                    headlineLarge = type.headlineLarge.copy(fontFamily = headingFont, fontWeight = FontWeight.Black),
                    headlineMedium = type.headlineMedium.copy(fontFamily = headingFont, fontWeight = FontWeight.Black),
                    titleLarge = type.titleLarge.copy(fontFamily = headingFont, fontWeight = FontWeight.Bold),
                    titleMedium = type.titleMedium.copy(fontFamily = headingFont, fontWeight = FontWeight.Bold),
                    titleSmall = type.titleSmall.copy(fontFamily = headingFont, fontWeight = FontWeight.Bold),
                    labelLarge = type.labelLarge.copy(fontFamily = FontFamily.Monospace)
                ) else type,
                shapes = if (dossier) Shapes(
                    small = CutCornerShape(4.dp), medium = CutCornerShape(8.dp),
                    large = CutCornerShape(12.dp), extraLarge = CutCornerShape(16.dp)
                ) else MaterialTheme.shapes,
                motionScheme = MotionScheme.expressive(),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveChoice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            RoundedCornerShape(28.dp)
        )
    ) {
        val width = maxWidth / labels.size
        val offset by animateDpAsState(
            width * selected,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "tonal thumb"
        )
        Box(
            Modifier.offset(x = offset, y = 4.dp).width(width).height(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp))
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = {
                        if (selected != index) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onSelect(index)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                    border = BorderStroke(0.dp, Color.Transparent),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color.Transparent,
                        inactiveContainerColor = Color.Transparent
                    )
                ) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveActions(
    labels: List<String>,
    enabled: List<Boolean>,
    actions: List<() -> Unit>
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, text ->
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val outer by animateDpAsState(
                if (pressed) 12.dp else 28.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "action outer corner"
            )
            val inner by animateDpAsState(
                if (pressed) 12.dp else 4.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "action inner corner"
            )
            val start = if (index == 0) outer else inner
            val end = if (index == labels.lastIndex) outer else inner
            FilledTonalButton(
                onClick = actions[index],
                enabled = enabled[index],
                interactionSource = source,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(start, end, end, start),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
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
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                previous = next
            }
        }
    }
}

@Composable
internal fun ExpressiveValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val haptic = rememberTuningHaptics(range.start.toInt(), range.endInclusive.toInt())
    val minimum = range.start.roundToInt()
    val maximum = range.endInclusive.roundToInt()
    val steps = (maximum - minimum - 1).coerceAtLeast(0)

    Slider(
        value = value.coerceIn(range),
        onValueChange = {
            val rounded = it.roundToInt().coerceIn(minimum, maximum)
            haptic(0, rounded)
            onValueChange(rounded.toFloat())
        },
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        valueRange = range,
        steps = steps,
        onValueChangeFinished = onFinished,
        interactionSource = source
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
            content.setRenderEffect(
                RenderEffect.createBlurEffect(16f, 16f, Shader.TileMode.CLAMP)
            )
        }
        onDispose {
            if (active) {
                val remaining = (blurOwners[content] ?: 1) - 1
                if (remaining <= 0) {
                    blurOwners.remove(content)
                    content.setRenderEffect(null)
                } else {
                    blurOwners[content] = remaining
                }
            }
        }
    }
}

/** Display the supplied artwork intact; keep branding independent of system fonts. */
@Composable
internal fun DossierHeader() {
    val dossier = LocalDossierTheme.current
    val artwork = androidx.compose.ui.res.painterResource(
        if (dossier) co.aospa.dolby.xiaomi.R.drawable.dolby_atmos_dossier
        else co.aospa.dolby.xiaomi.R.drawable.dolby_atmos_logo
    )
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = artwork.intrinsicSize.width / artwork.intrinsicSize.height
        // Bound height on tablets/landscape; narrow windows retain the complete image.
        val heightLimit = if (dossier) 220.dp else 88.dp
        val width = minOf(maxWidth, heightLimit * ratio)
        androidx.compose.foundation.Image(
            painter = artwork,
            contentDescription = androidx.compose.ui.res.stringResource(co.aospa.dolby.xiaomi.R.string.dolby_title),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            colorFilter = if (dossier) null else androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.width(width).aspectRatio(ratio)
        )
    }
}

@Composable
internal fun DossierBackdrop(modifier: Modifier = Modifier) {
    if (!LocalDossierTheme.current) return
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val flecks = remember {
        val random = kotlin.random.Random(650)
        List(700) { Triple(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    androidx.compose.foundation.Canvas(modifier) {
        // Fixed texture: redraw only with normal Compose invalidation, never animate noise.
        flecks.forEach { (x, y, strength) ->
            drawCircle(ink.copy(alpha = .025f + strength * .035f),
                .4.dp.toPx() + strength * 1.2.dp.toPx(),
                androidx.compose.ui.geometry.Offset(x * size.width, y * size.height))
        }
        val slash = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width, size.height * .35f)
            lineTo(size.width, size.height * .68f)
            lineTo(0f, size.height)
            lineTo(0f, size.height * .92f)
            close()
        }
        drawPath(slash, ink.copy(alpha = .04f))
        drawLine(accent.copy(alpha = .4f),
            androidx.compose.ui.geometry.Offset(0f, size.height * .92f),
            androidx.compose.ui.geometry.Offset(size.width, size.height * .35f), 1.dp.toPx())
    }
}

@Composable
internal fun DossierSection(number: String, title: String) {
    if (LocalDossierTheme.current) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ink = MaterialTheme.colorScheme.onSurface
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                val y = 1.dp.toPx()
                drawLine(ink.copy(alpha = .8f), androidx.compose.ui.geometry.Offset(0f, y),
                    androidx.compose.ui.geometry.Offset(size.width, y), 1.dp.toPx())
                drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * .78f, y + 1.dp.toPx()),
                    androidx.compose.ui.geometry.Offset(size.width, y + 1.dp.toPx()), 3.dp.toPx())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(number + " /", color = Color(0xFFEF443B),
                    style = MaterialTheme.typography.titleLarge)
                Text(title.uppercase(java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = ink.copy(alpha = .65f), thickness = .5.dp)
        }
    } else {
        Text(title, modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun DolbyHeadline(text: String) {
    Text(if (LocalDossierTheme.current) text.uppercase(java.util.Locale.getDefault()) else text,
        style = if (LocalDossierTheme.current) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.bodyLarge)
}
