package dev.bee.kanjianki

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class FlashcardPromptHeaderModel(
    val modeLabel: String,
    val question: String,
)

data class FlashcardHeroPanelModel(
    val glyph: String,
    val glyphSizeSp: Int,
    val typeface: Typeface?,
)

class FlashcardRevealState(initialRevealed: Boolean = false) {
    var isRevealed by mutableStateOf(initialRevealed)
        private set

    fun reveal() {
        isRevealed = true
    }
}

internal data class FlashcardCardModel(
    val promptHeader: FlashcardPromptHeaderModel,
    val heroPanel: FlashcardHeroPanelModel,
    val typingAnswer: TypingAnswerState?,
    val answerPanel: StudyAnswerPanelModel,
    val revealState: FlashcardRevealState,
    val typingReading: Boolean = false,
)
