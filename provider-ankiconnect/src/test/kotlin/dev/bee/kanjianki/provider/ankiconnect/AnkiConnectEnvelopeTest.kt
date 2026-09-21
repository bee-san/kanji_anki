package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEnvelope.Response
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectEnvelopeTest {
    @Test
    fun buildsASingleActionEnvelopeWithVersionSix() {
        val request = AnkiConnectEnvelope.request("version")
        assertEquals("version", request.action)
        val json = AnkiConnectJson.decode(request.json) as Json.Obj
        assertEquals(Json.Str("version"), json.entries["action"])
        assertEquals(Json.Num(6), json.entries["version"])
        assertFalse(json.entries.containsKey("key"))
    }

    @Test
    fun attachesTheApiKeyOnlyWhenPresent() {
        val request = AnkiConnectEnvelope.request(
            "findNotes",
            params = AnkiConnectJson.obj("query" to AnkiConnectJson.str("deck:Kiku")),
            apiKey = "s3cret",
        )
        val json = AnkiConnectJson.decode(request.json) as Json.Obj
        assertEquals(Json.Str("s3cret"), json.entries["key"])
        assertTrue(json.entries.containsKey("params"))
    }

    @Test
    fun refusesToBuildAnUnlistedAction() {
        assertThrows(IllegalArgumentException::class.java) {
            AnkiConnectEnvelope.request("deleteNotes")
        }
    }

    @Test
    fun multiRequestRepeatsVersionAndKeyInEveryNestedAction() {
        val request = AnkiConnectEnvelope.multiRequest(
            listOf(
                "version" to null,
                "deckNamesAndIds" to null,
            ),
            apiKey = "abc",
        )
        assertEquals("multi", request.action)
        val json = AnkiConnectJson.decode(request.json) as Json.Obj
        assertEquals(Json.Num(6), json.entries["version"])
        val actions = ((json.entries["params"] as Json.Obj).entries["actions"] as Json.Arr).items
        assertEquals(2, actions.size)
        actions.forEach { nested ->
            val obj = nested as Json.Obj
            assertEquals(Json.Num(6), obj.entries["version"])
            assertEquals(Json.Str("abc"), obj.entries["key"])
        }
    }

    @Test
    fun multiRequestRejectsAnUnlistedNestedAction() {
        assertThrows(IllegalArgumentException::class.java) {
            AnkiConnectEnvelope.multiRequest(listOf("version" to null, "sync" to null))
        }
    }

    @Test
    fun parsesASuccessEnvelope() {
        val response = AnkiConnectEnvelope.parse("""{"result":6,"error":null}""")
        assertEquals(Response.Ok(Json.Num(6)), response)
    }

    @Test
    fun parsesAFailureEnvelope() {
        val response = AnkiConnectEnvelope.parse("""{"result":null,"error":"unsupported action"}""")
        assertEquals(Response.Failed("unsupported action"), response)
    }

    @Test
    fun treatsAMissingOrExtraKeyEnvelopeAsProtocolError() {
        assertEquals(Response.ProtocolError, AnkiConnectEnvelope.parse("""{"result":6}"""))
        assertEquals(
            Response.ProtocolError,
            AnkiConnectEnvelope.parse("""{"result":6,"error":null,"extra":1}"""),
        )
    }

    @Test
    fun treatsANonEnvelopeBodyAsProtocolError() {
        assertEquals(Response.ProtocolError, AnkiConnectEnvelope.parse("6"))
        assertEquals(Response.ProtocolError, AnkiConnectEnvelope.parse("not json"))
        assertEquals(
            Response.ProtocolError,
            AnkiConnectEnvelope.parse("""{"result":6,"error":7}"""),
        )
    }

    @Test
    fun parsesEachNestedMultiEnvelope() {
        val body = """{"result":[{"result":6,"error":null},{"result":null,"error":"bad"}],"error":null}"""
        val responses = AnkiConnectEnvelope.parseMulti(body)
        assertEquals(2, responses.size)
        assertEquals(Response.Ok(Json.Num(6)), responses[0])
        assertEquals(Response.Failed("bad"), responses[1])
    }

    @Test
    fun multiParseFailsClosedWhenOuterShapeIsWrong() {
        assertEquals(
            listOf(Response.ProtocolError),
            AnkiConnectEnvelope.parseMulti("""{"result":6,"error":null}"""),
        )
        assertEquals(
            listOf(Response.ProtocolError),
            AnkiConnectEnvelope.parseMulti("""{"result":null,"error":"boom"}"""),
        )
    }

    @Test
    fun nestedMultiElementThatIsNotAnEnvelopeIsAProtocolError() {
        val body = """{"result":[6],"error":null}"""
        assertEquals(listOf(Response.ProtocolError), AnkiConnectEnvelope.parseMulti(body))
    }
}
