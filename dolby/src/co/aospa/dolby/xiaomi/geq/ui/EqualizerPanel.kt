/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CutCornerShape
import co.aospa.dolby.xiaomi.ui.LocalDossierTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Use the same dynamic surface and group corners as the native Settings preference adapter. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EqualizerPanel(
    modifier: Modifier = Modifier,
    connectedAbove: Boolean = false,
    connectedBelow: Boolean = false,
    content: @Composable () -> Unit
) {
    val dossier = LocalDossierTheme.current
    val ink = MaterialTheme.colorScheme.onSurface
    val radius = 24.dp
    val top = if (connectedAbove) 4.dp else radius
    val bottom = if (connectedBelow) 4.dp else radius
    Surface(
        modifier = modifier.drawWithContent {
            drawContent()
            if (dossier) {
                drawLine(ink.copy(alpha = .7f), Offset(2.dp.toPx(), 8.dp.toPx()),
                    Offset(2.dp.toPx(), size.height - 12.dp.toPx()), .7.dp.toPx())
                drawLine(Color(0xFFCF302A), Offset(12.dp.toPx(), size.height - 2.dp.toPx()),
                    Offset(size.width - 18.dp.toPx(), size.height - 2.dp.toPx()), 1.dp.toPx())
            }
        }.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = if (LocalDossierTheme.current) CutCornerShape(12.dp) else RoundedCornerShape(
            topStart = top,
            topEnd = top,
            bottomStart = bottom,
            bottomEnd = bottom
        ),
        content = content
    )
}
