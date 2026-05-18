package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class StudyTextCopyTest {
    @Test
    public void countAndCompactTextPreserveAppCopyHelpers() {
        assertEquals("1 item", StudyTextCopy.countText(1, "item", "items"));
        assertEquals("2 items", StudyTextCopy.countText(2, "item", "items"));
        assertEquals("", StudyTextCopy.compact(null, 12));
        assertEquals("short", StudyTextCopy.compact("short", 12));
        assertEquals("a very long s...", StudyTextCopy.compact("a very long sentence that should be shortened", 16));
    }

    @Test
    public void rowMeaningAndCleanLearnerTextUseDictionaryMeaningCleanup() {
        assertEquals("Split", StudyTextCopy.rowMeaning(row("裂", "meaning: split", "fallback", Collections.emptyList())));
        assertEquals("Fallback", StudyTextCopy.rowMeaning(row("裂", "", "fallback", Collections.emptyList())));
        assertEquals("Collection clue", StudyTextCopy.rowMeaning(null));
        assertEquals("Quiet", StudyTextCopy.cleanLearnerText("(suru verb) quiet", "", 72));
    }

    @Test
    public void sessionCluePrefersDictionaryThenRowThenPrompt() {
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(Collections.singletonList(kanjiEntry("裂", "split", "tear")));
        RecordsStudyModels.StudyItem item = studyItem("裂");
        RecordsImportModels.DashboardRow row = row("裂", "row meaning", "reason", Collections.emptyList());

        assertEquals("Split, tear", StudyTextCopy.sessionClue(lookup, session(item, row, "fallback prompt")));
        assertEquals("Row meaning", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(item, row, "fallback prompt")));
        assertEquals("Fallback prompt", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(studyItem("?"), null, "fallback prompt")));
        assertEquals("Collection clue", StudyTextCopy.sessionClue(DictionaryLookup.empty(), null));
    }

    @Test
    public void canonicalKanjiMeaningFallsBackWhenDictionaryHasNoGloss() {
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(Collections.singletonList(kanjiEntry("裂")));

        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(DictionaryLookup.empty(), "?", "fallback", 40));
        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(lookup, "裂", "fallback", 40));
        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(null, "裂", "fallback", 40));
        assertEquals("Very long meaning...", StudyTextCopy.canonicalKanjiMeaning(
                DictionaryLookup.fromKanjiEntries(Collections.singletonList(kanjiEntry("長", "very long meaning that compacts"))),
                "長",
                "fallback",
                21
        ));
    }

    @Test
    public void wordPromptPrefersWordReadingExampleExpression() {
        RecordsImportModels.Example active = example("active", "活動語");
        RecordsImportModels.Example suspended = example("suspended", "休止語");
        RecordsStudyModels.StudyItem item = studyItem("語");

        assertEquals("休止語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", Arrays.asList(active, suspended)), "prompt")));
        assertEquals("活動語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", Collections.singletonList(active)), "prompt")));
        assertEquals("語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", Collections.emptyList()), "prompt")));
        assertEquals("", StudyTextCopy.wordPrompt(null));
    }

    private static RecordsSchedulerModels.StudySession session(
            RecordsStudyModels.StudyItem item,
            RecordsImportModels.DashboardRow row,
            String prompt
    ) {
        return new RecordsSchedulerModels.StudySession(item, row, "token", StudyTaskTypes.KANJI_MEANING, false, prompt);
    }

    private static RecordsStudyModels.StudyItem studyItem(String kanji) {
        return new RecordsStudyModels.StudyItem(kanji, "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L);
    }

    private static RecordsImportModels.DashboardRow row(
            String kanji,
            String meaning,
            String reason,
            List<RecordsImportModels.Example> examples
    ) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                meaning,
                "reading",
                "search",
                1,
                reason,
                "reason text",
                1,
                0,
                1,
                examples
        );
    }

    private static RecordsImportModels.Example example(String sourceType, String expression) {
        return new RecordsImportModels.Example(
                sourceType,
                1L,
                2L,
                expression,
                "reading",
                "meaning",
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

    private static DictionaryLookup.KanjiEntry kanjiEntry(String literal, String... meanings) {
        return new DictionaryLookup.KanjiEntry(new DictionaryLookup.KanjiEntryFields(
                literal,
                Arrays.asList(meanings),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                0,
                0,
                null
        ));
    }
}
