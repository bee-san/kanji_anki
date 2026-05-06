package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HintPolicyTest {
    @Test
    public void minimalStageShowsOnlyCurrentStrokeCue() {
        StrokeGuide guide = new StrokeGuide(
                "拉",
                Arrays.asList(stroke(), stroke(), stroke())
        );

        List<HintPolicy.StrokeHint> hints = HintPolicy.hintsFor(guide, 2, 1, false);

        assertFalse(hints.get(0).visible);
        assertTrue(hints.get(1).visible);
        assertTrue(hints.get(1).current);
        assertFalse(hints.get(2).visible);
    }

    @Test
    public void blindHintRevealsOnlyOneStrokeCue() {
        StrokeGuide guide = new StrokeGuide(
                "拉",
                Arrays.asList(stroke(), stroke(), stroke())
        );

        List<HintPolicy.StrokeHint> hints = HintPolicy.hintsFor(guide, new HintState(HintLevel.BLIND, 1, 0), 0, false);

        assertEquals(3, hints.size());
        assertTrue(hints.get(0).visible);
        assertFalse(hints.get(0).numberVisible);
        assertFalse(hints.get(1).visible);
        assertFalse(hints.get(2).visible);
    }

    private InkStroke stroke() {
        return new InkStroke(Arrays.asList(new InkPoint(0f, 0f, 0), new InkPoint(1f, 1f, 1)));
    }
}
