package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HintProgressionTest {
    @Test
    public void visibilityFollowsGuideLadder() {
        HintProgression progression = new HintProgression();
        StrokeGuide guide = guide();

        HintVisibility trace = progression.visibility(HintState.fromWritingLevel(0), guide);
        HintVisibility outline = progression.visibility(HintState.fromWritingLevel(1), guide);
        HintVisibility minimal = progression.visibility(HintState.fromWritingLevel(2), guide);
        HintVisibility blind = progression.visibility(HintState.fromWritingLevel(3), guide);

        assertTrue(trace.tracePathsVisible());
        assertEquals(3, trace.visibleStrokeCount());
        assertFalse(outline.tracePathsVisible());
        assertTrue(outline.outlineVisible());
        assertTrue(minimal.startDotsVisible());
        assertFalse(minimal.strokeNumbersVisible());
        assertFalse(blind.strokeCountVisible());
    }

    @Test
    public void cleanPassAdvancesAndHintsHoldCurrentLevel() {
        HintProgression progression = new HintProgression();
        HintState state = HintState.initial();

        HintState outline = progression.afterReview(state, true, 0);
        HintState held = progression.afterReview(outline, true, 1);
        HintState minimal = progression.afterReview(held, true, 0);

        assertEquals(HintLevel.OUTLINE, outline.level());
        assertEquals(1, outline.consecutivePasses());
        assertEquals(HintLevel.OUTLINE, held.level());
        assertEquals(0, held.consecutivePasses());
        assertEquals(HintLevel.MINIMAL, minimal.level());
    }

    @Test
    public void failureMovesBackTowardMoreSupport() {
        HintProgression progression = new HintProgression();

        HintState afterFailure = progression.afterReview(HintState.fromWritingLevel(2), false, 0);

        assertEquals(HintLevel.OUTLINE, afterFailure.level());
        assertEquals(0, afterFailure.revealedStrokeCount());
    }

    @Test
    public void messyRecognizedWritingHoldsCurrentLevel() {
        HintProgression progression = new HintProgression();
        WritingAnalysis messy = new WritingAnalysis(
                WritingAnalysis.Status.CLOSE,
                "hard",
                true,
                "messy",
                null,
                null,
                HintLevel.MINIMAL,
                0
        );

        HintState afterMessy = progression.afterWriting(HintState.fromWritingLevel(2), messy);

        assertEquals(HintLevel.MINIMAL, afterMessy.level());
        assertEquals(0, afterMessy.revealedStrokeCount());
    }

    @Test
    public void revealNextExposesOneStrokeForNonTraceLevels() {
        HintProgression progression = new HintProgression();
        StrokeGuide guide = guide();
        HintState blind = HintState.fromWritingLevel(3);

        HintState revealed = progression.revealNext(blind, guide);
        HintVisibility visibility = progression.visibility(revealed, guide);

        assertEquals(HintLevel.BLIND, revealed.level());
        assertEquals(1, visibility.visibleStrokeCount());
    }

    @Test
    public void revealNextDropsToBroaderHelpOnlyAfterStrokeHintsAreExhausted() {
        HintProgression progression = new HintProgression();
        StrokeGuide guide = guide();

        HintState one = progression.revealNext(HintState.fromWritingLevel(3), guide);
        HintState two = progression.revealNext(one, guide);
        HintState three = progression.revealNext(two, guide);
        HintState broader = progression.revealNext(three, guide);

        assertEquals(HintLevel.BLIND, one.level());
        assertEquals(1, one.revealedStrokeCount());
        assertEquals(HintLevel.BLIND, three.level());
        assertEquals(3, three.revealedStrokeCount());
        assertEquals(HintLevel.MINIMAL, broader.level());
        assertEquals(0, broader.revealedStrokeCount());
    }

    private StrokeGuide guide() {
        return new StrokeGuide(
                "拉",
                Arrays.asList(stroke(), stroke(), stroke())
        );
    }

    private InkStroke stroke() {
        return new InkStroke(Arrays.asList(new InkPoint(0f, 0f, 0), new InkPoint(1f, 1f, 1)));
    }
}
