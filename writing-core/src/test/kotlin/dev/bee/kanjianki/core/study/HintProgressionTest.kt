package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HintProgressionTest {
    @Test
    fun visibilityFollowsGuideLadder() {
        val progression = HintProgression()
        val guide = guide()

        val trace = progression.visibility(HintState.fromWritingLevel(0), guide)
        val outline = progression.visibility(HintState.fromWritingLevel(1), guide)
        val minimal = progression.visibility(HintState.fromWritingLevel(2), guide)
        val blind = progression.visibility(HintState.fromWritingLevel(3), guide)

        assertTrue(trace.tracePathsVisible())
        assertEquals(3, trace.visibleStrokeCount())
        assertFalse(outline.tracePathsVisible())
        assertTrue(outline.outlineVisible())
        assertTrue(minimal.startDotsVisible())
        assertFalse(minimal.strokeNumbersVisible())
        assertFalse(blind.strokeCountVisible())
    }

    @Test
    fun nullStateAndGuideUseTraceFallbackWithoutVisibleStrokes() {
        val visibility = HintProgression().visibility(null, null)

        assertEquals(HintLevel.TRACE, visibility.level())
        assertEquals(0, visibility.visibleStrokeCount())
        assertTrue(visibility.tracePathsVisible())
    }

    @Test
    fun cleanPassAdvancesAndHintsHoldCurrentLevel() {
        val progression = HintProgression()
        val state = HintState.initial()

        val outline = progression.afterReview(state, true, 0)
        val held = progression.afterReview(outline, true, 1)
        val minimal = progression.afterReview(held, true, 0)

        assertEquals(HintLevel.OUTLINE, outline.level())
        assertEquals(1, outline.consecutivePasses())
        assertEquals(HintLevel.OUTLINE, held.level())
        assertEquals(0, held.consecutivePasses())
        assertEquals(HintLevel.MINIMAL, minimal.level())
    }

    @Test
    fun failureMovesBackTowardMoreSupport() {
        val progression = HintProgression()

        val afterFailure = progression.afterReview(HintState.fromWritingLevel(2), false, 0)

        assertEquals(HintLevel.OUTLINE, afterFailure.level())
        assertEquals(0, afterFailure.revealedStrokeCount())
    }

    @Test
    fun afterWritingNullOrWrongMovesBackTowardSupport() {
        val progression = HintProgression()
        val wrong = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "wrong",
            null,
            null,
            HintLevel.BLIND,
            2,
        )

        assertEquals(HintLevel.OUTLINE, progression.afterReview(HintState.fromWritingLevel(2), false, 0).level())
        assertEquals(HintLevel.MINIMAL, progression.afterWriting(HintState.fromWritingLevel(3), wrong).level())
    }

    @Test
    fun messyRecognizedWritingHoldsCurrentLevel() {
        val progression = HintProgression()
        val messy = WritingAnalysis(
            WritingAnalysis.Status.CLOSE,
            "hard",
            true,
            "messy",
            null,
            null,
            HintLevel.MINIMAL,
            0,
        )

        val afterMessy = progression.afterWriting(HintState.fromWritingLevel(2), messy)

        assertEquals(HintLevel.MINIMAL, afterMessy.level())
        assertEquals(0, afterMessy.revealedStrokeCount())
    }

    @Test
    fun modelAndRecognitionErrorsHoldCurrentLevel() {
        val progression = HintProgression()

        assertEquals(
            HintLevel.BLIND,
            progression.afterWriting(
                HintState.fromWritingLevel(3),
                WritingAnalysisEngine.modelUnavailable("not ready", HintLevel.BLIND, 0),
            ).level(),
        )
        assertEquals(
            HintLevel.BLIND,
            progression.afterWriting(
                HintState.fromWritingLevel(3),
                WritingAnalysisEngine.recognitionError(HintLevel.BLIND, 0),
            ).level(),
        )
    }

    @Test
    fun revealNextTraceAndOutlineUseBroaderHelpRules() {
        val progression = HintProgression()

        val trace = progression.revealNext(HintState.fromWritingLevel(0), guide())
        val outline = progression.revealNext(HintState.fromWritingLevel(1), guide())
        val noGuide = progression.revealNext(HintState.fromWritingLevel(3), null)

        assertEquals(HintLevel.TRACE, trace.level())
        assertEquals(3, trace.revealedStrokeCount())
        assertEquals(HintLevel.TRACE, outline.level())
        assertEquals(HintLevel.MINIMAL, noGuide.level())
    }

    @Test
    fun revealNextExposesOneStrokeForNonTraceLevels() {
        val progression = HintProgression()
        val guide = guide()
        val blind = HintState.fromWritingLevel(3)

        val revealed = progression.revealNext(blind, guide)
        val visibility = progression.visibility(revealed, guide)

        assertEquals(HintLevel.BLIND, revealed.level())
        assertEquals(1, visibility.visibleStrokeCount())
    }

    @Test
    fun revealNextDropsToBroaderHelpOnlyAfterStrokeHintsAreExhausted() {
        val progression = HintProgression()
        val guide = guide()

        val one = progression.revealNext(HintState.fromWritingLevel(3), guide)
        val two = progression.revealNext(one, guide)
        val three = progression.revealNext(two, guide)
        val broader = progression.revealNext(three, guide)

        assertEquals(HintLevel.BLIND, one.level())
        assertEquals(1, one.revealedStrokeCount())
        assertEquals(HintLevel.BLIND, three.level())
        assertEquals(3, three.revealedStrokeCount())
        assertEquals(HintLevel.MINIMAL, broader.level())
        assertEquals(0, broader.revealedStrokeCount())
    }

    @Test
    fun canRevealMoreHelpFollowsHintLevelAndGuideAvailability() {
        val progression = HintProgression()

        assertFalse(progression.canRevealMoreHelp(null, guide()))
        assertFalse(progression.canRevealMoreHelp(HintState.fromWritingLevel(0), guide()))
        assertTrue(progression.canRevealMoreHelp(HintState.fromWritingLevel(1), guide()))
        assertTrue(progression.canRevealMoreHelp(HintState.fromWritingLevel(2), null))
        assertTrue(progression.canRevealMoreHelp(HintState.fromWritingLevel(3), emptyGuide()))
    }

    @Test
    fun canRevealMoreHelpStopsWhenStrokeHintsAreExhausted() {
        val progression = HintProgression()
        val guide = guide()

        assertTrue(progression.canRevealMoreHelp(HintState(HintLevel.MINIMAL, 2, 0), guide))
        assertTrue(progression.canRevealMoreHelp(HintState(HintLevel.BLIND, 2, 0), guide))
        assertFalse(progression.canRevealMoreHelp(HintState(HintLevel.MINIMAL, 3, 0), guide))
        assertFalse(progression.canRevealMoreHelp(HintState(HintLevel.BLIND, 4, 0), guide))
    }

    @Test
    fun nullStateRevealedStrokeAndPassBranchesKeepStableProgression() {
        val progression = HintProgression()
        val guide = guide()
        val close = WritingAnalysis(
            WritingAnalysis.Status.CLOSE,
            "hard",
            true,
            "messy",
            null,
            null,
            HintLevel.TRACE,
            0,
        )
        val pass = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "clean",
            null,
            null,
            HintLevel.BLIND,
            0,
        )

        assertEquals(3, progression.revealNext(null, guide).revealedStrokeCount())
        assertEquals(HintLevel.TRACE, progression.afterReview(null, false, 0).level())
        assertEquals(
            HintLevel.BLIND,
            progression.afterReview(HintState(HintLevel.BLIND, 1, 2), true, 0).level(),
        )
        assertEquals(HintLevel.TRACE, progression.afterWriting(null, close).level())
        assertEquals(HintLevel.BLIND, progression.afterWriting(HintState.fromWritingLevel(2), pass).level())
    }

    private fun guide(): StrokeGuide {
        return StrokeGuide(
            "拉",
            listOf(stroke(), stroke(), stroke()),
        )
    }

    private fun emptyGuide(): StrokeGuide {
        return StrokeGuide("拉", emptyList())
    }

    private fun stroke(): InkStroke {
        return InkStroke(listOf(InkPoint(0f, 0f, 0), InkPoint(1f, 1f, 1)))
    }
}
