package dev.bee.kanjianki

import android.content.Context
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private data class WritingStatusDisplay(
    val text: String = "",
    val color: Int = WRITING_STATUS_MUTED_FALLBACK,
)

private const val WRITING_STATUS_MUTED_FALLBACK = 0xFF826084.toInt()

class WritingStatusState @JvmOverloads constructor(@Suppress("UNUSED_PARAMETER") context: Context? = null) {
    private var display by mutableStateOf(WritingStatusDisplay())

    internal val text: String
        get() = display.text

    internal val color: Int
        get() = display.color

    fun setStatus(value: String?, color: Int) {
        display = WritingStatusDisplay(text = value.orEmpty(), color = color)
    }

    fun getText(): CharSequence {
        return display.text
    }

    fun setText(value: CharSequence?) {
        setStatus(value?.toString(), display.color)
    }
}

class WritingResultStatusHandle @JvmOverloads constructor(context: Context? = null) {
    internal val status = WritingStatusState(context)
    internal var visible by mutableStateOf(false)
        private set

    fun show(value: String?, color: Int) {
        status.setStatus(value, color)
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun getText(): CharSequence {
        return status.getText()
    }

    fun getVisibility(): Int {
        return if (visible) View.VISIBLE else View.GONE
    }
}
