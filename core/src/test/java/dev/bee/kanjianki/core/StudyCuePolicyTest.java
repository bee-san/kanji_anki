package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class StudyCuePolicyTest {
    @Test
    public void dictionaryCueWinsOverCollectionFallback() {
        DictionaryLookup.KanjiEntryFields fields = new DictionaryLookup.KanjiEntryFields(
                "安",
                Arrays.asList("peace", "cheap", "duplicate ignored"),
                Collections.singletonList("アン"),
                Collections.singletonList("やす.い"),
                Collections.emptyList(),
                6,
                3,
                40,
                500,
                1200
        );
        DictionaryLookup lookup = DictionaryLookup.fromKanjiEntries(
                Collections.singletonList(new DictionaryLookup.KanjiEntry(fields))
        );
        RecordsSchedulerModels.StudySession session = session("安", false, BridgeScheduler.TASK_KANJI_MEANING);
        RecordsImportModels.Example example = example("安心", "アンシン", "old collection meaning");

        assertEquals(
                Arrays.asList("Peace, cheap", "Reading: あんしん", "From: 安心"),
                StudyCuePolicy.answerLines(lookup, session, example, false)
        );
    }

    @Test
    public void collectionFallbackIsCleanedWhenDictionaryHasNoEntry() {
        RecordsSchedulerModels.StudySession session = session("語", false, BridgeScheduler.TASK_KANJI_MEANING);
        RecordsImportModels.Example example = example("言語", "", "(noun) JMdict [x] 1. language\nspeech");

        assertEquals(
                Arrays.asList("Language speech", "Reading: ご", "From: 言語"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), session, example, false)
        );
        assertEquals("Small, tiny", StudyCuePolicy.displayGlosses(Arrays.asList(" small ", "small", "tiny"), 3));
        assertEquals("Collection clue", StudyCuePolicy.cleanFallbackMeaning("", "", 40));
        assertEquals("A very long clue with enough words to...", StudyCuePolicy.cleanFallbackMeaning(
                "a very long clue with enough words to be compacted without chopping the first word",
                "",
                40
        ));
    }

    @Test
    public void wordReadingCueUsesExampleReadingAndExpression() {
        RecordsSchedulerModels.StudySession session = session("読", false, BridgeScheduler.TASK_WORD_READING);
        RecordsImportModels.Example example = example("読書", "ドクショ", "");

        assertEquals(
                Arrays.asList("Reading: どくしょ", "From: 読書"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), session, example, true)
        );
    }

    @Test
    public void emptySessionsAndBlankWordReadingCuesUseCollectionClueFallback() {
        RecordsSchedulerModels.StudySession wordReading = session("読", false, BridgeScheduler.TASK_WORD_READING, "", null);

        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), null, null, false)
        );
        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), wordReading, example(null, null, ""), true)
        );
        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), wordReading, null, true)
        );
    }

    @Test
    public void nullRowsAndExamplesPreserveExistingFallbacks() {
        RecordsSchedulerModels.StudySession noRow = new RecordsSchedulerModels.StudySession(
                new RecordsStudyModels.StudyItem(
                        "空",
                        "new",
                        1234L,
                        0.0,
                        0.0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0L,
                        false,
                        "",
                        0L,
                        0,
                        "",
                        "",
                        100L
                ),
                null,
                "token",
                BridgeScheduler.TASK_KANJI_MEANING,
                false,
                ""
        );
        RecordsSchedulerModels.StudySession regular = session("語", false, BridgeScheduler.TASK_KANJI_MEANING);

        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), noRow, null, false)
        );
        assertEquals(
                Arrays.asList("Collection meaning", "Reading: ご", "From: 言語"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), regular, example("言語", "ゴ", ""), false)
        );
        assertEquals(
                Arrays.asList("Collection meaning", "Reading: ご"),
                StudyCuePolicy.answerLines(DictionaryLookup.empty(), regular, null, false)
        );
    }

    @Test
    public void exposesStudyCueForCoreCallers() {
        RecordsSchedulerModels.StudySession session = session("読", false, BridgeScheduler.TASK_WORD_READING);
        RecordsImportModels.Example example = example(" 読書 ", " ドクショ ", "");

        StudyCue cue = StudyCuePolicy.studyCue(DictionaryLookup.empty(), session, example, true);

        assertEquals("", cue.meaning);
        assertEquals("ドクショ", cue.reading);
        assertEquals("読書", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_ANKI, cue.meaningSource);
    }

    private static RecordsSchedulerModels.StudySession session(String kanji, boolean writingRequired, String taskType) {
        return session(kanji, writingRequired, taskType, "collection meaning", "ご");
    }

    private static RecordsSchedulerModels.StudySession session(
            String kanji,
            boolean writingRequired,
            String taskType,
            String primaryMeaning,
            String reading
    ) {
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem(
                kanji,
                "new",
                1234L,
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                writingRequired,
                "",
                0L,
                0,
                "answer-signature",
                "active-token",
                100L
        );
        RecordsImportModels.DashboardRow row = new RecordsImportModels.DashboardRow(
                kanji,
                null,
                primaryMeaning,
                reading,
                kanji,
                1,
                "reason",
                "Needs practice",
                1,
                0,
                0,
                Collections.emptyList()
        );
        return new RecordsSchedulerModels.StudySession(item, row, "session-token", taskType, writingRequired, "prompt text");
    }

    private static RecordsImportModels.Example example(String expression, String reading, String meaning) {
        return new RecordsImportModels.Example("anki", 1L, 2L, expression, reading, meaning, "", false, 0);
    }
}
