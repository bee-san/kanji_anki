package dev.bee.kanjianki.core.study;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HintStateTest {
    @Test
    public void constructorDefaultsLevelAndClampsCounters() {
        HintState state = new HintState(null, -3, -7);

        assertEquals(HintLevel.TRACE, state.level());
        assertEquals(0, state.revealedStrokeCount());
        assertEquals(0, state.consecutivePasses());
    }

    @Test
    public void copyHelpersKeepOnlyRelevantState() {
        HintState state = new HintState(HintLevel.MINIMAL, 2, 3);

        HintState levelChanged = state.withLevel(HintLevel.OUTLINE);
        HintState revealed = state.withRevealCount(4);
        HintState passed = state.withConsecutivePasses(5);

        assertEquals(HintLevel.OUTLINE, levelChanged.level());
        assertEquals(0, levelChanged.revealedStrokeCount());
        assertEquals(0, levelChanged.consecutivePasses());
        assertEquals(HintLevel.MINIMAL, revealed.level());
        assertEquals(4, revealed.revealedStrokeCount());
        assertEquals(3, revealed.consecutivePasses());
        assertEquals(HintLevel.MINIMAL, passed.level());
        assertEquals(2, passed.revealedStrokeCount());
        assertEquals(5, passed.consecutivePasses());
    }
}
