package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class HomeTextCopyTest {
    @Test
    public void sentenceCasePreservesNullEmptyAndFirstCharacterOnlyBehavior() {
        assertEquals("", HomeTextCopy.sentenceCase(null));
        assertEquals("", HomeTextCopy.sentenceCase(""));
        assertEquals("Synced today", HomeTextCopy.sentenceCase("synced today"));
        assertEquals("Already synced", HomeTextCopy.sentenceCase("Already synced"));
    }

    @Test
    public void focusHeadlinePreservesHomeMetricCopy() {
        RecordsSchedulerModels.AdaptiveLoadPlan waiting = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, Collections.emptyList(), 0, false, "");
        RecordsSchedulerModels.AdaptiveLoadPlan all = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 2, Arrays.asList("裂", "語"), 0, true, "all");
        RecordsSchedulerModels.AdaptiveLoadPlan focused = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 4, 1, Arrays.asList("裂", "語"), 0, false, "focus");

        assertEquals("Waiting", HomeTextCopy.focusHeadline(null));
        assertEquals("Waiting", HomeTextCopy.focusHeadline(waiting));
        assertEquals("All current", HomeTextCopy.focusHeadline(all));
        assertEquals("1 items left / 4", HomeTextCopy.focusHeadline(focused));
    }

    @Test
    public void homeSyncAndRecentMistakeCopyPreserveFallbacks() {
        assertEquals("Never synced", HomeTextCopy.homeSyncValue(null));
        assertEquals("Date unknown", HomeTextCopy.homeSyncValue(0L));
        assertEquals("Recent review miss", HomeTextCopy.recentMistakeTitle(null));
        assertEquals("Recent review miss", HomeTextCopy.recentMistakeTitle(""));
        assertEquals("split", HomeTextCopy.recentMistakeTitle("split"));
        assertEquals("Rated again on Unknown time", HomeTextCopy.recentMistakeSubtitle("again", "Unknown time"));
        assertEquals("Rated  on ", HomeTextCopy.recentMistakeSubtitle(null, null));
    }

    @Test
    public void streakCopyPreservesHomeMetricCopy() {
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(0));
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(-1));
        assertEquals("2-day streak", HomeTextCopy.streakHeadline(2));
        assertEquals("Not done today", HomeTextCopy.streakMetricBody(false, 0));
        assertEquals("Best: 5 days", HomeTextCopy.streakMetricBody(true, 5));
        assertEquals("Done today", HomeTextCopy.streakMetricBody(true, 0));
        assertEquals("1 day", HomeTextCopy.streakDayCount(1));
        assertEquals("3 days", HomeTextCopy.streakDayCount(3));
    }

    @Test
    public void reviewToastPreservesSavedCopyAndStreakSuffix() {
        assertEquals("Already saved.", HomeTextCopy.reviewToast(true, StudyRatings.GOOD, 2));
        assertEquals("Saved.", HomeTextCopy.reviewToast(false, StudyRatings.GOOD, 0));
        assertEquals("Saved. 2-day streak.", HomeTextCopy.reviewToast(false, StudyRatings.GOOD, 2));
        assertEquals(
                "Saved. This kanji will come back soon.",
                HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 0)
        );
        assertEquals(
                "Saved. This kanji will come back soon. 2-day streak.",
                HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 2)
        );
    }

    @Test
    public void homeShellCopyPreservesHeaderMetricsAndEmptyStates() {
        assertEquals("Kani", HomeTextCopy.appTitle());
        assertEquals("Your AnkiDroid companion app to cure kanji blindness", HomeTextCopy.appSubtitle());
        assertEquals("Sync AnkiDroid", HomeTextCopy.syncAnkiDroidLabel());
        assertEquals("Focus queue", HomeTextCopy.focusQueueTitle());
        assertEquals("View all", HomeTextCopy.viewAllLabel());
        assertEquals("No kanji queued yet", HomeTextCopy.noKanjiQueuedTitle());
        assertEquals(
                "After the first sync, this screen shows the kanji that need focused recall and writing practice.",
                HomeTextCopy.homeNoKanjiQueuedBody()
        );
        assertEquals("Sync AnkiDroid first to build a focus queue.", HomeTextCopy.focusQueueNoKanjiQueuedBody());
        assertEquals("Sync", HomeTextCopy.syncMetricLabel());
        assertEquals("Up to date", HomeTextCopy.syncMetricStatus(true));
        assertEquals("Tap to sync", HomeTextCopy.syncMetricStatus(false));
        assertEquals("Streak", HomeTextCopy.streakMetricLabel());
        assertEquals("Focus", HomeTextCopy.focusMetricLabel());
        assertEquals("Start focused practice", HomeTextCopy.studySupportText());
        assertEquals("Browse Kanji", HomeTextCopy.browseActionLabel());
        assertEquals("Recent mistakes", HomeTextCopy.recentMistakesTitle());
        assertEquals("Stats", HomeTextCopy.statsActionLabel());
        assertEquals("Games", HomeTextCopy.gamesActionLabel());
        assertEquals("Home", HomeTextCopy.homeLabel());
        assertEquals("No recent mistakes yet", HomeTextCopy.noRecentMistakesTitle());
        assertEquals("Missed and hard reviews will show here after you study.", HomeTextCopy.noRecentMistakesBody());
    }

    @Test
    public void syncCopyPreservesDialogResultAndFallbackText() {
        RecordsSyncModels.Settings settings = new RecordsSyncModels.Settings(
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
        );

        assertEquals("Sync AnkiDroid?", HomeTextCopy.syncDialogTitle());
        assertEquals(
                "Kani imports suspended Basic cards by default, keeps them safe locally, and only uses active cards when that import filter is enabled.",
                HomeTextCopy.syncDialogMessage(settings)
        );
        assertEquals("Sync cards", HomeTextCopy.syncDialogPositiveLabel());
        assertEquals("Cancel", HomeTextCopy.cancelLabel());
        assertEquals("Syncing AnkiDroid", HomeTextCopy.syncingTitle());
        assertEquals("Sync already running", HomeTextCopy.syncAlreadyRunningTitle());
        assertEquals("Kani is already reading AnkiDroid.", HomeTextCopy.syncAlreadyRunningFallback());
        assertEquals("Sync complete", HomeTextCopy.syncCompleteTitle());
        assertEquals("1 kanji ready to study", HomeTextCopy.syncReadyCountText(1));
        assertEquals("3 kanji ready to study", HomeTextCopy.syncReadyCountText(3));
        assertEquals(
                "1 candidate found from Anki. Auto Pareto: 1 item today.",
                HomeTextCopy.syncCandidateSummary(1, "Auto Pareto: 1 item today")
        );
        assertEquals(
                "2 candidates found from Anki. Auto Pareto: 2 items today.",
                HomeTextCopy.syncCandidateSummary(2, "Auto Pareto: 2 items today")
        );
        assertEquals("1 new archived suspended kanji added", HomeTextCopy.importedSuspendedKanjiText(1));
        assertEquals("4 new archived suspended kanji added", HomeTextCopy.importedSuspendedKanjiText(4));
        assertEquals("Sync needs attention", HomeTextCopy.syncNeedsAttentionTitle());
        assertEquals("Could not read AnkiDroid", HomeTextCopy.syncReadErrorTitle());
        assertEquals("Try again after checking AnkiDroid permissions.", HomeTextCopy.syncFailureFallback());
        assertEquals("Try sync again", HomeTextCopy.trySyncAgainLabel());
        assertThrows(NullPointerException.class, () -> HomeTextCopy.syncDialogMessage(null));
    }

    @Test
    public void browseResultHeadingPreservesBrowseCopy() {
        assertEquals("No matches", HomeTextCopy.browseResultHeading(0));
        assertEquals("No matches", HomeTextCopy.browseResultHeading(-1));
        assertEquals("2 kanji", HomeTextCopy.browseResultHeading(2));
        assertEquals("Showing first 300 matches", HomeTextCopy.browseResultHeading(300));
    }

    @Test
    public void browseStaticCopyAndFallbackMeaningStayCentralized() {
        assertEquals("Browse Kanji", HomeTextCopy.browseTitle());
        assertEquals("Local kanji from synced Kani data and study history.", HomeTextCopy.browseBody());
        assertEquals("Search kanji, meaning, reading, or examples", HomeTextCopy.browseSearchHint());
        assertEquals("Search", HomeTextCopy.browseSearchButtonLabel());
        assertEquals("No local kanji found", HomeTextCopy.browseEmptyTitle());
        assertEquals("Sync AnkiDroid first, or try a different search.", HomeTextCopy.browseEmptyBody());
        assertEquals("Kanji not found", HomeTextCopy.kanjiNotFoundTitle());
        assertEquals("This row may have disappeared after a sync.", HomeTextCopy.kanjiNotFoundBody());
        assertEquals("Meaning not stored yet", HomeTextCopy.browseItemMeaning(inventory("語", "", "")));
        assertEquals("language", HomeTextCopy.browseItemMeaning(inventory("語", "language", "")));
        assertEquals("1 local source · 2 examples", HomeTextCopy.browseInventorySummary(1, 2));
        assertEquals("3 local sources · 1 example", HomeTextCopy.browseInventorySummary(3, 1));
        assertEquals("SUSPENDED", HomeTextCopy.suspendedChipLabel());
        assertThrows(NullPointerException.class, () -> HomeTextCopy.browseItemMeaning(null));
    }

    @Test
    public void detailIdentityHelpersPreserveFallbackPriority() {
        RecordsImportModels.KanjiInventoryItem inventory = inventory("語", "language", "inventory:語");
        RecordsImportModels.DashboardRow row = row("裂", "row:裂");
        RecordsImportModels.DashboardRow rowWithReason = row("裂", "row:裂", "manual reason");

        assertEquals("裂", HomeTextCopy.detailDisplayKanji("fallback", row, inventory));
        assertEquals("語", HomeTextCopy.detailDisplayKanji("fallback", null, inventory));
        assertEquals("fallback", HomeTextCopy.detailDisplayKanji("fallback", null, null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(inventory("語", "", "")));
        assertEquals("language", HomeTextCopy.inventoryTitle(inventory));
        assertEquals("Why it is here", HomeTextCopy.detailReasonTitle());
        assertEquals(
                "This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history.",
                HomeTextCopy.historicalReasonText()
        );
        assertEquals("Current local practice evidence from AnkiDroid.", HomeTextCopy.activeReasonText(row));
        assertEquals("manual reason", HomeTextCopy.activeReasonText(rowWithReason));
        assertEquals("Anki browser: row:裂", HomeTextCopy.ankiBrowserLine("row:裂"));
        assertEquals("Review this now", HomeTextCopy.reviewNowLabel());
        assertEquals("Copy Anki search", HomeTextCopy.copyAnkiSearchLabel());
        assertEquals("Anki search", HomeTextCopy.ankiSearchClipLabel());
        assertEquals("Search copied", HomeTextCopy.ankiSearchCopiedToast());
        assertEquals("Suspend locally", HomeTextCopy.localSuspendButtonLabel(false));
        assertEquals("Unsuspend locally", HomeTextCopy.localSuspendButtonLabel(true));
        assertEquals("Kanji suspended locally.", HomeTextCopy.localSuspendToast(false));
        assertEquals("Kanji unsuspended.", HomeTextCopy.localSuspendToast(true));
        assertEquals("Examples", HomeTextCopy.examplesTitle());
        assertEquals("Local inventory", HomeTextCopy.localInventoryTitle());
        assertEquals("1 source note/card · 2 stored examples", HomeTextCopy.localInventorySummary(1, 2));
        assertEquals("3 source notes/cards · 1 stored example", HomeTextCopy.localInventorySummary(3, 1));
        assertEquals("Search: row:裂", HomeTextCopy.localInventorySearchLine("row:裂"));
        assertEquals(
                "Last seen locally " + DateTextPolicy.shortDateTime(123456789L),
                HomeTextCopy.localInventoryLastSeenLine(123456789L)
        );
        assertEquals("inventory:語", HomeTextCopy.detailBrowserSearch(row, inventory));
        assertEquals("row:裂", HomeTextCopy.detailBrowserSearch(row, inventory("語", "language", "")));
        assertEquals("", HomeTextCopy.detailBrowserSearch(row("裂", ""), null));
        assertEquals("Mature support 0 / target 2", HomeTextCopy.matureSupportTargetText(0, 2));
        assertEquals("Mature support 3 / target 4", HomeTextCopy.matureSupportTargetText(3, 4));
        assertEquals("Timeline will fill in after the next sync or review.", HomeTextCopy.timelineEmptyText());
        assertEquals("Recovery timeline", HomeTextCopy.recoveryTimelineTitle());
        assertEquals("No active Anki evidence in the latest local sync.", HomeTextCopy.noActiveEvidenceText());
    }

    @Test
    public void exampleCopyPreservesSourceExpressionAndMeaningCleanup() {
        RecordsImportModels.Example active = example("active", "活動語", "カツドウゴ", "(suru verb) action");
        RecordsImportModels.Example noReading = example("suspended", "停止語", "", "");

        assertEquals("ACTIVE", HomeTextCopy.exampleSourceLabel(active));
        assertEquals("SUSPENDED", HomeTextCopy.exampleSourceLabel(noReading));
        assertEquals("活動語  カツドウゴ", HomeTextCopy.exampleExpressionLine(active));
        assertEquals("停止語", HomeTextCopy.exampleExpressionLine(noReading));
        assertEquals("Action", HomeTextCopy.exampleMeaningLine(active));
        assertEquals("", HomeTextCopy.exampleMeaningLine(noReading));
        assertThrows(NullPointerException.class, () -> HomeTextCopy.exampleSourceLabel(null));
        assertThrows(NullPointerException.class, () -> HomeTextCopy.exampleExpressionLine(null));
        assertThrows(NullPointerException.class, () -> HomeTextCopy.exampleMeaningLine(null));
    }

    private static RecordsImportModels.KanjiInventoryItem inventory(String kanji, String meaning, String browserSearch) {
        return new RecordsImportModels.KanjiInventoryItem(kanji, meaning, "reading", browserSearch, 2, 3, false, 1000L);
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String browserSearch) {
        return row(kanji, browserSearch, "");
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String browserSearch, String reasonText) {
        return new RecordsImportModels.DashboardRow(
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
                Collections.emptyList()
        );
    }

    private static RecordsImportModels.Example example(
            String sourceType,
            String expression,
            String reading,
            String meaning
    ) {
        return new RecordsImportModels.Example(
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
        );
    }
}
