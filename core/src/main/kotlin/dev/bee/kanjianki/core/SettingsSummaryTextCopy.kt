package dev.bee.kanjianki.core

import java.util.Locale

object SettingsSummaryTextCopy {
    private const val SOURCE_ACTIVE = "active"
    private const val SOURCE_SUSPENDED = "suspended"

    @JvmStatic
    fun settingsImportSummary(settings: RecordsSyncModels.Settings?): String {
        val safeSettings = settings ?: throw NullPointerException("settings")
        val sources = mutableListOf<String>()
        if (safeSettings.importActiveCards) {
            sources.add(SOURCE_ACTIVE)
        }
        if (safeSettings.importSuspendedCards) {
            sources.add(SOURCE_SUSPENDED)
        }
        if (safeSettings.importTaggedCardsEnabled()) {
            sources.add("tagged")
        }
        if (safeSettings.importWeakCards) {
            sources.add("weak")
        }
        if (safeSettings.browserQueryImportEnabled()) {
            sources.add("query")
        }
        if (sources.isEmpty()) {
            return "No import sources selected"
        }
        return sources.joinToString(" + ") + "; " + matchingCardsSummary(safeSettings)
    }

    @JvmStatic
    fun matchingCardsSummary(settings: RecordsSyncModels.Settings?): String {
        val safeSettings = settings ?: throw NullPointerException("settings")
        val count = safeSettings.importMinMatchingCardsPerKanji
        return count.toString() + if (count == 1) " matching card per kanji" else " matching cards per kanji"
    }

    @JvmStatic
    fun syncStatusHeadline(success: Boolean, errorMessage: String?, suspendedCards: Int, importedKanji: Int): String {
        if (!success) {
            val safeErrorMessage = errorMessage?.takeIf { it.isNotBlank() } ?: "unknown error"
            return "Sync blocked: $safeErrorMessage"
        }
        return String.format(
            Locale.ROOT,
            "%d suspended cards archived, %d rare kanji added; active cards remain optional",
            suspendedCards,
            importedKanji,
        )
    }
}
