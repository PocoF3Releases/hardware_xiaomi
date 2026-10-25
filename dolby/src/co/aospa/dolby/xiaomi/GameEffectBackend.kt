/* SPDX-License-Identifier: Apache-2.0 */
package co.aospa.dolby.xiaomi

/** Protocol selection is a product contract, not inferred from a generic support flag. */
internal enum class GameEffectBackend(val key: String, val supportsTuning: Boolean) {
    NONE("none", false),
    FPSOP("fpsop", true),
    QCOM_QDSP("qcom-qdsp", false);

    companion object {
        fun fromKey(key: String): GameEffectBackend =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Reconstructed from the shipped Qualcomm platform_set_parameters, not an FPSOP translation. */
internal object QcomGameEffect {
    const val EFFECT_PROPERTY = "vendor.audio.game.sound.effect"
    const val ENABLE_PROPERTY = "vendor.audio.game.effect.enable"
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun enable(packageName: String): String {
        require(packagePattern.matches(packageName)) { "Invalid package name" }
        // The HAL's package buffer is 32 bytes, including the terminator.
        require(packageName.length < 32) { "Package name exceeds the native game contract" }
        return "audio_game_sound_effect_switch=on;audio_game_sound_mode_switch=on;" +
            "audio_game_package_name=$packageName"
    }

    fun disable(): String = "audio_game_sound_effect_switch=off;audio_game_sound_mode_switch=off"

    fun acknowledged(enabled: Boolean, read: (String) -> String): Boolean =
        read(EFFECT_PROPERTY) == enabled.toString() &&
            read(ENABLE_PROPERTY) == if (enabled) "on" else "off"
}
