package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

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

    @Test
    fun reviewButtonsTranslateToJapaneseLocale() {
        withJapaneseLocale {
            assertEquals("もう一度", StudyReviewButtonCopy.againLabel())
            assertEquals("できた", StudyReviewButtonCopy.goodLabel())
            assertEquals("もう一度: このカードを早めに再表示する", StudyReviewButtonCopy.againContentDescription())
            assertEquals("できた: 次回の復習を予定どおりに保つ", StudyReviewButtonCopy.goodContentDescription())
        }
    }

    private fun withJapaneseLocale(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
