/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val radius = 24.dp
    val top = if (connectedAbove) 4.dp else radius
    val bottom = if (connectedBelow) 4.dp else radius
    Surface(
        modifier = modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(
            topStart = top,
            topEnd = top,
            bottomStart = bottom,
            bottomEnd = bottom
        ),
        content = content
    )
}
