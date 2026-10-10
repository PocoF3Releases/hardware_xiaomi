/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import co.aospa.dolby.xiaomi.DolbyConstants.DsParam

/** App-facing DAP wire format. DMS fourccs and AudioFlinger-internal commands do not belong here. */
internal object DolbyWireCodec {
    const val SELECTED_TUNING = 4 // ds_config.h
    const val CPDP_VALUES = 5
    const val ENABLE = 0
    const val PROFILE = 0x0A000000
    const val RESET_PROFILE = 0x0C000000
    private const val SET_PROFILE_PARAMETER = 0x01000000
    private const val GET_PROFILE_PARAMETER = 0x01000005

    fun ints(vararg values: Int): ByteArray = ByteArray(values.size * 4).also { bytes ->
        values.forEachIndexed { index, value ->
            repeat(4) { byte -> bytes[index * 4 + byte] = (value ushr (byte * 8)).toByte() }
        }
    }

    fun scalar(event: Int, value: Int): ByteArray = ints(event, 1, value)

    fun profile(profile: Int, param: DsParam, values: IntArray): ByteArray {
        require(profile in 0..255) { "Invalid DAP profile" }
        require(values.size == param.length) { "Invalid payload length for $param" }
        // The legacy length field includes the profile, but not the parameter ID.
        return ints(SET_PROFILE_PARAMETER, values.size + 1, profile, param.id, *values)
    }

    fun profileQuery(profile: Int, param: DsParam): Int {
        require(profile in 0..255) { "Invalid DAP profile" }
        return GET_PROFILE_PARAMETER + (profile shl 8) + (param.id shl 16)
    }

    fun tuning(port: Int, id: String): ByteArray {
        require(port in 0..5) { "Invalid Dolby endpoint port" }
        // 255 is an app allocation limit, not a claim about the vendor catalog's maximum.
        require(id.length in 1..255 && id.all { it.code in 0x20..0x7e }) {
            "Invalid Dolby tuning ID"
        }
        // Stock DolbyEffectController: little-endian port + ASCII ID, no NUL terminator.
        return ints(port) + id.toByteArray(Charsets.US_ASCII)
    }

    fun decode(bytes: ByteArray, returnedSize: Int, count: Int): IntArray {
        require(count > 0 && count <= bytes.size / 4)
        check(returnedSize in count * 4..bytes.size && returnedSize % 4 == 0) {
            "Malformed DAP response: $returnedSize bytes for $count integers"
        }
        return IntArray(count) { index ->
            var value = 0
            repeat(4) { byte ->
                value = value or ((bytes[index * 4 + byte].toInt() and 0xff) shl (byte * 8))
            }
            value
        }
    }
}
