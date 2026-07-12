package dev.bee.kanjianki

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.StudyTextCopy

internal class RecognitionFailureCauseState {
    var visible by mutableStateOf(false)
        private set
    var interactionSource: String = "review-action"
        private set

    fun show(source: String) {
        interactionSource = source
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

@Composable
internal fun RecognitionFailureCauseDialog(
    state: RecognitionFailureCauseState,
    onCause: (FailureKind, String) -> Unit,
) {
    if (!state.visible) return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(StudyTextCopy.recognitionFailureTitle()) },
        text = { Text(StudyTextCopy.recognitionFailureBody()) },
        confirmButton = {
            TextButton(onClick = {
                val source = state.interactionSource
                state.dismiss()
                onCause(FailureKind.MEANING_UNKNOWN, source)
            }) {
                Text(StudyTextCopy.recognitionFailureMeaningChoice())
            }
        },
        dismissButton = {
            TextButton(onClick = {
                val source = state.interactionSource
                state.dismiss()
                onCause(FailureKind.VISUAL_CONFUSION, source)
            }) {
                Text(StudyTextCopy.recognitionFailureVisualChoice())
            }
        },
    )
}
