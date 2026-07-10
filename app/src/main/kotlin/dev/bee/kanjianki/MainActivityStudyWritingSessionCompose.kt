@file:JvmName("MainActivityStudyWritingSessionCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun WritingSessionCard(
    model: WritingSessionCardModel,
    modifier: Modifier = Modifier,
    onBrowseAction: Runnable? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KaniUiTokens.StudyShapeLarge,
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, KaniTheme.colors.border),
        shadowElevation = KaniUiTokens.StudyElevation,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            WritingPromptHeader(model.promptHeader)
            if (model.answerPanelState.visible) {
                StudyAnswerPanel(
                    model.answerPanel,
                    Modifier.padding(top = 12.dp, bottom = 10.dp),
                    onBrowseAction = onBrowseAction,
                )
            }
            WritingStatusText(model.status)
            WritingPadPanel(
                drawingPad = model.drawingPad,
                maxSizePx = model.padMaxSizePx,
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
            )
            WritingResultStatus(model.resultStatus)
        }
    }
}
