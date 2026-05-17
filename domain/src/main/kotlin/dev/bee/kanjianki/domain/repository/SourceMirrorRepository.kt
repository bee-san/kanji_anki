package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import kotlinx.coroutines.flow.Flow

interface SourceMirrorRepository {
    fun observeNote(noteId: NoteId): Flow<SourceNote?>

    suspend fun getNote(noteId: NoteId): SourceNote?

    suspend fun getCard(cardId: CardId): SourceCard?

    suspend fun listNotesForSync(syncRunId: SyncRunId): List<SourceNote>

    suspend fun listCardsForNote(noteId: NoteId): List<SourceCard>

    suspend fun listCardsForSync(syncRunId: SyncRunId): List<SourceCard>

    suspend fun upsertSnapshot(
        notes: List<SourceNote>,
        cards: List<SourceCard>,
    )
}
