/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.data

import java.util.Locale
import kotlin.math.roundToInt

/** Equal-width band slots; labels retain the engine's actual center frequencies. */
object EqualizerGraphScale {
    // DMS forwards Q4 gain integers unchanged: one raw unit is 1/16 dB.
    // This conversion is for labels only; curve geometry and saved gains stay raw.
    fun decibels(gain: Float): Float = gain / 16f

    fun axisLabel(gain: Int, locale: Locale): String = String.format(
        locale, if (gain == 0) "%.0f" else "%+.2f", decibels(gain.toFloat()))

    fun x(index: Int, count: Int): Float {
        require(count > 1 && index in 0 until count)
        return index.toFloat() / (count - 1)
    }

    fun nearestBand(position: Float, count: Int): Int {
        require(count > 1)
        return (position.coerceIn(0f, 1f) * (count - 1)).roundToInt()
    }

    fun y(gain: Int): Float = (100 - gain.coerceIn(-100, 100)) / 200f
    fun gain(y: Float): Int = ((1f - y.coerceIn(0f, 1f)) * 200f - 100f).roundToInt()
}
