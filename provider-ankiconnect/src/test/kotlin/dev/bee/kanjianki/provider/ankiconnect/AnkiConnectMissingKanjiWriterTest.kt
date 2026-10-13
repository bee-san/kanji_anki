package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.ConfirmedMissingKanjiNote
import dev.bee.kanjianki.syncapi.MissingKanjiProgressListener
import dev.bee.kanjianki.syncapi.MissingKanjiReceiptSink
import dev.bee.kanjianki.syncapi.MissingKanjiWriteFailureKind
import dev.bee.kanjianki.syncapi.MissingKanjiWriteProgress
import dev.bee.kanjianki.syncapi.MissingKanjiWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectMissingKanjiWriterTest {
    private val deck = MissingKanjiExportPlanner.DEFAULT_DECK_NAME

    private fun writer(
        anki: FakeAnkiCollection,
        keyProvider: () -> String? = { null },
    ) = AnkiConnectMissingKanjiWriter(anki.transport(), keyProvider)

    private fun candidates(vararg literals: String): List<MissingKanjiCandidate> =
        literals.map { literal ->
            MissingKanjiCandidate(literal, meanings = listOf("meaning of $literal"))
        }

    /** Records every receipt the writer claimed, so a test can assert on them. */
    private class RecordingSink(private val succeed: Boolean = true) : MissingKanjiReceiptSink {
        val recorded = mutableListOf<Pair<String, List<ConfirmedMissingKanjiNote>>>()

        override fun record(destinationKey: String, notes: List<ConfirmedMissingKanjiNote>): Boolean {
            recorded += destinationKey to notes
            return succeed
        }

        fun literals(): List<String> = recorded.flatMap { it.second }.map(ConfirmedMissingKanjiNote::literal)
    }

    // ---- First write -------------------------------------------------------

    @Test
    fun createsTheDeckModelAndNotesOnAnEmptyCollection() {
        val anki = FakeAnkiCollection()
        val sink = RecordingSink()

        val result = writer(anki).export(candidates("橋", "山"), deck, receiptSink = sink)

        assertNull(result.failureKind)
        assertTrue(result.completed)
        assertEquals(setOf("橋", "山"), result.createdNotes.keys)
        assertTrue(result.alreadyPresentNotes.isEmpty())
        assertTrue(MissingKanjiExportPlanner.MODEL_NAME in anki.modelNames())
        assertTrue(deck in anki.deckNames())
        // Written in the planner's order, which is by Jiten rank then codepoint.
        assertEquals(
            listOf(MissingKanjiExportPlanner.sourceId("山"), MissingKanjiExportPlanner.sourceId("橋")),
            anki.exportedSourceIds(),
        )
        assertEquals(listOf("山", "橋"), sink.literals())
    }

    /** The deck name has `::`, so Anki has to create the parent too. */
    @Test
    fun createsTheParentDeckChain() {
        val anki = FakeAnkiCollection()

        writer(anki).export(candidates("橋"), deck)

        assertTrue("Kani" in anki.deckNames())
        assertTrue(deck in anki.deckNames())
    }

    @Test
    fun writesTheKaniModelShapeItLaterVerifies() {
        val anki = FakeAnkiCollection()

        writer(anki).export(candidates("橋"), deck)
        // A second run finds the model it just created and must accept it; any
        // mismatch between what createModel writes and what the shape proof
        // requires would surface as a MODEL_COLLISION here.
        val second = writer(anki).export(candidates("橋"), deck)

        assertNull(second.failureKind)
        assertEquals(1, anki.countOf("createModel"))
    }

    @Test
    fun tagsEveryCreatedNoteAndTouchesNoSchedulingField() {
        val anki = FakeAnkiCollection()

        writer(anki).export(candidates("橋"), deck)

        val addNotes = anki.bodies.single { it.contains("\"addNotes\"") }
        assertTrue(addNotes.contains(MissingKanjiExportPlanner.TAG))
        for (field in listOf("due", "queue", "ivl", "interval", "factor", "ease", "reps", "lapses")) {
            assertFalse(field, addNotes.contains("\"$field\""))
        }
    }

    @Test
    fun anEmptyPlanWritesNothingAtAll() {
        val anki = FakeAnkiCollection()

        val result = writer(anki).export(emptyList(), deck)

        assertNull(result.failureKind)
        assertTrue(result.completed)
        assertFalse(anki.log.contains("createModel"))
        assertFalse(anki.log.contains("addNotes"))
    }

    @Test
    fun invalidLiteralsAreReportedWithoutBlockingTheValidOnes() {
        val anki = FakeAnkiCollection()

        val result = writer(anki).export(candidates("橋", "not a kanji", "山"), deck)

        assertNull(result.failureKind)
        assertEquals(setOf("橋", "山"), result.createdNotes.keys)
        assertEquals(setOf("not a kanji"), result.invalidLiterals)
        assertEquals(1, result.invalidCount)
        assertEquals(3, result.requestedCount)
    }

    // ---- Idempotence and reconciliation -----------------------------------

    /**
     * The central claim. A note Kani already wrote is recognized by its `SourceId`
     * and never written twice, even though the writer consults no local state to
     * decide that.
     */
    @Test
    fun aSecondExportCreatesNoDuplicates() {
        val anki = FakeAnkiCollection().withKaniModel().withExportedNote("橋", 900L)
            .withDeck(deck)
        val sink = RecordingSink()

        val result = writer(anki).export(candidates("橋", "山"), deck, receiptSink = sink)

        assertNull(result.failureKind)
        assertEquals(mapOf("橋" to 900L), result.alreadyPresentNotes)
        assertEquals(setOf("山"), result.createdNotes.keys)
        assertEquals(2, anki.exportedNoteIds().size)
        assertEquals(listOf("橋", "山"), sink.literals())
    }

    /**
     * Running the same export three times leaves exactly one note per kanji. This
     * is the zero-duplicate guarantee stated as a loop rather than as a single
     * step, because the failure it guards against is cumulative.
     */
    @Test
    fun repeatedExportsConvergeOnOneNotePerKanji() {
        val anki = FakeAnkiCollection()

        repeat(3) { writer(anki).export(candidates("橋", "山"), deck) }

        assertEquals(2, anki.exportedNoteIds().size)
        assertEquals(
            setOf(MissingKanjiExportPlanner.sourceId("橋"), MissingKanjiExportPlanner.sourceId("山")),
            anki.exportedSourceIds().toSet(),
        )
    }

    /**
     * The reason reconciliation is unconditional. Anki commits the notes and *then*
     * the connection drops, so the client never learns what happened. A writer that
     * trusted the (absent) response would re-create both notes on the retry.
     */
    @Test
    fun aConnectionLossAfterAServerSideCommitStillReconcilesAndNeverDuplicates() {
        val anki = FakeAnkiCollection()
        anki.afterAddNotes = {
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("dropped")
        }
        val sink = RecordingSink()

        val lost = writer(anki).export(candidates("橋", "山"), deck, receiptSink = sink)

        // The write is reported as failed — but the notes it created are known.
        assertEquals(MissingKanjiWriteFailureKind.NOT_AVAILABLE, lost.failureKind)
        assertEquals(setOf("橋", "山"), lost.createdNotes.keys)
        assertTrue(lost.unfinishedLiterals.isEmpty())
        assertEquals(listOf("山", "橋"), sink.literals())

        anki.afterAddNotes = { null }
        val retry = writer(anki).export(candidates("橋", "山"), deck)

        assertNull(retry.failureKind)
        assertEquals(setOf("橋", "山"), retry.alreadyPresentNotes.keys)
        assertEquals(2, anki.exportedNoteIds().size)
    }

    @Test
    fun aTimeoutAfterACommitIsReconciledTheSameWay() {
        val anki = FakeAnkiCollection()
        anki.afterAddNotes = { AnkiConnectTransport.HttpExchange.Result.Timeout }

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.NOT_AVAILABLE, result.failureKind)
        assertEquals(setOf("橋"), result.createdNotes.keys)
        assertEquals(1, anki.exportedNoteIds().size)
    }

    /**
     * A local receipt alone may never suppress a write. This is the restore/rebind
     * case: the caller's receipts say the note exists, but the collection says
     * otherwise, and the collection wins.
     */
    @Test
    fun aLocalReceiptCannotSuppressAWriteTheCollectionDoesNotHave() {
        val anki = FakeAnkiCollection().withKaniModel().withDeck(deck)
        val sink = RecordingSink()

        val result = writer(anki).export(candidates("橋"), deck, receiptSink = sink)

        assertNull(result.failureKind)
        assertEquals(setOf("橋"), result.createdNotes.keys)
        assertTrue(result.alreadyPresentNotes.isEmpty())
        assertEquals(1, anki.exportedNoteIds().size)
    }

    /**
     * `addNotes` is not batch-atomic. A mixed array — one id, one null — is a
     * partial result, and the notes that did land are reported as created while the
     * batch itself is INCOMPLETE_WRITE so the caller can offer CSV for the rest.
     */
    @Test
    fun aMixedAddNotesResponseIsAPartialResult() {
        val anki = FakeAnkiCollection()
        // Position 1 in the planner's order is 橋; Anki refuses just that one.
        anki.addOutcome = { index ->
            if (index == 1) FakeAnkiCollection.AddOutcome.REFUSED else FakeAnkiCollection.AddOutcome.CREATED
        }

        val result = writer(anki).export(candidates("橋", "山"), deck)

        assertEquals(MissingKanjiWriteFailureKind.INCOMPLETE_WRITE, result.failureKind)
        assertEquals(setOf("山"), result.createdNotes.keys)
        assertEquals(setOf("橋"), result.unfinishedLiterals)
        assertFalse(result.completed)
    }

    /**
     * The case only reconciliation can catch: Anki created the note but reported
     * null for it. The response says nothing landed; the collection says otherwise,
     * and the note is correctly reported as created so a retry will not duplicate
     * it.
     */
    @Test
    fun aNoteReportedNullButActuallyCreatedIsFoundByReconciliation() {
        val anki = FakeAnkiCollection()
        anki.addOutcome = { FakeAnkiCollection.AddOutcome.CREATED_REPORTED_NULL }
        val sink = RecordingSink()

        val result = writer(anki).export(candidates("橋"), deck, receiptSink = sink)

        assertEquals(MissingKanjiWriteFailureKind.INCOMPLETE_WRITE, result.failureKind)
        assertEquals(setOf("橋"), result.createdNotes.keys)
        assertEquals(listOf("橋"), sink.literals())
        assertEquals(1, anki.exportedNoteIds().size)

        val retry = writer(anki).export(candidates("橋"), deck)
        assertEquals(setOf("橋"), retry.alreadyPresentNotes.keys)
        assertEquals(1, anki.exportedNoteIds().size)
    }

    /** An entry that is not a note id at all cannot be attributed; reconcile instead. */
    @Test
    fun anAddNotesEntryOfTheWrongTypeIsTreatedAsUnknownNotSuccess() {
        val anki = FakeAnkiCollection()
        anki.addOutcome = { FakeAnkiCollection.AddOutcome.CREATED_REPORTED_GARBAGE }

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.INCOMPLETE_WRITE, result.failureKind)
        // Reconciliation still found it, so a retry will not duplicate it.
        assertEquals(setOf("橋"), result.createdNotes.keys)
    }

    /** A response array of the wrong length is unattributable, not a success. */
    @Test
    fun anAddNotesArrayOfTheWrongLengthIsNotTrusted() {
        val anki = FakeAnkiCollection().on("addNotes", FakeAnkiCollection.Reply.Result("[123]"))

        val result = writer(anki).export(candidates("橋", "山"), deck)

        assertEquals(MissingKanjiWriteFailureKind.INCOMPLETE_WRITE, result.failureKind)
        assertEquals(setOf("橋", "山"), result.unfinishedLiterals)
    }

    /** A non-array `addNotes` result is a protocol shape Kani will not interpret. */
    @Test
    fun anAddNotesResultThatIsNotAnArrayIsNotTrusted() {
        val anki = FakeAnkiCollection().on("addNotes", FakeAnkiCollection.Reply.Result("\"ok\""))

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.INCOMPLETE_WRITE, result.failureKind)
        assertEquals(setOf("橋"), result.unfinishedLiterals)
    }

    // ---- Batching ---------------------------------------------------------

    @Test
    fun writesInBatchesOfAtMostOneHundred() {
        val anki = FakeAnkiCollection()
        // 240 distinct CJK literals.
        val literals = (0 until 240).map { String(Character.toChars(0x4E00 + it)) }

        val result = writer(anki).export(candidates(*literals.toTypedArray()), deck)

        assertNull(result.failureKind)
        assertEquals(240, result.createdNotes.size)
        assertEquals(3, anki.countOf("addNotes"))
        assertEquals(240, anki.exportedNoteIds().size)
    }

    @Test
    fun everyAddNotesBatchIsWithinTheCap() {
        val anki = FakeAnkiCollection()
        val literals = (0 until 150).map { String(Character.toChars(0x4E00 + it)) }

        writer(anki).export(candidates(*literals.toTypedArray()), deck)

        val sizes = anki.bodies
            .filter { it.contains("\"addNotes\"") }
            .map { body -> Regex("\"deckName\"").findAll(body).count() }
        assertEquals(listOf(100, 50), sizes)
        assertTrue(sizes.all { it <= AnkiConnectRequests.MAX_ADD_NOTES })
    }

    /** A failed batch stops the run; the untouched literals stay unfinished. */
    @Test
    fun aFailedBatchStopsBeforeTheNextOne() {
        val anki = FakeAnkiCollection()
        val literals = (0 until 150).map { String(Character.toChars(0x4E00 + it)) }
        anki.on("addNotes", FakeAnkiCollection.Reply.Error("collection is closed"))

        val result = writer(anki).export(candidates(*literals.toTypedArray()), deck)

        assertEquals(MissingKanjiWriteFailureKind.TRANSIENT, result.failureKind)
        assertEquals(1, anki.countOf("addNotes"))
        assertEquals(150, result.unfinishedLiterals.size)
    }

    // ---- Collision --------------------------------------------------------

    /** Someone else's model shares the name. Kani refuses; it does not edit it. */
    @Test
    fun aModelWithTheSameNameButDifferentFieldsIsACollision() {
        val anki = FakeAnkiCollection().withModel(
            name = MissingKanjiExportPlanner.MODEL_NAME,
            fields = listOf("Front", "Back"),
        )

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.MODEL_COLLISION, result.failureKind)
        assertFalse(anki.log.contains("createModel"))
        assertFalse(anki.log.contains("addNotes"))
        assertEquals(setOf("橋"), result.unfinishedLiterals)
    }

    @Test
    fun aModelWithReorderedFieldsIsACollision() {
        val anki = FakeAnkiCollection().withModel(
            name = MissingKanjiExportPlanner.MODEL_NAME,
            fields = MissingKanjiExportPlanner.FIELD_NAMES.reversed(),
        )

        assertEquals(
            MissingKanjiWriteFailureKind.MODEL_COLLISION,
            writer(anki).export(candidates("橋"), deck).failureKind,
        )
    }

    @Test
    fun aModelWithDifferentCssIsACollision() {
        val anki = FakeAnkiCollection().withModel(
            name = MissingKanjiExportPlanner.MODEL_NAME,
            css = ".card { color: red; }",
        )

        assertEquals(
            MissingKanjiWriteFailureKind.MODEL_COLLISION,
            writer(anki).export(candidates("橋"), deck).failureKind,
        )
    }

    @Test
    fun aModelWithADifferentTemplateNameIsACollision() {
        val anki = FakeAnkiCollection().withModel(
            name = MissingKanjiExportPlanner.MODEL_NAME,
            templates = mapOf(
                "Card 1" to (
                    MissingKanjiExportPlanner.QUESTION_FORMAT to MissingKanjiExportPlanner.ANSWER_FORMAT
                    ),
            ),
        )

        assertEquals(
            MissingKanjiWriteFailureKind.MODEL_COLLISION,
            writer(anki).export(candidates("橋"), deck).failureKind,
        )
    }

    @Test
    fun aModelWithARewordedQuestionIsACollision() {
        val anki = FakeAnkiCollection().withModel(
            name = MissingKanjiExportPlanner.MODEL_NAME,
            templates = mapOf(
                MissingKanjiExportPlanner.TEMPLATE_NAME to
                    ("{{Kanji}}" to MissingKanjiExportPlanner.ANSWER_FORMAT),
            ),
        )

        assertEquals(
            MissingKanjiWriteFailureKind.MODEL_COLLISION,
            writer(anki).export(candidates("橋"), deck).failureKind,
        )
    }

    /** An extra template means the user added a card type; Kani will not write there. */
    @Test
    fun aModelWithAnExtraTemplateIsACollision() {
        val anki = FakeAnkiCollection().withModel(
            name = MissingKanjiExportPlanner.MODEL_NAME,
            templates = mapOf(
                MissingKanjiExportPlanner.TEMPLATE_NAME to (
                    MissingKanjiExportPlanner.QUESTION_FORMAT to MissingKanjiExportPlanner.ANSWER_FORMAT
                    ),
                "Reverse" to ("{{Meaning}}" to "{{Kanji}}"),
            ),
        )

        assertEquals(
            MissingKanjiWriteFailureKind.MODEL_COLLISION,
            writer(anki).export(candidates("橋"), deck).failureKind,
        )
    }

    /**
     * A filtered deck of that name is not a target. AnkiConnect exposes no
     * filtered-deck flag, so this is proved by `getDeckConfig` failing — a filtered
     * deck has no options group.
     */
    @Test
    fun aFilteredDeckWithTheTargetNameIsACollision() {
        val anki = FakeAnkiCollection().withDeck(deck, filtered = true)

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.DECK_COLLISION, result.failureKind)
        assertFalse(anki.log.contains("addNotes"))
        assertEquals(setOf("橋"), result.unfinishedLiterals)
    }

    @Test
    fun anOrdinaryPreexistingDeckIsReusedNotRecreated() {
        val anki = FakeAnkiCollection().withDeck(deck)

        val result = writer(anki).export(candidates("橋"), deck)

        assertNull(result.failureKind)
        assertFalse(anki.log.contains("createDeck"))
    }

    /**
     * A note in Kani's own verified model whose fields do not match means the
     * destination changed underneath the run. Refuse rather than write into it.
     */
    @Test
    fun aNoteWithDriftedFieldsInKanisModelIsACollision() {
        val anki = FakeAnkiCollection().withKaniModel().withDeck(deck)
            .withRawNote(901L, MissingKanjiExportPlanner.MODEL_NAME, mapOf("Front" to "橋"))

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.MODEL_COLLISION, result.failureKind)
        assertFalse(anki.log.contains("addNotes"))
    }

    /** A note from an unrelated model is simply not Kani's and is ignored. */
    @Test
    fun notesInOtherModelsAreNeverConsidered() {
        val anki = FakeAnkiCollection().withKaniModel().withDeck(deck)
            .withModel("Someone Else", id = 7L)
            .withRawNote(902L, "Someone Else", mapOf("SourceId" to MissingKanjiExportPlanner.sourceId("橋")))

        val result = writer(anki).export(candidates("橋"), deck)

        assertNull(result.failureKind)
        assertEquals(setOf("橋"), result.createdNotes.keys)
    }

    // ---- Availability, auth loss, capability ------------------------------

    @Test
    fun statusAdvertisesTheWriteOnlyWhenEveryNeededActionIsPresent() {
        val anki = FakeAnkiCollection()

        val status = writer(anki).status()

        assertEquals(CollectionAvailability.READY, status.availability)
        assertTrue(status.supports(CollectionCapability.MISSING_KANJI_WRITE))
    }

    /**
     * Each needed action, withheld one at a time. The read actions matter as much as
     * the write ones: without them Kani cannot prove the destination is its own, so
     * advertising the capability would hide the CSV path behind a write it must
     * refuse anyway.
     */
    @Test
    fun statusWithholdsTheWriteWhenAnyNeededActionIsMissing() {
        for (withheld in AnkiConnectMissingKanjiWriter.REQUIRED_WRITE_ACTIONS) {
            val anki = FakeAnkiCollection()
            anki.availableActions = AnkiConnectActions.optional - withheld

            val status = writer(anki).status()

            assertEquals(withheld, CollectionAvailability.READY, status.availability)
            assertFalse(withheld, status.supports(CollectionCapability.MISSING_KANJI_WRITE))
            assertTrue(withheld, status.message.contains(withheld))
        }
    }

    @Test
    fun anExportRefusesWhenANeededActionIsMissing() {
        for (withheld in AnkiConnectMissingKanjiWriter.REQUIRED_WRITE_ACTIONS) {
            val anki = FakeAnkiCollection()
            anki.availableActions = AnkiConnectActions.optional - withheld

            val result = writer(anki).export(candidates("橋"), deck)

            assertEquals(
                withheld,
                MissingKanjiWriteFailureKind.UNSUPPORTED_CAPABILITY,
                result.failureKind,
            )
            assertEquals(withheld, setOf("橋"), result.unfinishedLiterals)
            assertFalse(withheld, anki.log.contains("addNotes"))
        }
    }

    @Test
    fun aDeniedPermissionIsAuthRequired() {
        val anki = FakeAnkiCollection()
            .on("requestPermission", FakeAnkiCollection.Reply.Result("""{"permission":"denied"}"""))

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.AUTH_REQUIRED, result.failureKind)
        assertEquals(setOf("橋"), result.unfinishedLiterals)
        assertEquals(CollectionAvailability.AUTH_REQUIRED, writer(anki).status().availability)
    }

    /** Auth lost mid-write: refused, not retried forever. */
    @Test
    fun anApiKeyRejectedMidWriteIsAuthRequired() {
        val anki = FakeAnkiCollection()
            .on("addNotes", FakeAnkiCollection.Reply.Error("valid api key must be provided"))

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.AUTH_REQUIRED, result.failureKind)
        assertEquals(setOf("橋"), result.unfinishedLiterals)
    }

    @Test
    fun anUnreachableAnkiIsNotAvailable() {
        val anki = FakeAnkiCollection().on(
            "requestPermission",
            FakeAnkiCollection.Reply.Wire(
                AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("refused"),
            ),
        )

        assertEquals(
            MissingKanjiWriteFailureKind.NOT_AVAILABLE,
            writer(anki).export(candidates("橋"), deck).failureKind,
        )
    }

    @Test
    fun noOpenCollectionIsNotAWritableDestination() {
        val anki = FakeAnkiCollection()
        anki.profileIdentity = ""

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.UNSUPPORTED_CAPABILITY, result.failureKind)
        assertNull(result.destinationKey)
    }

    @Test
    fun aWrongWireVersionIsRefusedBeforeAnyWrite() {
        val anki = FakeAnkiCollection().on("version", FakeAnkiCollection.Reply.Result("5"))

        val result = writer(anki).export(candidates("橋"), deck)

        assertEquals(MissingKanjiWriteFailureKind.UNSUPPORTED_CAPABILITY, result.failureKind)
        assertFalse(anki.log.contains("addNotes"))
    }

    @Test
    fun aBlankDeckNameIsRefusedBeforeTheHandshake() {
        val anki = FakeAnkiCollection()

        for (name in listOf("", "   ")) {
            val result = writer(anki).export(candidates("橋"), name)

            assertEquals(name, MissingKanjiWriteFailureKind.INVALID_DECK_NAME, result.failureKind)
        }
        assertTrue(anki.log.isEmpty())
    }

    // ---- Cancellation ----------------------------------------------------

    @Test
    fun cancellationBeforeAnythingWritesNothing() {
        val anki = FakeAnkiCollection()

        val result = writer(anki).export(
            candidates("橋"),
            deck,
            cancellation = CollectionCancellation { true },
        )

        assertEquals(MissingKanjiWriteFailureKind.CANCELLED, result.failureKind)
        assertTrue(anki.log.isEmpty())
    }

    /**
     * Cancellation between batches keeps the first batch's confirmed notes, because
     * they really were created; the rest stay unfinished for the retry.
     */
    @Test
    fun cancellationBetweenBatchesKeepsWhatWasAlreadyWritten() {
        val anki = FakeAnkiCollection()
        val literals = (0 until 150).map { String(Character.toChars(0x4E00 + it)) }
        var addNotesSeen = 0
        anki.afterAddNotes = {
            addNotesSeen++
            null
        }

        val result = writer(anki).export(
            candidates(*literals.toTypedArray()),
            deck,
            cancellation = CollectionCancellation { addNotesSeen > 0 },
        )

        assertEquals(MissingKanjiWriteFailureKind.CANCELLED, result.failureKind)
        assertEquals(1, anki.countOf("addNotes"))
        assertEquals(100, result.createdNotes.size)
        assertEquals(50, result.unfinishedLiterals.size)
    }

    // ---- Receipts and destination key ------------------------------------

    @Test
    fun aReceiptFailureStopsTheRunAndIsReported() {
        val anki = FakeAnkiCollection()

        val result = writer(anki).export(
            candidates("橋"),
            deck,
            receiptSink = RecordingSink(succeed = false),
        )

        assertEquals(MissingKanjiWriteFailureKind.RECEIPT_PERSISTENCE, result.failureKind)
    }

    @Test
    fun aThrowingReceiptSinkIsAFailureNotACrash() {
        val anki = FakeAnkiCollection()
        val throwing = MissingKanjiReceiptSink { _, _ -> throw IllegalStateException("disk full") }

        val result = writer(anki).export(candidates("橋"), deck, receiptSink = throwing)

        assertEquals(MissingKanjiWriteFailureKind.RECEIPT_PERSISTENCE, result.failureKind)
    }

    /**
     * The destination key binds to the profile, not just the endpoint. Every profile
     * on a machine answers on the same loopback port, so an endpoint-only key would
     * let receipts earned against one profile suppress writes against another.
     */
    @Test
    fun theDestinationKeyChangesWithTheProfile() {
        val first = FakeAnkiCollection()
        val firstKey = writer(first).export(candidates("橋"), deck).destinationKey

        val second = FakeAnkiCollection()
        second.profileIdentity = "Someone Else"
        val secondKey = writer(second).export(candidates("橋"), deck).destinationKey

        assertTrue(firstKey!!.startsWith("ankiconnect:"))
        val keysDiffer = firstKey == secondKey
        assertFalse(keysDiffer)
    }

    /** A recreated model is a new destination, so old receipts cannot suppress it. */
    @Test
    fun theDestinationKeyChangesWithTheModelId() {
        val first = FakeAnkiCollection().withKaniModel(id = 500L).withDeck(deck)
        val second = FakeAnkiCollection().withKaniModel(id = 501L).withDeck(deck)

        val firstKey = writer(first).export(candidates("橋"), deck).destinationKey
        val secondKey = writer(second).export(candidates("橋"), deck).destinationKey

        assertEquals("ankiconnect:", firstKey!!.substringBefore(':') + ":")
        val keysDiffer = firstKey == secondKey
        assertFalse(keysDiffer)
    }

    /** Neither the endpoint nor the profile name appears in the key in the clear. */
    @Test
    fun theDestinationKeyDoesNotLeakTheEndpointOrProfileName() {
        val anki = FakeAnkiCollection()
        anki.profileIdentity = "Autumn's Japanese"

        val key = writer(anki).export(candidates("橋"), deck).destinationKey

        assertFalse(key!!.contains("Autumn"))
        assertFalse(key.contains("127.0.0.1"))
        assertFalse(key.contains("8765"))
    }

    @Test
    fun theSameProfileAndModelAlwaysProduceTheSameKey() {
        val anki = FakeAnkiCollection()

        val first = writer(anki).export(candidates("橋"), deck).destinationKey
        val second = writer(anki).export(candidates("山"), deck).destinationKey

        assertEquals(first, second)
    }

    // ---- Progress and API key --------------------------------------------

    @Test
    fun progressReportsMonotonicProcessedCounts() {
        val anki = FakeAnkiCollection()
        val seen = mutableListOf<MissingKanjiWriteProgress>()

        writer(anki).export(
            candidates("橋", "山"),
            deck,
            progress = MissingKanjiProgressListener { seen += it },
        )

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it.totalCount == 2 })
        assertEquals(seen.map(MissingKanjiWriteProgress::processedCount).sorted(), seen.map { it.processedCount })
        assertEquals(2, seen.last().processedCount)
        assertEquals(2, seen.last().createdCount)
    }

    @Test
    fun forwardsTheApiKeyOnEveryActionExceptTheKeylessPermissionProbe() {
        val anki = FakeAnkiCollection()

        writer(anki, keyProvider = { "s3cret" }).export(candidates("橋"), deck)

        anki.bodies.forEachIndexed { index, body ->
            val action = anki.log[index]
            if (action == "requestPermission") {
                assertFalse(action, body.contains("s3cret"))
            } else {
                assertTrue(action, body.contains("s3cret"))
            }
        }
    }

    // ---- Write surface ---------------------------------------------------

    /**
     * The deny-list proof at the writer level: across a full successful export, the
     * only actions that reach the wire are the allowlisted ones this flow declares,
     * and none of them can change scheduling state.
     */
    @Test
    fun sendsOnlyTheActionsThisFlowDeclares() {
        val anki = FakeAnkiCollection()

        writer(anki).export(candidates("橋"), deck)

        val expected = AnkiConnectMissingKanjiWriter.REQUIRED_WRITE_ACTIONS +
            setOf("requestPermission", "version", "apiReflect", "getMediaDirPath") +
            setOf("modelNamesAndIds", "modelFieldNames", "deckNamesAndIds", "findNotes", "notesInfo")
        val unexpected = anki.log.toSet() - expected
        assertEquals(emptySet<String>(), unexpected)
        assertTrue(anki.log.all(AnkiConnectActions::isAllowed))
    }

    /** The result type's own contract: unfinished work is never "completed". */
    @Test
    fun anUnfinishedResultIsNeverReportedComplete() {
        val result = MissingKanjiWriteResult(
            requestedCount = 1,
            validCount = 1,
            createdNotes = emptyMap(),
            alreadyPresentNotes = emptyMap(),
            invalidLiterals = emptySet(),
            invalidCount = 0,
            duplicateRequestCount = 0,
            unfinishedLiterals = setOf("橋"),
            destinationKey = null,
            failureKind = null,
        )

        assertFalse(result.completed)
    }
}
