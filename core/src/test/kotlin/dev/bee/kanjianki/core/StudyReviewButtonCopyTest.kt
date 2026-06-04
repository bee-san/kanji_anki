package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyReviewButtonCopyTest {
    @Test
    fun reviewButtonsUseAnkiRatingNames() {
        assertEquals("Again", StudyReviewButtonCopy.againLabel())
        assertEquals("Good", StudyReviewButtonCopy.goodLabel())
    }

    @Test
    fun reviewButtonDescriptionsExplainSchedulingEffect() {
        assertEquals("Again: show this card again sooner", StudyReviewButtonCopy.againContentDescription())
        assertEquals("Good: keep the next review on schedule", StudyReviewButtonCopy.goodContentDescription())
    }
}
