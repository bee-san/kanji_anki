package dev.bee.kanjianki.core

import java.util.Locale

object SettingsSummaryTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val SOURCE_ACTIVE = "active"
    private const val SOURCE_SUSPENDED = "suspended"

    @JvmStatic
    fun settingsImportSummary(settings: RecordsSyncModels.Settings?): String {
        val safeSettings = settings ?: throw NullPointerException("settings")
        val sources = mutableListOf<String>()
        if (safeSettings.importActiveCards) {
            sources.add(localizedText(SOURCE_ACTIVE, "有効"))
        }
        if (safeSettings.importSuspendedCards) {
            sources.add(localizedText(SOURCE_SUSPENDED, "停止"))
        }
        if (safeSettings.importTaggedCardsEnabled()) {
            sources.add(localizedText("tagged", "タグ付き"))
        }
        if (safeSettings.importWeakCards) {
            sources.add(localizedText("weak", "弱い"))
        }
        if (safeSettings.browserQueryImportEnabled()) {
            sources.add(localizedText("browser query", "ブラウザ検索"))
        }
        if (sources.isEmpty()) {
            return localizedText("Pick import sources", "インポート元を選んでください")
        }
        return if (isJapaneseLocale()) {
            sources.joinToString("＋") + "、" + matchingCardsSummary(safeSettings)
        } else {
            sources.joinToString(" + ") + "; " + matchingCardsSummary(safeSettings)
        }
    }

    @JvmStatic
    fun matchingCardsSummary(settings: RecordsSyncModels.Settings?): String {
        val safeSettings = settings ?: throw NullPointerException("settings")
        val count = safeSettings.importMinMatchingCardsPerKanji
        return if (isJapaneseLocale()) {
            "漢字ごとに${count}枚以上"
        } else {
            count.toString() + if (count == 1) "+ card per kanji" else "+ cards per kanji"
        }
    }

    @JvmStatic
    fun syncStatusHeadline(success: Boolean, errorMessage: String?, suspendedCards: Int, importedKanji: Int): String {
        if (!success) {
            val safeErrorMessage = errorMessage?.takeIf { it.isNotBlank() } ?: localizedText("unknown error", "不明なエラー")
            return if (isJapaneseLocale()) {
                "同期ブロック: $safeErrorMessage"
            } else {
                "Sync blocked: $safeErrorMessage"
            }
        }
        return if (isJapaneseLocale()) {
            String.format(
                Locale.ROOT,
                "%d件の停止カードをアーカイブ、%d件の珍しい漢字を追加",
                suspendedCards,
                importedKanji,
            )
        } else {
            String.format(
                Locale.ROOT,
                "%d suspended cards archived, %d rare kanji added",
                suspendedCards,
                importedKanji,
            )
        }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
