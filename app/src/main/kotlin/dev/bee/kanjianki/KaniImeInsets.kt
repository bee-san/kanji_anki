package dev.bee.kanjianki

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * True while the soft keyboard occupies space at the bottom of the window. Reading
 * the IME inset inside composition makes callers recompose as the keyboard animates
 * in and out, so layouts can reclaim its space (hide the bottom nav, compact the
 * study card) the moment it opens.
 */
@Composable
internal fun kaniImeVisible(): Boolean {
    return WindowInsets.ime.getBottom(LocalDensity.current) > 0
}

/**
 * Whether the study flashcard should switch to its compact layout: only while the
 * keyboard is open on an unrevealed typing card. Compact mode shrinks the hero
 * panel and drops secondary header chrome so the kanji prompt and the answer field
 * both fit above the IME instead of the focused field scrolling the kanji off-screen.
 */
internal fun studyCardImeCompact(
    imeVisible: Boolean,
    hasTypingAnswer: Boolean,
    revealed: Boolean,
): Boolean {
    return imeVisible && hasTypingAnswer && !revealed
}
