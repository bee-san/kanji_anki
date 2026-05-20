@file:JvmName("MainActivityStudyWritingChromeCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WritingStatusDisplay(
    val text: String = "",
    val color: Int = MainActivityUiSupport.STUDY_MUTED,
)

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

@Composable
internal fun WritingSectionTitle(title: String, color: Int) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        color = Color(color),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 24.sp,
        style = legacyTextStyle()
    )
}

@Composable
internal fun WritingStatusText(text: String, color: Int) {
    Text(
        text = text,
        color = Color(color),
        fontSize = 16.sp,
        lineHeight = 17.sp,
        style = legacyTextStyle()
    )
}

@Composable
internal fun WritingStatusText(state: WritingStatusState) {
    WritingStatusText(text = state.text, color = state.color)
}

@Composable
internal fun WritingResultStatus(handle: WritingResultStatusHandle) {
    if (handle.visible) {
        WritingStatusText(handle.status)
    }
}

private fun legacyTextStyle(): TextStyle {
    return TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
}
