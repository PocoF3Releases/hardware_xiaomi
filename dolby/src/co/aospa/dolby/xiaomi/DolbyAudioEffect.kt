/*
 * Copyright (C) 2023-24 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package co.aospa.dolby.xiaomi

import android.media.audiofx.AudioEffect
import android.os.SystemClock
import co.aospa.dolby.xiaomi.DolbyConstants.DsParam
import co.aospa.dolby.xiaomi.geq.data.EqualizerGains
import java.util.UUID

internal class DolbyHalException(val status: Int, operation: String) :
    IllegalStateException("$operation failed: AudioEffect status=$status")

internal class DolbyControlUnavailableException :
    IllegalStateException("Another client controls the Dolby effect")

/** Transport errors retain their status: lack of control is NOT an unsupported DAP feature. */
internal class DolbyAudioEffect(priority: Int, audioSession: Int) : AudioEffect(
    EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, priority, audioSession
) {
    var isDead = false
        private set

    private fun result(status: Int, operation: String): Int {
        if (status == ERROR_DEAD_OBJECT || status == ERROR_NO_INIT) isDead = true
        if (status < 0) throw DolbyHalException(status, operation)
        return status
    }

    override fun hasControl(): Boolean = try {
        super.hasControl()
    } catch (_: IllegalStateException) {
        isDead = true
        false
    }

    fun requireControl() {
        if (hasControl()) return
        if (isDead) throw DolbyHalException(ERROR_DEAD_OBJECT, "control")
        // A GET is allowed without ownership. Distinguish a dead server from a
        // healthy effect owned by a higher-priority client; do not churn handles.
        getIntParam(DolbyWireCodec.ENABLE)
        throw DolbyControlUnavailableException()
    }

    private fun write(param: Int, bytes: ByteArray) {
        requireControl()
        result(setParameter(param, bytes), "set parameter $param")
    }

    private fun read(param: Int, bytes: ByteArray): Int =
        result(getParameter(param, bytes), "get parameter $param")

    var dsOn: Boolean
        get() = when (val value = getIntParam(DolbyWireCodec.ENABLE)) {
            0 -> false
            1 -> true
            else -> error("Invalid Dolby processing state: $value")
        }
        set(value) {
            if (!value) {
                disableProcessing()
                return
            }
            try {
                setIntParam(DolbyWireCodec.ENABLE, 1)
                result(setEnabled(true), "enable framework gate")
                confirmProcessingState(true)
            } catch (failure: RuntimeException) {
                try {
                    disableProcessing()
                } catch (cleanup: RuntimeException) {
                    if (cleanup !== failure) failure.addSuppressed(cleanup)
                }
                throw failure
            }
        }

    private fun disableProcessing() {
        var failure: RuntimeException? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: RuntimeException) {
                val first = failure
                if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        // Attempt both gates even if one fails. Never save the temporary bypass as user intent.
        attempt { result(setEnabled(false), "disable framework gate") }
        attempt { setIntParam(DolbyWireCodec.ENABLE, 0) }
        failure?.let { throw it }
        confirmProcessingState(false)
    }

    private fun confirmProcessingState(expected: Boolean) {
        // DMS notifications can lag an accepted SET by a few milliseconds. An
        // immediate compensating SET can race the very state being confirmed.
        // Re-read only: never replay writes or hide transport/malformed replies.
        // This runs on DolbyController's IO worker, never AudioFlinger's render
        // thread. The bounded 35 ms grace is app policy, not a vendor constant.
        for (attempt in 0..PROCESSING_READBACK_DELAYS_MS.size) {
            val nativeEnabled = dsOn
            val frameworkEnabled = enabled
            if (nativeEnabled == expected && frameworkEnabled == expected) return
            check(attempt < PROCESSING_READBACK_DELAYS_MS.size) {
                "Dolby processing state not confirmed: requested=$expected, " +
                    "native=$nativeEnabled, framework=$frameworkEnabled"
            }
            SystemClock.sleep(PROCESSING_READBACK_DELAYS_MS[attempt])
        }
    }

    var profile: Int
        get() = getIntParam(DolbyWireCodec.PROFILE)
        set(value) {
            require(value in 0..255) { "Invalid DAP profile" }
            setIntParam(DolbyWireCodec.PROFILE, value)
        }

    fun setSelectedTuningDevice(port: Int, device: String) =
        write(DolbyWireCodec.SELECTED_TUNING, DolbyWireCodec.tuning(port, device))

    private fun setIntParam(param: Int, value: Int) =
        write(DolbyWireCodec.CPDP_VALUES, DolbyWireCodec.scalar(param, value))

    private fun getIntParam(param: Int): Int {
        val buffer = DolbyWireCodec.ints(param, 0, 0)
        val size = read(DolbyWireCodec.CPDP_VALUES + param, buffer)
        return DolbyWireCodec.decode(buffer, size, 1)[0]
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        require(profile in 0..255) { "Invalid DAP profile" }
        setIntParam(DolbyWireCodec.RESET_PROFILE, profile)
    }

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int = this.profile) {
        if (param == DsParam.GEQ_BAND_GAINS) EqualizerGains.validate(values)
        write(DolbyWireCodec.CPDP_VALUES, DolbyWireCodec.profile(profile, param, values))
    }

    fun setDapParameter(param: DsParam, enable: Boolean, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(if (enable) 1 else 0), profile)

    fun setDapParameter(param: DsParam, value: Int, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(value), profile)

    fun getDapParameter(param: DsParam, profile: Int = this.profile): IntArray {
        val buffer = ByteArray((param.length + 2) * 4)
        val size = read(DolbyWireCodec.profileQuery(profile, param), buffer)
        return DolbyWireCodec.decode(buffer, size, param.length)
    }

    fun getDapParameterBool(param: DsParam, profile: Int = this.profile): Boolean =
        getDapParameter(param, profile)[0] == 1

    fun getDapParameterInt(param: DsParam, profile: Int = this.profile): Int =
        getDapParameter(param, profile)[0]

    fun close() {
        var failure: RuntimeException? = null
        fun attempt(action: () -> Unit) {
            try {
                action()
            } catch (error: RuntimeException) {
                val first = failure
                if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
            }
        }
        attempt { setControlStatusListener(null) }
        attempt { setEnableStatusListener(null) }
        attempt { release() }
        isDead = true
        failure?.let { throw it }
    }

    companion object {
        private val PROCESSING_READBACK_DELAYS_MS = longArrayOf(5L, 10L, 20L)
        private val EFFECT_TYPE_DAP = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
    }
}
