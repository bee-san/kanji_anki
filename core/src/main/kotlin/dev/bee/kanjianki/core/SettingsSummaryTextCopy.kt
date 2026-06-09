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
            return localizedText("Turn on an import source", "インポート元を有効にしてください")
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
            "漢字ごとに${count}枚"
        } else {
            count.toString() + if (count == 1) " card per kanji" else " cards per kanji"
        }
    }

    @JvmStatic
    fun syncStatusHeadline(success: Boolean, errorMessage: String?, suspendedCards: Int, importedKanji: Int): String {
        if (!success) {
            val safeErrorMessage = errorMessage?.takeIf { it.isNotBlank() } ?: localizedText("unknown error", "不明なエラー")
            return if (isJapaneseLocale()) {
                "同期に失敗: $safeErrorMessage"
            } else {
                "Sync failed: $safeErrorMessage"
            }
        }
        return if (isJapaneseLocale()) {
            String.format(
                Locale.ROOT,
                "%d字を追加、%d枚の停止カードを保存",
                importedKanji,
                suspendedCards,
            )
        } else {
            String.format(
                Locale.ROOT,
                "%d kanji added; %d suspended archived",
                importedKanji,
                suspendedCards,
            )
        }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
