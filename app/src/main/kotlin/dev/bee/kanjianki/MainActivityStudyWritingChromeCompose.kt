@file:JvmName("MainActivityStudyWritingChromeCompose")

package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
internal fun WritingStatusText(text: String, color: Int) {
    Text(
        text = text,
        color = kaniColor(color),
        fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
        lineHeight = KaniUiTokens.StudyActionTextSizeSp.sp,
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
