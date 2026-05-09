package dev.bee.kanjianki.core;

import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeOrderEvaluation;
import dev.bee.kanjianki.core.study.WritingAnalysis;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RecordsValueCoverageTest {
    @Test
    public void timelineAndRepairRecordsNormalizeInputs() {
        Records.SimilarKanjiWritingRepair repair = new Records.SimilarKanjiWritingRepair(
                -1L,
                null,
                null,
                null,
                null,
                null,
                "",
                -2L,
                null,
                -3,
                -4L,
                -5L,
                -6L
        );

        assertEquals(0L, repair.id);
        assertEquals("", repair.targetKanji);
        assertEquals("pending", repair.status);
        assertEquals(0L, repair.dueAtMillis);
        assertEquals(0, repair.attempts);
        assertEquals("token", repair.withToken("token", 12L).activeToken);

        Records.KanjiTimelineEvent event = new Records.KanjiTimelineEvent(
                1L,
                "拉",
                2L,
                "review",
                "title",
                "detail",
                null,
                null,
                null,
                true,
                false,
                true,
                10,
                1,
                99L,
                "dedupe"
        );
        assertEquals("", event.sourceExpression);
        assertEquals("", event.sourceReading);
        assertEquals("", event.rating);

        Records.KanjiRecoveryTimeline timeline = new Records.KanjiRecoveryTimeline(
                new Records.KanjiInventoryItem("拉", "pull", "ら", "拉", 1, 1, false, 5L),
                row("拉"),
                item("拉"),
                Collections.singletonList(event)
        );
        assertEquals("拉", timeline.inventoryItem.kanji);
        assertEquals(1, timeline.events.size());
        assertEquals(1, new Records.KanjiRecoveryTimeline(row("拉"), item("拉"), Collections.singletonList(event)).events.size());
    }

    @Test
    public void taskMemoryLearningRepeatAndReviewStatsCoverFallbacks() {
        Records.TaskMemory fallback = new Records.TaskMemory("fallback", 1L, 2.0, 3.0, 4, 5, 1, "good", 6);
        assertSame(fallback, Records.TaskMemory.decode(null, fallback));
        assertSame(fallback, Records.TaskMemory.decode("too\tshort", fallback));
        assertSame(fallback, Records.TaskMemory.decode("new\tbad\t0.4\t5.0\t0\t0\t0\t\t0", fallback));

        Records.TaskMemory decoded = Records.TaskMemory.decode(
                new Records.TaskMemory(null, -1L, 0.4, 5.0, -2, -3, -4, null, -5).encode(),
                null
        );
        assertEquals("new", decoded.state);
        assertEquals(0L, decoded.dueAtMillis);
        assertEquals("", decoded.lastRating);
        assertEquals(0, decoded.consecutivePasses);
        assertEquals(0L, decoded.lastPassedDueAtMillis);
        Records.TaskMemory promoted = fallback.withDueAtMillis(10L);
        assertEquals(10L, promoted.dueAtMillis);
        assertEquals(fallback.consecutivePasses, promoted.consecutivePasses);

        Records.LearningRepeat repeat = new Records.LearningRepeat(null, null, null, "bad", -1, -2L, null, -3L, -4L);
        assertEquals("", repeat.kanji);
        assertEquals(Records.LEARNING_REPEAT_NEW, repeat.repeatType);
        assertEquals(0, repeat.stepIndex);
        assertEquals("tok", repeat.withToken("tok", 10L).activeToken);
        assertEquals(3, repeat.withStep(3, 20L, 30L).stepIndex);

        assertEquals(1.0, new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0).retentionProxy(), 0.001);
        assertEquals(0.25, new Records.ReviewStats(4, 1, 1, 1, 1, 4, 1).writingFailureRate(), 0.001);
    }

    @Test
    public void strokeEvaluationAndPointValuesCoverAccessorsAndFallbacks() {
        StrokeOrderEvaluation empty = new StrokeOrderEvaluation(-1, -1, -1, null, null, null, null, 2.0);
        assertEquals(0, empty.expectedCount());
        assertEquals(0, empty.attemptedCount());
        assertEquals(0, empty.orderedMatchCount());
        assertEquals(1.0, empty.score(), 0.001);
        assertFalse(empty.complete());
        assertFalse(empty.exactOrder());
        assertFalse(empty.passed());

        StrokeOrderEvaluation exact = new StrokeOrderEvaluation(
                2,
                2,
                2,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                -1.0
        );
        assertTrue(exact.complete());
        assertTrue(exact.exactOrder());
        assertTrue(exact.passed());
        assertEquals(0.0, exact.score(), 0.001);

        StrokeOrderEvaluation imperfect = new StrokeOrderEvaluation(
                2,
                2,
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("2"),
                0.5
        );
        assertTrue(imperfect.complete());
        assertFalse(imperfect.exactOrder());
        assertEquals(Collections.singletonList("2"), imperfect.outOfPositionStrokeIds());
        assertEquals(Collections.emptyList(), imperfect.missingStrokeIds());
        assertEquals(Collections.emptyList(), imperfect.extraStrokeIds());
        assertEquals(Collections.emptyList(), imperfect.duplicateStrokeIds());

        InkPoint point = new InkPoint(0.25f, 0.5f, 7L);
        assertEquals(new InkPoint(25f, 100f, 7L), point.scaled(100f, 200f));
        Object nonPoint = "not a point";
        boolean equalsNonPoint = point.equals(nonPoint);
        assertFalse(equalsNonPoint);
        assertNotEquals(point, new InkPoint(0.25f, 0.6f, 7L));
        assertEquals(point.hashCode(), new InkPoint(0.25f, 0.5f, 7L).hashCode());
    }

    @Test
    public void writingAnalysisAndDiagnosisCoverFallbacks() {
        WritingAnalysis fallback = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                null,
                Collections.singletonList(new RecognitionCandidate("拉", null)),
                null,
                null,
                -1
        );

        assertEquals("", fallback.message);
        assertEquals(HintLevel.BLIND, fallback.hintLevel());
        assertEquals(0, fallback.hintsUsed());
        assertTrue(fallback.passed());
        assertFalse(fallback.failed());
        assertEquals((0.78 * 0.55) + (0.7 * 0.45), fallback.confidenceScore(), 0.001);

        WritingAnalysis failed = new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "wrong",
                Collections.emptyList(),
                null
        );
        assertTrue(failed.failed());
        assertEquals(0.0, failed.confidenceScore(), 0.001);

        StrokeDiagnosis diagnosis = StrokeDiagnosis.builder()
                .add(null, 1)
                .add(StrokeDiagnosis.Label.WRONG_ORDER, -1)
                .add(StrokeDiagnosis.Label.WRONG_ORDER, -1)
                .build();
        assertFalse(diagnosis.isEmpty());
        assertTrue(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER));
        assertTrue(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 0));
        assertFalse(diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE));
        assertFalse(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 1));
        assertEquals(2, diagnosis.plus(StrokeDiagnosis.Label.MISSING_STROKE, 2).entries.size());
        assertTrue(StrokeDiagnosis.builder().build().isEmpty());
    }

    @Test
    public void releaseInfoAndAdaptiveLoadPlanCoverNullBranches() {
        Records.ReleaseInfo emptyRelease = new Records.ReleaseInfo("v0", "https://example", Collections.emptyList());
        assertNull(emptyRelease.apkAsset());
        assertNull(emptyRelease.checksumAssetFor("kani.apk"));

        Records.AdaptiveLoadPlan complete = new Records.AdaptiveLoadPlan(
                true,
                100,
                1,
                0,
                Arrays.asList("拉"),
                0,
                false,
                null
        );
        assertTrue(complete.autoMode);
        assertEquals("", complete.status);
        assertTrue(complete.focusComplete());

        Records.AdaptiveLoadPlan all = new Records.AdaptiveLoadPlan(100, 1, 0, Arrays.asList("拉"), 0, true, "all");
        assertFalse(all.focusComplete());
    }

    private static Records.DashboardRow row(String kanji) {
        return new Records.DashboardRow(kanji, 1, "meaning", "reading", kanji, 10, "reason", "reason", 1, 0, 0, Collections.emptyList());
    }

    private static Records.StudyItem item(String kanji) {
        return new Records.StudyItem(kanji, "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);
    }
}
