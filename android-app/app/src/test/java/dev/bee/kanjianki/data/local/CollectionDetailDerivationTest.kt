package dev.bee.kanjianki.data.local

import dev.bee.kanjianki.data.ankidroid.AnkiDroidNoteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionDetailDerivationTest {
    @Test
    fun `collection detail derivation is collection-derived instead of fixture-derived`() {
        val detail = CollectionDetailDerivation.derive(
            settings = AndroidDefaults.settings(),
            notes = listOf(
                AnkiDroidNoteSnapshot(
                    noteId = 1L,
                    modelName = "Kiku",
                    expression = "学ぶ",
                    reading = "まなぶ",
                    meaning = "to study; to learn",
                    fields = mapOf(
                        "Expression" to "学ぶ",
                        "Reading" to "まなぶ",
                        "Meaning" to "to study; to learn",
                    ),
                    tags = listOf("jlpt"),
                ),
            ),
        ).getValue("学")

        assertEquals("学", detail.kanji)
        assertEquals("to study", detail.keyword)
        assertEquals(listOf("to study", "to learn"), detail.meanings)
        assertEquals(listOf("まなぶ"), detail.kunReadings)
        assertEquals(listOf("学ぶ"), detail.collectionExamples)
    }
}
