package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class StudyCollectionLookupTest {
    @Test
    public void dashboardRowByKanjiReturnsMatchingRow() {
        RecordsImportModels.DashboardRow expected = row("語");

        RecordsImportModels.DashboardRow result = StudyCollectionLookup.dashboardRowByKanji(
                Arrays.asList(row("字"), expected),
                "語"
        );

        assertSame(expected, result);
    }

    @Test
    public void dashboardRowByKanjiReturnsNullForMissingOrUnsafeInputs() {
        assertNull(StudyCollectionLookup.dashboardRowByKanji(Collections.singletonList(row("語")), "字"));
        assertNull(StudyCollectionLookup.dashboardRowByKanji(null, "語"));
        assertNull(StudyCollectionLookup.dashboardRowByKanji(Collections.singletonList(row("語")), null));
        assertNull(StudyCollectionLookup.dashboardRowByKanji(Collections.singletonList(null), "語"));
    }

    @Test
    public void studyItemByKanjiReturnsMatchingItem() {
        RecordsStudyModels.StudyItem expected = item("語");

        RecordsStudyModels.StudyItem result = StudyCollectionLookup.studyItemByKanji(
                Arrays.asList(item("字"), expected),
                "語"
        );

        assertSame(expected, result);
    }

    @Test
    public void studyItemByKanjiReturnsNullForMissingOrUnsafeInputs() {
        assertNull(StudyCollectionLookup.studyItemByKanji(Collections.singletonList(item("語")), "字"));
        assertNull(StudyCollectionLookup.studyItemByKanji(null, "語"));
        assertNull(StudyCollectionLookup.studyItemByKanji(Collections.singletonList(item("語")), null));
        assertNull(StudyCollectionLookup.studyItemByKanji(Collections.singletonList(null), "語"));
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                1,
                "weak_support",
                "reason",
                1,
                0,
                0,
                Collections.emptyList()
        );
    }

    private static RecordsStudyModels.StudyItem item(String kanji) {
        return new RecordsStudyModels.StudyItem(kanji, "review", 1000L, 1.0, 2.0, 1, 0, 0, 0, "", 1000L);
    }
}
