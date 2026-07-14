package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

internal const val MAX_STUDY_TYPED_DRAFT_CHARS = 8_192

class TypingAnswerState @JvmOverloads constructor(initialText: String = "") {
    private var value by mutableStateOf(initialText.take(MAX_STUDY_TYPED_DRAFT_CHARS))

    internal var onTextChanged: ((String) -> Unit)? = null

    private var boundsInWindow: Rect? = null

    internal val text: String
        get() = value

    fun getText(): CharSequence {
        return value
    }

    fun containsWindowPoint(x: Float, y: Float): Boolean {
        val bounds = boundsInWindow ?: return false
        return x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
    }

    internal fun updateText(value: String) {
        this.value = value.take(MAX_STUDY_TYPED_DRAFT_CHARS)
        onTextChanged?.invoke(this.value)
    }

    internal fun updateBounds(bounds: Rect) {
        boundsInWindow = bounds
    }

    internal fun clearBounds() {
        boundsInWindow = null
    }
}
