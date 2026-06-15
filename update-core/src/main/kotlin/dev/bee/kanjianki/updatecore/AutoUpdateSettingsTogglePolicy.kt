package dev.bee.kanjianki.updatecore

import java.util.Locale

object AutoUpdateSettingsTogglePolicy {
    const val ENABLED_MESSAGE = "Automatic updates turned on."
    const val DISABLED_MESSAGE = "Automatic updates turned off."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun toggle(currentlyEnabled: Boolean): ToggleResult {
        val enabled = !currentlyEnabled
        return ToggleResult(
            enabled,
            if (enabled) localizedText(ENABLED_MESSAGE, "自動アップデートをオンにしました。") else localizedText(
                DISABLED_MESSAGE,
                "自動アップデートをオフにしました。",
            ),
        )
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    class ToggleResult(
        private val enabled: Boolean,
        private val message: String,
    ) {
        fun enabled(): Boolean = enabled
        fun message(): String = message
    }
}
