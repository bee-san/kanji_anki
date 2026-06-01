package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Test

class WritingHintPolicyTest {
    @Test
    fun targetedWritingStartsNoHigherThanOutline() {
        assertEquals(HintLevel.OUTLINE, WritingHintPolicy.initialHintState(3, 12, 4, true).level())
        assertEquals(HintLevel.TRACE, WritingHintPolicy.initialHintState(0, 12, 4, true).level())
    }

    @Test
    fun jvmStaticBridgeRemainsCovered() {
        val bridge = WritingHintPolicy::class.java.declaredMethods.single { it.name == "initialHintState" }
        assertEquals(HintLevel.OUTLINE, (bridge.invoke(null, 3, 12, 4, true) as HintState).level())
    }

    @Test
    fun firstReviewStartsNoHigherThanOutline() {
        assertEquals(HintLevel.OUTLINE, WritingHintPolicy.initialHintState(3, 0, 4, false).level())
    }

    @Test
    fun firstLearningStepStartsNoHigherThanOutline() {
        assertEquals(HintLevel.OUTLINE, WritingHintPolicy.initialHintState(3, 7, 0, false).level())
    }

    @Test
    fun matureWritingUsesStoredLevel() {
        assertEquals(HintLevel.BLIND, WritingHintPolicy.initialHintState(3, 7, 2, false).level())
        assertEquals(HintLevel.MINIMAL, WritingHintPolicy.initialHintState(2, 7, 2, false).level())
    }

    @Test
    fun storedWritingLevelIsClampedToKnownRange() {
        assertEquals(HintLevel.TRACE, WritingHintPolicy.initialHintState(-1, 7, 2, false).level())
        assertEquals(HintLevel.BLIND, WritingHintPolicy.initialHintState(99, 7, 2, false).level())
    }
}
