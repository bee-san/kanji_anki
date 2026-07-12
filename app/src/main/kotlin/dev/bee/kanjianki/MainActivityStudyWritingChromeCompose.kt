@file:JvmName("MainActivityStudyWritingChromeCompose")

package dev.bee.kanjianki

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
internal fun WritingStatusText(
    text: String,
    color: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = kaniColor(color),
        fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
        lineHeight = KaniUiTokens.StudyActionTextSizeSp.sp,
        style = legacyTextStyle()
    )
}

@Composable
internal fun WritingStatusText(
    state: WritingStatusState,
    modifier: Modifier = Modifier,
) {
    WritingStatusText(
        text = state.text,
        color = state.color,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
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
