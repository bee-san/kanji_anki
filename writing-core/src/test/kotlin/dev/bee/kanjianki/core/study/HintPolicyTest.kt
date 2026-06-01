package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HintPolicyTest {
    @Test
    fun missingGuideHasNoHints() {
        assertTrue(HintPolicy.hintsFor(null, null as HintState?, 0, false).isEmpty())
        assertTrue(HintPolicy.hintsFor(StrokeGuide("拉", emptyList()), 0, 0, false).isEmpty())
    }

    @Test
    fun jvmStaticBridgeRemainsCovered() {
        val bridge = HintPolicy::class.java.getMethod(
            "hintsFor",
            StrokeGuide::class.java,
            Integer.TYPE,
            Integer.TYPE,
            java.lang.Boolean.TYPE,
        )

        @Suppress("UNCHECKED_CAST")
        val hints = bridge.invoke(null, StrokeGuide("拉", listOf(stroke(), stroke(), stroke())), 0, 0, false) as List<HintPolicy.StrokeHint>

        assertEquals(3, hints.size)
        assertTrue(hints[0].visible)
    }

    @Test
    fun minimalStageShowsOnlyCurrentStrokeCue() {
        val guide = StrokeGuide(
            "拉",
            listOf(stroke(), stroke(), stroke()),
        )

        val hints = HintPolicy.hintsFor(guide, 2, 1, false)

        assertFalse(hints[0].visible)
        assertTrue(hints[1].visible)
        assertTrue(hints[1].current)
        assertFalse(hints[2].visible)
    }

    @Test
    fun revealModeShowsAllStrokesButOnlyTraceShowsNumbers() {
        val guide = StrokeGuide(
            "拉",
            listOf(stroke(), stroke()),
        )

        val trace = HintPolicy.hintsFor(guide, HintState.fromWritingLevel(0), 0, true)
        val blind = HintPolicy.hintsFor(guide, HintState.fromWritingLevel(3), 0, true)

        assertTrue(trace[0].visible)
        assertTrue(trace[0].numberVisible)
        assertTrue(blind[1].visible)
        assertFalse(blind[1].numberVisible)
        assertEquals(0.95f, blind[0].alpha, 0.001f)
    }

    @Test
    fun blindHintRevealsOnlyOneStrokeCue() {
        val guide = StrokeGuide(
            "拉",
            listOf(stroke(), stroke(), stroke()),
        )

        val hints = HintPolicy.hintsFor(guide, HintState(HintLevel.BLIND, 1, 0), 0, false)
        val pastFirst = HintPolicy.hintsFor(guide, HintState(HintLevel.BLIND, 1, 0), 1, false)

        assertEquals(3, hints.size)
        assertTrue(hints[0].visible)
        assertFalse(hints[0].numberVisible)
        assertFalse(hints[1].visible)
        assertFalse(hints[2].visible)
        assertFalse(pastFirst[0].visible)
        assertTrue(pastFirst[1].visible)
    }

    @Test
    fun traceAndOutlineStatesShowDifferentCurrentEmphasis() {
        val guide = StrokeGuide("拉", listOf(stroke(), stroke()))

        val trace = HintPolicy.hintsFor(guide, HintState(HintLevel.TRACE, 1, 0), 1, false)
        val outline = HintPolicy.hintsFor(guide, HintState(HintLevel.OUTLINE, 1, 0), 1, false)

        assertTrue(trace[0].numberVisible)
        assertEquals(0.62f, trace[0].alpha, 0.001f)
        assertEquals(0.95f, trace[1].alpha, 0.001f)
        assertFalse(outline[0].numberVisible)
        assertEquals(0.20f, outline[0].alpha, 0.001f)
        assertEquals(0.9f, outline[1].alpha, 0.001f)
    }

    @Test
    fun nullStateUsesInitialTraceAndMinimalCanRevealNextStroke() {
        val guide = StrokeGuide("拉", listOf(stroke(), stroke(), stroke()))

        val trace = HintPolicy.hintsFor(guide, null, 5, false)
        val minimal = HintPolicy.hintsFor(guide, HintState(HintLevel.MINIMAL, 1, 1), 0, false)

        assertTrue(trace[2].current)
        assertTrue(trace[0].numberVisible)
        assertTrue(minimal[1].visible)
        assertEquals(0.58f, minimal[1].alpha, 0.001f)
        assertFalse(minimal[2].visible)
    }

    @Test
    fun strokeHintLegacyConstructorKeepsNumbersVisible() {
        val hint = HintPolicy.StrokeHint(1, true, 0.5f, false, stroke())

        assertEquals(1, hint.strokeIndex)
        assertTrue(hint.visible)
        assertTrue(hint.numberVisible)
        assertFalse(hint.current)
    }

    private fun stroke(): InkStroke {
        return InkStroke(listOf(InkPoint(0f, 0f, 0), InkPoint(1f, 1f, 1)))
    }
}
