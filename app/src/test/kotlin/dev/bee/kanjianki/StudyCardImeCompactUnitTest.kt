package dev.bee.kanjianki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCardImeCompactUnitTest {
    @Test
    fun unrevealedTypingCardCompactsWhenImeVisible() {
        assertTrue(studyCardImeCompact(imeVisible = true, hasTypingAnswer = true, revealed = false))
    }

    @Test
    fun unrevealedTypingCardCompactsEvenBeforeTheImeOpens() {
        // KB1: the card is laid out compact from the first frame (before the
        // auto-focus opens the keyboard), so nothing reshapes when the IME
        // then animates in. This is the case the old IME-gated rule flipped.
        assertTrue(studyCardImeCompact(imeVisible = false, hasTypingAnswer = true, revealed = false))
    }

    @Test
    fun nonTypingCardsNeverCompact() {
        // Recognition/font/word-reading cards do not open the IME themselves; even a
        // transient keyboard must not shrink their hero.
        assertFalse(studyCardImeCompact(imeVisible = true, hasTypingAnswer = false, revealed = false))
    }

    @Test
    fun revealedCardShowsTheFullAnswerPanelLayout() {
        assertFalse(studyCardImeCompact(imeVisible = true, hasTypingAnswer = true, revealed = true))
    }
}
