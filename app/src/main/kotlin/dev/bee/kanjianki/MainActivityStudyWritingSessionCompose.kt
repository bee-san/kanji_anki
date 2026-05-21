@file:JvmName("MainActivityStudyWritingSessionCompose")

package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp

internal fun writingSessionCardView(activity: MainActivityStudy, model: WritingSessionCardModel): View {
    return ComposeView(activity).apply {
        setContent {
            MaterialTheme {
                WritingSessionCard(model)
            }
        }
    }
}

@Composable
fun WritingSessionCard(model: WritingSessionCardModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            WritingPromptHeader(model.promptHeader)
            if (model.answerPanelState.visible) {
                StudyAnswerPanel(model.answerPanel, Modifier.padding(top = 12.dp, bottom = 10.dp))
            }
            WritingSectionTitle(title = model.writingTitle, color = model.writingTitleColor)
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
