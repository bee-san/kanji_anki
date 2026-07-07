package dev.bee.kanjianki.core

import java.util.Locale

/**
 * Toggle policy for the diagnostic debug log. Mirrors [AutoSyncSettingsTogglePolicy]:
 * the settings screen asks the policy for the new persisted state plus the localized
 * confirmation message, keeping the decision JVM-testable.
 */
object DebugLogSettingsTogglePolicy {
    const val ENABLED_MESSAGE: String = "Debug log turned on."
    const val DISABLED_MESSAGE: String = "Debug log turned off."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun enable(): ToggleResult {
        return ToggleResult(true, localizedText(ENABLED_MESSAGE, "デバッグログをオンにしました。"))
    }

    @JvmStatic
    fun disable(): ToggleResult {
        return ToggleResult(false, localizedText(DISABLED_MESSAGE, "デバッグログをオフにしました。"))
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    @JvmRecord
    data class ToggleResult(val enabled: Boolean, val message: String) {
        override fun toString(): String {
            return "ToggleResult[enabled=$enabled, message=$message]"
        }
    }
}
