package dev.bee.kanjianki;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class LegacyStudyReviewBridgeTest {
    private static final long DAY = 86_400_000L;

    private final LegacyStudyReviewBridge bridge = new LegacyStudyReviewBridge();

    @Test
    public void matchesBridgeSchedulerForDueReviewPassPromotion() {
        long dueAt = 30L * DAY;
        RecordsStudyModels.TaskMemory memory = memory("review", dueAt, 5.0, 6.0, 4, 0, 0, "good", 7);
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(memory)
                .activeToken("pass")
                .build();

        assertMatchesBridgeScheduler(
                item,
                request("裂", "pass", BridgeScheduler.RATING_GOOD),
                dueAt
        );
    }

    @Test
    public void matchesBridgeSchedulerForReviewAgainDemotion() {
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .realAgainStreak(2)
                .activeToken("again")
                .build();

        assertMatchesBridgeScheduler(
                item,
                request("裂", "again", BridgeScheduler.RATING_AGAIN),
                1_000L
        );
    }

    @Test
    public void matchesBridgeSchedulerForNewLearningRepeat() {
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem(
                "裂",
                "new",
                0L,
                0.4,
                5.0,
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
                "learn",
                10L
        );

        assertMatchesBridgeScheduler(
                item,
                request("裂", "learn", BridgeScheduler.RATING_GOOD),
                1_000L
        );
    }

    @Test
    public void matchesBridgeSchedulerForDuplicateToken() {
        RecordsStudyModels.StudyItem item = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withToken("dupe");
        RecordsSchedulerModels.ReviewRequest request = request("裂", "dupe", BridgeScheduler.RATING_GOOD);
        Set<String> schedulerConsumed = new HashSet<>();
        schedulerConsumed.add("dupe");
        Set<String> bridgeConsumed = new HashSet<>();
        bridgeConsumed.add("dupe");

        RecordsSchedulerModels.ReviewResult expected = new BridgeScheduler().applyReview(
                item,
                request,
                schedulerConsumed,
                1_000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsSchedulerModels.LearningStepSettings.defaults()
        );
        RecordsSchedulerModels.ReviewResult actual = bridge.applyReview(
                item,
                request,
                bridgeConsumed,
                1_000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsSchedulerModels.LearningStepSettings.defaults(),
                RecordsBase.StudyLadderSettings.defaults()
        );

        assertReviewResultEquals(expected, actual);
        assertEquals(schedulerConsumed, bridgeConsumed);
    }

    private void assertMatchesBridgeScheduler(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            long nowMillis
    ) {
        Set<String> schedulerConsumed = new HashSet<>();
        Set<String> bridgeConsumed = new HashSet<>();
        RecordsSchedulerModels.SchedulerParameters parameters = RecordsSchedulerModels.SchedulerParameters.defaults();
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        RecordsSchedulerModels.LearningStepSettings learning = RecordsSchedulerModels.LearningStepSettings.defaults();
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults();

        RecordsSchedulerModels.ReviewResult expected = new BridgeScheduler().applyReview(
                item,
                request,
                schedulerConsumed,
                nowMillis,
                parameters,
                settings,
                learning
        );
        RecordsSchedulerModels.ReviewResult actual = bridge.applyReview(
                item,
                request,
                bridgeConsumed,
                nowMillis,
                parameters,
                settings,
                learning,
                ladder
        );

        assertReviewResultEquals(expected, actual);
        assertEquals(schedulerConsumed, bridgeConsumed);
    }

    private static void assertReviewResultEquals(
            RecordsSchedulerModels.ReviewResult expected,
            RecordsSchedulerModels.ReviewResult actual
    ) {
        assertEquals(expected.appliedRating, actual.appliedRating);
        assertEquals(expected.duplicate, actual.duplicate);
        assertEquals(expected.message, actual.message);
        assertStudyItemEquals(expected.item, actual.item);
    }

    private static void assertStudyItemEquals(
            RecordsStudyModels.StudyItem expected,
            RecordsStudyModels.StudyItem actual
    ) {
        assertEquals(expected.kanji, actual.kanji);
        assertEquals(expected.state, actual.state);
        assertEquals(expected.dueAtMillis, actual.dueAtMillis);
        assertEquals(expected.stability, actual.stability, 0.0);
        assertEquals(expected.difficulty, actual.difficulty, 0.0);
        assertEquals(expected.totalReviews, actual.totalReviews);
        assertEquals(expected.lapses, actual.lapses);
        assertEquals(expected.learningStep, actual.learningStep);
        assertEquals(expected.writingLevel, actual.writingLevel);
        assertEquals(expected.recognitionStage, actual.recognitionStage);
        assertEquals(expected.consecutiveFailedRecognitionDays, actual.consecutiveFailedRecognitionDays);
        assertEquals(expected.lastFailedRecognitionDayMillis, actual.lastFailedRecognitionDayMillis);
        assertEquals(expected.writingRemediationPending, actual.writingRemediationPending);
        assertEquals(expected.matureIntervalDays, actual.matureIntervalDays);
        assertEquals(expected.answerSignature, actual.answerSignature);
        assertEquals(expected.activeToken, actual.activeToken);
        assertEquals(expected.rung, actual.rung);
        assertEquals(expected.phase, actual.phase);
        assertEquals(expected.realPassStreak, actual.realPassStreak);
        assertEquals(expected.realAgainStreak, actual.realAgainStreak);
        assertEquals(expected.lastRealReviewDueAtMillis, actual.lastRealReviewDueAtMillis);
        assertEquals(expected.hasSimilarKanji, actual.hasSimilarKanji);
        assertEquals(expected.typingMeaningMemory.encode(), actual.typingMeaningMemory.encode());
        assertEquals(expected.meaningKanjiMemory.encode(), actual.meaningKanjiMemory.encode());
        assertEquals(expected.kanjiMeaningMemory.encode(), actual.kanjiMeaningMemory.encode());
        assertEquals(expected.fontMeaningMemory.encode(), actual.fontMeaningMemory.encode());
        assertEquals(expected.wordReadingMemory.encode(), actual.wordReadingMemory.encode());
        assertEquals(expected.writingRemediationMemory.encode(), actual.writingRemediationMemory.encode());
        assertEquals(expected.similarKanjiMemory.encode(), actual.similarKanjiMemory.encode());
    }

    private static RecordsSchedulerModels.ReviewRequest request(String kanji, String token, String rating) {
        return new RecordsSchedulerModels.ReviewRequest(
                kanji,
                token,
                rating,
                false,
                false,
                false,
                0
        );
    }

    private static RecordsStudyModels.StudyItem reviewItem(
            String kanji,
            RecordsBase.LadderRung rung,
            long dueAtMillis
    ) {
        RecordsStudyModels.TaskMemory memory = memory(
                "review",
                dueAtMillis,
                5.0,
                6.0,
                1,
                0,
                0,
                "good",
                1
        );
        return new RecordsStudyModels.StudyItem(
                kanji,
                "review",
                dueAtMillis,
                5.0,
                6.0,
                1,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                "",
                0L,
                1,
                "",
                "",
                10L,
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                memory,
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                RecordsStudyModels.TaskMemory.initial(),
                rung,
                RecordsBase.SchedulerPhase.REVIEW,
                0,
                0,
                0L,
                false,
                RecordsStudyModels.TaskMemory.initial()
        );
    }

    private static RecordsStudyModels.TaskMemory memory(
            String state,
            long dueAtMillis,
            double stability,
            double difficulty,
            int totalReviews,
            int lapses,
            int learningStep,
            String lastRating,
            int matureIntervalDays
    ) {
        return new RecordsStudyModels.TaskMemory(
                state,
                dueAtMillis,
                stability,
                difficulty,
                totalReviews,
                lapses,
                learningStep,
                lastRating,
                matureIntervalDays
        );
    }
}
