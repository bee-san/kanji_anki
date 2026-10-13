package dev.bee.kanjianki.anki

import dev.bee.kanjianki.core.AnkiKanjiInventoryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiKanjiInventoryReaderTest {
    @Test
    fun streamsArbitraryFieldsIntoAnAggregateOnlyInventory() {
        val progress = mutableListOf<AnkiKanjiInventoryProgress>()
        val reader = readerWith(
            note(1, 100, listOf("<ruby>確認<rt>かくにん</rt></ruby>", "[sound:語.mp3]")),
            note(2, 200, listOf("箱 &amp; \uD840\uDC00", "確認")),
        )

        val inventory = reader.read(progress::add)

        assertEquals(setOf("確", "認", "箱", "\uD840\uDC00"), inventory.literals)
        assertEquals(2, inventory.notesScanned)
        assertEquals(4, inventory.fieldsScanned)
        assertEquals(2, inventory.modelCount)
        assertEquals(0, progress.first().notesScanned)
        assertTrue(progress.all { it.isIndeterminate })
        assertEquals(4, progress.last().uniqueKanjiCount)
    }

    @Test
    fun carriesSkippedRowsIntoWarningWithoutDiscardingCompletedMembership() {
        val reader = AnkiKanjiInventoryReader(
            AnkiKanjiInventoryReader.NoteStream { consumer, progress ->
                consumer.onNote(note(1, 100, listOf("確認")))
                progress.onProgress(
                    AnkiDroidCollectionInventoryGateway.ScanProgress(
                        notesRead = 1,
                        skippedNotes = 1,
                    ),
                )
                AnkiDroidCollectionInventoryGateway.ScanResult(
                    notesRead = 1,
                    skippedNotes = 1,
                    modelCount = 1,
                    queryMode = AnkiDroidCollectionInventoryGateway.QueryMode.DIRECT_SQL_PAGED,
                )
            },
        )

        val inventory = reader.read()

        assertEquals(setOf("確", "認"), inventory.literals)
        assertNotNull(inventory.malformedRowWarning)
    }

    private fun readerWith(
        vararg notes: AnkiDroidCollectionInventoryGateway.CollectionNote,
    ): AnkiKanjiInventoryReader {
        return AnkiKanjiInventoryReader(
            AnkiKanjiInventoryReader.NoteStream { consumer, progress ->
                var read = 0
                for (note in notes) {
                    consumer.onNote(note)
                    read += 1
                    progress.onProgress(
                        AnkiDroidCollectionInventoryGateway.ScanProgress(read, 0),
                    )
                }
                AnkiDroidCollectionInventoryGateway.ScanResult(
                    notesRead = read,
                    skippedNotes = 0,
                    modelCount = notes.map { it.modelId }.distinct().size,
                    queryMode = AnkiDroidCollectionInventoryGateway.QueryMode.DIRECT_SQL_PAGED,
                )
            },
        )
    }

    private fun note(
        id: Long,
        modelId: Long,
        fields: List<String>,
    ): AnkiDroidCollectionInventoryGateway.CollectionNote {
        return AnkiDroidCollectionInventoryGateway.CollectionNote(
            noteId = id,
            modelId = modelId,
            modelName = "not retained",
            fieldNames = fields.indices.map { "Field $it" },
            fields = fields,
        )
    }
}
