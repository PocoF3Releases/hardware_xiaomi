/*
 * Material Symbols by Google
 * SPDX-License-Identifier: Apache-2.0
 * Official Rounded, 24px, weight 400, grade 0, fill 0.
 * Source/provenance: dolby/licenses/material-symbols.md
 */
package co.aospa.dolby.xiaomi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object DialogSymbols {
    val auto_awesome =
        ImageVector.Builder("auto_awesome", 24.dp, 24.dp, 24f, 24f)
            .apply {
                addPath(
                    pathData =
                        PathParser()
                            .parsePathString(
                                "M19,8.3Q18.875,8.3 18.738,8.225Q18.6,8.15 18.55,8L17.75,6.25L16,5.45Q15.85,5.4 15.775,5.262Q15.7,5.125 15.7,5Q15.7,4.875 15.775,4.737Q15.85,4.6 16,4.55L17.75,3.75L18.55,2Q18.6,1.85 18.738,1.775Q18.875,1.7 19,1.7Q19.125,1.7 19.263,1.775Q19.4,1.85 19.45,2L20.25,3.75L22,4.55Q22.15,4.6 22.225,4.737Q22.3,4.875 22.3,5Q22.3,5.125 22.225,5.262Q22.15,5.4 22,5.45L20.25,6.25L19.45,8Q19.4,8.15 19.263,8.225Q19.125,8.3 19,8.3ZM19,22.3Q18.875,22.3 18.738,22.225Q18.6,22.15 18.55,22L17.75,20.25L16,19.45Q15.85,19.4 15.775,19.262Q15.7,19.125 15.7,19Q15.7,18.875 15.775,18.738Q15.85,18.6 16,18.55L17.75,17.75L18.55,16Q18.6,15.85 18.738,15.775Q18.875,15.7 19,15.7Q19.125,15.7 19.263,15.775Q19.4,15.85 19.45,16L20.25,17.75L22,18.55Q22.15,18.6 22.225,18.738Q22.3,18.875 22.3,19Q22.3,19.125 22.225,19.262Q22.15,19.4 22,19.45L20.25,20.25L19.45,22Q19.4,22.15 19.263,22.225Q19.125,22.3 19,22.3ZM9,18.575Q8.725,18.575 8.475,18.425Q8.225,18.275 8.1,18L6.5,14.5L3,12.9Q2.725,12.775 2.575,12.525Q2.425,12.275 2.425,12Q2.425,11.725 2.575,11.475Q2.725,11.225 3,11.1L6.5,9.5L8.1,6Q8.225,5.725 8.475,5.575Q8.725,5.425 9,5.425Q9.275,5.425 9.525,5.575Q9.775,5.725 9.9,6L11.5,9.5L15,11.1Q15.275,11.225 15.425,11.475Q15.575,11.725 15.575,12Q15.575,12.275 15.425,12.525Q15.275,12.775 15,12.9L11.5,14.5L9.9,18Q9.775,18.275 9.525,18.425Q9.275,18.575 9,18.575ZM9,15.15 L10,13 12.15,12 10,11 9,8.85 8,11 5.85,12 8,13ZM9,12Z"
                            )
                            .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    val music_note =
        ImageVector.Builder("music_note", 24.dp, 24.dp, 960f, 960f)
            .apply {
                addPath(
                    pathData =
                        PathParser()
                            .parsePathString(
                                "M400,840Q334,840 287,793Q240,746 240,680Q240,614 287,567Q334,520 400,520Q423,520 442.5,525.5Q462,531 480,542L480,160Q480,143 491.5,131.5Q503,120 520,120L680,120Q697,120 708.5,131.5Q720,143 720,160L720,240Q720,257 708.5,268.5Q697,280 680,280L560,280L560,680Q560,746 513,793Q466,840 400,840Z"
                            )
                            .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    val movie =
        ImageVector.Builder("movie", 24.dp, 24.dp, 960f, 960f)
            .apply {
                addPath(
                    pathData =
                        PathParser()
                            .parsePathString(
                                "M160,160L225,290Q232,304 245,312Q258,320 273,320Q303,320 319,294.5Q335,269 321,242L280,160L360,160L425,290Q432,304 445,312Q458,320 473,320Q503,320 519,294.5Q535,269 521,242L480,160L560,160L625,290Q632,304 645,312Q658,320 673,320Q703,320 719,294.5Q735,269 721,242L680,160L800,160Q833,160 856.5,183.5Q880,207 880,240L880,720Q880,753 856.5,776.5Q833,800 800,800L160,800Q127,800 103.5,776.5Q80,753 80,720L80,240Q80,207 103.5,183.5Q127,160 160,160L160,160ZM160,400L160,720Q160,720 160,720Q160,720 160,720L800,720Q800,720 800,720Q800,720 800,720L800,400L160,400ZM160,400L160,400L160,720Q160,720 160,720Q160,720 160,720L160,720Q160,720 160,720Q160,720 160,720L160,400Z"
                            )
                            .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    val mic =
        ImageVector.Builder("mic", 24.dp, 24.dp, 960f, 960f)
            .apply {
                addPath(
                    pathData =
                        PathParser()
                            .parsePathString(
                                "M480,560Q430,560 395,525Q360,490 360,440L360,200Q360,150 395,115Q430,80 480,80Q530,80 565,115Q600,150 600,200L600,440Q600,490 565,525Q530,560 480,560ZM480,320Q480,320 480,320Q480,320 480,320L480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320Q480,320 480,320L480,320Q480,320 480,320Q480,320 480,320ZM440,800L440,717Q348,704 282.5,639Q217,574 203,481Q201,464 212,452Q223,440 240,440Q257,440 268.5,451.5Q280,463 284,480Q298,550 353.5,595Q409,640 480,640Q552,640 607,594.5Q662,549 676,480Q680,463 691.5,451.5Q703,440 720,440Q737,440 748,452Q759,464 757,481Q743,572 678,638Q613,704 520,717L520,800Q520,817 508.5,828.5Q497,840 480,840Q463,840 451.5,828.5Q440,817 440,800ZM480,480Q497,480 508.5,468.5Q520,457 520,440L520,200Q520,183 508.5,171.5Q497,160 480,160Q463,160 451.5,171.5Q440,183 440,200L440,440Q440,457 451.5,468.5Q463,480 480,480Z"
                            )
                            .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
    val tune =
        ImageVector.Builder("tune", 24.dp, 24.dp, 960f, 960f)
            .apply {
                addPath(
                    pathData =
                        PathParser()
                            .parsePathString(
                                "M480,840Q463,840 451.5,828.5Q440,817 440,800L440,640Q440,623 451.5,611.5Q463,600 480,600Q497,600 508.5,611.5Q520,623 520,640L520,680L800,680Q817,680 828.5,691.5Q840,703 840,720Q840,737 828.5,748.5Q817,760 800,760L520,760L520,800Q520,817 508.5,828.5Q497,840 480,840ZM160,760Q143,760 131.5,748.5Q120,737 120,720Q120,703 131.5,691.5Q143,680 160,680L320,680Q337,680 348.5,691.5Q360,703 360,720Q360,737 348.5,748.5Q337,760 320,760L160,760ZM320,600Q303,600 291.5,588.5Q280,577 280,560L280,520L160,520Q143,520 131.5,508.5Q120,497 120,480Q120,463 131.5,451.5Q143,440 160,440L280,440L280,400Q280,383 291.5,371.5Q303,360 320,360Q337,360 348.5,371.5Q360,383 360,400L360,560Q360,577 348.5,588.5Q337,600 320,600ZM480,520Q463,520 451.5,508.5Q440,497 440,480Q440,463 451.5,451.5Q463,440 480,440L800,440Q817,440 828.5,451.5Q840,463 840,480Q840,497 828.5,508.5Q817,520 800,520L480,520ZM640,360Q623,360 611.5,348.5Q600,337 600,320L600,160Q600,143 611.5,131.5Q623,120 640,120Q657,120 668.5,131.5Q680,143 680,160L680,200L800,200Q817,200 828.5,211.5Q840,223 840,240Q840,257 828.5,268.5Q817,280 800,280L680,280L680,320Q680,337 668.5,348.5Q657,360 640,360ZM160,280Q143,280 131.5,268.5Q120,257 120,240Q120,223 131.5,211.5Q143,200 160,200L480,200Q497,200 508.5,211.5Q520,223 520,240Q520,257 508.5,268.5Q497,280 480,280L160,280Z"
                            )
                            .toNodes(),
                    fill = SolidColor(Color.Black),
                )
            }
            .build()
}

@Composable
internal fun SelectionIcon(image: ImageVector, selected: Boolean) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                image,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun profileIcon(base: Int): ImageVector =
    when (base) {
        0 -> DialogSymbols.auto_awesome
        1 -> DialogSymbols.movie
        2 -> DialogSymbols.music_note
        8 -> DialogSymbols.mic
        else -> DialogSymbols.tune
    }
