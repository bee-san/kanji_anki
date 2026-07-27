package dev.bee.kanjianki

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.StudyTextCopy

data class StudyUndoEntry(
    val snapshot: AppliedReviewSnapshot,
    val label: String,
    val createdAtMillis: Long,
)

class StudyUndoState(
    private val onChanged: () -> Unit = {},
) {
    var pending: StudyUndoEntry? = null
        private set

    fun capture(snapshot: AppliedReviewSnapshot, label: String, createdAtMillis: Long) {
        pending = StudyUndoEntry(snapshot, label, createdAtMillis)
        onChanged()
    }

    fun undoMessageOrNull(): String? {
        return pending?.let { StudyTextCopy.reviewUndoMessage(it.label) }
    }

    fun clear() {
        if (pending == null) return
        pending = null
        onChanged()
    }
}
