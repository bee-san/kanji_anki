package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingPrimerStageTest {
    private val guide = StrokeGuide("拉", listOf(InkStroke(listOf(InkPoint(0.1f, 0.1f, 0L), InkPoint(0.9f, 0.1f, 1L)))))

    @Test
    fun primerRunsOnlyOnAGuidedFirstWritingAppearanceWithStrokeData() {
        assertTrue(WritingPrimerStage.shouldPrime(true, true, HintState.initial(), guide))
        assertTrue(WritingPrimerStage.shouldPrime(true, true, null, guide))
        assertFalse(WritingPrimerStage.shouldPrime(false, true, HintState.initial(), guide))
        assertFalse(WritingPrimerStage.shouldPrime(true, true, HintState(HintLevel.OUTLINE, 0, 0), guide))
        assertFalse(WritingPrimerStage.shouldPrime(true, true, HintState(HintLevel.BLIND, 0, 0), guide))
        assertFalse(WritingPrimerStage.shouldPrime(true, true, HintState.initial(), null))
        assertFalse(WritingPrimerStage.shouldPrime(true, true, HintState.initial(), StrokeGuide("拉", emptyList())))
        assertFalse(WritingPrimerStage.shouldPrime(true, false, HintState.initial(), guide))
    }

    @Test
    fun eachStageExposesOnlyWhatItsStepNeeds() {
        assertEquals(
            listOf(false, false, true),
            WritingPrimerStage.values().map { it.referenceVisible() },
        )
        assertEquals(
            listOf(false, true, true),
            WritingPrimerStage.values().map { it.padInputEnabled() },
        )
        assertEquals(
            listOf(false, false, true),
            WritingPrimerStage.values().map { it.guideRevealed() },
        )
    }
}
