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
        assertEquals("1 null", StudyTextCopy.countText(1, null, "items"));
        assertEquals("2 null", StudyTextCopy.countText(2, "item", null));
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
        assertEquals("Collection clue", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(studyItem("?"), null, null)));
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

    @Test
    public void heroQuestionUsesWordReadingTaskOnly() {
        assertEquals("What is the reading?", StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", Collections.emptyList()), "prompt", StudyTaskTypes.WORD_READING)));
        assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", Collections.emptyList()), "prompt", StudyTaskTypes.KANJI_MEANING)));
        assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(null));
    }

    @Test
    public void collectionMeaningForSessionUsesSelectedExampleThenRowMeaning() {
        RecordsImportModels.Example active = example("active", "活動語", "active meaning");
        RecordsImportModels.Example suspended = example("suspended", "休止語", "suspended meaning");
        RecordsStudyModels.StudyItem item = studyItem("語");
        RecordsImportModels.DashboardRow row = row("語", "language", "reason", Arrays.asList(active, suspended));

        assertEquals("suspended meaning", StudyTextCopy.collectionMeaningForSession(session(item, row, "prompt", StudyTaskTypes.WORD_READING)));
        assertEquals("active meaning", StudyTextCopy.collectionMeaningForSession(session(item, row, "prompt", StudyTaskTypes.KANJI_MEANING)));
        assertEquals("language", StudyTextCopy.collectionMeaningForSession(session(item, row("語", "language", "reason", Collections.emptyList()), "prompt")));
        assertEquals("", StudyTextCopy.collectionMeaningForSession(null));
        assertEquals("", StudyTextCopy.collectionMeaningForSession(session(item, null, "prompt")));
    }

    @Test
    public void meaningKanjiChoiceCopyCleansLearnerMeaningAndPreservesResultBranches() {
        RecordsImportModels.MeaningKanjiChoiceCard card = new RecordsImportModels.MeaningKanjiChoiceCard(
                "静",
                "(suru verb) quiet",
                "しず",
                Arrays.asList("静", "青", "清", "晴")
        );

        assertEquals(
                "Which kanji means Quiet?",
                StudyTextCopy.meaningKanjiChoiceQuestion(card, "fallback")
        );
        assertEquals(
                "Correct. 静 means Quiet.",
                StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", true)
        );
        assertEquals(
                "Answer: 静 \u00b7 Quiet",
                StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", false)
        );
        assertEquals(
                "Which kanji means Fallback clue?",
                StudyTextCopy.meaningKanjiChoiceQuestion(null, "fallback clue")
        );
        assertEquals("Typing answer accepted.", StudyTextCopy.typingAnswerAcceptedToast());
    }

    @Test
    public void studyDoneCopyPreservesFocusAndRunSummaryText() {
        assertEquals("Today's focus done", StudyTextCopy.studyDoneTitle());
        assertEquals(
                "Kani finished today's adaptive focus. You can stop here, or keep going through all current problem kanji.",
                StudyTextCopy.adaptiveFocusDoneBody()
        );
        assertEquals(
                "Kani finished the Study now set. You can stop here, or explicitly continue through all current problem kanji.",
                StudyTextCopy.studyRunDoneBody()
        );
        assertEquals("Today's focus: 0 items left / 7", StudyTextCopy.adaptiveFocusDoneSummary(7));
        assertEquals("1 kanji moved forward this session", StudyTextCopy.movedForwardSummary(1));
        assertEquals("3 kanji moved forward this session", StudyTextCopy.movedForwardSummary(3));
        assertEquals("1 missed and will come back", StudyTextCopy.missedSummary(1));
        assertEquals("2 missed and will come back", StudyTextCopy.missedSummary(2));
        assertEquals("1 task completed", StudyTextCopy.completedTaskSummary(1));
        assertEquals("4 tasks completed", StudyTextCopy.completedTaskSummary(4));
    }

    @Test
    public void similarRepairPromptPreservesRepairCopyBranches() {
        assertEquals(
                "Repair the shape mix-up for pull. You picked 提; write 拉.",
                StudyTextCopy.similarRepairPrompt(repair("拉", "提", "pull"))
        );
        assertEquals(
                "Repair the shape mix-up. Write 拉.",
                StudyTextCopy.similarRepairPrompt(repair("拉", "", ""))
        );
        assertEquals("Repair saved.", StudyTextCopy.similarWritingRepairSavedToast(true));
        assertEquals("Saved. Try that repair again.", StudyTextCopy.similarWritingRepairSavedToast(false));
    }

    @Test
    public void studyReasonLineIsHiddenFromStudyCards() {
        RecordsStudyModels.StudyItem item = studyItem("裂");
        RecordsImportModels.DashboardRow row = row("裂", "split", "reason", Collections.emptyList());
        RecordsSchedulerModels.StudySession session = session(item, row, "prompt");

        assertEquals("", StudyTextCopy.studyReasonLine(true, session, 3, 1000L));
        assertEquals("", StudyTextCopy.studyReasonLine(false, session, 3, 1000L));
        assertEquals("", StudyTextCopy.studyReasonLine(false, null, 3, 1000L));
        assertEquals("", StudyTextCopy.studyReasonLine(false, session(item, null, "prompt"), 3, 1000L));
    }

    @Test
    public void copyHelpersTolerateLegacyNullItemSessionSentinel() {
        RecordsImportModels.DashboardRow row = row("裂", "split", "reason", Collections.emptyList());
        RecordsSchedulerModels.StudySession session = session(null, row, "fallback prompt");

        assertEquals("Split", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session));
        assertEquals("", StudyTextCopy.wordPrompt(session));
        assertEquals("", StudyTextCopy.studyReasonLine(false, session, 3, 1000L));
    }

    private static RecordsSchedulerModels.StudySession session(
            RecordsStudyModels.StudyItem item,
            RecordsImportModels.DashboardRow row,
            String prompt
    ) {
        return session(item, row, prompt, StudyTaskTypes.KANJI_MEANING);
    }

    private static RecordsSchedulerModels.StudySession session(
            RecordsStudyModels.StudyItem item,
            RecordsImportModels.DashboardRow row,
            String prompt,
            String taskType
    ) {
        return new RecordsSchedulerModels.StudySession(item, row, "token", taskType, false, prompt);
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
        return example(sourceType, expression, "meaning");
    }

    private static RecordsImportModels.Example example(String sourceType, String expression, String meaning) {
        return new RecordsImportModels.Example(
                sourceType,
                1L,
                2L,
                expression,
                "reading",
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

    private static RecordsImportModels.SimilarKanjiWritingRepair repair(String repairKanji, String wrongSelection, String promptMeaning) {
        return new RecordsImportModels.SimilarKanjiWritingRepair(
                1L,
                repairKanji,
                repairKanji,
                repairKanji + "|" + wrongSelection,
                wrongSelection,
                promptMeaning,
                "pending",
                0L,
                "",
                0,
                0L,
                0L,
                0L
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
