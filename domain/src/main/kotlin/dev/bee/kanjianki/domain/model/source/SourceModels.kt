package dev.bee.kanjianki.domain.model.source

import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId

data class SourceNote(
    val noteId: NoteId,
    val modelName: String,
    val expression: String,
    val reading: String,
    val meaning: String,
    val sentence: String,
    val fieldsJson: String,
    val tags: String,
    val lastSeenSyncId: SyncRunId,
) {
    init {
        require(modelName.isNotBlank()) { "modelName must not be blank" }
    }
}

data class SourceCard(
    val cardId: CardId,
    val noteId: NoteId,
    val deckName: String,
    val ord: Int,
    val queue: Int,
    val type: Int,
    val due: Int,
    val intervalDays: Int,
    val reps: Int,
    val lapses: Int,
    val fsrsStability: Double?,
    val fsrsDifficulty: Double?,
    val fsrsRetrievability: Double?,
    val lastSeenSyncId: SyncRunId,
) {
    init {
        require(ord >= 0) { "card ord must be non-negative" }
        require(intervalDays >= 0) { "intervalDays must be non-negative" }
        require(reps >= 0) { "reps must be non-negative" }
        require(lapses >= 0) { "lapses must be non-negative" }
        requireFiniteOrNull(fsrsStability, "fsrsStability")
        requireFiniteOrNull(fsrsDifficulty, "fsrsDifficulty")
        requireFiniteOrNull(fsrsRetrievability, "fsrsRetrievability")
    }
}

private fun requireFiniteOrNull(value: Double?, name: String) {
    require(value == null || value.isFinite()) { "$name must be finite when present" }
}
