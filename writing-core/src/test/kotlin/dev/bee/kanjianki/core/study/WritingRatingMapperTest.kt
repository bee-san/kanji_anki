package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WritingRatingMapperTest {
    @Test
    fun nullWritingInputsFallBackToAgain() {
        val mapper = WritingRatingMapper()

        assertEquals(StudyRating.AGAIN, mapper.applyRequestedRating(null, true, null, false))
        assertEquals(StudyRating.AGAIN, mapper.suggestedRating(null))
        assertEquals(StudyRating.AGAIN, mapper.maxAllowedRating(null))
        assertEquals(StudyRating.AGAIN, StudyRating.fromCode(null))
    }

    @Test
    fun failedRequiredWritingCapsRequestedRatingToAgain() {
        val mapper = WritingRatingMapper()
        val failed = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "wrong",
            emptyList(),
            null,
        )

        val applied = mapper.applyRequestedRating(StudyRating.EASY, true, failed, false)

        assertFalse(failed.passed())
        assertEquals(StudyRating.AGAIN, applied)
    }

    @Test
    fun manualOverrideKeepsRequestedRatingWhenAnalysisFails() {
        val mapper = WritingRatingMapper()
        val failed = WritingAnalysis(WritingAnalysis.Status.WRONG, "again", false, "wrong", emptyList(), null)

        val applied = mapper.applyRequestedRating(StudyRating.GOOD, true, failed, true)

        assertEquals(StudyRating.GOOD, applied)
    }

    @Test
    fun passedLowConfidenceWritingStaysHardAndMediumConfidenceIsGood() {
        val mapper = WritingRatingMapper()
        val lowConfidence = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "hard",
            true,
            "weak",
            listOf(RecognitionCandidate("拉", 0.45f)),
            cleanStrokeOrder(),
            HintLevel.BLIND,
            0,
        )
        val mediumConfidence = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "ok",
            listOf(RecognitionCandidate("拉", 0.50f)),
            cleanStrokeOrder(),
            HintLevel.BLIND,
            0,
        )

        assertEquals(StudyRating.HARD, mapper.suggestedRating(lowConfidence))
        assertEquals(StudyRating.HARD, mapper.maxAllowedRating(lowConfidence))
        assertEquals(StudyRating.GOOD, mapper.suggestedRating(mediumConfidence))
    }

    @Test
    fun blindHighConfidenceWritingCanBeEasy() {
        val mapper = WritingRatingMapper()
        val blind = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "easy",
            true,
            "clean",
            listOf(RecognitionCandidate("拉", 0.98f)),
            cleanStrokeOrder(),
        )

        assertEquals(StudyRating.EASY, mapper.maxAllowedRating(blind))
        assertEquals(StudyRating.EASY, mapper.suggestedRating(blind))
    }

    @Test
    fun traceOrHintAssistedWritingCapsAtHard() {
        val mapper = WritingRatingMapper()
        val trace = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "easy",
            true,
            "clean",
            listOf(RecognitionCandidate("拉", 0.99f)),
            cleanStrokeOrder(),
            HintLevel.TRACE,
            0,
        )
        val hintedBlind = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "easy",
            true,
            "clean",
            listOf(RecognitionCandidate("拉", 0.99f)),
            cleanStrokeOrder(),
            HintLevel.BLIND,
            1,
        )

        assertEquals(StudyRating.HARD, mapper.applyRequestedRating(StudyRating.EASY, true, trace, false))
        assertEquals(StudyRating.HARD, mapper.applyRequestedRating(StudyRating.EASY, true, hintedBlind, false))
    }

    @Test
    fun outlineWritingCapsEasyAtGood() {
        val mapper = WritingRatingMapper()
        val outline = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "easy",
            true,
            "clean",
            listOf(RecognitionCandidate("拉", 0.99f)),
            cleanStrokeOrder(),
            HintLevel.OUTLINE,
            0,
        )

        assertEquals(StudyRating.GOOD, mapper.applyRequestedRating(StudyRating.EASY, true, outline, false))
    }

    @Test
    fun messyRecognizedWritingCapsAtHard() {
        val mapper = WritingRatingMapper()
        val messy = WritingAnalysis(
            WritingAnalysis.Status.CLOSE,
            "hard",
            true,
            "messy",
            listOf(RecognitionCandidate("拉", 0.99f)),
            cleanStrokeOrder(),
            HintLevel.BLIND,
            0,
        )

        assertEquals(StudyRating.HARD, mapper.suggestedRating(messy))
        assertEquals(StudyRating.HARD, mapper.applyRequestedRating(StudyRating.EASY, true, messy, false))
    }

    @Test
    fun recognitionOnlySessionLeavesRatingUnchanged() {
        val mapper = WritingRatingMapper()

        val applied = mapper.applyRequestedRating(StudyRating.EASY, false, null, false)

        assertEquals(StudyRating.EASY, applied)
        assertEquals("easy", StudyRating.EASY.code())
        assertEquals(StudyRating.AGAIN, StudyRating.fromCode("unexpected"))
        assertEquals(StudyRating.GOOD, StudyRating.fromCode("good"))
        assertEquals(StudyRating.GOOD, StudyRating.EASY.cappedAt(StudyRating.GOOD))
        assertEquals(StudyRating.HARD, StudyRating.HARD.cappedAt(StudyRating.GOOD))
    }

    @Test
    fun hintVisibilityNormalizesNullLevelAndNegativeStrokeCount() {
        val visibility = HintVisibility(null, false, false, false, false, false, -4)

        assertEquals(HintLevel.TRACE, visibility.level())
        assertEquals(0, visibility.visibleStrokeCount())
    }

    @Test
    fun writingAnalysisDefaultsConfidenceAndHintOptions() {
        val passedWithoutScore = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            null,
            listOf(RecognitionCandidate("拉", null)),
            null,
            null,
            -3,
        )
        val close = WritingAnalysis(
            WritingAnalysis.Status.CLOSE,
            "hard",
            true,
            "close",
            emptyList(),
            null,
        )
        val failedWithoutScore = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "wrong",
            listOf(RecognitionCandidate("拉", null)),
            null,
            HintLevel.BLIND,
            "ignored",
        )
        val oneHintOption = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            emptyList(),
            null,
            HintLevel.TRACE,
        )
        val manyHintOptions = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            emptyList(),
            null,
            HintLevel.OUTLINE,
            3,
            "ignored",
        )
        val nullHintOptions = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            emptyList(),
            null,
            hintOptions = null,
        )
        val emptyHintOptions = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            emptyList(),
            null,
            hintOptions = emptyArray(),
        )

        assertEquals("", passedWithoutScore.message)
        assertEquals(HintLevel.BLIND, passedWithoutScore.hintLevel())
        assertEquals(0, passedWithoutScore.hintsUsed())
        assertEquals(0.744, passedWithoutScore.confidenceScore(), 0.001)
        assertEquals(0.0, failedWithoutScore.confidenceScore(), 0.001)
        assertEquals(0, failedWithoutScore.hintsUsed())
        assertEquals(HintLevel.TRACE, oneHintOption.hintLevel())
        assertEquals(0, oneHintOption.hintsUsed())
        assertEquals(HintLevel.OUTLINE, manyHintOptions.hintLevel())
        assertEquals(3, manyHintOptions.hintsUsed())
        assertEquals(HintLevel.BLIND, nullHintOptions.hintLevel())
        assertEquals(0, nullHintOptions.hintsUsed())
        assertEquals(HintLevel.BLIND, emptyHintOptions.hintLevel())
        assertEquals(0, emptyHintOptions.hintsUsed())
        assertFalse(close.failed())
    }

    @Test
    fun nonBlindOrHintedHighConfidenceWritingDoesNotEarnEasy() {
        val mapper = WritingRatingMapper()
        val minimal = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            listOf(RecognitionCandidate("拉", 0.99f)),
            cleanStrokeOrder(),
            HintLevel.MINIMAL,
            0,
        )
        val hintedBlind = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            listOf(RecognitionCandidate("拉", 0.99f)),
            cleanStrokeOrder(),
            HintLevel.BLIND,
            1,
        )

        assertEquals(StudyRating.GOOD, mapper.suggestedRating(minimal))
        assertEquals(StudyRating.GOOD, mapper.suggestedRating(hintedBlind))
        assertEquals(
            StudyRating.AGAIN,
            mapper.suggestedRating(
                WritingAnalysis(
                    WritingAnalysis.Status.WRONG,
                    "again",
                    false,
                    "wrong",
                    emptyList(),
                    null,
                )
            )
        )
        assertEquals(
            StudyRating.AGAIN,
            mapper.maxAllowedRating(
                WritingAnalysis(
                    WritingAnalysis.Status.WRONG,
                    "again",
                    false,
                    "wrong",
                    emptyList(),
                    null,
                )
            )
        )
    }

    private fun cleanStrokeOrder(): StrokeOrderEvaluator.StrokeOrderResult {
        val guide = StrokeGuide(
            "拉",
            listOf(
                InkStroke(listOf(InkPoint(0.1f, 0.1f, 0L), InkPoint(0.9f, 0.1f, 1L)))
            )
        )
        val sample = WritingSample(
            listOf(
                InkStroke(listOf(InkPoint(10f, 10f, 0L), InkPoint(90f, 10f, 1L)))
            ),
            100f,
            100f,
        )
        return StrokeOrderEvaluator.evaluate(guide, sample)
    }
}
