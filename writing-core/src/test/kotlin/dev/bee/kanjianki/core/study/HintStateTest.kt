package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class HintStateTest {
    @Test
    fun hintLevelNavigationClampsAtEnds() {
        assertEquals(0, HintLevel.TRACE.writingLevel())
        assertEquals(HintLevel.OUTLINE, HintLevel.TRACE.next())
        assertEquals(HintLevel.BLIND, HintLevel.BLIND.next())
        assertEquals(HintLevel.MINIMAL, HintLevel.BLIND.previous())
        assertEquals(HintLevel.TRACE, HintLevel.TRACE.previous())
        assertEquals(HintLevel.TRACE, HintLevel.fromWritingLevel(-1))
        assertEquals(HintLevel.BLIND, HintLevel.fromWritingLevel(99))
    }

    @Test
    fun constructorDefaultsLevelAndClampsCounters() {
        val state = HintState(null, -3, -7)

        assertEquals(HintLevel.TRACE, state.level())
        assertEquals(0, state.revealedStrokeCount())
        assertEquals(0, state.consecutivePasses())
    }

    @Test
    fun copyHelpersKeepOnlyRelevantState() {
        val state = HintState(HintLevel.MINIMAL, 2, 3)

        val levelChanged = state.withLevel(HintLevel.OUTLINE)
        val revealed = state.withRevealCount(4)
        val passed = state.withConsecutivePasses(5)

        assertEquals(HintLevel.OUTLINE, levelChanged.level())
        assertEquals(0, levelChanged.revealedStrokeCount())
        assertEquals(0, levelChanged.consecutivePasses())
        assertEquals(HintLevel.MINIMAL, revealed.level())
        assertEquals(4, revealed.revealedStrokeCount())
        assertEquals(3, revealed.consecutivePasses())
        assertEquals(HintLevel.MINIMAL, passed.level())
        assertEquals(2, passed.revealedStrokeCount())
        assertEquals(5, passed.consecutivePasses())
    }
}
