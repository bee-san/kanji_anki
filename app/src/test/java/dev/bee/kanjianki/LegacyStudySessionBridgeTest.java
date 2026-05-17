package dev.bee.kanjianki;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class LegacyStudySessionBridgeTest {
    private final LegacyStudySessionBridge bridge = new LegacyStudySessionBridge();

    @Test
    public void matchesBridgeSchedulerForDueReviewTokenReuse() {
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                row("謎", 10),
                row("裂", 80)
        );
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(
                reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L, "low"),
                reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L, "kept")
        );

        assertMatchesBridgeScheduler(items, rows, 1_000L, 0L, null, RecordsSyncModels.Settings.kikuDefaults());
    }

    @Test
    public void matchesBridgeSchedulerForWriteAndRelearningPriority() {
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                row("謎", 100),
                row("習", 10),
                row("裂", 1)
        );
        RecordsStudyModels.StudyItem review = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L, "review");
        RecordsStudyModels.StudyItem relearning = reviewItem("習", RecordsBase.LadderRung.KANJI_MEANING, 0L, "relearn")
                .copyBuilder()
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build();
        RecordsStudyModels.StudyItem write = reviewItem("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L, "write");

        assertMatchesBridgeScheduler(
                Arrays.asList(review, relearning, write),
                rows,
                1_000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults()
        );
        assertMatchesBridgeScheduler(
                Arrays.asList(review, relearning),
                rows,
                1_000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults()
        );
    }

    @Test
    public void matchesBridgeSchedulerForNewCardSortMode() {
        RecordsSyncModels.Settings difficultySort = settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                rankedRow("低", 300, 90, example("低", 3.0, 0.60)),
                rankedRow("難", 100, 10, example("難", 8.0, 0.90))
        );
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(
                newItem("低", "low-token"),
                newItem("難", "hard-token")
        );

        assertMatchesBridgeScheduler(items, rows, 2_000L, 0L, null, difficultySort);
    }

    @Test
    public void matchesBridgeSchedulerForNothingDueOrAllowed() {
        RecordsStudyModels.StudyItem future = reviewItem(
                "裂",
                RecordsBase.LadderRung.KANJI_MEANING,
                2_000L,
                "future"
        );

        assertMatchesBridgeScheduler(
                Collections.singletonList(future),
                Collections.singletonList(row("裂", 30)),
                1_000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults()
        );
        assertMatchesBridgeScheduler(
                Collections.singletonList(newItem("裂", "allowed")),
                Collections.singletonList(row("裂", 30)),
                1_000L,
                0L,
                Collections.singleton("提"),
                RecordsSyncModels.Settings.kikuDefaults()
        );
    }

    private void assertMatchesBridgeScheduler(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            java.util.Set<String> allowedKanji,
            RecordsSyncModels.Settings settings
    ) {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults();
        RecordsSchedulerModels.StudySession expected = new BridgeScheduler().nextSession(
                items,
                rows,
                nowMillis,
                studyAheadMillis,
                allowedKanji,
                settings,
                ladder
        );
        RecordsSchedulerModels.StudySession actual = bridge.nextSession(
                items,
                rows,
                nowMillis,
                studyAheadMillis,
                allowedKanji,
                settings,
                ladder
        );

        assertSessionEquals(expected, actual);
    }

    private static void assertSessionEquals(
            RecordsSchedulerModels.StudySession expected,
            RecordsSchedulerModels.StudySession actual
    ) {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertEquals(expected.token, actual.token);
        assertEquals(expected.taskType, actual.taskType);
        assertEquals(expected.writingRequired, actual.writingRequired);
        assertEquals(expected.prompt, actual.prompt);
        assertSame(expected.row, actual.row);
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

    private static RecordsStudyModels.StudyItem newItem(String kanji, String token) {
        return new RecordsStudyModels.StudyItem(
                kanji,
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
                token,
                10L
        );
    }

    private static RecordsStudyModels.StudyItem reviewItem(
            String kanji,
            RecordsBase.LadderRung rung,
            long dueAtMillis,
            String token
    ) {
        boolean writing = rung == RecordsBase.LadderRung.WRITE_KANJI;
        return new RecordsStudyModels.StudyItem(
                kanji,
                "review",
                dueAtMillis,
                5.0,
                6.0,
                1,
                0,
                0,
                writing ? 1 : 0,
                0,
                0,
                0L,
                writing,
                "",
                0L,
                1,
                "",
                token,
                10L
        ).copyBuilder()
                .rung(rung)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
    }

    private static RecordsImportModels.DashboardRow row(String kanji, int score) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                score,
                "reason",
                "reason text",
                1,
                score > 15 ? 1 : 0,
                0,
                new ArrayList<>()
        );
    }

    private static RecordsImportModels.DashboardRow rankedRow(
            String kanji,
            Integer rank,
            int score,
            RecordsImportModels.Example... examples
    ) {
        ArrayList<RecordsImportModels.Example> list = new ArrayList<>();
        Collections.addAll(list, examples);
        return new RecordsImportModels.DashboardRow(
                kanji,
                rank,
                "meaning",
                "reading",
                "search",
                score,
                "reason",
                "reason text",
                1,
                score > 15 ? 1 : 0,
                0,
                list
        );
    }

    private static RecordsImportModels.Example example(String kanji, Double difficulty, Double retrievability) {
        long id = kanji.codePointAt(0);
        return new RecordsImportModels.Example(
                "active",
                id,
                id + 1L,
                kanji,
                "reading",
                "meaning",
                "",
                false,
                0,
                10,
                3,
                20.0,
                difficulty,
                retrievability
        );
    }

    private RecordsSyncModels.Settings settingsWithSortMode(String mode) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                defaults.importActiveCards,
                defaults.importSuspendedCards,
                defaults.importTaggedCards,
                defaults.importTags,
                defaults.importWeakCards,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                defaults.importMinMatchingCardsPerKanji,
                defaults.importBrowserQueryCards,
                defaults.importBrowserQuery,
                mode
        );
    }
}
