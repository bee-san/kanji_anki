package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEnvelope.Response
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectReads.CardInfo
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectReads.NoteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnkiConnectReadsTest {
    private fun result(body: String) = (AnkiConnectEnvelope.parse(body) as Response.Ok).result

    @Test
    fun parsesNamesAndIds() {
        val result = result("""{"result":{"Basic":1483883011648,"Kiku":1483883011649},"error":null}""")
        assertEquals(
            mapOf("Basic" to 1483883011648L, "Kiku" to 1483883011649L),
            AnkiConnectReads.namesAndIds(result),
        )
    }

    @Test
    fun rejectsNamesAndIdsWithNonNumericId() {
        val result = result("""{"result":{"Basic":"nope"},"error":null}""")
        assertNull(AnkiConnectReads.namesAndIds(result))
        assertNull(AnkiConnectReads.namesAndIds(result("""{"result":[],"error":null}""")))
    }

    @Test
    fun parsesFieldNames() {
        val result = result("""{"result":["Front","Back"],"error":null}""")
        assertEquals(listOf("Front", "Back"), AnkiConnectReads.fieldNames(result))
        assertNull(AnkiConnectReads.fieldNames(result("""{"result":[1],"error":null}""")))
    }

    @Test
    fun parsesFindIds() {
        val result = result("""{"result":[1494723142483,1494703460437],"error":null}""")
        assertEquals(listOf(1494723142483L, 1494703460437L), AnkiConnectReads.ids(result))
        assertNull(AnkiConnectReads.ids(result("""{"result":["x"],"error":null}""")))
        assertNull(AnkiConnectReads.ids(result("""{"result":{},"error":null}""")))
    }

    @Test
    fun parsesNotesInfo() {
        val body = """
            {"result":[{"noteId":1502298033753,"modelName":"Kiku","tags":["kani_archived"],
            "fields":{"Expression":{"value":"脱出","order":0},"Reading":{"value":"だっしゅつ","order":1}}}],
            "error":null}
        """.trimIndent().replace("\n", "")
        val notes = AnkiConnectReads.notesInfo(result(body))
        assertEquals(
            listOf(
                NoteInfo(
                    noteId = 1502298033753L,
                    modelName = "Kiku",
                    tags = listOf("kani_archived"),
                    fields = linkedMapOf("Expression" to "脱出", "Reading" to "だっしゅつ"),
                ),
            ),
            notes,
        )
    }

    @Test
    fun rejectsMalformedNotesInfo() {
        assertNull(AnkiConnectReads.notesInfo(result("""{"result":{},"error":null}""")))
        // missing modelName
        assertNull(
            AnkiConnectReads.notesInfo(
                result("""{"result":[{"noteId":1,"tags":[],"fields":{}}],"error":null}"""),
            ),
        )
        // field not wrapped in {value, order}
        assertNull(
            AnkiConnectReads.notesInfo(
                result("""{"result":[{"noteId":1,"modelName":"M","tags":[],"fields":{"F":"bare"}}],"error":null}"""),
            ),
        )
    }

    @Test
    fun parsesCardsInfo() {
        val body = """
            {"result":[{"cardId":1498938915662,"note":1502298033753,"deckName":"Kiku",
            "modelName":"Kiku","queue":2,"due":5,"interval":30,"reps":4,"lapses":1}],"error":null}
        """.trimIndent().replace("\n", "")
        val cards = AnkiConnectReads.cardsInfo(result(body))
        assertEquals(
            listOf(
                CardInfo(
                    cardId = 1498938915662L,
                    noteId = 1502298033753L,
                    deckName = "Kiku",
                    modelName = "Kiku",
                    queue = 2,
                    due = 5,
                    interval = 30,
                    reps = 4,
                    lapses = 1,
                ),
            ),
            cards,
        )
    }

    @Test
    fun rejectsMalformedCardsInfo() {
        assertNull(AnkiConnectReads.cardsInfo(result("""{"result":"nope","error":null}""")))
        assertNull(
            AnkiConnectReads.cardsInfo(
                result("""{"result":[{"cardId":1,"note":2}],"error":null}"""),
            ),
        )
    }
}
