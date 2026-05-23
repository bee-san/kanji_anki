package dev.bee.kanjianki.core.study;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WritingHintPolicyTest {
    @Test
    public void targetedWritingStartsNoHigherThanOutline() {
        assertEquals(HintLevel.OUTLINE, WritingHintPolicy.initialHintState(3, 12, 4, true).level());
        assertEquals(HintLevel.TRACE, WritingHintPolicy.initialHintState(0, 12, 4, true).level());
    }

    @Test
    public void firstReviewStartsNoHigherThanOutline() {
        assertEquals(HintLevel.OUTLINE, WritingHintPolicy.initialHintState(3, 0, 4, false).level());
    }

    @Test
    public void firstLearningStepStartsNoHigherThanOutline() {
        assertEquals(HintLevel.OUTLINE, WritingHintPolicy.initialHintState(3, 7, 0, false).level());
    }

    @Test
    public void matureWritingUsesStoredLevel() {
        assertEquals(HintLevel.BLIND, WritingHintPolicy.initialHintState(3, 7, 2, false).level());
        assertEquals(HintLevel.MINIMAL, WritingHintPolicy.initialHintState(2, 7, 2, false).level());
    }

    @Test
    public void storedWritingLevelIsClampedToKnownRange() {
        assertEquals(HintLevel.TRACE, WritingHintPolicy.initialHintState(-1, 7, 2, false).level());
        assertEquals(HintLevel.BLIND, WritingHintPolicy.initialHintState(99, 7, 2, false).level());
    }
}
