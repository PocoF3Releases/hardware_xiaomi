/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

import android.media.audiofx.AudioEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Legacy software VQE. Never shares the media DAP parameter namespace. */
internal class DolbyVoiceEffect(session: Int) : AudioEffect(EFFECT_TYPE_NULL, UUID.fromString(
    "64a0f614-7fa4-48b8-b081-d59dc954616f"), 100, session) {
    fun startProcessing() {
        // ds_config.h: VQE_VALUES=10, VQE_ENABLE event=0; one integer value.
        val payload = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(1).putInt(1).array()
        checkStatus(setParameter(10, payload))
        val reply = ByteArray(4)
        check(getParameter(10, reply) >= 4 &&
            ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN).int == 1) {
            "VQE did not confirm enable"
        }
        checkStatus(setEnabled(true))
        check(enabled) { "VQE effect did not enable" }
    }

    fun close() {
        try { checkStatus(setEnabled(false)) } finally { release() }
    }
}
