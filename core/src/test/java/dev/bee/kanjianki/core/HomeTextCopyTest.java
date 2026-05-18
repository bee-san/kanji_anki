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
        assertEquals("No local kanji found", HomeTextCopy.browseEmptyTitle());
        assertEquals("Sync AnkiDroid first, or try a different search.", HomeTextCopy.browseEmptyBody());
        assertEquals("Kanji not found", HomeTextCopy.kanjiNotFoundTitle());
        assertEquals("This row may have disappeared after a sync.", HomeTextCopy.kanjiNotFoundBody());
        assertEquals("Meaning not stored yet", HomeTextCopy.browseItemMeaning(inventory("語", "", "")));
        assertEquals("language", HomeTextCopy.browseItemMeaning(inventory("語", "language", "")));
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
        assertEquals("inventory:語", HomeTextCopy.detailBrowserSearch(row, inventory));
        assertEquals("row:裂", HomeTextCopy.detailBrowserSearch(row, inventory("語", "language", "")));
        assertEquals("", HomeTextCopy.detailBrowserSearch(row("裂", ""), null));
        assertEquals("Mature support 0 / target 2", HomeTextCopy.matureSupportTargetText(0, 2));
        assertEquals("Mature support 3 / target 4", HomeTextCopy.matureSupportTargetText(3, 4));
        assertEquals("Timeline will fill in after the next sync or review.", HomeTextCopy.timelineEmptyText());
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
