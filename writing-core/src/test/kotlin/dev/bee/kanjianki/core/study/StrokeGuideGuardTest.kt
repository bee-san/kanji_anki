package dev.bee.kanjianki.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGuideGuardTest {
    @Test
    fun acceptsPointsNearExpectedGuidedStroke() {
        val decision = StrokeGuideGuard.evaluatePoint(
            twoStrokeGuide(),
            0,
            1000f,
            1000f,
            210f,
            500f,
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun rejectsPointsClearlyFarFromExpectedStroke() {
        val decision = StrokeGuideGuard.evaluatePoint(
            twoStrokeGuide(),
            0,
            1000f,
            1000f,
            900f,
            500f,
        )

        assertFalse(decision.allowed)
        assertEquals(1, decision.strokeNumber)
        assertEquals("Stay close to stroke 1.", decision.message)
    }

    @Test
    fun usesCommittedStrokeCountToChooseNextGuideStroke() {
        val nearSecond = StrokeGuideGuard.evaluatePoint(
            twoStrokeGuide(),
            1,
            1000f,
            1000f,
            720f,
            500f,
        )
        val nearFirstAfterFirstCommitted = StrokeGuideGuard.evaluatePoint(
            twoStrokeGuide(),
            1,
            1000f,
            1000f,
            200f,
            500f,
        )

        assertTrue(nearSecond.allowed)
        assertFalse(nearFirstAfterFirstCommitted.allowed)
        assertEquals(2, nearFirstAfterFirstCommitted.strokeNumber)
    }

    @Test
    fun rejectsExtraStrokeAfterGuideIsComplete() {
        val decision = StrokeGuideGuard.evaluatePoint(
            twoStrokeGuide(),
            2,
            1000f,
            1000f,
            720f,
            500f,
        )

        assertFalse(decision.allowed)
        assertEquals("All guided strokes are already drawn.", decision.message)
    }

    @Test
    fun missingGuideInvalidBoundsAndEmptyStrokeStayFreeform() {
        assertTrue(StrokeGuideGuard.evaluatePoint(null, 0, 1000f, 1000f, 900f, 500f).allowed)
        assertTrue(StrokeGuideGuard.evaluatePoint(StrokeGuide("空", emptyList()), 0, 1000f, 1000f, 900f, 500f).allowed)
        assertTrue(StrokeGuideGuard.evaluatePoint(twoStrokeGuide(), 0, 0f, 1000f, 900f, 500f).allowed)
        assertTrue(
            StrokeGuideGuard.evaluatePoint(
                StrokeGuide("空", listOf(InkStroke(emptyList()))),
                0,
                1000f,
                1000f,
                900f,
                500f,
            ).allowed,
        )
    }

    @Test
    fun singlePointGuideRejectsFarStart() {
        val guide = StrokeGuide(
            "點",
            listOf(InkStroke(listOf(InkPoint(0.5f, 0.5f, 0L)))),
        )

        assertTrue(StrokeGuideGuard.evaluatePoint(guide, 0, 1000f, 1000f, 520f, 510f).allowed)
        assertFalse(StrokeGuideGuard.evaluatePoint(guide, 0, 1000f, 1000f, 950f, 950f).allowed)
    }

    @Test
    fun javaNullGuideContentsStayFreeformOrIgnored() {
        val nullStrokeGuide = guideWithNullStroke()
        val nullPointGuide = guideWithNullPoint()

        assertTrue(StrokeGuideGuard.evaluatePoint(nullStrokeGuide, 0, 1000f, 1000f, 900f, 500f).allowed)
        assertTrue(StrokeGuideGuard.evaluatePoint(nullPointGuide, 0, 1000f, 1000f, 510f, 500f).allowed)
        assertFalse(StrokeGuideGuard.evaluatePoint(nullPointGuide, 0, 1000f, 1000f, 950f, 950f).allowed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun guideWithNullStroke(): StrokeGuide {
        return StrokeGuide("空", listOf<InkStroke?>(null) as List<InkStroke>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun guideWithNullPoint(): StrokeGuide {
        return StrokeGuide(
            "空",
            listOf(
                InkStroke(listOf<InkPoint?>(null, InkPoint(0.5f, 0.5f, 0L)) as List<InkPoint>),
            ),
        )
    }

    private fun twoStrokeGuide(): StrokeGuide {
        return StrokeGuide(
            "川",
            listOf(
                InkStroke(
                    listOf(
                        InkPoint(0.20f, 0.20f, 0L),
                        InkPoint(0.20f, 0.80f, 10L),
                    ),
                ),
                InkStroke(
                    listOf(
                        InkPoint(0.72f, 0.22f, 0L),
                        InkPoint(0.72f, 0.82f, 10L),
                    ),
                ),
            ),
        )
    }
}
