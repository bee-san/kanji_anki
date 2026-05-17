package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.repository.SourceMirrorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSourceMirrorRepository(
    private val database: KaniRoomDatabase,
) : SourceMirrorRepository {
    private val notes = database.sourceNoteDao()
    private val cards = database.sourceCardDao()

    override fun observeNote(noteId: NoteId): Flow<SourceNote?> =
        notes.observe(noteId.value).map { it?.toDomain() }

    override suspend fun getNote(noteId: NoteId): SourceNote? =
        notes.get(noteId.value)?.toDomain()

    override suspend fun getCard(cardId: CardId): SourceCard? =
        cards.get(cardId.value)?.toDomain()

    override suspend fun listNotesForSync(syncRunId: SyncRunId): List<SourceNote> =
        notes.listForSync(syncRunId.value).map { it.toDomain() }

    override suspend fun listCardsForNote(noteId: NoteId): List<SourceCard> =
        cards.listForNote(noteId.value).map { it.toDomain() }

    override suspend fun listCardsForSync(syncRunId: SyncRunId): List<SourceCard> =
        cards.listForSync(syncRunId.value).map { it.toDomain() }

    override suspend fun upsertSnapshot(
        notes: List<SourceNote>,
        cards: List<SourceCard>,
    ) {
        database.withTransaction {
            this@RoomSourceMirrorRepository.notes.upsertAll(notes.map { it.toEntity() })
            this@RoomSourceMirrorRepository.cards.upsertAll(cards.map { it.toEntity() })
        }
    }
}
