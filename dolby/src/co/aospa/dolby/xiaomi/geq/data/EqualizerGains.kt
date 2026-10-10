/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.geq.data

/** Lossless representation of the twenty values accepted by the existing DAP bridge. */
object EqualizerGains {
    const val BAND_COUNT = 20
    const val MIN_GAIN = -100
    const val MAX_GAIN = 100

    fun parse(value: String): IntArray {
        val parts = value.split(",")
        require(parts.size == BAND_COUNT) { "Expected $BAND_COUNT equalizer bands" }
        return parts.map { it.trim().toInt() }.toIntArray().also(::validate)
    }

    fun validate(values: IntArray) {
        require(values.size == BAND_COUNT) { "Expected $BAND_COUNT equalizer bands" }
        require(values.all { it in MIN_GAIN..MAX_GAIN }) { "Equalizer gain out of range" }
    }

    fun canShift(bands: List<BandGain>, amount: Int): Boolean =
        bands.size == BAND_COUNT && bands.all {
            it.gain.toLong() + amount.toLong() in MIN_GAIN.toLong()..MAX_GAIN.toLong()
        }

    fun shift(bands: List<BandGain>, amount: Int): List<BandGain> {
        encode(bands) // Validate order and values before editing.
        require(canShift(bands, amount)) { "Adjustment exceeds equalizer bounds" }
        return bands.map { it.copy(gain = it.gain + amount) }
    }

    fun decode(value: String): List<BandGain> =
        parse(value).mapIndexed { index, gain -> BandGain(index + 1, gain) }

    fun encode(bands: List<BandGain>): String {
        require(bands.map { it.band } == (1..BAND_COUNT).toList()) {
            "Equalizer bands must be ordered from 1 to $BAND_COUNT"
        }
        val gains = bands.map { it.gain }.toIntArray()
        validate(gains)
        return gains.joinToString(",")
    }
}
