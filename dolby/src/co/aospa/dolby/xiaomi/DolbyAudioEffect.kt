/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.media.audiofx.AudioEffect
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyConstants.DsParam
import co.aospa.dolby.xiaomi.geq.data.EqualizerGains
import java.util.UUID

internal class DolbyAudioEffect(priority: Int, audioSession: Int) : AudioEffect(
    EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, priority, audioSession
) {

    override fun hasControl(): Boolean = try {
        super.hasControl()
    } catch (_: IllegalStateException) {
        false
    }

    var dsOn: Boolean
        get() = getIntParam(EFFECT_PARAM_ENABLE) == 1
        set(value) {
            if (!value) {
                disableProcessing()
            } else {
                try {
                    setIntParam(EFFECT_PARAM_ENABLE, 1)
                    checkStatus(setEnabled(true))
                    check(enabled) { "Dolby framework gate did not enable" }
                } catch (failure: RuntimeException) {
                    try {
                        disableProcessing()
                    } catch (cleanup: RuntimeException) {
                        if (failure !== cleanup) failure.addSuppressed(cleanup)
                    }
                    throw failure
                }
            }
        }

    private fun disableProcessing() {
        var failure: RuntimeException? = null
        // Both gates must be attempted; a failed framework call cannot skip native off.
        try {
            checkStatus(setEnabled(false))
        } catch (error: RuntimeException) {
            failure = error
        }
        try {
            setIntParam(EFFECT_PARAM_ENABLE, 0)
        } catch (error: RuntimeException) {
            val first = failure
            if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    var profile: Int
        get() = getIntParam(EFFECT_PARAM_PROFILE)
        set(value) {
            setIntParam(EFFECT_PARAM_PROFILE, value)
        }

    /** Stock DolbyEffectController parameter 4: LE port followed by tuning-ID bytes. */
    fun setSelectedTuningDevice(port: Int, device: String) {
        require(port in 0..5) { "Invalid Dolby endpoint port" }
        require(device.isNotEmpty() && device.all { it.code in 0x20..0x7e }) {
            "Dolby tuning ID must be printable ASCII"
        }
        check(hasControl()) { "Dolby effect control unavailable" }
        val id = device.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(4 + id.size)
        int32ToByteArray(port, payload, 0)
        id.copyInto(payload, destinationOffset = 4)
        checkStatus(setParameter(4, payload))
    }

    private fun setIntParam(param: Int, value: Int) {
        dlog(TAG, "setIntParam($param, $value)")
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        int32ToByteArray(1, buf, 4)
        int32ToByteArray(value, buf, 8)
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    private fun getIntParam(param: Int): Int {
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        val size = getParameter(EFFECT_PARAM_CPDP_VALUES + param, buf)
        checkStatus(size)
        check(size in 4..buf.size) { "Invalid Dolby scalar response: $size bytes" }
        return byteArrayToInt32(buf).also {
            dlog(TAG, "getIntParam($param): $it")
        }
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        dlog(TAG, "resetProfileSpecificSettings: profile=$profile")
        setIntParam(EFFECT_PARAM_RESET_PROFILE_SETTINGS, profile)
    }

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int = this.profile) {
        dlog(TAG, "setDapParameter: profile=$profile param=$param")
        require(profile in 0..255) { "Profile does not fit the Dolby request" }
        require(values.size == param.length) { "Invalid payload length for $param" }
        if (param == DsParam.GEQ_BAND_GAINS) EqualizerGains.validate(values)
        val length = values.size
        val buf = ByteArray((length + 4) * 4)
        int32ToByteArray(EFFECT_PARAM_SET_PROFILE_PARAMETER, buf, 0)
        int32ToByteArray(length + 1, buf, 4)
        int32ToByteArray(profile, buf, 8)
        int32ToByteArray(param.id, buf, 12)
        int32ArrayToByteArray(values, buf, 16)
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    fun setDapParameter(param: DsParam, enable: Boolean, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(if (enable) 1 else 0), profile)

    fun setDapParameter(param: DsParam, value: Int, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(value), profile)

    fun getDapParameter(param: DsParam, profile: Int = this.profile): IntArray {
        dlog(TAG, "getDapParameter: profile=$profile param=$param")
        require(profile in 0..255) { "Profile does not fit the Dolby request" }
        val length = param.length
        val buf = ByteArray((length + 2) * 4)
        val p = (param.id shl 16) + (profile shl 8) + EFFECT_PARAM_GET_PROFILE_PARAMETER
        val size = getParameter(p, buf)
        checkStatus(size)
        check(size in length * 4..buf.size) { "Invalid Dolby response for $param: $size bytes" }
        return byteArrayToInt32Array(buf, length)
    }

    fun getDapParameterBool(param: DsParam, profile: Int = this.profile): Boolean =
        getDapParameter(param, profile)[0] == 1

    fun getDapParameterInt(param: DsParam, profile: Int = this.profile): Int =
        getDapParameter(param, profile)[0]

    companion object {
        private const val TAG = "DolbyAudioEffect"
        private val EFFECT_TYPE_DAP =
            UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")

        private const val EFFECT_PARAM_ENABLE = 0
        private const val EFFECT_PARAM_CPDP_VALUES = 5
        private const val EFFECT_PARAM_PROFILE = 0xA000000
        private const val EFFECT_PARAM_SET_PROFILE_PARAMETER = 0x1000000
        private const val EFFECT_PARAM_GET_PROFILE_PARAMETER = 0x1000005
        private const val EFFECT_PARAM_RESET_PROFILE_SETTINGS = 0xC000000

        private fun int32ToByteArray(value: Int, dst: ByteArray, index: Int) {
            var idx = index
            dst[idx++] = (value and 0xff).toByte()
            dst[idx++] = ((value ushr 8) and 0xff).toByte()
            dst[idx++] = ((value ushr 16) and 0xff).toByte()
            dst[idx] = ((value ushr 24) and 0xff).toByte()
        }

        private fun byteArrayToInt32(ba: ByteArray): Int {
            return ((ba[3].toInt() and 0xff) shl 24) or
                    ((ba[2].toInt() and 0xff) shl 16) or
                    ((ba[1].toInt() and 0xff) shl 8) or
                    (ba[0].toInt() and 0xff)
        }

        private fun int32ArrayToByteArray(src: IntArray, dst: ByteArray, index: Int) {
            var idx = index
            for (x in src) {
                dst[idx++] = (x and 0xff).toByte()
                dst[idx++] = ((x ushr 8) and 0xff).toByte()
                dst[idx++] = ((x ushr 16) and 0xff).toByte()
                dst[idx++] = ((x ushr 24) and 0xff).toByte()
            }
        }

        private fun byteArrayToInt32Array(ba: ByteArray, dstLength: Int): IntArray {
            val srcLength = ba.size shr 2
            val dst = IntArray(dstLength.coerceAtMost(srcLength))
            for (i in dst.indices) {
                dst[i] = ((ba[i * 4 + 3].toInt() and 0xff) shl 24) or
                        ((ba[i * 4 + 2].toInt() and 0xff) shl 16) or
                        ((ba[i * 4 + 1].toInt() and 0xff) shl 8) or
                        (ba[i * 4].toInt() and 0xff)
            }
            return dst
        }
    }
}
