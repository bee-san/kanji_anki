package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectRequestsTest {
    private fun decode(request: AnkiConnectEnvelope.Request): Json.Obj =
        AnkiConnectJson.decode(request.json) as Json.Obj

    private fun params(request: AnkiConnectEnvelope.Request): Json.Obj =
        decode(request).entries["params"] as Json.Obj

    @Test
    fun paramlessReadsOmitParamsAndKeyByDefault() {
        val models = AnkiConnectRequests.modelNamesAndIds()
        assertEquals("modelNamesAndIds", models.action)
        assertFalse(decode(models).entries.containsKey("params"))
        assertFalse(decode(models).entries.containsKey("key"))

        assertEquals("deckNamesAndIds", AnkiConnectRequests.deckNamesAndIds().action)
    }

    @Test
    fun modelFieldNamesCarriesTheModelName() {
        val request = AnkiConnectRequests.modelFieldNames("Kiku", apiKey = "k")
        assertEquals(Json.Str("Kiku"), params(request).entries["modelName"])
        assertEquals(Json.Str("k"), decode(request).entries["key"])
    }

    @Test
    fun findNotesAndFindCardsCarryTheQuery() {
        assertEquals(
            Json.Str("deck:Kiku"),
            params(AnkiConnectRequests.findNotes("deck:Kiku")).entries["query"],
        )
        assertEquals(
            Json.Str("is:due"),
            params(AnkiConnectRequests.findCards("is:due")).entries["query"],
        )
    }

    @Test
    fun notesInfoAndCardsInfoCarryIdArrays() {
        val notes = params(AnkiConnectRequests.notesInfo(listOf(1L, 2L))).entries["notes"] as Json.Arr
        assertEquals(listOf(Json.Num(1), Json.Num(2)), notes.items)

        val cards = params(AnkiConnectRequests.cardsInfo(listOf(9L))).entries["cards"] as Json.Arr
        assertEquals(listOf(Json.Num(9)), cards.items)
    }

    @Test
    fun everyReadActionIsOnTheAllowlist() {
        val actions = listOf(
            AnkiConnectRequests.modelNamesAndIds(),
            AnkiConnectRequests.deckNamesAndIds(),
            AnkiConnectRequests.modelFieldNames("M"),
            AnkiConnectRequests.findNotes("q"),
            AnkiConnectRequests.findCards("q"),
            AnkiConnectRequests.notesInfo(listOf(1L)),
            AnkiConnectRequests.cardsInfo(listOf(1L)),
        )
        actions.forEach { assertTrue(AnkiConnectActions.isAllowed(it.action)) }
    }
}
