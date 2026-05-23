package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

class TypingAnswerState @JvmOverloads constructor(initialText: String = "") {
    private var value by mutableStateOf(initialText)

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
        this.value = value
    }

    internal fun updateBounds(bounds: Rect) {
        boundsInWindow = bounds
    }
}
