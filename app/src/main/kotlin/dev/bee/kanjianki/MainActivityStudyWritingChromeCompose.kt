@file:JvmName("MainActivityStudyWritingChromeCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class WritingStatusState(
    val text: String = "",
    val color: Int = MainActivityUiSupport.STUDY_MUTED,
)

class WritingStatusView(context: Context) : FrameLayout(context) {
    private var state by mutableStateOf(WritingStatusState())

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(
            ComposeView(context).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setContent {
                    MaterialTheme {
                        WritingStatusText(text = state.text, color = state.color)
                    }
                }
            }
        )
    }

    fun setStatus(value: String?, color: Int) {
        state = WritingStatusState(text = value.orEmpty(), color = color)
    }

    fun getText(): CharSequence {
        return state.text
    }

    fun setText(value: CharSequence?) {
        setStatus(value?.toString(), state.color)
    }
}

class WritingResultStatusHandle(context: Context) {
    private val statusView = WritingStatusView(context)

    fun view(): View = statusView

    fun show(value: String?, color: Int) {
        statusView.setStatus(value, color)
        statusView.visibility = View.VISIBLE
    }

    fun hide() {
        statusView.visibility = View.GONE
    }

    fun getText(): CharSequence {
        return statusView.getText()
    }

    fun getVisibility(): Int {
        return statusView.visibility
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

private fun legacyTextStyle(): TextStyle {
    return TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
}
