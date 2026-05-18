package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

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
    public void browseResultHeadingPreservesBrowseCopy() {
        assertEquals("No matches", HomeTextCopy.browseResultHeading(0));
        assertEquals("No matches", HomeTextCopy.browseResultHeading(-1));
        assertEquals("2 kanji", HomeTextCopy.browseResultHeading(2));
        assertEquals("Showing first 300 matches", HomeTextCopy.browseResultHeading(300));
    }

    @Test
    public void detailIdentityHelpersPreserveFallbackPriority() {
        RecordsImportModels.KanjiInventoryItem inventory = inventory("語", "language", "inventory:語");
        RecordsImportModels.DashboardRow row = row("裂", "row:裂");

        assertEquals("裂", HomeTextCopy.detailDisplayKanji("fallback", row, inventory));
        assertEquals("語", HomeTextCopy.detailDisplayKanji("fallback", null, inventory));
        assertEquals("fallback", HomeTextCopy.detailDisplayKanji("fallback", null, null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(inventory("語", "", "")));
        assertEquals("language", HomeTextCopy.inventoryTitle(inventory));
        assertEquals("inventory:語", HomeTextCopy.detailBrowserSearch(row, inventory));
        assertEquals("row:裂", HomeTextCopy.detailBrowserSearch(row, inventory("語", "language", "")));
        assertEquals("", HomeTextCopy.detailBrowserSearch(row("裂", ""), null));
    }

    private static RecordsImportModels.KanjiInventoryItem inventory(String kanji, String meaning, String browserSearch) {
        return new RecordsImportModels.KanjiInventoryItem(kanji, meaning, "reading", browserSearch, 2, 3, false, 1000L);
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String browserSearch) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                browserSearch,
                1,
                "reason",
                "reason text",
                1,
                0,
                1,
                Collections.emptyList()
        );
    }
}
