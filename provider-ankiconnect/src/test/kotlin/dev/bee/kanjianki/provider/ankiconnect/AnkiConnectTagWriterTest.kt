package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectTagWriterTest {
    private fun writer(
        exchange: ScriptedAnkiConnectExchange,
        keyProvider: () -> String? = { null },
        batchSize: Int = AnkiConnectReadPlanner.MAX_MULTI_ACTIONS,
    ) = AnkiConnectTagWriter(exchange.transport(), keyProvider, batchSize)

    /** An Anki that accepts every `addTags`. Success is a null result. */
    private fun accepting(): ScriptedAnkiConnectExchange =
        ScriptedAnkiConnectExchange().onResult("addTags", "null")

    @Test
    fun tagsEveryRequestedNote() {
        val exchange = accepting()

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L, 12L, 13L))

        assertEquals(setOf(11L, 12L, 13L), outcome.tagged)
        assertTrue(outcome.failed.isEmpty())
        assertEquals(3, outcome.requested)
    }

    /**
     * The load-bearing shape decision: one note per action. A single action for the
     * whole set would be one round trip, but a failure would then be attributable
     * only to the set, and the caller could not tell which notes to retry.
     */
    @Test
    fun sendsOneNotePerActionInsideOneMultiRequest() {
        val exchange = accepting()

        writer(exchange).addTag(ProviderNotePolicy.ARCHIVED_TAG, setOf(11L, 12L, 13L))

        val actions = exchange.anyBodiesFor("addTags")
        assertEquals(3, actions.size)
        assertTrue(actions.all { requestedNoteIds(it).size == 1 })
        assertEquals(listOf(11L, 12L, 13L), actions.flatMap(::requestedNoteIds))
        // …and all three rode one round trip, not three.
        assertEquals(1, exchange.bodiesFor("multi").size)
    }

    /** Only Kani's own tags are ever written, and verbatim. */
    @Test
    fun writesTheTagItWasGivenAndNothingElse() {
        for (tag in listOf(ProviderNotePolicy.ARCHIVED_TAG, ProviderNotePolicy.REPAIRED_TAG)) {
            val exchange = accepting()

            writer(exchange).addTag(tag, setOf(11L))

            val sent = exchange.anyBodiesFor("addTags").single()
            assertEquals(tag, requestedTags(sent))
        }
    }

    /**
     * The reason every nested envelope is inspected. A `multi` partial failure
     * arrives as a 200 OK outer envelope wrapping a mix of successes and errors; a
     * caller that checked only the outer envelope would report the failed notes as
     * tagged and never retry them.
     */
    @Test
    fun aPartialFailureIsAttributedToTheExactNotesThatFailed() {
        val exchange = ScriptedAnkiConnectExchange().onRaw("addTags") { body ->
            val noteId = requestedNoteIds(body).single()
            if (noteId == 12L) {
                AnkiConnectTransport.HttpExchange.Result.Ok(
                    200,
                    """{"result":null,"error":"note was not found: 12"}""",
                )
            } else {
                AnkiConnectTransport.HttpExchange.Result.Ok(200, """{"result":null,"error":null}""")
            }
        }

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L, 12L, 13L))

        assertEquals(setOf(11L, 13L), outcome.tagged)
        assertEquals(setOf(12L), outcome.failed)
    }

    /**
     * A nested array Kani cannot align position-for-position with the batch fails
     * the whole batch. Guessing an alignment is the one outcome worse than a retry:
     * it would report notes as tagged that are not, and the caller would stop
     * retrying them.
     */
    @Test
    fun aMisalignedMultiResponseFailsTheWholeBatchRatherThanGuessing() {
        val short = ScriptedAnkiConnectExchange().onResult("multi", """[{"result":null,"error":null}]""")
        assertEquals(
            setOf(11L, 12L, 13L),
            writer(short).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L, 12L, 13L)).failed,
        )

        val long = ScriptedAnkiConnectExchange().onResult(
            "multi",
            """[{"result":null,"error":null},{"result":null,"error":null},{"result":null,"error":null}]""",
        )
        assertEquals(
            setOf(11L, 12L),
            writer(long).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L, 12L)).failed,
        )
    }

    /** A nested element that is not an envelope at all is a failure, not a success. */
    @Test
    fun anUnparseableNestedEnvelopeIsAFailure() {
        val exchange = ScriptedAnkiConnectExchange()
            .onResult("multi", """[{"result":null,"error":null},"not an envelope"]""")

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L, 12L))

        assertEquals(setOf(11L), outcome.tagged)
        assertEquals(setOf(12L), outcome.failed)
    }

    /** An outer error (not a nested one) fails every note in the batch. */
    @Test
    fun anOuterErrorFailsEveryNoteInTheBatch() {
        val exchange = ScriptedAnkiConnectExchange().onError("multi", "valid api key must be provided")

        val outcome = writer(exchange).addTag(ProviderNotePolicy.ARCHIVED_TAG, setOf(11L, 12L))

        assertTrue(outcome.tagged.isEmpty())
        assertEquals(setOf(11L, 12L), outcome.failed)
    }

    /**
     * Every provider failure shape resolves to "not tagged yet". A tag write runs
     * after the sync has already committed, so throwing here would turn a
     * cosmetic write-back problem into a failed sync.
     */
    @Test
    fun everyProviderFailureShapeDegradesToUntaggedRatherThanThrowing() {
        val shapes = listOf(
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("refused"),
            AnkiConnectTransport.HttpExchange.Result.Timeout,
            AnkiConnectTransport.HttpExchange.Result.TooLarge,
            AnkiConnectTransport.HttpExchange.Result.Ok(500, ""),
            AnkiConnectTransport.HttpExchange.Result.Ok(200, "not json"),
            AnkiConnectTransport.HttpExchange.Result.Ok(200, """{"result":"not an array","error":null}"""),
        )

        for (shape in shapes) {
            val exchange = ScriptedAnkiConnectExchange().onRaw("multi") { shape }

            val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L))

            assertEquals(shape.toString(), setOf(11L), outcome.failed)
            assertTrue(shape.toString(), outcome.tagged.isEmpty())
        }
    }

    /**
     * Batches are bounded, because `multi` shares one response body and one
     * deadline across every nested action.
     */
    @Test
    fun writesInBoundedMultiBatches() {
        val exchange = accepting()
        val noteIds = (1L..60L).toSet()

        val outcome = writer(exchange, batchSize = 25).addTag(ProviderNotePolicy.ARCHIVED_TAG, noteIds)

        assertEquals(noteIds, outcome.tagged)
        assertEquals(3, exchange.bodiesFor("multi").size)
        assertEquals(60, exchange.anyBodiesFor("addTags").size)
    }

    /** A batch size larger than the `multi` cap is clamped, never honored. */
    @Test
    fun aBatchSizeAboveTheMultiCapIsClamped() {
        val exchange = accepting()

        writer(exchange, batchSize = 10_000).addTag(ProviderNotePolicy.ARCHIVED_TAG, (1L..30L).toSet())

        assertEquals(2, exchange.bodiesFor("multi").size)
    }

    /**
     * Cancellation stops before the next batch and reports the untouched notes as
     * failed — they are simply still untagged, and the caller retries them. The
     * notes already written stay reported as tagged, because they really are.
     */
    @Test
    fun cancellationStopsAtTheBatchBoundaryAndKeepsEarlierWritesReported() {
        val exchange = accepting()
        var batches = 0
        val cancelAfterFirstBatch = CollectionCancellation { batches++ > 0 }

        val outcome = writer(exchange, batchSize = 2)
            .addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L, 12L, 13L, 14L), cancelAfterFirstBatch)

        assertEquals(setOf(11L, 12L), outcome.tagged)
        assertEquals(setOf(13L, 14L), outcome.failed)
        assertEquals(1, exchange.bodiesFor("multi").size)
    }

    /** Cancellation before the first batch writes nothing at all. */
    @Test
    fun cancellationBeforeTheFirstBatchWritesNothing() {
        val exchange = accepting()

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L)) { true }

        assertEquals(setOf(11L), outcome.failed)
        assertTrue(exchange.received.isEmpty())
    }

    /** An empty request is not a request. */
    @Test
    fun noNotesMeansNoRequest() {
        val exchange = accepting()

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, emptySet())

        assertEquals(0, outcome.requested)
        assertTrue(exchange.received.isEmpty())
    }

    /**
     * A non-positive id is not a note. Sending it would make AnkiConnect fail an
     * otherwise healthy batch, so it is dropped before the wire rather than
     * reported as a failure the caller would retry forever.
     */
    @Test
    fun nonPositiveNoteIdsAreDroppedBeforeTheWire() {
        val exchange = accepting()

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(0L, -1L, 11L))

        assertEquals(setOf(11L), outcome.tagged)
        assertTrue(outcome.failed.isEmpty())
        assertEquals(listOf(11L), exchange.anyBodiesFor("addTags").flatMap(::requestedNoteIds))
    }

    /** Every id dropped means no request survives to send. */
    @Test
    fun onlyInvalidNoteIdsMeansNoRequest() {
        val exchange = accepting()

        val outcome = writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(0L, -5L))

        assertEquals(0, outcome.requested)
        assertTrue(exchange.received.isEmpty())
    }

    @Test
    fun aBlankTagIsRefusedByContract() {
        val exchange = accepting()

        for (tag in listOf("", "   ")) {
            val error = runCatching { writer(exchange).addTag(tag, setOf(11L)) }.exceptionOrNull()
            assertTrue(tag, error is IllegalArgumentException)
        }
        assertTrue(exchange.received.isEmpty())
    }

    /**
     * `addTags` is idempotent in Anki, which is why this writer needs no
     * read-modify-write cycle: a retry after an ambiguous outcome cannot
     * double-tag, and Kani never rewrites the whole `tags` value (which would
     * clobber a tag the user added between the read and the write).
     */
    @Test
    fun neverReadsOrRewritesTheWholeTagValue() {
        val exchange = accepting()

        writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L))
        // A retry of the same note is safe and still just an addTags.
        writer(exchange).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L))

        // Two round trips, both `multi`; no read of the note's existing tags.
        assertEquals(listOf("multi", "multi"), exchange.actions())
        assertEquals(2, exchange.anyBodiesFor("addTags").size)
        assertFalse(exchange.received.any { it.contains("notesInfo") })
    }

    @Test
    fun forwardsTheApiKeyOnTheOuterAndEveryNestedAction() {
        val exchange = accepting()

        writer(exchange, keyProvider = { "s3cret" }).addTag(ProviderNotePolicy.REPAIRED_TAG, setOf(11L))

        assertTrue(exchange.bodiesFor("multi").single().contains("s3cret"))
        assertTrue(exchange.anyBodiesFor("addTags").single().contains("s3cret"))
    }

    /** The note ids an `addTags` request body named. */
    private fun requestedNoteIds(body: String): List<Long> {
        val notes = params(body)?.entries?.get("notes") as? AnkiConnectJson.Json.Arr
            ?: return emptyList()
        return notes.items.mapNotNull { (it as? AnkiConnectJson.Json.Num)?.value }
    }

    /** The tag an `addTags` request body named. */
    private fun requestedTags(body: String): String? =
        (params(body)?.entries?.get("tags") as? AnkiConnectJson.Json.Str)?.value

    private fun params(body: String): AnkiConnectJson.Json.Obj? =
        (AnkiConnectJson.decode(body) as? AnkiConnectJson.Json.Obj)
            ?.entries?.get("params") as? AnkiConnectJson.Json.Obj
}
