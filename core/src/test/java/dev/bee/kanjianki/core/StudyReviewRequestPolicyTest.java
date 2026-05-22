package dev.bee.kanjianki.core;

import org.junit.Test;

import java.lang.reflect.Modifier;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudyReviewRequestPolicyTest {
    @Test
    public void mapsWritingOutcomeIntoReviewPayload() {
        RecordsSchedulerModels.StudySession session = session("書", true, BridgeScheduler.TASK_WRITE_KANJI);

        StudyReviewRequestPolicy.MappedReview mapped = StudyReviewRequestPolicy.from(
                session,
                StudyReviewRequestPolicy.writingOutcome(true, false, StudyRatings.HARD),
                2,
                StudyRatings.EASY,
                false
        );
        RecordsSchedulerModels.ReviewRequest request = mapped.request();

        assertEquals(StudyRatings.HARD, mapped.ratingCode());
        assertEquals(StudyRatings.HARD, request.rating);
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
                StudyReviewRequestPolicy.from(writingSession, null, 0, StudyRatings.EASY, true);
        StudyReviewRequestPolicy.MappedReview nonWriting =
                StudyReviewRequestPolicy.from(readingSession, null, 0, StudyRatings.GOOD, false);

        assertEquals(StudyRatings.EASY, override.ratingCode());
        assertFalse(override.request().writingPassed);
        assertTrue(override.request().manualOverride);
        assertEquals(StudyRatings.GOOD, nonWriting.ratingCode());
        assertTrue(nonWriting.request().writingPassed);
        assertFalse(nonWriting.request().writingClean);
    }

    @Test
    public void distinguishesCleanPassAndFailedWritingOutcomes() {
        RecordsSchedulerModels.StudySession writingSession = session("清", true, BridgeScheduler.TASK_WRITE_KANJI);

        StudyReviewRequestPolicy.MappedReview clean = StudyReviewRequestPolicy.from(
                writingSession,
                StudyReviewRequestPolicy.writingOutcome(true, true, StudyRatings.GOOD),
                1,
                StudyRatings.GOOD,
                false
        );
        StudyReviewRequestPolicy.MappedReview fail = StudyReviewRequestPolicy.from(
                writingSession,
                StudyReviewRequestPolicy.writingOutcome(false, false, StudyRatings.AGAIN),
                3,
                StudyRatings.GOOD,
                false
        );

        assertTrue(clean.request().writingPassed);
        assertTrue(clean.request().writingClean);
        assertEquals(StudyRatings.GOOD, clean.ratingCode());
        assertFalse(fail.request().writingPassed);
        assertFalse(fail.request().writingClean);
        assertEquals(StudyRatings.AGAIN, fail.ratingCode());
        assertEquals(3, fail.request().hintsUsed);
    }

    @Test
    public void defaultsUnknownRatingsToAgain() {
        RecordsSchedulerModels.StudySession writingSession = session("迷", true, BridgeScheduler.TASK_WRITE_KANJI);

        StudyReviewRequestPolicy.MappedReview mapped =
                StudyReviewRequestPolicy.from(writingSession, null, 4, "not-a-rating", false);

        assertEquals(StudyRatings.AGAIN, mapped.ratingCode());
        assertEquals(StudyRatings.AGAIN, mapped.request().rating);
        assertFalse(mapped.request().writingPassed);
        assertEquals(4, mapped.request().hintsUsed);
    }

    @Test
    public void capsRequestedRatingAtWritingOutcomeCeiling() {
        RecordsSchedulerModels.StudySession writingSession = session("線", true, BridgeScheduler.TASK_WRITE_KANJI);

        StudyReviewRequestPolicy.MappedReview hardCap = StudyReviewRequestPolicy.from(
                writingSession,
                StudyReviewRequestPolicy.writingOutcome(true, false, StudyRatings.HARD),
                0,
                StudyRatings.EASY,
                false
        );
        StudyReviewRequestPolicy.MappedReview goodCap = StudyReviewRequestPolicy.from(
                writingSession,
                StudyReviewRequestPolicy.writingOutcome(true, true, StudyRatings.GOOD),
                0,
                StudyRatings.EASY,
                false
        );
        StudyReviewRequestPolicy.MappedReview easyCap = StudyReviewRequestPolicy.from(
                writingSession,
                StudyReviewRequestPolicy.writingOutcome(true, true, StudyRatings.EASY),
                0,
                StudyRatings.EASY,
                false
        );

        assertEquals(StudyRatings.HARD, hardCap.ratingCode());
        assertEquals(StudyRatings.GOOD, goodCap.ratingCode());
        assertEquals(StudyRatings.EASY, easyCap.ratingCode());
    }

    @Test
    public void policyResultConstructorsStayPrivate() throws NoSuchMethodException {
        assertTrue(Modifier.isPrivate(StudyReviewRequestPolicy.WritingOutcome.class
                .getDeclaredConstructor(boolean.class, boolean.class, String.class)
                .getModifiers()));
        assertTrue(Modifier.isPrivate(StudyReviewRequestPolicy.MappedReview.class
                .getDeclaredConstructor(RecordsSchedulerModels.ReviewRequest.class, String.class)
                .getModifiers()));
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
