package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.AnkiFieldTextNormalizer
import dev.bee.kanjianki.core.AnkiKanjiInventoryCollector
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionInventoryConsumer
import dev.bee.kanjianki.syncapi.CollectionInventoryNote
import dev.bee.kanjianki.syncapi.CollectionInventoryResult
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectInventoryGatewayTest {
    /** A scripted AnkiConnect with a healthy handshake and two note types. */
    private fun readyExchange(
        overrides: ScriptedAnkiConnectExchange.() -> Unit = {},
    ): ScriptedAnkiConnectExchange {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("requestPermission", """{"permission":"granted","requireApikey":false}""")
        exchange.onResult("version", "6")
        exchange.onResult(
            "apiReflect",
            """{"scopes":["actions"],"actions":${
                AnkiConnectActions.required.joinToString(",", "[", "]") { """"$it"""" }
            }}""",
        )
        exchange.onResult("getMediaDirPath", """"User 1"""")
        exchange.onResult("modelNamesAndIds", """{"Kiku":42,"Basic":7}""")
        exchange.onResult("findNotes", "[11,12]")
        exchange.onResult(
            "notesInfo",
            """[${
                ScriptedAnkiConnectExchange.noteRow(
                    11L,
                    "Kiku",
                    listOf("Expression" to "確認", "Reading" to "かくにん"),
                )
            },${
                ScriptedAnkiConnectExchange.noteRow(
                    12L,
                    "Basic",
                    listOf("Front" to "補助<br>", "Back" to "help"),
                )
            }]""",
        )
        exchange.overrides()
        return exchange
    }

    private fun gateway(exchange: ScriptedAnkiConnectExchange) =
        AnkiConnectInventoryGateway(exchange.transport())

    private fun collect(
        exchange: ScriptedAnkiConnectExchange,
    ): Pair<List<CollectionInventoryNote>, CollectionInventoryResult> {
        val seen = mutableListOf<CollectionInventoryNote>()
        val result = gateway(exchange).scan(CollectionInventoryConsumer { seen += it })
        return seen to result
    }

    /**
     * The scan spans every note type, not the configured sync model. A gateway that
     * quietly restricted itself to the configured model would make Missing Kanji
     * report kanji the user already has as missing.
     */
    @Test
    fun scansEveryNoteTypeAndReportsTheCollectionModelCount() {
        val (notes, result) = collect(readyExchange())

        assertEquals(listOf("Kiku", "Basic"), notes.map { it.modelName })
        assertEquals(2, result.notesRead)
        assertEquals(0, result.skippedNotes)
        assertEquals(2, result.modelCount)
        assertEquals(CollectionInventoryResult.QueryMode.PROVIDER_SEARCH, result.queryMode)
    }

    /** The collection-wide query, not the configured model's search. */
    @Test
    fun usesTheCollectionWideQuery() {
        val exchange = readyExchange()
        gateway(exchange).scan(CollectionInventoryConsumer { })

        val findNotes = exchange.bodiesFor("findNotes").single()
        assertTrue(findNotes.contains(AnkiConnectInventoryGateway.COLLECTION_WIDE_QUERY))
        assertFalse(findNotes.contains("note:"))
    }

    /**
     * `notesInfo` reports a model *name*; the numeric id comes from the name map.
     * A note type created between the two calls has no id, and a fabricated one
     * would collide with a real model, so it reports the sentinel instead.
     */
    @Test
    fun resolvesModelIdsFromTheNameMapAndFallsBackForAnUnlistedModel() {
        val exchange = readyExchange { onResult("modelNamesAndIds", """{"Kiku":42}""") }

        val (notes, _) = collect(exchange)

        assertEquals(42L, notes.first { it.modelName == "Kiku" }.modelId)
        assertEquals(
            AnkiConnectInventoryGateway.UNKNOWN_MODEL_ID,
            notes.first { it.modelName == "Basic" }.modelId,
        )
    }

    /**
     * The point of the whole class: field text passes through the consumer and is
     * not retained. What survives the scan is the aggregate the collector builds —
     * kanji literals and counts — with no note ids, field names, or raw text.
     */
    @Test
    fun feedsTheAggregateCollectorAndRetainsNoRawFieldText() {
        val collector = AnkiKanjiInventoryCollector()
        val exchange = readyExchange()

        val result = gateway(exchange).scan(
            CollectionInventoryConsumer { note ->
                note.fields.forEach {
                    collector.addNormalizedField(AnkiFieldTextNormalizer.normalize(it))
                }
            },
        )
        val inventory = collector.finish(
            notesScanned = result.notesRead,
            skippedNotes = result.skippedNotes,
            modelCount = result.modelCount,
        )

        assertEquals(setOf("確", "認", "補", "助"), inventory.literals)
        assertEquals(2, inventory.notesScanned)
        assertEquals(4, inventory.uniqueKanjiCount)
        // The markup in the Basic note's Front field never reaches the inventory.
        assertTrue(inventory.literals.none { it.contains("<") })
    }

    /** One unparseable row is counted, not fatal. */
    @Test
    fun isolatesAMalformedRowInsteadOfFailingTheScan() {
        val exchange = readyExchange {
            onResult("findNotes", "[11,12,13]")
            onResult(
                "notesInfo",
                """[${
                    ScriptedAnkiConnectExchange.noteRow(11L, "Kiku", listOf("Expression" to "橋"))
                },{"noteId":null},${
                    ScriptedAnkiConnectExchange.noteRow(13L, "Kiku", listOf("Expression" to "渡"))
                }]""",
            )
        }

        val (notes, result) = collect(exchange)

        assertEquals(listOf(11L, 13L), notes.map { it.noteId })
        assertEquals(2, result.notesRead)
        assertEquals(1, result.skippedNotes)
    }

    /**
     * Progress reports against the known total, and reports per batch. A scan of a
     * large collection with no progress is indistinguishable from a hang, and the
     * skipped rows must be counted toward completion or the bar never fills.
     */
    @Test
    fun reportsProgressPerBatchAgainstTheKnownTotal() {
        val seen = mutableListOf<CollectionProgress>()
        gateway(readyExchange()).scan(
            CollectionInventoryConsumer { },
            CollectionProgressListener { seen += it },
        )

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.stage == CollectionProgress.Stage.READING_INVENTORY })
        val last = seen.last()
        assertEquals(2, last.completed)
        assertEquals(2, last.total)
    }

    /**
     * `findNotes` answers with the whole ID array in one response, so the cap is
     * the only protection at that step — and it must fail loudly rather than
     * truncate, because a truncated inventory reports kanji the user has as
     * missing.
     */
    @Test
    fun anOversizeIdResponseFailsBeforeAnyDetailRead() {
        val exchange = readyExchange {
            onResult("findNotes") {
                (1..AnkiConnectReadPlanner.MAX_ID_COUNT + 1).joinToString(",", "[", "]")
            }
        }

        val failure = assertThrows(AnkiConnectReadPlanner.OversizeIdResponseException::class.java) {
            gateway(exchange).scan(CollectionInventoryConsumer { })
        }

        assertEquals(AnkiConnectReadPlanner.MAX_ID_COUNT, failure.cap)
        assertTrue(exchange.bodiesFor("notesInfo").isEmpty())
    }

    @Test
    fun cancellationBeforeTheFirstRequestStopsTheScan() {
        val exchange = readyExchange()
        val failure = assertThrows(CollectionFailure::class.java) {
            gateway(exchange).scan(
                CollectionInventoryConsumer { },
                CollectionProgressListener.NONE,
            ) { true }
        }

        assertEquals(CollectionFailureKind.CANCELLED, failure.kind)
        assertTrue(exchange.bodiesFor("findNotes").isEmpty())
    }

    /**
     * Cancellation is checked per row, not only per batch: a batch is up to 500
     * rendered notes, which is enough work to be worth interrupting.
     */
    @Test
    fun cancellationIsCheckedPerRowNotOnlyPerBatch() {
        var delivered = 0
        val failure = assertThrows(CollectionFailure::class.java) {
            gateway(readyExchange()).scan(
                CollectionInventoryConsumer { delivered++ },
                CollectionProgressListener.NONE,
            ) { delivered >= 1 }
        }

        assertEquals(CollectionFailureKind.CANCELLED, failure.kind)
        assertEquals(1, delivered)
    }

    /** A ready Anki advertises the inventory capability and nothing else. */
    @Test
    fun readyStatusAdvertisesOnlyTheInventoryCapability() {
        val status = gateway(readyExchange()).status()

        assertTrue(status.isReady())
        assertEquals(setOf(CollectionCapability.COLLECTION_INVENTORY), status.capabilities)
        assertTrue(status.message.contains("API v6"))
    }

    /**
     * Availability mapping is shared with the collection gateway so the two cannot
     * classify the same Anki differently. Pinning one non-ready step here proves
     * the wiring; [AnkiConnectStatusMappingTest] covers the whole table.
     */
    @Test
    fun aNonReadyHandshakeCarriesNoCapabilities() {
        val exchange = readyExchange { onResult("getMediaDirPath", "null") }

        val status = gateway(exchange).status()

        assertEquals(CollectionAvailability.INVALID_CONFIGURATION, status.availability)
        assertTrue(status.capabilities.isEmpty())
    }

    @Test
    fun anUnexpectedModelResponseIsAProtocolFailure() {
        val exchange = readyExchange { onResult("modelNamesAndIds", """["Kiku"]""") }

        val failure = assertThrows(CollectionFailure::class.java) {
            gateway(exchange).scan(CollectionInventoryConsumer { })
        }

        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun anAnkiConnectErrorFailsTheScanWithTheMappedKind() {
        val exchange = readyExchange { onError("findNotes", "invalid api key provided") }

        val failure = assertThrows(CollectionFailure::class.java) {
            gateway(exchange).scan(CollectionInventoryConsumer { })
        }

        assertEquals(CollectionFailureKind.AUTH_REQUIRED, failure.kind)
    }

    @Test
    fun anEmptyCollectionScansToZeroWithoutADetailRequest() {
        val exchange = readyExchange { onResult("findNotes", "[]") }

        val (notes, result) = collect(exchange)

        assertTrue(notes.isEmpty())
        assertEquals(0, result.notesRead)
        assertEquals(0, result.skippedNotes)
        assertTrue(exchange.bodiesFor("notesInfo").isEmpty())
    }

    /**
     * Detail reads are batched, and the batch size adapts down by observed bytes
     * exactly as the configured-model read does, so a collection of heavy notes
     * cannot be pulled in one oversize response.
     */
    @Test
    fun readsDetailInBoundedAdaptiveBatches() {
        val ids = (1L..250L).toList()
        val exchange = readyExchange {
            onResult("findNotes", ids.joinToString(",", "[", "]"))
            onResult("notesInfo") { body ->
                requestedNoteIds(body).joinToString(",", "[", "]") { id ->
                    ScriptedAnkiConnectExchange.noteRow(id, "Kiku", listOf("Expression" to "橋"))
                }
            }
        }

        val (notes, result) = collect(exchange)

        assertEquals(250, notes.size)
        assertEquals(250, result.notesRead)
        val batchSizes = exchange.bodiesFor("notesInfo").map { requestedNoteIds(it).size }
        assertTrue("batches were $batchSizes", batchSizes.size > 1)
        assertTrue(batchSizes.all { it <= AnkiConnectReadPlanner.MAX_BATCH })
        assertEquals(250, batchSizes.sum())
    }

    @Test
    fun forwardsTheApiKeyToEveryRequest() {
        val exchange = readyExchange()
        AnkiConnectInventoryGateway(exchange.transport()) { "s3cret" }
            .scan(CollectionInventoryConsumer { })

        assertTrue(exchange.bodiesFor("modelNamesAndIds").all { it.contains("s3cret") })
        assertTrue(exchange.bodiesFor("findNotes").all { it.contains("s3cret") })
        assertTrue(exchange.bodiesFor("notesInfo").all { it.contains("s3cret") })
    }

    /** The note ids a `notesInfo` request body asked for. */
    private fun requestedNoteIds(body: String): List<Long> {
        val params = (AnkiConnectJson.decode(body) as? AnkiConnectJson.Json.Obj)
            ?.entries?.get("params") as? AnkiConnectJson.Json.Obj
            ?: return emptyList()
        val notes = params.entries["notes"] as? AnkiConnectJson.Json.Arr ?: return emptyList()
        return notes.items.mapNotNull { (it as? AnkiConnectJson.Json.Num)?.value }
    }
}
