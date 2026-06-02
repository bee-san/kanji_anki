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
        assertEquals("Again: repeat this card sooner", StudyReviewButtonCopy.againContentDescription())
        assertEquals("Good: schedule the next review", StudyReviewButtonCopy.goodContentDescription())
    }
}
