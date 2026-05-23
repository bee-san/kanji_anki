package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class StrokeGuideGuardTest {
    @Test
    public void acceptsPointsNearExpectedGuidedStroke() {
        StrokeGuideGuard.Decision decision = StrokeGuideGuard.evaluatePoint(
                twoStrokeGuide(),
                0,
                1000f,
                1000f,
                210f,
                500f
        );

        assertTrue(decision.allowed);
    }

    @Test
    public void rejectsPointsClearlyFarFromExpectedStroke() {
        StrokeGuideGuard.Decision decision = StrokeGuideGuard.evaluatePoint(
                twoStrokeGuide(),
                0,
                1000f,
                1000f,
                900f,
                500f
        );

        assertFalse(decision.allowed);
        assertEquals(1, decision.strokeNumber);
        assertEquals("Stay close to stroke 1.", decision.message);
    }

    @Test
    public void usesCommittedStrokeCountToChooseNextGuideStroke() {
        StrokeGuideGuard.Decision nearSecond = StrokeGuideGuard.evaluatePoint(
                twoStrokeGuide(),
                1,
                1000f,
                1000f,
                720f,
                500f
        );
        StrokeGuideGuard.Decision nearFirstAfterFirstCommitted = StrokeGuideGuard.evaluatePoint(
                twoStrokeGuide(),
                1,
                1000f,
                1000f,
                200f,
                500f
        );

        assertTrue(nearSecond.allowed);
        assertFalse(nearFirstAfterFirstCommitted.allowed);
        assertEquals(2, nearFirstAfterFirstCommitted.strokeNumber);
    }

    @Test
    public void rejectsExtraStrokeAfterGuideIsComplete() {
        StrokeGuideGuard.Decision decision = StrokeGuideGuard.evaluatePoint(
                twoStrokeGuide(),
                2,
                1000f,
                1000f,
                720f,
                500f
        );

        assertFalse(decision.allowed);
        assertEquals("All guided strokes are already drawn.", decision.message);
    }

    @Test
    public void missingGuideInvalidBoundsAndEmptyStrokeStayFreeform() {
        assertTrue(StrokeGuideGuard.evaluatePoint(null, 0, 1000f, 1000f, 900f, 500f).allowed);
        assertTrue(StrokeGuideGuard.evaluatePoint(new StrokeGuide("空", Collections.emptyList()), 0, 1000f, 1000f, 900f, 500f).allowed);
        assertTrue(StrokeGuideGuard.evaluatePoint(twoStrokeGuide(), 0, 0f, 1000f, 900f, 500f).allowed);
        assertTrue(StrokeGuideGuard.evaluatePoint(
                new StrokeGuide("空", Collections.singletonList(new InkStroke(Collections.emptyList()))),
                0,
                1000f,
                1000f,
                900f,
                500f
        ).allowed);
    }

    @Test
    public void singlePointGuideRejectsFarStart() {
        StrokeGuide guide = new StrokeGuide(
                "点",
                Collections.singletonList(new InkStroke(Collections.singletonList(new InkPoint(0.5f, 0.5f, 0L))))
        );

        assertTrue(StrokeGuideGuard.evaluatePoint(guide, 0, 1000f, 1000f, 520f, 510f).allowed);
        assertFalse(StrokeGuideGuard.evaluatePoint(guide, 0, 1000f, 1000f, 950f, 950f).allowed);
    }

    @Test
    public void javaNullGuideContentsStayFreeformOrIgnored() {
        StrokeGuide nullStrokeGuide = new StrokeGuide("空", Collections.singletonList(null));
        StrokeGuide nullPointGuide = new StrokeGuide(
                "空",
                Collections.singletonList(new InkStroke(Arrays.asList(null, new InkPoint(0.5f, 0.5f, 0L))))
        );

        assertTrue(StrokeGuideGuard.evaluatePoint(nullStrokeGuide, 0, 1000f, 1000f, 900f, 500f).allowed);
        assertTrue(StrokeGuideGuard.evaluatePoint(nullPointGuide, 0, 1000f, 1000f, 510f, 500f).allowed);
        assertFalse(StrokeGuideGuard.evaluatePoint(nullPointGuide, 0, 1000f, 1000f, 950f, 950f).allowed);
    }

    private static StrokeGuide twoStrokeGuide() {
        return new StrokeGuide(
                "川",
                Arrays.asList(
                        new InkStroke(Arrays.asList(
                                new InkPoint(0.20f, 0.20f, 0L),
                                new InkPoint(0.20f, 0.80f, 10L)
                        )),
                        new InkStroke(Arrays.asList(
                                new InkPoint(0.72f, 0.22f, 0L),
                                new InkPoint(0.72f, 0.82f, 10L)
                        ))
                )
        );
    }
}
