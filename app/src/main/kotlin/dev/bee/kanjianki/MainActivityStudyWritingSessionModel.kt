package dev.bee.kanjianki

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class WritingSessionCardModel(
    val promptHeader: WritingPromptHeaderModel,
    val answerPanel: StudyAnswerPanelModel,
    val answerPanelState: WritingAnswerPanelState,
    val writingTitle: String,
    val writingTitleColor: Int,
    val status: WritingStatusState,
    val drawingPad: View,
    val padMaxSizePx: Int,
    val resultStatus: WritingResultStatusHandle,
)

class WritingAnswerPanelState(initialVisible: Boolean = false) {
    var visible by mutableStateOf(initialVisible)
        private set

    fun updateVisible(value: Boolean) {
        visible = value
    }
}
