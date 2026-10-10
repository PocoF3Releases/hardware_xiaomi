/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

/** Readback, not a claim that a vendor DSP route is audibly processing samples. */
internal data class DolbyRuntimeState(
    val phase: String = "INITIALIZING",
    val nativeEnabled: Boolean? = null,
    val frameworkEnabled: Boolean? = null,
    val hasControl: Boolean = false,
    val acknowledgedTuning: String? = null,
    val tuningCommandSupported: Boolean? = null,
    val serverGeneration: Long = -1,
    val routedDeviceType: Int? = null,
    val speakerTuning: String = "automatic",
    val speakerTuningSupported: Boolean = false
)
