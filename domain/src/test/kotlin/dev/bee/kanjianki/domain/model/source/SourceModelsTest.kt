package dev.bee.kanjianki.domain.model.source

import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceModelsTest {
    @Test
    fun sourceNoteRequiresModelName() {
        assertThrows(IllegalArgumentException::class.java) {
            SourceNote(
                noteId = NoteId(1),
                modelName = "",
                expression = "日本",
                reading = "にほん",
                meaning = "Japan",
                sentence = "",
                fieldsJson = "{}",
                tags = "",
                lastSeenSyncId = SyncRunId(1),
            )
        }
    }

    @Test
    fun sourceCardRejectsInvalidCountersAndFsrsValues() {
        assertThrows(IllegalArgumentException::class.java) {
            validCard(intervalDays = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validCard(fsrsStability = Double.NaN)
        }
    }

    @Test
    fun sourceCardMaturityRequiresActiveCardAndConfiguredInterval() {
        assertTrue(validCard(intervalDays = 21, suspended = false).mature(21))
        assertFalse(validCard(intervalDays = 21, suspended = true).mature(21))
        assertThrows(IllegalArgumentException::class.java) {
            validCard().mature(0)
        }
    }

    private fun validCard(
        intervalDays: Int = 0,
        suspended: Boolean = true,
        fsrsStability: Double? = null,
    ): SourceCard = SourceCard(
        cardId = CardId(1),
        noteId = NoteId(2),
        deckName = "Kiku",
        ord = 0,
        queue = -1,
        type = 0,
        due = 0,
        intervalDays = intervalDays,
        reps = 0,
        lapses = 0,
        suspended = suspended,
        browserQueryMatched = false,
        fsrsStability = fsrsStability,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(3),
    )
}
