package dev.bee.kanjianki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCardImeCompactUnitTest {
    @Test
    fun compactRequiresKeyboardOpenOnAnUnrevealedTypingCard() {
        assertTrue(studyCardImeCompact(imeVisible = true, hasTypingAnswer = true, revealed = false))
    }

    @Test
    fun keyboardClosedKeepsTheFullCardLayout() {
        assertFalse(studyCardImeCompact(imeVisible = false, hasTypingAnswer = true, revealed = false))
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
