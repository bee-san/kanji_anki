package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.source.SourceCardEntity
import dev.bee.kanjianki.data.source.SourceNoteEntity
import dev.bee.kanjianki.data.sync.SyncRunEntity
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncRun
import dev.bee.kanjianki.domain.model.sync.SyncRunStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryMappersTest {
    @Test
    fun sourceNoteRoundTripsThroughEntity() {
        val note = SourceNote(
            noteId = NoteId(10),
            modelName = "Kiku",
            expression = "日本",
            reading = "にほん",
            meaning = "Japan",
            sentence = "日本へ行く",
            fieldsJson = """{"Expression":"日本"}""",
            tags = "kiku",
            lastSeenSyncId = SyncRunId(5),
        )

        assertEquals(note, note.toEntity().toDomain())
    }

    @Test
    fun sourceCardRoundTripsThroughEntity() {
        val card = SourceCard(
            cardId = CardId(11),
            noteId = NoteId(10),
            deckName = "Mining",
            ord = 0,
            queue = -1,
            type = 2,
            due = 30,
            intervalDays = 21,
            reps = 7,
            lapses = 1,
            fsrsStability = 12.5,
            fsrsDifficulty = 6.5,
            fsrsRetrievability = null,
            lastSeenSyncId = SyncRunId(5),
        )

        assertEquals(card, card.toEntity().toDomain())
    }

    @Test
    fun syncRunMapsStatusWireName() {
        val syncRun = SyncRun(
            id = SyncRunId(3),
            startedAt = 100,
            finishedAt = 200,
            status = SyncRunStatus.SUCCESS,
            activeNotesCount = 1,
            activeCardsCount = 2,
            suspendedCardsArchivedCount = 3,
            suspendedKanjiImportedCount = 4,
            deletedNotesCount = 5,
            deletedCardsCount = 6,
            errorCode = null,
            errorMessage = null,
            removalMessage = "done",
        )

        assertEquals(syncRun, syncRun.toEntity().toDomain())
        assertEquals("success", syncRun.toEntity().status)
    }

    @Test
    fun rawEntitiesMapToDomainIds() {
        assertEquals(NoteId(1), SourceNoteEntity(1, "Kiku", "", "", "", "", "{}", "", 9).toDomain().noteId)
        assertEquals(CardId(2), SourceCardEntity(2, 1, "", 0, 0, 0, 0, 0, 0, 0, null, null, null, 9).toDomain().cardId)
        assertEquals(SyncRunId(4), SyncRunEntity(4, 1, null, "success", 0, 0, 0, 0, 0, 0, null, null, null).toDomain().id)
    }
}
