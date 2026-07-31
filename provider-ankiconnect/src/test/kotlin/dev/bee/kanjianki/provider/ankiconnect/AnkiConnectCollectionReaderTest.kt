package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectCollectionReaderTest {
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val model = settings.modelName

    private fun scriptedCollection(
        noteIds: List<Long>,
        cardsFor: (Long) -> List<String> = { noteId ->
            listOf(ScriptedAnkiConnectExchange.cardRow(noteId * 10, noteId, model))
        },
        notesInfo: ((List<Long>) -> String)? = null,
    ): ScriptedAnkiConnectExchange {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":1607392319495}""")
        exchange.onResult("findNotes", noteIds.joinToString(",", "[", "]"))
        exchange.onResult("findCards") { body ->
            requestedIds(body, "query")
            noteIds.flatMap { cardsFor(it) }
                .mapIndexed { index, _ -> index }
                .joinToString(",", "[", "]") { index -> "${noteIds[0] * 10 + index}" }
        }
        exchange.onResult("notesInfo") { body ->
            val requested = requestedIds(body, "notes")
            notesInfo?.invoke(requested) ?: requested.joinToString(",", "[", "]") { noteId ->
                ScriptedAnkiConnectExchange.noteRow(
                    noteId,
                    model,
                    listOf(
                        settings.expressionField to "語$noteId",
                        settings.readingField to "reading$noteId",
                        settings.meaningField to "meaning$noteId",
                        settings.sentenceField to "sentence$noteId",
                        settings.frequencyField to "1",
                        settings.frequencySortField to "1",
                    ),
                    tags = listOf("kani_test"),
                )
            }
        }
        return exchange
    }

    private fun requestedIds(body: String, key: String): List<Long> {
        val params = (AnkiConnectJson.decode(body) as AnkiConnectJson.Json.Obj)
            .entries["params"] as? AnkiConnectJson.Json.Obj ?: return emptyList()
        val arr = params.entries[key] as? AnkiConnectJson.Json.Arr ?: return emptyList()
        return arr.items.mapNotNull { (it as? AnkiConnectJson.Json.Num)?.value }
    }

    @Test
    fun readsNotesAndCardsIntoTheNeutralSnapshot() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[11,12]")
        exchange.onResult("notesInfo") { body ->
            requestedIds(body, "notes").joinToString(",", "[", "]") { noteId ->
                ScriptedAnkiConnectExchange.noteRow(
                    noteId,
                    model,
                    listOf(settings.expressionField to "脱出$noteId"),
                    tags = listOf("mined"),
                )
            }
        }
        exchange.onResult("findCards", "[110,120]")
        exchange.onResult("cardsInfo") { body ->
            requestedIds(body, "cards").joinToString(",", "[", "]") { cardId ->
                ScriptedAnkiConnectExchange.cardRow(cardId, cardId / 10, model, interval = 40)
            }
        }

        val result = AnkiConnectCollectionReader(exchange.transport()).read(settings)

        assertEquals(listOf(11L, 12L), result.snapshot.notes.map { it.noteId })
        assertEquals(listOf(110L, 120L), result.snapshot.cards.map { it.cardId })
        assertEquals(0, result.skipped.total)
        // Note fields are projected through the same required-field selection the
        // AnkiDroid gateway uses, so absent fields read as empty, not missing.
        val note = result.snapshot.notes.first()
        assertEquals("脱出11", note.expression(settings))
        assertEquals("", note.meaning(settings))
        assertEquals(42L, note.modelId)
        assertEquals(listOf("mined"), note.tags)
        val card = result.snapshot.cards.first()
        assertEquals(40, card.intervalDays)
        assertEquals("Default", card.deckName)
        assertFalse(card.suspended)
    }

    @Test
    fun neverFabricatesFsrsMemoryState() {
        val exchange = scriptedCollection(listOf(7L))
        exchange.onResult("cardsInfo") { body ->
            requestedIds(body, "cards").joinToString(",", "[", "]") { cardId ->
                ScriptedAnkiConnectExchange.cardRow(cardId, 7L, model)
            }
        }
        val provider = AnkiConnectCollectionReader(exchange.transport())
            .readProviderCollection(settings)

        assertTrue(provider.snapshot.cards.isNotEmpty())
        provider.snapshot.cards.forEach { card ->
            assertNull(card.fsrsStability)
            assertNull(card.fsrsDifficulty)
            assertNull(card.fsrsRetrievability)
        }
        // The capability must not be advertised, so seeding falls back to
        // interval/lapses evidence instead of trusting a fabricated memory state.
        assertFalse(CollectionCapability.FSRS_MEMORY_STATE in provider.capabilities)
        assertTrue(CollectionCapability.SOURCE_IDENTITY in provider.capabilities)
        assertNotNull(provider.sourceIdentity)
    }

    @Test
    fun bindsSourceIdentityToTheEndpointWithoutLeakingIt() {
        val exchange = scriptedCollection(listOf(3L))
        exchange.onResult("cardsInfo") { body ->
            requestedIds(body, "cards").joinToString(",", "[", "]") { cardId ->
                ScriptedAnkiConnectExchange.cardRow(cardId, 3L, model)
            }
        }
        val identity = AnkiConnectCollectionReader(exchange.transport())
            .readProviderCollection(settings)
            .sourceIdentity!!

        val evidence = identity.redactedEvidence()
        assertEquals(
            dev.bee.kanjianki.syncapi.CollectionProviderKind.ANKI_CONNECT,
            evidence.providerKind,
        )
        assertEquals(1, evidence.noteIdSampleSize)
        assertEquals(1, evidence.cardIdSampleSize)
        // toString must stay redacted: no endpoint, no raw ids.
        assertFalse(identity.toString().contains("127.0.0.1"))
    }

    @Test
    fun usesTheSameConfiguredModelQueryAsTheAndroidProvider() {
        val exchange = scriptedCollection(listOf(1L))
        exchange.onResult("cardsInfo", "[]")
        AnkiConnectCollectionReader(exchange.transport()).read(settings)

        val findNotes = exchange.bodiesFor("findNotes").single()
        val expected = dev.bee.kanjianki.syncdomain.ProviderNotePolicy.modelSearch(model)
        assertTrue("query was $findNotes", findNotes.contains(AnkiConnectJson.encode(AnkiConnectJson.str(expected))))
    }

    @Test
    fun mergesBrowserQueryNotesAndMarksTheirCards() {
        val browserSettings = RecordsSyncModels.Settings(
            model,
            settings.templateName,
            settings.expressionField,
            settings.readingField,
            settings.meaningField,
            settings.sentenceField,
            settings.frequencyField,
            settings.frequencySortField,
            settings.matureDays,
            settings.matureSupportThreshold,
            settings.suspendedRankMin,
            settings.suspendedRankMax,
            settings.activeQueueCap,
            settings.newPerDay,
            settings.writingTriggerMissDays,
            settings.recognitionPromotionPasses,
            settings.realDueReviewsToMove,
            settings.importActiveCards,
            settings.importSuspendedCards,
            settings.importTaggedCards,
            emptyList<String>(),
            settings.importWeakCards,
            settings.importWeakFsrsDifficultyThreshold,
            settings.importWeakLapsesThreshold,
            settings.importMinMatchingCardsPerKanji,
            true,
            "tag:leech",
            settings.newCardSortMode,
            settings.ladderPromotionIntervalDays,
            settings.ladderDemotionFailStreak,
            settings.ladderPromotionMinPasses,
        )
        assertTrue(browserSettings.browserQueryImportEnabled())

        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes") { body ->
            // The configured-model query returns note 1; the browser query adds 2.
            if (body.contains("tag:leech")) "[2]" else "[1]"
        }
        exchange.onResult("notesInfo") { body ->
            requestedIds(body, "notes").joinToString(",", "[", "]") { noteId ->
                ScriptedAnkiConnectExchange.noteRow(
                    noteId,
                    if (noteId == 1L) model else "Other",
                    listOf(settings.expressionField to "note$noteId"),
                )
            }
        }
        exchange.onResult("findCards", "[10,20]")
        exchange.onResult("cardsInfo") { body ->
            requestedIds(body, "cards").joinToString(",", "[", "]") { cardId ->
                ScriptedAnkiConnectExchange.cardRow(
                    cardId,
                    cardId / 10,
                    if (cardId == 10L) model else "Other",
                )
            }
        }

        val result = AnkiConnectCollectionReader(exchange.transport()).read(browserSettings)

        assertEquals(listOf(1L, 2L), result.snapshot.notes.map { it.noteId })
        // Only the configured model's notes carry its model id.
        assertEquals(42L, result.snapshot.notes[0].modelId)
        assertEquals(0L, result.snapshot.notes[1].modelId)
        val marked = result.snapshot.cards.associate { it.cardId to it.browserQueryMatched }
        assertEquals(mapOf(10L to false, 20L to true), marked)
    }

    @Test
    fun dropsNonZeroOrdinalsOnTheConfiguredModelButKeepsOtherModels() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[1]")
        exchange.onResult("notesInfo") {
            "[${ScriptedAnkiConnectExchange.noteRow(1, model, listOf(settings.expressionField to "x"))}]"
        }
        exchange.onResult("findCards", "[10,11,12]")
        exchange.onResult("cardsInfo") {
            listOf(
                ScriptedAnkiConnectExchange.cardRow(10, 1, model, ord = 0),
                ScriptedAnkiConnectExchange.cardRow(11, 1, model, ord = 1),
                ScriptedAnkiConnectExchange.cardRow(12, 1, "Other", ord = 3),
            ).joinToString(",", "[", "]")
        }

        val result = AnkiConnectCollectionReader(exchange.transport()).read(settings)

        assertEquals(listOf(10L, 12L), result.snapshot.cards.map { it.cardId })
    }

    @Test
    fun isolatesMalformedNoteAndCardRows() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[1,2]")
        exchange.onResult("notesInfo") {
            // Second row is missing modelName.
            """[${ScriptedAnkiConnectExchange.noteRow(1, model, listOf(settings.expressionField to "ok"))},""" +
                """{"noteId":2,"tags":[],"fields":{}}]"""
        }
        exchange.onResult("findCards", "[10,11]")
        exchange.onResult("cardsInfo") {
            // Second row is missing queue.
            """[${ScriptedAnkiConnectExchange.cardRow(10, 1, model)},""" +
                """{"cardId":11,"note":1,"deckName":"D","modelName":${'"'}$model${'"'}}]"""
        }

        val result = AnkiConnectCollectionReader(exchange.transport()).read(settings)

        assertEquals(listOf(1L), result.snapshot.notes.map { it.noteId })
        assertEquals(listOf(10L), result.snapshot.cards.map { it.cardId })
        assertEquals(1, result.skipped.notes)
        assertEquals(1, result.skipped.cards)
        assertEquals(2, result.skipped.total)
    }

    @Test
    fun groupsCardsByOwningNoteDeterministically() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[1,2]")
        exchange.onResult("notesInfo") { body ->
            requestedIds(body, "notes").joinToString(",", "[", "]") { noteId ->
                ScriptedAnkiConnectExchange.noteRow(noteId, model, listOf(settings.expressionField to "n$noteId"))
            }
        }
        exchange.onResult("findCards", "[20,10,21,11]")
        exchange.onResult("cardsInfo") { body ->
            // Provider returns them shuffled relative to note order.
            requestedIds(body, "cards").joinToString(",", "[", "]") { cardId ->
                ScriptedAnkiConnectExchange.cardRow(cardId, if (cardId < 20) 1L else 2L, model)
            }
        }

        val cards = AnkiConnectCollectionReader(exchange.transport()).read(settings).snapshot.cards
        // Note 1's cards precede note 2's regardless of provider order.
        assertEquals(listOf(10L, 11L, 20L, 21L), cards.map { it.cardId })
    }

    @Test
    fun normalizesSuspensionAndSubDayIntervals() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[1]")
        exchange.onResult("notesInfo") {
            "[${ScriptedAnkiConnectExchange.noteRow(1, model, listOf(settings.expressionField to "x"))}]"
        }
        exchange.onResult("findCards", "[10,11,12]")
        exchange.onResult("cardsInfo") {
            listOf(
                // Suspended (queue -1) and buried (queue -2) both normalize to suspended.
                ScriptedAnkiConnectExchange.cardRow(10, 1, model, queue = -1, interval = 21),
                ScriptedAnkiConnectExchange.cardRow(11, 1, model, queue = -2, interval = 21),
                // Negative ivl is seconds for a sub-day learning card: floors to 0 days.
                ScriptedAnkiConnectExchange.cardRow(12, 1, model, queue = 1, interval = -600),
            ).joinToString(",", "[", "]")
        }

        val cards = AnkiConnectCollectionReader(exchange.transport()).read(settings).snapshot.cards
            .associateBy { it.cardId }

        assertTrue(cards.getValue(10L).suspended)
        assertTrue(cards.getValue(11L).suspended)
        assertFalse(cards.getValue(12L).suspended)
        assertEquals(0, cards.getValue(12L).intervalDays)
        // A suspended card must not be counted mature.
        assertFalse(cards.getValue(10L).mature(settings.matureDays))
    }

    @Test
    fun batchesDetailReadsAndReportsProgress() {
        val noteIds = (1L..250L).toList()
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", noteIds.joinToString(",", "[", "]"))
        exchange.onResult("notesInfo") { body ->
            requestedIds(body, "notes").joinToString(",", "[", "]") { noteId ->
                ScriptedAnkiConnectExchange.noteRow(noteId, model, listOf(settings.expressionField to "n$noteId"))
            }
        }
        exchange.onResult("findCards", "[]")
        exchange.onResult("cardsInfo", "[]")

        val progress = mutableListOf<CollectionProgress>()
        val result = AnkiConnectCollectionReader(exchange.transport())
            .read(settings, CollectionProgressListener(progress::add))

        assertEquals(250, result.snapshot.notes.size)
        // 250 ids at the default 100-row start batch: no single request carries all.
        val batchSizes = exchange.bodiesFor("notesInfo").map { requestedIds(it, "notes").size }
        assertTrue("batches were $batchSizes", batchSizes.size > 1)
        assertTrue(batchSizes.all { it <= AnkiConnectReadPlanner.MAX_BATCH })
        assertEquals(250, batchSizes.sum())
        val readingNotes = progress.filter { it.stage == CollectionProgress.Stage.READING_NOTES }
        assertEquals(250, readingNotes.last().completed)
        assertEquals(250, readingNotes.last().total)
    }

    @Test
    fun rejectsAnOversizeIdResponseBeforeReadingDetail() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes") {
            (1..AnkiConnectReadPlanner.MAX_ID_COUNT + 1).joinToString(",", "[", "]")
        }

        assertThrows(AnkiConnectReadPlanner.OversizeIdResponseException::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        // No detail read was attempted.
        assertTrue(exchange.bodiesFor("notesInfo").isEmpty())
    }

    @Test
    fun failsAsInvalidConfigurationWhenTheModelIsMissing() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"Basic":1}""")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.INVALID_CONFIGURATION, failure.kind)
        assertFalse(failure.retryable)
    }

    @Test
    fun mapsAnkiConnectAuthErrorsToAuthRequired() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onError("modelNamesAndIds", "valid api key must be provided")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.AUTH_REQUIRED, failure.kind)
    }

    @Test
    fun mapsOtherAnkiConnectErrorsToTransient() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onError("modelNamesAndIds", "collection is not open")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
        assertTrue(failure.retryable)
    }

    @Test
    fun mapsUnreachableAnkiToNotAvailable() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onRaw("modelNamesAndIds") { AnkiConnectTransport.HttpExchange.Result.Timeout }

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.NOT_AVAILABLE, failure.kind)
        assertTrue(failure.retryable)
    }

    @Test
    fun mapsTransportCancellationToCancelled() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onRaw("modelNamesAndIds") { AnkiConnectTransport.HttpExchange.Result.Cancelled }

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.CANCELLED, failure.kind)
    }

    @Test
    fun mapsNonLoopbackResolutionToInvalidConfiguration() {
        val exchange = ScriptedAnkiConnectExchange()
        val transport = AnkiConnectTransport(
            endpoint = (
                AnkiConnectEndpoint.parse(AnkiConnectEndpoint.DEFAULT_URL)
                    as AnkiConnectEndpoint.Result.Valid
                ).endpoint,
            exchange = exchange,
            addressResolver = { arrayOf(java.net.InetAddress.getByName("93.184.216.34")) },
        )

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(transport).read(settings)
        }
        assertEquals(CollectionFailureKind.INVALID_CONFIGURATION, failure.kind)
    }

    @Test
    fun mapsOversizeAndHttpErrorsToTransient() {
        listOf(
            AnkiConnectTransport.HttpExchange.Result.TooLarge,
            AnkiConnectTransport.HttpExchange.Result.Ok(500, "boom"),
        ).forEach { result ->
            val exchange = ScriptedAnkiConnectExchange()
            exchange.onRaw("modelNamesAndIds") { result }
            val failure = assertThrows(CollectionFailure::class.java) {
                AnkiConnectCollectionReader(exchange.transport()).read(settings)
            }
            assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
        }
    }

    @Test
    fun mapsAConnectionFailureToNotAvailable() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onRaw("modelNamesAndIds") {
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("refused")
        }

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.NOT_AVAILABLE, failure.kind)
    }

    @Test
    fun failsAsTransientOnAMalformedResult() {
        val exchange = ScriptedAnkiConnectExchange()
        // A string where an object of name->id belongs.
        exchange.onResult("modelNamesAndIds", "\"nope\"")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun failsAsTransientOnAProtocolErrorBody() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onRaw("modelNamesAndIds") {
            AnkiConnectTransport.HttpExchange.Result.Ok(200, "not json at all")
        }

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun cancellationStopsTheReadBeforeAnyRequest() {
        val exchange = scriptedCollection(listOf(1L))

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport())
                .read(settings, CollectionProgressListener.NONE, CollectionCancellation { true })
        }
        assertEquals(CollectionFailureKind.CANCELLED, failure.kind)
        assertTrue(exchange.received.isEmpty())
    }

    @Test
    fun cancellationMidBatchStopsWithoutDrainingTheCollection() {
        val noteIds = (1L..250L).toList()
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", noteIds.joinToString(",", "[", "]"))
        exchange.onResult("notesInfo") { body ->
            requestedIds(body, "notes").joinToString(",", "[", "]") { noteId ->
                ScriptedAnkiConnectExchange.noteRow(noteId, model, listOf(settings.expressionField to "n$noteId"))
            }
        }
        // Cancel as soon as the first notesInfo batch has been served.
        val cancellation = CollectionCancellation { exchange.bodiesFor("notesInfo").isNotEmpty() }

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport())
                .read(settings, CollectionProgressListener.NONE, cancellation)
        }
        assertEquals(CollectionFailureKind.CANCELLED, failure.kind)
        assertEquals(1, exchange.bodiesFor("notesInfo").size)
    }

    @Test
    fun readsAnEmptyCollectionWithoutCardRequests() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[]")

        val result = AnkiConnectCollectionReader(exchange.transport()).read(settings)

        assertTrue(result.snapshot.notes.isEmpty())
        assertTrue(result.snapshot.cards.isEmpty())
        assertTrue(exchange.bodiesFor("findCards").isEmpty())
    }

    @Test
    fun listsNoteTypesWithTheirFields() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42,"Basic":7}""")
        exchange.onResult("modelFieldNames") { body ->
            val name = ((AnkiConnectJson.decode(body) as AnkiConnectJson.Json.Obj)
                .entries["params"] as AnkiConnectJson.Json.Obj)
                .entries["modelName"].let { (it as AnkiConnectJson.Json.Str).value }
            if (name == model) """["Expression","MainDefinition"]""" else """["Front","Back"]"""
        }

        val types = AnkiConnectCollectionReader(exchange.transport()).noteTypes()

        assertEquals(listOf(model, "Basic"), types.map { it.name })
        assertEquals(listOf(42L, 7L), types.map { it.modelId })
        assertEquals(listOf("Expression", "MainDefinition"), types[0].fields)
        assertEquals(listOf("Front", "Back"), types[1].fields)
    }

    @Test
    fun noteTypeListingFailsAsTransientOnMalformedFieldNames() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("modelFieldNames", """{"not":"an array"}""")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).noteTypes()
        }
        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun noteTypeListingFailsAsTransientOnMalformedModelList() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", "[]")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).noteTypes()
        }
        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun attachesTheApiKeyToEveryRequestWhenPresent() {
        val exchange = scriptedCollection(listOf(1L))
        exchange.onResult("cardsInfo", "[]")

        AnkiConnectCollectionReader(exchange.transport(), keyProvider = { "s3cret" }).read(settings)

        assertTrue(exchange.received.isNotEmpty())
        assertTrue(exchange.received.all { it.contains("\"key\":\"s3cret\"") })
    }

    @Test
    fun failsAsTransientWhenFindNotesReturnsANonArray() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", """{"nope":1}""")

        val failure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
    }

    @Test
    fun failsAsTransientWhenDetailReadsReturnNonArrays() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[1]")
        exchange.onResult("notesInfo", """{"nope":1}""")

        val notesFailure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, notesFailure.kind)

        exchange.onResult("notesInfo") {
            "[${ScriptedAnkiConnectExchange.noteRow(1, model, listOf(settings.expressionField to "x"))}]"
        }
        exchange.onResult("findCards", """{"nope":1}""")
        val cardsFailure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, cardsFailure.kind)

        exchange.onResult("findCards", "[10]")
        exchange.onResult("cardsInfo", """{"nope":1}""")
        val detailFailure = assertThrows(CollectionFailure::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertEquals(CollectionFailureKind.TRANSIENT, detailFailure.kind)
    }

    @Test
    fun rejectsAnOversizeCardIdResponse() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("findNotes", "[1]")
        exchange.onResult("notesInfo") {
            "[${ScriptedAnkiConnectExchange.noteRow(1, model, listOf(settings.expressionField to "x"))}]"
        }
        exchange.onResult("findCards") {
            (1..AnkiConnectReadPlanner.MAX_ID_COUNT + 1).joinToString(",", "[", "]")
        }

        assertThrows(AnkiConnectReadPlanner.OversizeIdResponseException::class.java) {
            AnkiConnectCollectionReader(exchange.transport()).read(settings)
        }
        assertTrue(exchange.bodiesFor("cardsInfo").isEmpty())
    }

    @Test
    fun readsAreRepeatableForAnUnchangedCollection() {
        val reader = { AnkiConnectCollectionReader(scriptedCollection(listOf(5L, 6L)).also {
            it.onResult("cardsInfo") { body ->
                requestedIds(body, "cards").joinToString(",", "[", "]") { cardId ->
                    ScriptedAnkiConnectExchange.cardRow(cardId, 5L, model)
                }
            }
        }.transport()) }

        val first = reader().read(settings).snapshot
        val second = reader().read(settings).snapshot

        assertEquals(first.notes.map { it.noteId }, second.notes.map { it.noteId })
        assertEquals(first.cards.map { it.cardId }, second.cards.map { it.cardId })
    }
}
