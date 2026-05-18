package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StudyReviewRequestPolicy;
import dev.bee.kanjianki.core.study.WritingAnalysis;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class AppValueBehaviorTest {
    @Test
    public void studyCueTextsUseDictionaryCueBeforeCollectionFallback() {
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

        List<String> lines = StudyCueTexts.answerLines(lookup, session, example, false);

        assertEquals(Arrays.asList("Peace, cheap", "Reading: あんしん", "From: 安心"), lines);
    }

    @Test
    public void studyCueTextsFallbacksCleanCollectionClues() {
        RecordsSchedulerModels.StudySession session = session("語", false, BridgeScheduler.TASK_KANJI_MEANING);
        RecordsImportModels.Example example = example("言語", "", "(noun) JMdict [x] 1. language\nspeech");

        List<String> lines = StudyCueTexts.answerLines(DictionaryLookup.empty(), session, example, false);

        assertEquals(Arrays.asList("Language speech", "Reading: ご", "From: 言語"), lines);
        assertEquals("Small, tiny", StudyCueTexts.displayGlosses(Arrays.asList(" small ", "small", "tiny"), 3));
        assertEquals("Collection clue", StudyCueTexts.cleanFallbackMeaning("", "", 40));
        assertEquals("A very long clue with enough words to...", StudyCueTexts.cleanFallbackMeaning(
                "a very long clue with enough words to be compacted without chopping the first word",
                "",
                40
        ));
    }

    @Test
    public void studyCueTextsHandleEmptyAndWordReadingSessions() {
        List<String> emptyLines = StudyCueTexts.answerLines(DictionaryLookup.empty(), null, null, false);
        RecordsSchedulerModels.StudySession session = session("読", false, BridgeScheduler.TASK_WORD_READING);
        RecordsImportModels.Example example = example("読書", "ドクショ", "");

        List<String> wordReadingLines = StudyCueTexts.answerLines(DictionaryLookup.empty(), session, example, true);

        assertEquals(Collections.singletonList("Collection clue"), emptyLines);
        assertEquals(Arrays.asList("Reading: どくしょ", "From: 読書"), wordReadingLines);
    }

    @Test
    public void wordReadingCueDoesNotInventReadingWhenExampleAndRowAreBlank() {
        RecordsSchedulerModels.StudySession session = session("読", false, BridgeScheduler.TASK_WORD_READING, "", "");
        RecordsImportModels.Example blankExample = example("", "", "");

        List<String> lines = StudyCueTexts.answerLines(DictionaryLookup.empty(), session, blankExample, true);

        assertEquals(Collections.singletonList("Collection clue"), lines);
    }

    @Test
    public void studyCueTextsHandleNullRowsAndExamples() {
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
        RecordsSchedulerModels.StudySession wordReading = session("読", false, BridgeScheduler.TASK_WORD_READING, "", null);
        RecordsImportModels.Example emptyMeaning = example("言語", "ゴ", "");
        RecordsImportModels.Example nullExpression = example(null, null, "");

        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCueTexts.answerLines(DictionaryLookup.empty(), noRow, null, false)
        );
        assertEquals(
                Arrays.asList("Collection meaning", "Reading: ご", "From: 言語"),
                StudyCueTexts.answerLines(DictionaryLookup.empty(), regular, emptyMeaning, false)
        );
        assertEquals(
                Arrays.asList("Collection meaning", "Reading: ご"),
                StudyCueTexts.answerLines(DictionaryLookup.empty(), regular, null, false)
        );
        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCueTexts.answerLines(DictionaryLookup.empty(), wordReading, nullExpression, true)
        );
        assertEquals(
                Collections.singletonList("Collection clue"),
                StudyCueTexts.answerLines(DictionaryLookup.empty(), wordReading, null, true)
        );
    }

    @Test
    public void studyReviewRequestsMapWritingAnalysisIntoReviewPayload() {
        RecordsSchedulerModels.StudySession session = session("書", true, BridgeScheduler.TASK_WRITE_KANJI);
        WritingAnalysis analysis = new WritingAnalysis(
                WritingAnalysis.Status.CLOSE,
                "hard",
                true,
                "Close enough to pass, but not clean.",
                Collections.emptyList(),
                null
        );

        StudyReviewRequestPolicy.MappedReview mapped = StudyReviewRequestPolicy.from(session, analysis, 2, "easy", false);
        RecordsSchedulerModels.ReviewRequest request = mapped.request();

        assertEquals("hard", mapped.ratingCode());
        assertEquals("hard", request.rating);
        assertEquals("書", request.kanji);
        assertEquals("session-token", request.token);
        assertTrue(request.writingRequired);
        assertTrue(request.writingPassed);
        assertFalse(request.writingClean);
        assertFalse(request.manualOverride);
        assertEquals(2, request.hintsUsed);
        assertEquals(BridgeScheduler.TASK_WRITE_KANJI, request.taskType);
        assertEquals("answer-signature", request.answerSignature);
        assertEquals("prompt text", request.prompt);
    }

    @Test
    public void studyReviewRequestsRespectManualOverrideAndNonWritingTasks() {
        RecordsSchedulerModels.StudySession writingSession = session("筆", true, BridgeScheduler.TASK_WRITE_KANJI);
        RecordsSchedulerModels.StudySession readingSession = session("読", false, BridgeScheduler.TASK_WORD_READING);

        StudyReviewRequestPolicy.MappedReview override = StudyReviewRequestPolicy.from(writingSession, null, 0, "easy", true);
        StudyReviewRequestPolicy.MappedReview nonWriting = StudyReviewRequestPolicy.from(readingSession, null, 0, "good", false);

        assertEquals("easy", override.ratingCode());
        assertFalse(override.request().writingPassed);
        assertTrue(override.request().manualOverride);
        assertEquals("good", nonWriting.ratingCode());
        assertTrue(nonWriting.request().writingPassed);
        assertFalse(nonWriting.request().writingClean);
    }

    @Test
    public void studyReviewRequestsDistinguishCleanPassAndFailedWritingAnalysis() {
        RecordsSchedulerModels.StudySession writingSession = session("清", true, BridgeScheduler.TASK_WRITE_KANJI);
        WritingAnalysis cleanPass = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                "Clean pass.",
                Collections.singletonList(new RecognitionCandidate("清", 0.95f)),
                null
        );
        WritingAnalysis failed = new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "Wrong shape.",
                Collections.emptyList(),
                null
        );

        StudyReviewRequestPolicy.MappedReview clean = StudyReviewRequestPolicy.from(writingSession, cleanPass, 1, "good", false);
        StudyReviewRequestPolicy.MappedReview fail = StudyReviewRequestPolicy.from(writingSession, failed, 3, "good", false);

        assertTrue(clean.request().writingPassed);
        assertTrue(clean.request().writingClean);
        assertEquals("good", clean.ratingCode());
        assertFalse(fail.request().writingPassed);
        assertFalse(fail.request().writingClean);
        assertEquals("again", fail.ratingCode());
        assertEquals(3, fail.request().hintsUsed);
    }

    @Test
    public void studyTokenFactoryKeepsActiveTokensAndCreatesKanjiPrefixedTokens() {
        String existing = StudyTokenFactory.studyItem("学", "already-active");
        String generated = StudyTokenFactory.studyItem("学", "");
        String generatedFromNull = StudyTokenFactory.studyItem("習", null);

        assertEquals("already-active", existing);
        assertTrue(generated.startsWith("学-"));
        assertNotEquals("学-", generated);
        UUID.fromString(generated.substring("学-".length()));
        assertTrue(generatedFromNull.startsWith("習-"));
        UUID.fromString(generatedFromNull.substring("習-".length()));
    }

    @Test
    public void uiDateTextFormatsDeterministicRelativeDurations() {
        long now = 1_000_000L;

        assertEquals("date unknown", UiDateText.humanSyncTime(0L));
        assertEquals("due now", UiDateText.dueText(now, now));
        assertEquals("due in 1 min", UiDateText.dueText(now + 59_000L, now));
        assertEquals("due in 2 min", UiDateText.dueText(now + 120_000L, now));
        assertEquals("due in 3 hr", UiDateText.dueText(now + 3_600_000L * 3L, now));
        assertEquals("Unknown time", UiDateText.timelineDate(0L));
        assertEquals("not yet", UiDateText.autoUpdateLastCheckText(0L));
    }

    @Test
    public void uiDateTextFormatsOlderDueDatesAndNonZeroAutoUpdateChecks() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = 0L;
            long later = 3L * 24L * 3_600_000L;

            assertEquals("due Jan 4, 1970", UiDateText.dueText(later, now));
            assertTrue(UiDateText.timelineDate(later).contains("Jan 4, 1970"));
            assertTrue(UiDateText.autoUpdateLastCheckText(later).contains("Jan 4, 1970"));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void uiDateTextFormatsTodayYesterdayAndOlderSyncTimes() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 9);
            today.set(Calendar.MINUTE, 30);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Calendar yesterday = (Calendar) today.clone();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            Calendar older = (Calendar) today.clone();
            older.add(Calendar.DAY_OF_YEAR, -3);

            assertTrue(UiDateText.humanSyncTime(today.getTimeInMillis()).startsWith("today at "));
            assertTrue(UiDateText.humanSyncTime(yesterday.getTimeInMillis()).startsWith("yesterday at "));
            assertFalse(UiDateText.humanSyncTime(older.getTimeInMillis()).startsWith("today at "));
            assertFalse(UiDateText.humanSyncTime(older.getTimeInMillis()).startsWith("yesterday at "));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void uiDateTextComputesLocalDayBoundaries() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 15, 13, 45, 30);
        calendar.set(Calendar.MILLISECOND, 987);
        long now = calendar.getTimeInMillis();

        long nextDayStart = UiDateText.nextLocalDayStart(now);
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(nextDayStart);

        assertTrue(UiDateText.sameLocalDay(now, now + 1L));
        assertFalse(UiDateText.sameLocalDay(now, nextDayStart));
        assertEquals(0, next.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, next.get(Calendar.MINUTE));
        assertEquals(0, next.get(Calendar.SECOND));
        assertEquals(0, next.get(Calendar.MILLISECOND));
    }

    @Test
    public void uiDateTextSameLocalDayRejectsDifferentEraAndYear() {
        Calendar ad = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        ad.clear();
        ad.set(Calendar.ERA, GregorianCalendar.AD);
        ad.set(Calendar.YEAR, 2026);
        ad.set(Calendar.DAY_OF_YEAR, 1);
        Calendar nextYear = (Calendar) ad.clone();
        nextYear.add(Calendar.YEAR, 1);
        Calendar bc = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        bc.clear();
        bc.set(Calendar.ERA, GregorianCalendar.BC);
        bc.set(Calendar.YEAR, 1);
        bc.set(Calendar.DAY_OF_YEAR, 1);

        assertFalse(UiDateText.sameLocalDay(ad.getTimeInMillis(), nextYear.getTimeInMillis()));
        assertFalse(UiDateText.sameLocalDay(ad.getTimeInMillis(), bc.getTimeInMillis()));
    }

    private static RecordsSchedulerModels.StudySession session(String kanji, boolean writingRequired, String taskType) {
        return session(kanji, writingRequired, taskType, "collection meaning", "ゴ");
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
