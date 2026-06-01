package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingAnalysisEngineTest {
    @Test
    fun exactTargetCandidatePassesWithCleanOrder() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate("拉", 0.99f)),
        )

        assertTrue(analysis.writingPassed)
        assertTrue(analysis.passed())
    }

    @Test
    fun cleanSecondChoiceTargetPassesWithGoodRating() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate("提", 0.99f), RecognitionCandidate("拉", 0.96f)),
        )

        assertTrue(analysis.writingPassed)
        assertEquals(WritingAnalysis.Status.PASS, analysis.status)
        assertEquals("good", analysis.rating)
    }

    @Test
    fun candidateThatOnlyContainsTargetDoesNotPass() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate("拉麺", 0.99f)),
        )

        assertFalse(analysis.writingPassed)
        assertTrue(analysis.failed())
    }

    @Test
    fun missingStrokeGuideStillPassesRecognitionOnlyMatch() {
        val analysis = WritingAnalysisEngine.analyze(
            "鿃",
            sample(),
            StrokeGuide("鿃", emptyList()),
            listOf(RecognitionCandidate("鿃", 0.93f)),
        )

        assertTrue(analysis.writingPassed)
        assertEquals(WritingAnalysis.Status.CLOSE, analysis.status)
        assertEquals("good", analysis.rating)
        assertTrue(analysis.message.contains("Stroke order could not be checked"))
    }

    @Test
    fun missingStrokeGuideSecondCandidateUsesHardRating() {
        val analysis = WritingAnalysisEngine.analyze(
            "鿃",
            sample(),
            StrokeGuide("鿃", emptyList()),
            listOf(RecognitionCandidate("提", 0.93f), RecognitionCandidate("鿃", 0.75f)),
        )

        assertTrue(analysis.writingPassed)
        assertEquals(WritingAnalysis.Status.CLOSE, analysis.status)
        assertEquals("hard", analysis.rating)
        assertTrue(analysis.message.contains("Recognized as the target kanji, but"))
    }

    @Test
    fun missingStrokeGuideDoesNotPassWrongRecognition() {
        val analysis = WritingAnalysisEngine.analyze(
            "鿃",
            sample(),
            StrokeGuide("鿃", emptyList()),
            listOf(RecognitionCandidate("提", 0.93f)),
        )

        assertFalse(analysis.writingPassed)
        assertEquals(WritingAnalysis.Status.NO_STROKE_DATA, analysis.status)
        assertTrue(analysis.message.contains("I could not read that as the target kanji"))
    }

    @Test
    fun analysisCarriesHintContextForRatingCaps() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate("拉", 0.99f)),
            HintLevel.OUTLINE,
            2,
        )

        assertTrue(analysis.writingPassed)
        assertEquals(HintLevel.OUTLINE, analysis.hintLevel())
        assertEquals(2, analysis.hintsUsed())
    }

    @Test
    fun noInkAndRecognizerFailuresCarryHintContext() {
        val noInk = WritingAnalysisEngine.noInk(HintLevel.TRACE, 3)
        val defaultNoInk = WritingAnalysisEngine.noInk()
        val unavailable = WritingAnalysisEngine.modelUnavailable("offline", HintLevel.OUTLINE, 1)
        val defaultUnavailable = WritingAnalysisEngine.modelUnavailable("offline")
        val recognitionError = WritingAnalysisEngine.recognitionError(HintLevel.MINIMAL, 2)
        val defaultRecognitionError = WritingAnalysisEngine.recognitionError()

        assertEquals(WritingAnalysis.Status.NO_INK, noInk.status)
        assertEquals(HintLevel.TRACE, noInk.hintLevel())
        assertEquals(3, noInk.hintsUsed())
        assertEquals(HintLevel.BLIND, defaultNoInk.hintLevel())
        assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, unavailable.status)
        assertEquals("offline", unavailable.message)
        assertEquals(HintLevel.OUTLINE, unavailable.hintLevel())
        assertEquals(HintLevel.BLIND, defaultUnavailable.hintLevel())
        assertEquals(WritingAnalysis.Status.RECOGNITION_ERROR, recognitionError.status)
        assertEquals("The handwriting checker could not read this attempt. Try once more.", recognitionError.message)
        assertEquals(HintLevel.MINIMAL, recognitionError.hintLevel())
        assertEquals(HintLevel.BLIND, defaultRecognitionError.hintLevel())
    }

    @Test
    fun nullOrBlankRecognitionDoesNotMatchTarget() {
        val blank = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate(null, 0.2f)),
        )
        val variationSelector = WritingAnalysisEngine.analyze(
            "拉",
            sample(),
            guide(),
            listOf(RecognitionCandidate(" 拉" + String(Character.toChars(0xFE0F)) + " ", 0.99f)),
        )
        val noCandidates = WritingAnalysisEngine.analyze("拉", sample(), guide(), null as List<RecognitionCandidate>?)
        val emptyCandidates = WritingAnalysisEngine.analyze("拉", sample(), guide(), emptyList())
        val noSample = WritingAnalysisEngine.analyze("拉", null, guide(), emptyList())
        val emptySample = WritingAnalysisEngine.analyze("拉", WritingSample.empty(), guide(), emptyList())
        val nullTarget = WritingAnalysisEngine.analyze(null, sample(), guide(), listOf(RecognitionCandidate("拉", 0.99f)))

        assertFalse(blank.writingPassed)
        assertEquals(WritingAnalysis.Status.WRONG, blank.status)
        assertTrue(variationSelector.writingPassed)
        assertEquals(WritingAnalysis.Status.WRONG, noCandidates.status)
        assertEquals(WritingAnalysis.Status.WRONG, emptyCandidates.status)
        assertEquals(WritingAnalysis.Status.NO_INK, noSample.status)
        assertEquals(WritingAnalysis.Status.NO_INK, emptySample.status)
        assertEquals(WritingAnalysis.Status.WRONG, nullTarget.status)
    }

    @Test
    fun badStrokeOrderFailsEvenWhenRecognitionMatches() {
        val analysis = WritingAnalysisEngine.analyze(
            "拉",
            extraStrokeSample(),
            guide(),
            listOf(RecognitionCandidate("拉", 0.99f)),
        )

        assertFalse(analysis.writingPassed)
        assertEquals(WritingAnalysis.Status.WRONG, analysis.status)
        assertFalse(analysis.message.isEmpty())
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(
                InkStroke(listOf(InkPoint(0.1f, 0.1f, 0), InkPoint(0.9f, 0.1f, 1))),
                InkStroke(listOf(InkPoint(0.1f, 0.3f, 0), InkPoint(0.9f, 0.3f, 1))),
            ),
        )
    }

    private fun sample(): WritingSample {
        return WritingSample(
            listOf(
                InkStroke(listOf(InkPoint(10f, 10f, 0), InkPoint(90f, 10f, 1))),
                InkStroke(listOf(InkPoint(10f, 30f, 0), InkPoint(90f, 30f, 1))),
            ),
            100f,
            100f,
        )
    }

    private fun extraStrokeSample(): WritingSample {
        return WritingSample(
            listOf(
                InkStroke(listOf(InkPoint(10f, 10f, 0), InkPoint(90f, 10f, 1))),
                InkStroke(listOf(InkPoint(10f, 30f, 0), InkPoint(90f, 30f, 1))),
                InkStroke(listOf(InkPoint(10f, 50f, 0), InkPoint(90f, 50f, 1))),
                InkStroke(listOf(InkPoint(10f, 70f, 0), InkPoint(90f, 70f, 1))),
            ),
            100f,
            100f,
        )
    }
}
