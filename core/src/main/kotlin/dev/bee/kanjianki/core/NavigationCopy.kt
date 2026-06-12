package dev.bee.kanjianki.core

import java.util.Locale

/** Labels and accessibility copy for the persistent bottom navigation bar. */
object NavigationCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    @JvmStatic
    fun homeLabel(): String = localizedText("Home", "ホーム")

    @JvmStatic
    fun studyLabel(): String = localizedText("Study", "学習")

    @JvmStatic
    fun statsLabel(): String = localizedText("Stats", "統計")

    @JvmStatic
    fun settingsLabel(): String = localizedText("Settings", "設定")

    @JvmStatic
    fun navItemContentDescription(label: String, selected: Boolean): String {
        return if (selected) {
            localizedText("$label tab, selected", "${label}タブ、選択中")
        } else {
            localizedText("$label tab", "${label}タブ")
        }
    }
}
