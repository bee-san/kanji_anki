package dev.bee.kanjianki.core.study;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudyReviewRequestPolicyTest {
    @Test
    public void mapsWritingAnalysisIntoReviewPayload() {
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
    public void respectsManualOverrideAndNonWritingTasks() {
        RecordsSchedulerModels.StudySession writingSession = session("筆", true, BridgeScheduler.TASK_WRITE_KANJI);
        RecordsSchedulerModels.StudySession readingSession = session("読", false, BridgeScheduler.TASK_WORD_READING);

        StudyReviewRequestPolicy.MappedReview override =
                StudyReviewRequestPolicy.from(writingSession, null, 0, "easy", true);
        StudyReviewRequestPolicy.MappedReview nonWriting =
                StudyReviewRequestPolicy.from(readingSession, null, 0, "good", false);

        assertEquals("easy", override.ratingCode());
        assertFalse(override.request().writingPassed);
        assertTrue(override.request().manualOverride);
        assertEquals("good", nonWriting.ratingCode());
        assertTrue(nonWriting.request().writingPassed);
        assertFalse(nonWriting.request().writingClean);
    }

    @Test
    public void distinguishesCleanPassAndFailedWritingAnalysis() {
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

        StudyReviewRequestPolicy.MappedReview clean =
                StudyReviewRequestPolicy.from(writingSession, cleanPass, 1, "good", false);
        StudyReviewRequestPolicy.MappedReview fail =
                StudyReviewRequestPolicy.from(writingSession, failed, 3, "good", false);

        assertTrue(clean.request().writingPassed);
        assertTrue(clean.request().writingClean);
        assertEquals("good", clean.ratingCode());
        assertFalse(fail.request().writingPassed);
        assertFalse(fail.request().writingClean);
        assertEquals("again", fail.ratingCode());
        assertEquals(3, fail.request().hintsUsed);
    }

    @Test
    public void defaultsUnknownRatingsToAgain() {
        RecordsSchedulerModels.StudySession writingSession = session("迷", true, BridgeScheduler.TASK_WRITE_KANJI);

        StudyReviewRequestPolicy.MappedReview mapped =
                StudyReviewRequestPolicy.from(writingSession, null, 4, "not-a-rating", false);

        assertEquals("again", mapped.ratingCode());
        assertEquals("again", mapped.request().rating);
        assertFalse(mapped.request().writingPassed);
        assertEquals(4, mapped.request().hintsUsed);
    }

    private static RecordsSchedulerModels.StudySession session(String kanji, boolean writingRequired, String taskType) {
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
                "collection meaning",
                "ご",
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
}
