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
 * Whether the study flashcard should render in its compact layout. This is a
 * function of card *state*, not live IME visibility: an unrevealed typing card
 * auto-focuses its answer field and force-opens the keyboard on entry, so it is
 * laid out compact from the first frame (hero panel shrunk, secondary header
 * chrome dropped) and nothing reshapes when the IME then animates in (KB1).
 * `imeVisible` is retained as a parameter for call-site clarity but no longer
 * gates the result — non-typing cards never compact because `hasTypingAnswer`
 * is false, and a revealed typing card returns to the full answer-panel layout.
 */
internal fun studyCardImeCompact(
    @Suppress("UNUSED_PARAMETER") imeVisible: Boolean,
    hasTypingAnswer: Boolean,
    revealed: Boolean,
): Boolean {
    return hasTypingAnswer && !revealed
}
