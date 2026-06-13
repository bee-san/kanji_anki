package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.bee.kanjianki.core.StudyTextCopy

internal data class StudyUndoEntry(
    val snapshot: StudyReviewActions.AppliedReviewSnapshot,
    val label: String,
    val createdAtMillis: Long,
)

internal class StudyUndoState {
    var pending by mutableStateOf<StudyUndoEntry?>(null)
        private set

    fun capture(snapshot: StudyReviewActions.AppliedReviewSnapshot, label: String, createdAtMillis: Long) {
        pending = StudyUndoEntry(snapshot, label, createdAtMillis)
    }

    fun undoMessageOrNull(): String? {
        return pending?.let { StudyTextCopy.reviewUndoMessage(it.label) }
    }

    fun clear() {
        pending = null
    }
}
