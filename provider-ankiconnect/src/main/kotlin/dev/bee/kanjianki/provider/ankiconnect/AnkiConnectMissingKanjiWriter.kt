package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionSourceStatus
import dev.bee.kanjianki.syncapi.ConfirmedMissingKanjiNote
import dev.bee.kanjianki.syncapi.MissingKanjiProgressListener
import dev.bee.kanjianki.syncapi.MissingKanjiReceiptSink
import dev.bee.kanjianki.syncapi.MissingKanjiWriteFailureKind
import dev.bee.kanjianki.syncapi.MissingKanjiWriteProgress
import dev.bee.kanjianki.syncapi.MissingKanjiWriteResult
import dev.bee.kanjianki.syncapi.MissingKanjiWriter
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

/**
 * The only non-tag provider write Kani performs on the desktop: creating notes in
 * its own dedicated deck and note type for kanji the user has no card for. It is
 * additive and idempotent, and it never touches an existing note, model, deck
 * configuration, or any scheduling field.
 *
 * Five properties are load-bearing, and each one is a way this could quietly
 * corrupt a user's collection:
 *
 * - **Reconciliation is the source of truth, not the write's own answer.**
 *   `addNotes` is not batch-atomic and its response is not always available: a
 *   timeout, a cancellation, or a dropped connection leaves Kani genuinely unable
 *   to say which notes Anki committed. So after *every* outcome — success, mixed
 *   result, AnkiConnect error, protocol error, timeout, cancellation, connection
 *   loss — the whole intended batch is re-read back by its stable `SourceId`
 *   before a single receipt is recorded. The response is still inspected entry by
 *   entry, but only to decide whether the batch *completed*; which notes exist is
 *   always answered by the collection. This also means a local receipt can never
 *   suppress a write on its own: a restored database or a rebound profile
 *   reconciles against the provider first and re-creates whatever is genuinely
 *   missing.
 * - **A collision is never rewritten.** An existing `Kani Missing Kanji` model is
 *   reused only when its field list, CSS, and single card template are byte-exact
 *   matches for what Kani would have created; anything else is another tool's or
 *   the user's model that merely shares a name, and Kani refuses rather than
 *   editing it. The same holds for the deck: a filtered deck of that name is a
 *   refusal, not a target.
 * - **Unprovable means refused.** Proving the shape needs `modelTemplates`,
 *   `modelStyling`, and — because AnkiConnect exposes no filtered-deck flag at all
 *   — `getDeckConfig`, which fails on a filtered deck precisely because a filtered
 *   deck has no options group. An AnkiConnect that withholds any of them cannot
 *   prove compatibility, so this writer reports the capability as absent and the
 *   user exports CSV instead of Kani assuming the destination is safe.
 * - **`destination_key` binds to the source, not just the endpoint.** Every Anki
 *   profile on a machine answers on the same loopback port, so an endpoint-only
 *   key would let receipts earned against one profile suppress writes against
 *   another. The key digests [AnkiConnectSourceKey]'s endpoint+profile binding and
 *   pins the model id, so a recreated model or a switched profile is a different
 *   destination.
 * - **CSV stays the fallback for every refusal.** Unsupported actions, a
 *   collision, auth loss, and an unfinished write all come back as a typed
 *   [MissingKanjiWriteFailureKind] with the unfinished literals named, so the
 *   caller can offer the CSV path for exactly the kanji that did not land.
 */
