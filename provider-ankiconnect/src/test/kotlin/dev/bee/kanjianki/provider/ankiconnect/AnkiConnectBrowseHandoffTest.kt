package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectBrowseHandoffTest {
    private fun handoff(
        exchange: ScriptedAnkiConnectExchange,
        keyProvider: () -> String? = { null },
    ) = AnkiConnectBrowseHandoff(exchange.transport(), keyProvider)

    private fun accepting(): ScriptedAnkiConnectExchange =
        ScriptedAnkiConnectExchange().onResult("guiBrowse", "[110,111]")

    @Test
    fun acceptedHandoffReportsSuccess() {
        assertTrue(handoff(accepting()).browse("deck:current"))
    }

    /**
     * The load-bearing property: what the user sees in Anki is exactly the search
     * Kani showed them. Rewriting or re-escaping the query here would break that,
     * so the query must reach `guiBrowse` byte-for-byte — including the quoting
     * [TextUtil.browserSearchForKanji] already applied.
     */
    @Test
    fun passesKanisOwnSearchThroughVerbatim() {
        val exchange = accepting()
        val query = TextUtil.browserSearchForKanji("橋", RecordsSyncModels.Settings.kikuDefaults())

        handoff(exchange).browse(query)

        val sent = exchange.bodiesFor("guiBrowse").single()
        val decoded = AnkiConnectJson.decode(sent) as AnkiConnectJson.Json.Obj
        val params = decoded.entries["params"] as AnkiConnectJson.Json.Obj
        assertEquals(query, (params.entries["query"] as AnkiConnectJson.Json.Str).value)
    }

    /**
     * A blank query is refused locally. AnkiConnect would answer it by selecting
     * the user's entire collection in their browser, which is not a handoff.
     */
    @Test
    fun refusesABlankQueryWithoutSendingARequest() {
        val exchange = accepting()

        assertFalse(handoff(exchange).browse(""))
        assertFalse(handoff(exchange).browse("   "))
        assertTrue(exchange.bodiesFor("guiBrowse").isEmpty())
    }

    /**
     * The matched card ids come back and are deliberately dropped. Reading them
     * would make this a collection read, with a read's capability-gating and
     * bounding obligations, for no benefit.
     */
    @Test
    fun ignoresWhateverAnkiReturns() {
        for (result in listOf("[]", "[110]", "null", """"unexpected"""")) {
            val exchange = ScriptedAnkiConnectExchange().onResult("guiBrowse", result)
            assertTrue(result, handoff(exchange).browse("deck:current"))
        }
    }

    /**
     * An older AnkiConnect answers an unknown action with an error, and the caller
     * needs that to be a failure so it can fall back to showing the query for the
     * user to copy — rather than silently reporting a handoff that never happened.
     */
    @Test
    fun anUnsupportedActionFails() {
        val exchange = ScriptedAnkiConnectExchange()
            .onError("guiBrowse", "unsupported action")

        val failure = assertThrows(CollectionFailure::class.java) {
            handoff(exchange).browse("deck:current")
        }

        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun anApiKeyErrorFailsAsAuthRequired() {
        val exchange = ScriptedAnkiConnectExchange()
            .onError("guiBrowse", "valid api key must be provided")

        val failure = assertThrows(CollectionFailure::class.java) {
            handoff(exchange).browse("deck:current")
        }

        assertEquals(CollectionFailureKind.AUTH_REQUIRED, failure.kind)
        assertFalse(failure.retryable)
    }

    @Test
    fun anUnreachableAnkiFailsAsNotAvailable() {
        val exchange = ScriptedAnkiConnectExchange().onRaw("guiBrowse") {
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("refused")
        }

        val failure = assertThrows(CollectionFailure::class.java) {
            handoff(exchange).browse("deck:current")
        }

        assertEquals(CollectionFailureKind.NOT_AVAILABLE, failure.kind)
    }

    @Test
    fun anUnparseableResponseIsAProtocolFailure() {
        val exchange = ScriptedAnkiConnectExchange().onRaw("guiBrowse") {
            AnkiConnectTransport.HttpExchange.Result.Ok(200, "not json")
        }

        val failure = assertThrows(CollectionFailure::class.java) {
            handoff(exchange).browse("deck:current")
        }

        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
        assertTrue(failure.message!!.contains("guiBrowse"))
    }

    @Test
    fun forwardsTheApiKey() {
        val exchange = accepting()

        handoff(exchange) { "s3cret" }.browse("deck:current")

        assertTrue(exchange.bodiesFor("guiBrowse").single().contains("s3cret"))
    }
}
