package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeTextCopyTest {
    @Test
    fun sentenceCasePreservesNullEmptyAndFirstCharacterOnlyBehavior() {
        assertEquals("", HomeTextCopy.sentenceCase(null))
        assertEquals("", HomeTextCopy.sentenceCase(""))
        assertEquals("Synced today", HomeTextCopy.sentenceCase("synced today"))
        assertEquals("Already synced", HomeTextCopy.sentenceCase("Already synced"))
    }

    @Test
    fun focusHeadlinePreservesHomeMetricCopy() {
        val waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList(), 0, false, "")
        val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 2, listOf("裂", "語"), 0, true, "all")
        val focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 4, 1, listOf("裂", "語"), 0, false, "focus")

        assertEquals("Waiting", HomeTextCopy.focusHeadline(null))
        assertEquals("Waiting", HomeTextCopy.focusHeadline(waiting))
        assertEquals("All current", HomeTextCopy.focusHeadline(all))
        assertEquals("1 left · target 4", HomeTextCopy.focusHeadline(focused))
    }

    @Test
    fun homeSyncAndRecentMistakeCopyPreserveFallbacks() {
        assertEquals("Never synced", HomeTextCopy.homeSyncValue(null))
        assertEquals("Date unknown", HomeTextCopy.homeSyncValue(0L))
        assertEquals("Recent mistake", HomeTextCopy.recentMistakeTitle(null))
        assertEquals("Recent mistake", HomeTextCopy.recentMistakeTitle(""))
        assertEquals("split", HomeTextCopy.recentMistakeTitle("split"))
        assertEquals("Again · Unknown time", HomeTextCopy.recentMistakeSubtitle("again", "Unknown time"))
        assertEquals("Recent miss", HomeTextCopy.recentMistakeSubtitle(null, null))
    }

    @Test
    fun streakCopyPreservesHomeMetricCopy() {
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(0))
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(-1))
        assertEquals("2-day streak", HomeTextCopy.streakHeadline(2))
        assertEquals("Not done today", HomeTextCopy.streakMetricBody(false, 0))
        assertEquals("Best: 5 days", HomeTextCopy.streakMetricBody(true, 5))
        assertEquals("Done today", HomeTextCopy.streakMetricBody(true, 0))
        assertEquals("1 day", HomeTextCopy.streakDayCount(1))
        assertEquals("3 days", HomeTextCopy.streakDayCount(3))
    }

    @Test
    fun reviewToastPreservesSavedCopyAndStreakSuffix() {
        assertEquals("Already saved.", HomeTextCopy.reviewToast(true, StudyRatings.GOOD, 2))
        assertEquals("Saved.", HomeTextCopy.reviewToast(false, null, 0))
        assertEquals("Saved. 2-day streak.", HomeTextCopy.reviewToast(false, "unknown", 2))
        assertEquals(
            "Saved. This kanji moved forward.",
            HomeTextCopy.reviewToast(false, StudyRatings.GOOD, 0)
        )
        assertEquals(
            "Saved. This kanji moved forward. 2-day streak.",
            HomeTextCopy.reviewToast(false, StudyRatings.EASY, 2)
        )
        assertEquals(
            "Saved. This kanji stays in practice.",
            HomeTextCopy.reviewToast(false, StudyRatings.HARD, 0)
        )
        assertEquals(
            "Saved. This kanji stays in practice. 2-day streak.",
            HomeTextCopy.reviewToast(false, StudyRatings.HARD, 2)
        )
        assertEquals(
            "Saved. This kanji will come back soon.",
            HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 0)
        )
        assertEquals(
            "Saved. This kanji will come back soon. 2-day streak.",
            HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 2)
        )
    }

    @Test
    fun homeShellCopyPreservesHeaderMetricsAndEmptyStates() {
        assertEquals("Kani", HomeTextCopy.appTitle())
        assertEquals("", HomeTextCopy.appSubtitle())
        assertEquals("Sync AnkiDroid", HomeTextCopy.syncAnkiDroidLabel())
        assertEquals("Focus queue", HomeTextCopy.focusQueueTitle())
        assertEquals("View all", HomeTextCopy.viewAllLabel())
        assertEquals("No kanji queued yet", HomeTextCopy.noKanjiQueuedTitle())
        assertEquals(
            "Sync AnkiDroid to build your focus queue.",
            HomeTextCopy.homeNoKanjiQueuedBody()
        )
        assertEquals("Sync AnkiDroid first to build a focus queue.", HomeTextCopy.focusQueueNoKanjiQueuedBody())
        assertEquals("Sync", HomeTextCopy.syncMetricLabel())
        assertEquals("Up to date", HomeTextCopy.syncMetricStatus(true))
        assertEquals("Tap to sync", HomeTextCopy.syncMetricStatus(false))
        assertEquals("Streak", HomeTextCopy.streakMetricLabel())
        assertEquals("Focus", HomeTextCopy.focusMetricLabel())
        assertEquals("Browse Kanji", HomeTextCopy.browseActionLabel())
        assertEquals("Recent mistakes", HomeTextCopy.recentMistakesTitle())
        assertEquals("Stats", HomeTextCopy.statsActionLabel())
        assertEquals("Games", HomeTextCopy.gamesActionLabel())
        assertEquals("Home", HomeTextCopy.homeLabel())
        assertEquals("No recent mistakes yet", HomeTextCopy.noRecentMistakesTitle())
        assertEquals("Missed and hard reviews appear here.", HomeTextCopy.noRecentMistakesBody())
    }

    @Test
    fun syncCopyPreservesDialogResultAndFallbackText() {
        val settings = RecordsSyncModels.Settings(
            "Basic",
            "kanji",
            "meaning",
            "reading",
            "deck",
            "",
            "",
            "",
            2,
            1,
            1000,
            20,
            5
        )

        assertEquals("Sync AnkiDroid?", HomeTextCopy.syncDialogTitle())
        assertEquals(
            "Kani imports suspended Basic cards by default, archives them locally, and only uses active cards when that filter is enabled.",
            HomeTextCopy.syncDialogMessage(settings)
        )
        assertEquals("Sync cards", HomeTextCopy.syncDialogPositiveLabel())
        assertEquals("Cancel", HomeTextCopy.cancelLabel())
        assertEquals("Syncing AnkiDroid", HomeTextCopy.syncingTitle())
        assertEquals("Sync already running", HomeTextCopy.syncAlreadyRunningTitle())
        assertEquals("Kani is already reading AnkiDroid.", HomeTextCopy.syncAlreadyRunningFallback())
        assertEquals("Sync complete", HomeTextCopy.syncCompleteTitle())
        assertEquals("1 kanji ready to study", HomeTextCopy.syncReadyCountText(1))
        assertEquals("3 kanji ready to study", HomeTextCopy.syncReadyCountText(3))
        assertEquals(
            "1 candidate found from Anki. Auto Pareto: 1 item today.",
            HomeTextCopy.syncCandidateSummary(1, "Auto Pareto: 1 item today")
        )
        assertEquals(
            "2 candidates found from Anki. Auto Pareto: 2 items today.",
            HomeTextCopy.syncCandidateSummary(2, "Auto Pareto: 2 items today")
        )
        assertEquals("1 new archived suspended kanji added", HomeTextCopy.importedSuspendedKanjiText(1))
        assertEquals("4 new archived suspended kanji added", HomeTextCopy.importedSuspendedKanjiText(4))
        assertEquals("AnkiDroid needs attention", HomeTextCopy.syncNeedsAttentionTitle())
        assertEquals("Could not read AnkiDroid", HomeTextCopy.syncReadErrorTitle())
        assertEquals("Try again after checking AnkiDroid permissions.", HomeTextCopy.syncFailureFallback())
        assertEquals("Try sync again", HomeTextCopy.trySyncAgainLabel())
        assertThrows(NullPointerException::class.java) { HomeTextCopy.syncDialogMessage(null) }
    }

    @Test
    fun browseResultHeadingPreservesBrowseCopy() {
        assertEquals("No matches", HomeTextCopy.browseResultHeading(0))
        assertEquals("No matches", HomeTextCopy.browseResultHeading(-1))
        assertEquals("2 kanji", HomeTextCopy.browseResultHeading(2))
        assertEquals("Showing first 300 matches", HomeTextCopy.browseResultHeading(300))
    }

    @Test
    fun browseStaticCopyAndFallbackMeaningStayCentralized() {
        assertEquals("Browse Kanji", HomeTextCopy.browseTitle())
        assertEquals("Search kanji, meaning, reading, or examples", HomeTextCopy.browseSearchHint())
        assertEquals("Search", HomeTextCopy.browseSearchButtonLabel())
        assertEquals("No local kanji found", HomeTextCopy.browseEmptyTitle())
        assertEquals("Sync AnkiDroid first, or try a different search.", HomeTextCopy.browseEmptyBody())
        assertEquals("Kanji not found", HomeTextCopy.kanjiNotFoundTitle())
        assertEquals("No local record found.", HomeTextCopy.kanjiNotFoundBody())
        assertEquals("Meaning not stored yet", HomeTextCopy.browseItemMeaning(inventory("語", "", "")))
        assertEquals("language", HomeTextCopy.browseItemMeaning(inventory("語", "language", "")))
        assertEquals("1 local source · 2 examples", HomeTextCopy.browseInventorySummary(1, 2))
        assertEquals("3 local sources · 1 example", HomeTextCopy.browseInventorySummary(3, 1))
        assertEquals("SUSPENDED", HomeTextCopy.suspendedChipLabel())
        assertEquals("relearning", HomeTextCopy.relearningChipLabel())
        assertEquals("Back to Browse", HomeTextCopy.backToBrowseKanjiLabel())
        assertThrows(NullPointerException::class.java) { HomeTextCopy.browseItemMeaning(null) }
    }

    @Test
    fun detailIdentityHelpersPreserveFallbackPriority() {
        val inventory = inventory("語", "language", "inventory:語")
        val row = row("裂", "row:裂")
        val rowWithReason = row("裂", "row:裂", "manual reason")

        assertEquals(
            listOf(
                "裂",
                "語",
                "fallback",
                "Historical recovery",
                "Historical recovery",
                "language",
                "No longer active, but kept in local recovery history.",
                "Current local practice evidence.",
                "manual reason"
            ),
            listOf(
                HomeTextCopy.detailDisplayKanji("fallback", row, inventory),
                HomeTextCopy.detailDisplayKanji("fallback", null, inventory),
                HomeTextCopy.detailDisplayKanji("fallback", null, null),
                HomeTextCopy.inventoryTitle(null),
                HomeTextCopy.inventoryTitle(inventory("語", "", "")),
                HomeTextCopy.inventoryTitle(inventory),
                HomeTextCopy.historicalReasonText(),
                HomeTextCopy.activeReasonText(row),
                HomeTextCopy.activeReasonText(rowWithReason)
            )
        )
        assertEquals(
            listOf(
                "Anki browser: row:裂",
                "Review this now",
                "Copy Anki search",
                "Anki search",
                "Search copied",
                "Suspend locally",
                "Unsuspend locally",
                "Kanji suspended locally.",
                "Kanji unsuspended.",
                "Examples",
                "Local inventory",
                "1 source note/card · 2 stored examples",
                "3 source notes/cards · 1 stored example",
                "Search: row:裂",
                "Last seen locally ${DateTextPolicy.shortDateTime(123456789L)}"
            ),
            listOf(
                HomeTextCopy.ankiBrowserLine("row:裂"),
                HomeTextCopy.reviewNowLabel(),
                HomeTextCopy.copyAnkiSearchLabel(),
                HomeTextCopy.ankiSearchClipLabel(),
                HomeTextCopy.ankiSearchCopiedToast(),
                HomeTextCopy.localSuspendButtonLabel(false),
                HomeTextCopy.localSuspendButtonLabel(true),
                HomeTextCopy.localSuspendToast(false),
                HomeTextCopy.localSuspendToast(true),
                HomeTextCopy.examplesTitle(),
                HomeTextCopy.localInventoryTitle(),
                HomeTextCopy.localInventorySummary(1, 2),
                HomeTextCopy.localInventorySummary(3, 1),
                HomeTextCopy.localInventorySearchLine("row:裂"),
                HomeTextCopy.localInventoryLastSeenLine(123456789L)
            )
        )
        assertEquals(
            listOf(
                "inventory:語",
                "row:裂",
                "",
                "Mature support 0 / target 2",
                "Mature support 3 / target 4",
                "Timeline will fill in after the next sync or review.",
                "Recovery timeline",
                "No active Anki evidence in the latest local sync."
            ),
            listOf(
                HomeTextCopy.detailBrowserSearch(row, inventory),
                HomeTextCopy.detailBrowserSearch(row, inventory("語", "language", "")),
                HomeTextCopy.detailBrowserSearch(row("裂", ""), null),
                HomeTextCopy.matureSupportTargetText(0, 2),
                HomeTextCopy.matureSupportTargetText(3, 4),
                HomeTextCopy.timelineEmptyText(),
                HomeTextCopy.recoveryTimelineTitle(),
                HomeTextCopy.noActiveEvidenceText()
            )
        )
    }

    @Test
    fun exampleCopyPreservesSourceExpressionAndMeaningCleanup() {
        val active = example("active", "活動語", "カツドウゴ", "(suru verb) action")
        val noReading = example("suspended", "停止語", "", "")

        assertEquals("ACTIVE", HomeTextCopy.exampleSourceLabel(active))
        assertEquals("SUSPENDED", HomeTextCopy.exampleSourceLabel(noReading))
        assertEquals("活動語  カツドウゴ", HomeTextCopy.exampleExpressionLine(active))
        assertEquals("停止語", HomeTextCopy.exampleExpressionLine(noReading))
        assertEquals("Action", HomeTextCopy.exampleMeaningLine(active))
        assertEquals("", HomeTextCopy.exampleMeaningLine(noReading))
        assertThrows(NullPointerException::class.java) { HomeTextCopy.exampleSourceLabel(null) }
        assertThrows(NullPointerException::class.java) { HomeTextCopy.exampleExpressionLine(null) }
        assertThrows(NullPointerException::class.java) { HomeTextCopy.exampleMeaningLine(null) }
    }

    private fun inventory(kanji: String, meaning: String, browserSearch: String): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(kanji, meaning, "reading", browserSearch, 2, 3, false, 1000L)
    }

    private fun row(kanji: String, browserSearch: String): RecordsImportModels.DashboardRow {
        return row(kanji, browserSearch, "")
    }

    private fun row(kanji: String, browserSearch: String, reasonText: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            browserSearch,
            1,
            "reason",
            reasonText,
            1,
            0,
            1,
            emptyList<RecordsImportModels.Example>()
        )
    }

    private fun example(
        sourceType: String,
        expression: String,
        reading: String,
        meaning: String
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            sourceType,
            1L,
            2L,
            expression,
            reading,
            meaning,
            "sentence",
            false,
            0,
            0,
            0,
            null,
            null,
            null
        )
    }
}
