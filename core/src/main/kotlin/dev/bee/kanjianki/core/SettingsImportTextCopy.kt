package dev.bee.kanjianki.core

import java.util.Locale

/**
 * Import & sync control labels, in shared `:core`.
 *
 * Only the labels the shared Import section renders. The tag list and browser-query
 * text inputs — and their dependent toggles — need a text-field control the shared
 * vocabulary does not have yet, so this covers the self-contained source toggles only,
 * the honest subset the desktop section ports first.
 */
object SettingsImportTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun activeCardsLabel(): String = localizedText("Import active cards", "アクティブカードを取り込む")

    @JvmStatic
    fun suspendedCardsLabel(): String = localizedText("Import suspended cards", "保留カードを取り込む")

    @JvmStatic
    fun weakCardsLabel(): String = localizedText("Import weak cards", "苦手カードを取り込む")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
