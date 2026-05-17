package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote

internal fun SourceNoteEntity.toDomain(): SourceNote = SourceNote(
    noteId = NoteId(noteId),
    modelName = modelName,
    expression = expression,
    reading = reading,
    meaning = meaning,
    sentence = sentence,
    fieldsJson = fieldsJson,
    tags = tags,
    lastSeenSyncId = SyncRunId(lastSeenSyncId),
)

internal fun SourceNote.toEntity(): SourceNoteEntity = SourceNoteEntity(
    noteId = noteId.value,
    modelName = modelName,
    expression = expression,
    reading = reading,
    meaning = meaning,
    sentence = sentence,
    fieldsJson = fieldsJson,
    tags = tags,
    lastSeenSyncId = lastSeenSyncId.value,
)

internal fun SourceCardEntity.toDomain(): SourceCard = SourceCard(
    cardId = CardId(cardId),
    noteId = NoteId(noteId),
    deckName = deckName,
    ord = ord,
    queue = queue,
    type = type,
    due = due,
    intervalDays = intervalDays,
    reps = reps,
    lapses = lapses,
    fsrsStability = fsrsStability,
    fsrsDifficulty = fsrsDifficulty,
    fsrsRetrievability = fsrsRetrievability,
    lastSeenSyncId = SyncRunId(lastSeenSyncId),
)

internal fun SourceCard.toEntity(): SourceCardEntity = SourceCardEntity(
    cardId = cardId.value,
    noteId = noteId.value,
    deckName = deckName,
    ord = ord,
    queue = queue,
    type = type,
    due = due,
    intervalDays = intervalDays,
    reps = reps,
    lapses = lapses,
    fsrsStability = fsrsStability,
    fsrsDifficulty = fsrsDifficulty,
    fsrsRetrievability = fsrsRetrievability,
    lastSeenSyncId = lastSeenSyncId.value,
)
