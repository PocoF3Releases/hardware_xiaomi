/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

/** A bounded burst, reset by real audio/user events, not by its own retry timer. */
internal class DolbyRetryPolicy {
    private var attempts = 0
    fun reset() { attempts = 0 }
    fun nextDelay(): Long? = DELAYS.getOrNull(attempts)?.also { attempts++ }
    companion object {
        private val DELAYS = longArrayOf(250, 500, 1000, 2000, 4000)
    }
}
