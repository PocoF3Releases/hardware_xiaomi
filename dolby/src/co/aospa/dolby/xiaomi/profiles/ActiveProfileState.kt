/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi.profiles

import co.aospa.dolby.xiaomi.geq.data.BandGain

internal data class ActiveProfileState(
    val key: String = "0",
    val name: String = "",
    val base: Int = 0,
    val profiles: List<DolbyProfile> = emptyList(),
    val enabled: Boolean = false,
    val gains: List<BandGain> = List(20) { BandGain(it + 1, 0) },
    val settings: Map<String, Any> = emptyMap(),
    val loaded: Boolean = false,
    val error: String? = null
)