class AnkiConnectMissingKanjiWriter(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
) : MissingKanjiWriter {
    private val handshake = AnkiConnectHandshake(transport)

    /**
     * Whether this Anki can host the Missing Kanji flow. Reports
     * [CollectionCapability.MISSING_KANJI_WRITE] only when the handshake is ready
     * *and* every action needed both to write and to prove the destination's shape
     * was reported by `apiReflect` — a declared-but-absent write is worse than an
     * undeclared one, because the caller would hide the CSV path behind it.
     */
    override fun status(): CollectionSourceStatus {
        val result = handshake.run(keyProvider())
        val ready = result as? AnkiConnectHandshake.Status.Ready
            ?: return CollectionSourceStatus(
                AnkiConnectStatusMapping.availabilityFor(result),
                emptySet(),
                AnkiConnectStatusMapping.messageFor(result),
            )
        val missing = missingWriteActions(ready)
        if (missing.isEmpty()) {
            return CollectionSourceStatus(
                CollectionAvailability.READY,
                setOf(CollectionCapability.MISSING_KANJI_WRITE),
                AnkiConnectStatusMapping.messageFor(ready),
            )
        }
        return CollectionSourceStatus(
            CollectionAvailability.READY,
            emptySet(),
            "This AnkiConnect cannot create Kani's Missing Kanji deck safely " +
                "(missing ${missing.sorted().joinToString(", ")}). Export a CSV instead.",
        )
    }

    override fun export(
        candidates: Iterable<MissingKanjiCandidate>,
        deckName: String,
        progress: MissingKanjiProgressListener,
        receiptSink: MissingKanjiReceiptSink,
        cancellation: CollectionCancellation,
    ): MissingKanjiWriteResult {
        val plan = MissingKanjiExportPlanner.plan(candidates)
        val state = ExportState(plan)
        return try {
            exportChecked(plan, state, deckName.trim(), progress, receiptSink, cancellation)
        } catch (abort: WriteAbort) {
            state.result(abort.kind)
        }
    }

    @Suppress("ReturnCount")
    private fun exportChecked(
        plan: MissingKanjiExportPlanner.Plan,
        state: ExportState,
        deckName: String,
        progress: MissingKanjiProgressListener,
        receiptSink: MissingKanjiReceiptSink,
        cancellation: CollectionCancellation,
    ): MissingKanjiWriteResult {
        if (deckName.isEmpty()) return state.result(MissingKanjiWriteFailureKind.INVALID_DECK_NAME)
        if (cancellation.isCancelled()) return state.result(MissingKanjiWriteFailureKind.CANCELLED)

        val handshakeResult = handshake.run(keyProvider())
        val ready = handshakeResult as? AnkiConnectHandshake.Status.Ready
            ?: return state.result(availabilityFailure(handshakeResult))
        if (missingWriteActions(ready).isNotEmpty()) {
            return state.result(MissingKanjiWriteFailureKind.UNSUPPORTED_CAPABILITY)
        }
        val profile = ready.profileIdentity?.takeIf(String::isNotBlank)
            ?: return state.result(MissingKanjiWriteFailureKind.NOT_AVAILABLE)

        progress.onProgress(state.progress())
        if (plan.notes.isEmpty()) return state.result()

        val session = Session(keyProvider(), deckName, cancellation)
        val modelId = ensureModel(session)
        val destinationKey = destinationKey(
            AnkiConnectSourceKey.of(transport.endpointUrl(), profile),
            modelId,
        )
        state.destinationKey = destinationKey
        checkCancellation(session)

        val existing = queryExportedSourceIds(session)
        val plannedBySource = plan.notes.associateBy(MissingKanjiExportPlanner.ExportNote::sourceId)
        val alreadyPresent = ArrayList<ConfirmedMissingKanjiNote>()
        for ((sourceId, noteId) in existing) {
            val note = plannedBySource[sourceId] ?: continue
            state.alreadyPresent[note.literal] = noteId
            state.unfinished.remove(note.literal)
            alreadyPresent.add(ConfirmedMissingKanjiNote(note.literal, noteId))
        }
        if (!recordReceipts(receiptSink, destinationKey, alreadyPresent)) {
            return state.result(MissingKanjiWriteFailureKind.RECEIPT_PERSISTENCE)
        }
        progress.onProgress(state.progress())

        val pending = plan.notes.filter { note -> note.literal in state.unfinished }
        if (pending.isEmpty()) return state.result()

        checkCancellation(session)
        ensureDeck(session)
        for (batch in pending.chunked(AnkiConnectRequests.MAX_ADD_NOTES)) {
            if (cancellation.isCancelled()) {
                return state.result(MissingKanjiWriteFailureKind.CANCELLED)
            }
            val failure = writeAndReconcileBatch(
                session = session,
                batch = batch,
                previouslyExisting = existing,
                destinationKey = destinationKey,
                state = state,
                receiptSink = receiptSink,
            )
            progress.onProgress(state.progress())
            if (failure != null) return state.result(failure)
        }
        return state.result()
    }

    /**
     * Sends one `addNotes` batch and then reconciles the whole batch by `SourceId`,
     * whatever the send did.
     *
     * The reconciliation is unconditional on purpose. If the connection dropped
     * after Anki committed the notes but before the response arrived, the only
     * honest answer to "what exists now?" comes from re-reading the collection; a
     * client that trusted the missing response would create every note a second
     * time on the retry.
     */
    private fun writeAndReconcileBatch(
        session: Session,
        batch: List<MissingKanjiExportPlanner.ExportNote>,
        previouslyExisting: MutableMap<String, Long>,
        destinationKey: String,
        state: ExportState,
        receiptSink: MissingKanjiReceiptSink,
    ): MissingKanjiWriteFailureKind? {
        val request = AnkiConnectRequests.addNotes(
            batch.map { note -> newNote(session.deckName, note) },
            session.key,
        )
        var sendFailure: MissingKanjiWriteFailureKind? = null
        val reportedCount = when (val outcome = send(request)) {
            is Sent.Ok -> addedNoteCount(outcome.result, batch.size)
            is Sent.Problem -> {
                sendFailure = outcome.kind
                UNKNOWN_COUNT
            }
        }

        // Reconcile before receipts, and before deciding anything about the batch.
        // This read deliberately ignores cancellation: the notes are already in the
        // collection, and abandoning the read here would lose the only record of
        // which ones, so the next run would write them again.
        val reconciled = queryExportedSourceIds(session, honorCancellation = false)
        val confirmed = ArrayList<ConfirmedMissingKanjiNote>()
        for (note in batch) {
            val noteId = reconciled[note.sourceId] ?: continue
            if (!previouslyExisting.containsKey(note.sourceId)) {
                state.created[note.literal] = noteId
                confirmed.add(ConfirmedMissingKanjiNote(note.literal, noteId))
            }
            state.unfinished.remove(note.literal)
        }
        previouslyExisting.putAll(reconciled)
        if (!recordReceipts(receiptSink, destinationKey, confirmed)) {
            return MissingKanjiWriteFailureKind.RECEIPT_PERSISTENCE
        }
        if (sendFailure != null) return sendFailure
        return if (reportedCount != batch.size || confirmed.size != batch.size) {
            MissingKanjiWriteFailureKind.INCOMPLETE_WRITE
        } else {
            null
        }
    }

    /**
     * The id of a `Kani Missing Kanji` model Kani may write to, creating it when it
     * does not exist. Throws [WriteAbort] with
     * [MissingKanjiWriteFailureKind.MODEL_COLLISION] when a model of that name
     * exists but is not byte-exactly Kani's.
     */
    private fun ensureModel(session: Session): Long {
        val existingId = modelId(session)
        if (existingId != null) {
            if (!modelShapeMatches(session)) {
                throw WriteAbort(MissingKanjiWriteFailureKind.MODEL_COLLISION)
            }
            return existingId
        }
        checkCancellation(session)
        expectOk(
            AnkiConnectRequests.createModel(
                modelName = MissingKanjiExportPlanner.MODEL_NAME,
                fieldNames = MissingKanjiExportPlanner.FIELD_NAMES,
                css = MissingKanjiExportPlanner.CSS,
                templates = listOf(
                    AnkiConnectRequests.CardTemplate(
                        name = MissingKanjiExportPlanner.TEMPLATE_NAME,
                        front = MissingKanjiExportPlanner.QUESTION_FORMAT,
                        back = MissingKanjiExportPlanner.ANSWER_FORMAT,
                    ),
                ),
                apiKey = session.key,
            ),
        )
        // Read back rather than trust the create: the shape Kani will write into
        // has to be the shape Kani verified, even on its own model.
        val createdId = modelId(session)
            ?: throw WriteAbort(MissingKanjiWriteFailureKind.MODEL_COLLISION)
        if (!modelShapeMatches(session)) {
            throw WriteAbort(MissingKanjiWriteFailureKind.MODEL_COLLISION)
        }
        return createdId
    }

    /** The id of the Missing Kanji model, or null when no model has that name. */
    private fun modelId(session: Session): Long? {
        val names = AnkiConnectReads.namesAndIds(
            expectOk(AnkiConnectRequests.modelNamesAndIds(session.key)),
        ) ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        val id = names[MissingKanjiExportPlanner.MODEL_NAME] ?: return null
        // A model Kani cannot address is a collision, not a model to create over.
        if (id <= 0L) throw WriteAbort(MissingKanjiWriteFailureKind.MODEL_COLLISION)
        return id
    }

    /**
     * Whether the existing model's fields, CSS, and templates are byte-exactly what
     * Kani would have created. Anything else — an extra template, a reworded
     * question, a reordered field list — means the name belongs to someone else's
     * note type.
     */
    private fun modelShapeMatches(session: Session): Boolean {
        val modelName = MissingKanjiExportPlanner.MODEL_NAME
        val fields = AnkiConnectReads.fieldNames(
            expectOk(AnkiConnectRequests.modelFieldNames(modelName, session.key)),
        ) ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        if (fields != MissingKanjiExportPlanner.FIELD_NAMES) return false
        checkCancellation(session)

        val styling = expectOk(AnkiConnectRequests.modelStyling(modelName, session.key))
        val css = ((styling as? Json.Obj)?.entries?.get("css") as? Json.Str)?.value
            ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        if (css != MissingKanjiExportPlanner.CSS) return false
        checkCancellation(session)

        val templates = (expectOk(AnkiConnectRequests.modelTemplates(modelName, session.key)) as? Json.Obj)
            ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        val template = templates.entries[MissingKanjiExportPlanner.TEMPLATE_NAME] as? Json.Obj
        return templates.entries.size == 1 &&
            template != null &&
            (template.entries["Front"] as? Json.Str)?.value == MissingKanjiExportPlanner.QUESTION_FORMAT &&
            (template.entries["Back"] as? Json.Str)?.value == MissingKanjiExportPlanner.ANSWER_FORMAT
    }

    /**
     * Makes sure Kani's deck exists and is an ordinary deck it may add cards to.
     *
     * AnkiConnect reports no filtered-deck flag, so an existing deck is proved
     * ordinary by asking for its options group: a filtered deck has none, and
     * `getDeckConfig` errors. A deck Kani cannot prove ordinary is a
     * [MissingKanjiWriteFailureKind.DECK_COLLISION] rather than a target, because
     * `addNotes` into a filtered deck is not something Kani can undo.
     */
    private fun ensureDeck(session: Session) {
        val existed = deckExists(session)
        if (!existed) {
            expectOk(AnkiConnectRequests.createDeck(session.deckName, session.key))
            checkCancellation(session)
            if (!deckExists(session)) {
                throw WriteAbort(MissingKanjiWriteFailureKind.DECK_COLLISION)
            }
        }
        when (send(AnkiConnectRequests.getDeckConfig(session.deckName, session.key))) {
            is Sent.Ok -> return
            is Sent.Problem -> throw WriteAbort(MissingKanjiWriteFailureKind.DECK_COLLISION)
        }
    }

    private fun deckExists(session: Session): Boolean {
        val decks = AnkiConnectReads.namesAndIds(
            expectOk(AnkiConnectRequests.deckNamesAndIds(session.key)),
        ) ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        val id = decks[session.deckName] ?: return false
        if (id <= 0L) throw WriteAbort(MissingKanjiWriteFailureKind.DECK_COLLISION)
        return true
    }

    /**
     * Every note already in Kani's model, keyed by its stable `SourceId`. This is
     * the idempotence mechanism: the `SourceId` is derived from the kanji literal,
     * so a note Kani wrote in an earlier run — or in a run whose response was lost
     * — is recognized without consulting any local state.
     *
     * Re-read once per batch rather than cached, because the point is to observe
     * what the server actually committed.
     *
     * [honorCancellation] is false for the post-write reconciliation. Cancelling a
     * read that is establishing what a completed write created would discard the
     * only record of it, and the next run would create those notes a second time;
     * cancellation is honored before the next batch instead.
     */
    private fun queryExportedSourceIds(
        session: Session,
        honorCancellation: Boolean = true,
    ): MutableMap<String, Long> {
        val search = ProviderNotePolicy.modelSearch(MissingKanjiExportPlanner.MODEL_NAME)
        val ids = AnkiConnectReads.ids(
            expectOk(AnkiConnectRequests.findNotes(search, session.key)),
        ) ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        try {
            AnkiConnectReadPlanner.requireWithinIdCap(ids.size)
        } catch (_: AnkiConnectReadPlanner.OversizeIdResponseException) {
            throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
        }
        val bySource = LinkedHashMap<String, Long>()
        for (batch in AnkiConnectReadPlanner.batches(ids)) {
            if (honorCancellation) checkCancellation(session)
            val notes = AnkiConnectReads.notesInfo(
                expectOk(AnkiConnectRequests.notesInfo(batch, session.key)),
            ) ?: throw WriteAbort(MissingKanjiWriteFailureKind.TRANSIENT)
            for (note in notes) {
                if (note.fields.keys.toList() != MissingKanjiExportPlanner.FIELD_NAMES) {
                    // The model was verified, so a row that does not carry its
                    // fields means the destination changed underneath this run.
                    throw WriteAbort(MissingKanjiWriteFailureKind.MODEL_COLLISION)
                }
                val sourceId = note.fields[SOURCE_ID_FIELD].orEmpty()
                if (note.noteId > 0L && sourceId.startsWith(MissingKanjiExportPlanner.SOURCE_ID_PREFIX)) {
                    bySource.putIfAbsent(sourceId, note.noteId)
                }
            }
        }
        return bySource
    }

    private fun newNote(
        deckName: String,
        note: MissingKanjiExportPlanner.ExportNote,
    ): AnkiConnectRequests.NewNote = AnkiConnectRequests.NewNote(
        deckName = deckName,
        modelName = MissingKanjiExportPlanner.MODEL_NAME,
        fields = MissingKanjiExportPlanner.FIELD_NAMES.zip(note.fields).toMap(),
        tags = listOf(MissingKanjiExportPlanner.TAG),
    )

    /**
     * How many of [expected] notes `addNotes` said it created, or [UNKNOWN_COUNT]
     * when the answer cannot be aligned with the batch.
     *
     * AnkiConnect answers with one entry per requested note: a note id, or null for
     * one it refused (a duplicate, or an invalid note). A mixed array is therefore a
     * *partial* result, not a failure — and an array of the wrong length, or one
     * carrying entries of some other type, is an answer Kani cannot attribute at
     * all, so it counts as unknown and lets reconciliation decide.
     */
    private fun addedNoteCount(result: Json, expected: Int): Int {
        val items = (result as? Json.Arr)?.items ?: return UNKNOWN_COUNT
        if (items.size != expected) return UNKNOWN_COUNT
        var added = 0
        for (item in items) {
            when (item) {
                is Json.Num -> if (item.value > 0L) added++ else return UNKNOWN_COUNT
                Json.Null -> Unit
                else -> return UNKNOWN_COUNT
            }
        }
        return added
    }

    private fun recordReceipts(
        receiptSink: MissingKanjiReceiptSink,
        destinationKey: String,
        notes: List<ConfirmedMissingKanjiNote>,
    ): Boolean {
        if (notes.isEmpty()) return true
        return try {
            receiptSink.record(destinationKey, Collections.unmodifiableList(notes))
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Sends [request] and returns its success result, or throws [WriteAbort]. */
    private fun expectOk(request: AnkiConnectEnvelope.Request): Json =
        when (val outcome = send(request)) {
            is Sent.Ok -> outcome.result
            is Sent.Problem -> throw WriteAbort(outcome.kind)
        }

    /**
     * One round trip, classified. Failures are typed rather than thrown so a caller
     * that must reconcile before giving up (the `addNotes` path) can.
     */
    private fun send(request: AnkiConnectEnvelope.Request): Sent {
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            is AnkiConnectTransport.Exchange.Failure -> return Sent.Problem(transportFailure(exchange))
        }
        return when (val response = AnkiConnectEnvelope.parse(body)) {
            is AnkiConnectEnvelope.Response.Ok -> Sent.Ok(response.result)
            is AnkiConnectEnvelope.Response.Failed -> Sent.Problem(errorFailure(response.message))
            AnkiConnectEnvelope.Response.ProtocolError ->
                Sent.Problem(MissingKanjiWriteFailureKind.TRANSIENT)
        }
    }

    private fun checkCancellation(session: Session) {
        if (session.cancellation.isCancelled()) {
            throw WriteAbort(MissingKanjiWriteFailureKind.CANCELLED)
        }
    }

    /** One export's immutable per-call context. */
    private class Session(
        val key: String?,
        val deckName: String,
        val cancellation: CollectionCancellation,
    )

    /** The outcome of one round trip. */
    private sealed interface Sent {
        data class Ok(val result: Json) : Sent

        data class Problem(val kind: MissingKanjiWriteFailureKind) : Sent
    }

    private class WriteAbort(val kind: MissingKanjiWriteFailureKind) : RuntimeException()

    private class ExportState(private val plan: MissingKanjiExportPlanner.Plan) {
        val created = LinkedHashMap<String, Long>()
        val alreadyPresent = LinkedHashMap<String, Long>()
        val unfinished = LinkedHashSet(plan.notes.map(MissingKanjiExportPlanner.ExportNote::literal))
        var destinationKey: String? = null

        fun progress(): MissingKanjiWriteProgress = MissingKanjiWriteProgress(
            totalCount = plan.notes.size,
            processedCount = plan.notes.size - unfinished.size,
            createdCount = created.size,
            alreadyPresentCount = alreadyPresent.size,
        )

        fun result(failureKind: MissingKanjiWriteFailureKind? = null): MissingKanjiWriteResult =
            MissingKanjiWriteResult(
                requestedCount = plan.requestedCount,
                validCount = plan.notes.size,
                createdNotes = Collections.unmodifiableMap(LinkedHashMap(created)),
                alreadyPresentNotes = Collections.unmodifiableMap(LinkedHashMap(alreadyPresent)),
                invalidLiterals = plan.invalidLiterals,
                invalidCount = plan.invalidCount,
                duplicateRequestCount = plan.duplicateCount,
                unfinishedLiterals = Collections.unmodifiableSet(LinkedHashSet(unfinished)),
                destinationKey = destinationKey,
                failureKind = failureKind,
            )
    }

    companion object {
        /** The `addNotes` entry count Kani could not attribute to the batch. */
        private const val UNKNOWN_COUNT = -1

        /** The field the stable external id lives in. */
        private const val SOURCE_ID_FIELD = "SourceId"

        /**
         * The actions this flow needs. `createDeck`/`createModel`/`addNotes` do the
         * writing; `modelTemplates`/`modelStyling`/`getDeckConfig` are reads, and
         * they are just as required, because without them Kani cannot prove the
         * destination is its own and must refuse rather than guess.
         */
        @JvmField
        val REQUIRED_WRITE_ACTIONS: Set<String> = setOf(
            "createDeck",
            "createModel",
            "addNotes",
            "modelTemplates",
            "modelStyling",
            "getDeckConfig",
        )

        /** The required actions this ready Anki did not report. */
        fun missingWriteActions(ready: AnkiConnectHandshake.Status.Ready): Set<String> =
            REQUIRED_WRITE_ACTIONS.filterNotTo(LinkedHashSet()) {
                it in ready.availableOptionalActions
            }

        /**
         * The `destination_key` for notes written to [modelId] on the collection
         * identified by [sourceKey].
         *
         * The source key is digested rather than stored: it carries the endpoint and
         * the Anki profile name, and [AnkiConnectSourceKey] holds that neither is
         * persisted in the clear. Digesting keeps the key stable and comparable
         * without recording where the user's collection lives or what they named it.
         * The model id is appended plainly, so a model deleted and recreated is a
         * new destination and old receipts cannot suppress a rewrite into it.
         */
        fun destinationKey(sourceKey: String, modelId: Long): String =
            "ankiconnect:${digest(sourceKey)}:$modelId"

        private fun digest(sourceKey: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(sourceKey.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }

        private fun availabilityFailure(
            status: AnkiConnectHandshake.Status,
        ): MissingKanjiWriteFailureKind =
            when (AnkiConnectStatusMapping.availabilityFor(status)) {
                CollectionAvailability.AUTH_REQUIRED -> MissingKanjiWriteFailureKind.AUTH_REQUIRED
                CollectionAvailability.INVALID_CONFIGURATION ->
                    MissingKanjiWriteFailureKind.UNSUPPORTED_CAPABILITY
                CollectionAvailability.NOT_AVAILABLE -> MissingKanjiWriteFailureKind.NOT_AVAILABLE
                // A ready handshake never reaches here; treat it as retryable.
                CollectionAvailability.READY -> MissingKanjiWriteFailureKind.TRANSIENT
            }

        private fun transportFailure(
            failure: AnkiConnectTransport.Exchange.Failure,
        ): MissingKanjiWriteFailureKind = when (failure.reason) {
            AnkiConnectTransport.Reason.CANCELLED -> MissingKanjiWriteFailureKind.CANCELLED
            AnkiConnectTransport.Reason.TIMEOUT,
            AnkiConnectTransport.Reason.CONNECTION_FAILED,
            AnkiConnectTransport.Reason.NON_LOOPBACK_RESOLUTION,
            -> MissingKanjiWriteFailureKind.NOT_AVAILABLE
            AnkiConnectTransport.Reason.HTTP_ERROR_STATUS,
            AnkiConnectTransport.Reason.RESPONSE_TOO_LARGE,
            -> MissingKanjiWriteFailureKind.TRANSIENT
        }

        /**
         * An AnkiConnect `error` string. Auth loss has to be distinguished from a
         * transient problem here for the same reason [AnkiConnectStatusMapping] does
         * it: retrying a rejected API key forever is the failure mode.
         */
        private fun errorFailure(message: String): MissingKanjiWriteFailureKind {
            val lowered = message.lowercase()
            return if (lowered.contains("api key") || lowered.contains("authentication")) {
                MissingKanjiWriteFailureKind.AUTH_REQUIRED
            } else {
                MissingKanjiWriteFailureKind.TRANSIENT
            }
        }
    }
}
