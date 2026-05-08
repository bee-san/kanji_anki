package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HintPolicyTest {
    @Test
    public void missingGuideHasNoHints() {
        assertTrue(HintPolicy.hintsFor(null, (HintState) null, 0, false).isEmpty());
        assertTrue(HintPolicy.hintsFor(new StrokeGuide("拉", java.util.Collections.emptyList()), 0, 0, false).isEmpty());
    }

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
    public void revealModeShowsAllStrokesButOnlyTraceShowsNumbers() {
        StrokeGuide guide = new StrokeGuide(
                "拉",
                Arrays.asList(stroke(), stroke())
        );

        List<HintPolicy.StrokeHint> trace = HintPolicy.hintsFor(guide, HintState.fromWritingLevel(0), 0, true);
        List<HintPolicy.StrokeHint> blind = HintPolicy.hintsFor(guide, HintState.fromWritingLevel(3), 0, true);

        assertTrue(trace.get(0).visible);
        assertTrue(trace.get(0).numberVisible);
        assertTrue(blind.get(1).visible);
        assertFalse(blind.get(1).numberVisible);
        assertEquals(0.95f, blind.get(0).alpha, 0.001f);
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

    @Test
    public void strokeHintLegacyConstructorKeepsNumbersVisible() {
        HintPolicy.StrokeHint hint = new HintPolicy.StrokeHint(1, true, 0.5f, false, stroke());

        assertEquals(1, hint.strokeIndex);
        assertTrue(hint.visible);
        assertTrue(hint.numberVisible);
        assertFalse(hint.current);
    }

    private InkStroke stroke() {
        return new InkStroke(Arrays.asList(new InkPoint(0f, 0f, 0), new InkPoint(1f, 1f, 1)));
    }
}
