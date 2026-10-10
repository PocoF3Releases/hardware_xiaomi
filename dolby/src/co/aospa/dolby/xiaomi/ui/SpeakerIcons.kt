/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.ui

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

internal object SpeakerIcons {
    private fun device(name: String, data: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    PathParser().parsePathString(data).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            .build()

    val portrait =
        device(
            "portrait",
            "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2ZM11 18h2",
        )
    val landscape =
        device(
            "landscape",
            "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM18 11v2",
        )
    val off =
        device(
            "off",
            "M3 3l18 18M9 3a9 9 0 0 1 12 9c0 2-.6 3.7-1.6 5.2M6 5.4A9 9 0 0 0 12 21c2 0 3.8-.6 5.3-1.7",
        )
    val warm =
        device(
            "warm",
            "M5 4c-4 4 4 4 0 8s4 4 0 8M12 4c-4 4 4 4 0 8s4 4 0 8M19 4c-4 4 4 4 0 8s4 4 0 8",
        )
}
