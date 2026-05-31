package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeOrderEvaluatorTest {
    @Test
    fun matchingStrokeStartsAndEndsPassCleanly() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 30f, 90f, 30f),
            )
        )

        assertTrue(result.acceptable)
        assertTrue(result.clean)
        assertTrue(result.diagnosis.isEmpty())
    }

    @Test
    fun reversedStrokeDirectionIsPenalized() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                stroke(90f, 10f, 10f, 10f),
                stroke(10f, 30f, 90f, 30f),
            )
        )

        assertFalse(result.clean)
        assertTrue(result.score < 1f)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1))
    }

    @Test
    fun scaledAndTranslatedWritingStillChecksOrder() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                stroke(220f, 240f, 460f, 240f),
                stroke(220f, 300f, 460f, 300f),
            )
        )

        assertTrue(result.acceptable)
        assertTrue(result.clean)
        assertTrue(result.diagnosis.isEmpty())
    }

    @Test
    fun missingStrokeReportsMissingStroke() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(stroke(10f, 10f, 90f, 10f))
        )

        assertTrue(result.acceptable)
        assertFalse(result.clean)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, 2))
    }

    @Test
    fun skippedMiddleStrokeReportsThatGuideStrokeMissing() {
        val result = StrokeOrderEvaluator.evaluate(
            threeStrokeGuide(),
            sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 50f, 90f, 50f),
            )
        )

        assertTrue(result.acceptable)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, 2))
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE, 3))
    }

    @Test
    fun swappedStrokesReportWrongOrder() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                stroke(10f, 30f, 90f, 30f),
                stroke(10f, 10f, 90f, 10f),
            )
        )

        assertTrue(result.acceptable)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 1))
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 2))
    }

    @Test
    fun weakButRecognizedAttemptReportsRecognizedButMessy() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(
                stroke(90f, 10f, 10f, 10f),
                stroke(10f, 30f, 90f, 30f),
            ),
            guide(),
            listOf(RecognitionCandidate("拉", 0.99f))
        )

        assertTrue(analysis.writingPassed)
        assertEquals("hard", analysis.rating)
        assertFalse(analysis.strokeOrder!!.clean)
        assertTrue(analysis.strokeOrder.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1))
        assertTrue(analysis.strokeOrder.diagnosis.hasLabel(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY))
    }

    @Test
    fun diagnosisDoesNotAlterPassFailOrRating() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(
                stroke(90f, 10f, 10f, 10f),
                stroke(10f, 30f, 90f, 30f),
            ),
            guide(),
            listOf(RecognitionCandidate("拉", 0.99f))
        )

        assertTrue(analysis.strokeOrder!!.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1))
        assertTrue(analysis.writingPassed)
        assertEquals("hard", analysis.rating)
        assertEquals(StudyRating.HARD, WritingRatingMapper().applyRequestedRating(StudyRating.GOOD, true, analysis, false))
    }

    @Test
    fun missingGuideDoesNotSilentlyPass() {
        val result = StrokeOrderEvaluator.evaluate(null, sample(stroke(0f, 0f, 1f, 1f)))

        assertTrue(result.missingGuide)
        assertFalse(result.clean)
        assertTrue(result.diagnosis.isEmpty())
    }

    @Test
    fun emptyGuideAndEmptySampleReturnExplicitFailures() {
        val missingGuide = StrokeOrderEvaluator.evaluate(StrokeGuide("拉", emptyList()), sample(stroke(0f, 0f, 1f, 1f)))
        val noInk = StrokeOrderEvaluator.evaluate(guide(), WritingSample.empty())
        val nullSample = StrokeOrderEvaluator.evaluate(guide(), null)

        assertTrue(missingGuide.missingGuide)
        assertFalse(missingGuide.acceptable)
        assertFalse(noInk.missingGuide)
        assertFalse(noInk.acceptable)
        assertEquals("No ink was drawn.", noInk.message)
        assertEquals("No ink was drawn.", nullSample.message)
    }

    @Test
    fun emptyStrokeAndNullPointsProduceRoughFailureWithDefaultBounds() {
        val guide = StrokeGuide(
            "拉",
            listOf(InkStroke(emptyList()))
        )
        val sample = sample(stroke(null, InkPoint(100f, 100f, 1L)))

        val result = StrokeOrderEvaluator.evaluate(guide, sample)

        assertFalse(result.clean)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1))
    }

    @Test
    fun emptySampleStrokeBeforeInkProducesRoughFailure() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                InkStroke(emptyList()),
                stroke(10f, 10f, 90f, 10f),
            )
        )

        assertFalse(result.acceptable)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1))
    }

    @Test
    fun nullStrokeEndpointsProduceRoughFailure() {
        val nullGuideEnd = StrokeGuide(
            "拉",
            listOf(stroke(InkPoint(0.1f, 0.1f, 0L), null))
        )
        val normalSample = sample(stroke(10f, 10f, 90f, 10f))
        val nullSampleEnd = sample(stroke(InkPoint(10f, 10f, 0L), null))

        val guideEndMissing = StrokeOrderEvaluator.evaluate(nullGuideEnd, normalSample)
        val sampleEndMissing = StrokeOrderEvaluator.evaluate(
            StrokeGuide("拉", listOf(InkStroke(listOf(InkPoint(0.1f, 0.1f, 0L), InkPoint(0.9f, 0.1f, 1L))))),
            nullSampleEnd
        )

        assertFalse(guideEndMissing.clean)
        assertFalse(sampleEndMissing.clean)
        assertTrue(guideEndMissing.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1))
        assertTrue(sampleEndMissing.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1))
    }

    @Test
    fun shortStrokeDoesNotReportWrongDirectionWhenDirectMatchIsStronger() {
        val result = StrokeOrderEvaluator.evaluate(
            singleStrokeGuide(0.1f, 0.1f, 0.2f, 0.1f),
            sample(stroke(10f, 10f, 20f, 10f))
        )

        assertTrue(result.clean)
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1))
        assertTrue(result.diagnosis.isEmpty())
    }

    @Test
    fun directionlessRecognizableStrokeDoesNotReportRoughShape() {
        val result = StrokeOrderEvaluator.evaluate(
            singleStrokeGuide(0.1f, 0.1f, 0.9f, 0.1f),
            sample(
                InkStroke(
                    listOf(
                        InkPoint(100f, 0f, 0L),
                        InkPoint(0f, 0f, 1L),
                        InkPoint(50f, 0f, 2L),
                    )
                )
            )
        )

        assertFalse(result.clean)
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_DIRECTION, 1))
        assertFalse(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1))
    }

    @Test
    fun roughShapeWithCorrectCountIsNotAcceptable() {
        val result = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                stroke(90f, 90f, 90f, 90f),
                stroke(90f, 90f, 90f, 90f),
                stroke(90f, 90f, 90f, 90f),
                stroke(90f, 90f, 90f, 90f),
            )
        )

        assertFalse(result.acceptable)
        assertFalse(result.clean)
        assertTrue(result.diagnosis.hasLabel(StrokeDiagnosis.Label.ROUGH_SHAPE, 1))
    }

    @Test
    fun withDiagnosisNormalizesNullDiagnosis() {
        val clean = StrokeOrderEvaluator.evaluate(
            guide(),
            sample(
                stroke(10f, 10f, 90f, 10f),
                stroke(10f, 30f, 90f, 30f),
            )
        )

        assertTrue(clean.withDiagnosis(null).diagnosis.isEmpty())
    }

    @Test
    fun strokeOrderEvaluationNormalizesBoundsAndIncompleteStates() {
        val normalized = StrokeOrderEvaluation(
            -1,
            -1,
            -1,
            null,
            listOf("extra"),
            listOf("duplicate"),
            listOf("late"),
            2.0,
        )
        val exact = StrokeOrderEvaluation(
            1,
            1,
            1,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            1.0,
        )
        val wrongOrder = StrokeOrderEvaluation(
            1,
            1,
            1,
            emptyList(),
            emptyList(),
            emptyList(),
            listOf("1"),
            0.9,
        )
        val wrongCount = StrokeOrderEvaluation(
            2,
            1,
            1,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            0.7,
        )
        val missing = StrokeOrderEvaluation(
            1,
            1,
            1,
            listOf("1"),
            emptyList(),
            emptyList(),
            emptyList(),
            0.7,
        )
        val extra = StrokeOrderEvaluation(
            1,
            1,
            1,
            emptyList(),
            listOf("2"),
            emptyList(),
            emptyList(),
            0.7,
        )
        val duplicate = StrokeOrderEvaluation(
            1,
            1,
            1,
            emptyList(),
            emptyList(),
            listOf("1"),
            emptyList(),
            0.7,
        )

        assertEquals(0, normalized.expectedCount())
        assertEquals(1.0, normalized.score(), 0.001)
        assertFalse(normalized.complete())
        assertTrue(exact.complete())
        assertTrue(exact.exactOrder())
        assertFalse(wrongOrder.exactOrder())
        assertFalse(wrongCount.complete())
        assertFalse(missing.complete())
        assertFalse(extra.complete())
        assertFalse(duplicate.complete())
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(
                stroke(0.1f, 0.1f, 0.9f, 0.1f),
                stroke(0.1f, 0.3f, 0.9f, 0.3f),
            )
        )
    }

    private fun threeStrokeGuide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(
                stroke(0.1f, 0.1f, 0.9f, 0.1f),
                stroke(0.1f, 0.3f, 0.9f, 0.3f),
                stroke(0.1f, 0.5f, 0.9f, 0.5f),
            )
        )
    }

    private fun singleStrokeGuide(x1: Float, y1: Float, x2: Float, y2: Float): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(stroke(x1, y1, x2, y2))
        )
    }

    private fun sample(vararg strokes: InkStroke): WritingSample {
        return WritingSample(strokes.toList(), 100f, 100f)
    }

    private fun stroke(x1: Float, y1: Float, x2: Float, y2: Float): InkStroke {
        return stroke(InkPoint(x1, y1, 0L), InkPoint(x2, y2, 1L))
    }

    @Suppress("UNCHECKED_CAST")
    private fun stroke(start: InkPoint?, end: InkPoint?): InkStroke {
        return InkStroke(listOf(start, end) as List<InkPoint>)
    }
}
