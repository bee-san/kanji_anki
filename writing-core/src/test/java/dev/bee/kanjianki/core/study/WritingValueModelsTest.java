package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WritingValueModelsTest {
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
}
