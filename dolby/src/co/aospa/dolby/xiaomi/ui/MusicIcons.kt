/*
 * Lucide artwork: Copyright (c) 2026 Lucide Icons and Contributors, ISC.
 * Custom music/EQ artwork: Copyright (C) 2026 The LineageOS Project, Apache-2.0.
 * Full provenance: dolby/licenses/misound-icons.md.
 */
package co.aospa.dolby.xiaomi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object MusicIcons {
    private fun outline(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .apply {
                paths.forEach { path ->
                    addPath(
                        pathData = PathParser().parsePathString(path).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    val disc_3 =
        outline(
            "disc-3",
            "M2.0,12.0a10.0,10.0 0 1,0 20.0,0a10.0,10.0 0 1,0 -20.0,0",
            "M6 12c0-1.7.7-3.2 1.8-4.2",
            "M10.0,12.0a2.0,2.0 0 1,0 4.0,0a2.0,2.0 0 1,0 -4.0,0",
            "M18 12c0 1.7-.7 3.2-1.8 4.2",
        )
    val mic_vocal =
        outline(
            "mic-vocal",
            "m11 7.601-5.994 8.19a1 1 0 0 0 .1 1.298l.817.818a1 1 0 0 0 1.314.087L15.09 12",
            "M16.5 21.174C15.5 20.5 14.372 20 13 20c-2.058 0-3.928 2.356-6 2-2.072-.356-2.775-3.369-1.5-4.5",
            "M11.0,7.0a5.0,5.0 0 1,0 10.0,0a5.0,5.0 0 1,0 -10.0,0",
        )
    val piano =
        outline(
            "piano",
            "M10 13v4",
            "M14 13v4",
            "M18 13v4",
            "M2 13h20",
            "M22 11.5A3.5 3.5 0 0018.5 8a3.52 3.52 0 01-3.173-2A7 7 0 002 9v10a2 2 0 002 2h16a2 2 0 002-2z",
            "M6 13v4",
        )
    val hand_metal =
        outline(
            "hand-metal",
            "M18 12.5V10a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1.4",
            "M14 11V9a2 2 0 1 0-4 0v2",
            "M10 10.5V5a2 2 0 1 0-4 0v9",
            "m7 15-1.76-1.76a2 2 0 0 0-2.83 2.82l3.6 3.6C7.5 21.14 9.2 22 12 22h2a8 8 0 0 0 8-8V7a2 2 0 1 0-4 0v5",
        )
    val audio_lines =
        outline("audio-lines", "M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")
    val sliders_vertical =
        outline(
            "sliders-vertical",
            "M10 8h4",
            "M12 21v-9",
            "M12 8V3",
            "M17 16h4",
            "M19 12V3",
            "M19 21v-5",
            "M3 14h4",
            "M5 10V3",
            "M5 21v-7",
        )

    // Custom supplements share the source grid, stroke and rounded terminals.
    val electric_guitar =
        outline(
            "electric_guitar",
            "M13 11l6-6 2 2-6 6",
            "M19 5l1-3 2 2-1 3",
            "M13 11c-2-2-4-2-5 0l-1 2-4 2 1 5 5 1 2-4 2-1c2-1 2-3 0-5Z",
            "M7 16l1 1",
        )
    val saxophone =
        outline(
            "saxophone",
            "M7 3h3v11a3 3 0 0 0 6 0v-3h4v3a7 7 0 0 1-14 0V6L4 4",
            "M16 11l-1-3 6 1-1 2",
            "M10 8h2M10 11h2",
        )
    val harmonica =
        outline(
            "harmonica",
            "M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z",
            "M2 10h20M6 10v5M10 10v5M14 10v5M18 10v5",
        )
}
