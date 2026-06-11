package dev.bee.kanjianki.core

import java.util.Locale

object AutoSyncSettingsTogglePolicy {
    const val ENABLED_MESSAGE: String = "Daily sync turned on."
    const val DISABLED_MESSAGE: String = "Daily sync turned off."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun enable(): ToggleResult {
        return ToggleResult(true, localizedText(ENABLED_MESSAGE, "毎日の同期をオンにしました。"))
    }

    @JvmStatic
    fun disable(): ToggleResult {
        return ToggleResult(false, localizedText(DISABLED_MESSAGE, "毎日の同期をオフにしました。"))
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
