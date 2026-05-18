package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrokeOrderEvaluatorTest {
    @Test
    public void matchingStrokeStartsAndEndsPassCleanly() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 30f, 90f, 30f)
        ));

        assertTrue(result.acceptable);
        assertTrue(result.clean);
        assertTrue(result.diagnosis.isEmpty());
    }

    @Test
    public void reversedStrokeDirectionIsPenalized() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(90f, 10f, 10f, 10f),
                stroke(10f, 30f, 90f, 30f)
        ));

        assertFalse(result.clean);
        assertTrue(result.score < 1f);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1));
    }

    @Test
    public void scaledAndTranslatedWritingStillChecksOrder() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(220f, 240f, 460f, 240f),
                stroke(220f, 300f, 460f, 300f)
        ));

        assertTrue(result.acceptable);
        assertTrue(result.clean);
        assertTrue(result.diagnosis.isEmpty());
    }

    @Test
    public void missingStrokeReportsMissingStroke() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(10f, 10f, 90f, 10f)
        ));

        assertTrue(result.acceptable);
        assertFalse(result.clean);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, 2));
    }

    @Test
    public void skippedMiddleStrokeReportsThatGuideStrokeMissing() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(threeStrokeGuide(), sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 50f, 90f, 50f)
        ));

        assertTrue(result.acceptable);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, 2));
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, 3));
    }

    @Test
    public void swappedStrokesReportWrongOrder() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(10f, 30f, 90f, 30f),
                stroke(10f, 10f, 90f, 10f)
        ));

        assertTrue(result.acceptable);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 1));
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 2));
    }

    @Test
    public void weakButRecognizedAttemptReportsRecognizedButMessy() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                sample(
                        stroke(90f, 10f, 10f, 10f),
                        stroke(10f, 30f, 90f, 30f)
                ),
                guide(),
                Collections.singletonList(new RecognitionCandidate("拉", 0.99f))
        );

        assertTrue(analysis.writingPassed);
        assertEquals("hard", analysis.rating);
        assertFalse(analysis.strokeOrder.clean);
        assertTrue(analysis.strokeOrder.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1));
        assertTrue(analysis.strokeOrder.diagnosis.hasLabel(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY));
    }

    @Test
    public void diagnosisDoesNotAlterPassFailOrRating() {
        WritingAnalysis analysis = WritingAnalysisEngine.analyze(
                "拉",
                sample(
                        stroke(90f, 10f, 10f, 10f),
                        stroke(10f, 30f, 90f, 30f)
                ),
                guide(),
                Collections.singletonList(new RecognitionCandidate("拉", 0.99f))
        );

        assertTrue(analysis.strokeOrder.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1));
        assertTrue(analysis.writingPassed);
        assertEquals("hard", analysis.rating);
        assertEquals(StudyRating.HARD, new WritingRatingMapper().applyRequestedRating(StudyRating.GOOD, true, analysis, false));
    }

    @Test
    public void missingGuideDoesNotSilentlyPass() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(null, sample(stroke(0f, 0f, 1f, 1f)));

        assertTrue(result.missingGuide);
        assertFalse(result.clean);
        assertTrue(result.diagnosis.isEmpty());
    }

    @Test
    public void emptyGuideAndEmptySampleReturnExplicitFailures() {
        StrokeOrderEvaluator.StrokeOrderResult missingGuide = StrokeOrderEvaluator.evaluate(new StrokeGuide("拉", Collections.emptyList()), sample(stroke(0f, 0f, 1f, 1f)));
        StrokeOrderEvaluator.StrokeOrderResult noInk = StrokeOrderEvaluator.evaluate(guide(), WritingSample.empty());
        StrokeOrderEvaluator.StrokeOrderResult nullSample = StrokeOrderEvaluator.evaluate(guide(), null);

        assertTrue(missingGuide.missingGuide);
        assertFalse(missingGuide.acceptable);
        assertFalse(noInk.missingGuide);
        assertFalse(noInk.acceptable);
        assertEquals("No ink was drawn.", noInk.message);
        assertEquals("No ink was drawn.", nullSample.message);
    }

    @Test
    public void emptyStrokeAndNullPointsProduceRoughFailureWithDefaultBounds() {
        StrokeGuide guide = new StrokeGuide(
                "拉",
                Arrays.asList(new InkStroke(Collections.emptyList()))
        );
        WritingSample sample = sample(new InkStroke(Arrays.asList(
                null,
                new InkPoint(100f, 100f, 1)
        )));

        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide, sample);

        assertFalse(result.clean);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1));
    }

    @Test
    public void emptySampleStrokeBeforeInkProducesRoughFailure() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(
                guide(),
                sample(
                        new InkStroke(Collections.emptyList()),
                        stroke(10f, 10f, 90f, 10f)
                )
        );

        assertFalse(result.acceptable);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1));
    }

    @Test
    public void nullStrokeEndpointsProduceRoughFailure() {
        StrokeGuide nullGuideEnd = new StrokeGuide(
                "拉",
                Collections.singletonList(new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), null)))
        );
        WritingSample normalSample = sample(stroke(10f, 10f, 90f, 10f));
        WritingSample nullSampleEnd = sample(new InkStroke(Arrays.asList(new InkPoint(10f, 10f, 0), null)));

        StrokeOrderEvaluator.StrokeOrderResult guideEndMissing = StrokeOrderEvaluator.evaluate(nullGuideEnd, normalSample);
        StrokeOrderEvaluator.StrokeOrderResult sampleEndMissing = StrokeOrderEvaluator.evaluate(
                new StrokeGuide("拉", Collections.singletonList(new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), new InkPoint(0.9f, 0.1f, 1))))),
                nullSampleEnd
        );

        assertFalse(guideEndMissing.clean);
        assertFalse(sampleEndMissing.clean);
        assertTrue(guideEndMissing.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1));
        assertTrue(sampleEndMissing.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1));
    }

    @Test
    public void shortStrokeDoesNotReportWrongDirectionWhenDirectMatchIsStronger() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(
                singleStrokeGuide(0.1f, 0.1f, 0.2f, 0.1f),
                sample(stroke(10f, 10f, 20f, 10f))
        );

        assertTrue(result.clean);
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1));
        assertTrue(result.diagnosis.isEmpty());
    }

    @Test
    public void directionlessRecognizableStrokeDoesNotReportRoughShape() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(
                singleStrokeGuide(0.1f, 0.1f, 0.9f, 0.1f),
                sample(new InkStroke(Arrays.asList(
                        new InkPoint(100f, 0f, 0),
                        new InkPoint(0f, 0f, 1),
                        new InkPoint(50f, 0f, 2)
                )))
        );

        assertFalse(result.clean);
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1));
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1));
    }

    @Test
    public void roughShapeWithCorrectCountIsNotAcceptable() {
        StrokeOrderEvaluator.StrokeOrderResult result = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(90f, 90f, 90f, 90f),
                stroke(90f, 90f, 90f, 90f),
                stroke(90f, 90f, 90f, 90f),
                stroke(90f, 90f, 90f, 90f)
        ));

        assertFalse(result.acceptable);
        assertFalse(result.clean);
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1));
    }

    @Test
    public void withDiagnosisNormalizesNullDiagnosis() {
        StrokeOrderEvaluator.StrokeOrderResult clean = StrokeOrderEvaluator.evaluate(guide(), sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 30f, 90f, 30f)
        ));

        assertTrue(clean.withDiagnosis(null).diagnosis.isEmpty());
    }

    @Test
    public void strokeOrderEvaluationNormalizesBoundsAndIncompleteStates() {
        StrokeOrderEvaluation normalized = new StrokeOrderEvaluation(
                -1,
                -1,
                -1,
                null,
                Collections.singletonList("extra"),
                Collections.singletonList("duplicate"),
                Collections.singletonList("late"),
                2.0
        );
        StrokeOrderEvaluation exact = new StrokeOrderEvaluation(
                1,
                1,
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                1.0
        );
        StrokeOrderEvaluation wrongOrder = new StrokeOrderEvaluation(
                1,
                1,
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("1"),
                0.9
        );
        StrokeOrderEvaluation wrongCount = new StrokeOrderEvaluation(
                2,
                1,
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0.7
        );
        StrokeOrderEvaluation missing = new StrokeOrderEvaluation(
                1,
                1,
                1,
                Collections.singletonList("1"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0.7
        );
        StrokeOrderEvaluation extra = new StrokeOrderEvaluation(
                1,
                1,
                1,
                Collections.emptyList(),
                Collections.singletonList("2"),
                Collections.emptyList(),
                Collections.emptyList(),
                0.7
        );
        StrokeOrderEvaluation duplicate = new StrokeOrderEvaluation(
                1,
                1,
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("1"),
                Collections.emptyList(),
                0.7
        );

        assertEquals(0, normalized.expectedCount());
        assertEquals(1.0, normalized.score(), 0.001);
        assertFalse(normalized.complete());
        assertTrue(exact.complete());
        assertTrue(exact.exactOrder());
        assertFalse(wrongOrder.exactOrder());
        assertFalse(wrongCount.complete());
        assertFalse(missing.complete());
        assertFalse(extra.complete());
        assertFalse(duplicate.complete());
    }

    private StrokeGuide guide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), new InkPoint(0.9f, 0.1f, 1))),
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.3f, 0), new InkPoint(0.9f, 0.3f, 1)))
                )
        );
    }

    private StrokeGuide threeStrokeGuide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.1f, 0), new InkPoint(0.9f, 0.1f, 1))),
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.3f, 0), new InkPoint(0.9f, 0.3f, 1))),
                        new InkStroke(Arrays.asList(new InkPoint(0.1f, 0.5f, 0), new InkPoint(0.9f, 0.5f, 1)))
                )
        );
    }

    private StrokeGuide singleStrokeGuide(float x1, float y1, float x2, float y2) {
        return new StrokeGuide(
                "拉",
                Collections.singletonList(new InkStroke(Arrays.asList(new InkPoint(x1, y1, 0), new InkPoint(x2, y2, 1))))
        );
    }

    private WritingSample sample(InkStroke... strokes) {
        return new WritingSample(Arrays.asList(strokes), 100f, 100f);
    }

    private InkStroke stroke(float x1, float y1, float x2, float y2) {
        return new InkStroke(Arrays.asList(new InkPoint(x1, y1, 0), new InkPoint(x2, y2, 1)));
    }
}
