@file:JvmName("MainActivityStudyWritingChromeCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
