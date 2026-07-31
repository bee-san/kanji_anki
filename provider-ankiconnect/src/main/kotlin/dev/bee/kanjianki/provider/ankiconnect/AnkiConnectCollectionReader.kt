package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.AnkiKanjiInventoryCollector
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.CollectionSourceIdentity
import dev.bee.kanjianki.syncapi.NoteTypeDescriptor
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy

/**
 * Reads an Anki collection over AnkiConnect into the provider-neutral
 * [RecordsSyncModels.CollectionSnapshot], so downstream sync/analysis code is
 * identical to the AnkiDroid path.
 *
 * Parity rules this reader is responsible for:
 *
 * - **Query identity.** The configured-model query and the optional browser
 *   query both come from [ProviderNotePolicy], the same helper AnkiDroid uses,
 *   so a given settings object selects the same notes on both providers.
 * - **Deterministic ordering.** Notes come back in the provider's `findNotes`
 *   order and cards are grouped by their owning note in that same note order,
 *   matching `AnkiDroidCardReader`'s per-note grouping. Two reads of an
 *   unchanged collection produce identical snapshots.
 * - **No fabrication.** AnkiConnect exposes no FSRS memory state, so
 *   stability/difficulty/retrievability stay null and the
 *   [CollectionCapability.FSRS_MEMORY_STATE] capability is not advertised.
 *   Seeding must fall back to interval/lapses evidence rather than inventing a
 *   memory state.
 * - **Bounded work.** ID responses are capped and detail reads are batched and
 *   byte-adaptive via [AnkiConnectReadPlanner].
 * - **Malformed-row isolation.** One unparseable note or card is skipped and
 *   counted, not allowed to abort the whole read.
 * - **Cancellation.** Checked before every network round trip, so a cancelled
 *   sync stops within one batch instead of draining the collection.
 */
class AnkiConnectCollectionReader(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
) {
    /** Counts of rows the provider returned but Kani could not use. */
    data class SkippedRows(val notes: Int, val cards: Int) {
        val total: Int get() = notes + cards
    }

    /** A completed read plus the diagnostics the caller may want to surface. */
    data class ReadResult(
        val snapshot: RecordsSyncModels.CollectionSnapshot,
        val skipped: SkippedRows,
    ) {
        /**
         * Non-null when malformed rows reached the shared malformed-row warning
         * threshold ([AnkiKanjiInventoryCollector.warningThreshold]) — the same
         * 1%-of-rows (min 1, max 100) rule the inventory scan uses, so isolated
         * damage is tolerated quietly but widespread damage is surfaced. Reusing
         * the inventory's warning type means the existing user-facing copy applies
         * to this provider unchanged.
         */
        val malformedRowWarning: AnkiKanjiInventory.MalformedRowWarning?
            get() {
                if (skipped.total <= 0) return null
                val rowsSeen = snapshot.notes.size + snapshot.cards.size + skipped.total
                val threshold = AnkiKanjiInventoryCollector.warningThreshold(rowsSeen)
                return if (skipped.total >= threshold) {
                    AnkiKanjiInventory.MalformedRowWarning(skipped.total, threshold)
                } else {
                    null
                }
            }
    }

    /**
     * Lists note types with their fields, for the settings model picker. Field
     * names are fetched through bounded `multi` groups rather than one round trip
     * per model, because a large collection can hold dozens of note types.
     */
    @Throws(CollectionFailure::class)
    fun noteTypes(): List<NoteTypeDescriptor> {
        val key = keyProvider()
        val models = AnkiConnectReads.namesAndIds(
            resultOf(AnkiConnectRequests.modelNamesAndIds(key)),
        ) ?: throw protocolFailure("modelNamesAndIds")
        val names = models.keys.toList()
        val fieldsByName = LinkedHashMap<String, List<String>>(names.size)
        for (group in AnkiConnectReadPlanner.multiGroups(names)) {
            val responses = multiResponsesOf(
                AnkiConnectRequests.modelFieldNamesMulti(group, key),
                expected = group.size,
            )
            group.forEachIndexed { index, name ->
                fieldsByName[name] = AnkiConnectReads.fieldNames(responses[index])
                    ?: throw protocolFailure("modelFieldNames")
            }
        }
        return models.map { (name, modelId) ->
            NoteTypeDescriptor(modelId, name, fieldsByName.getValue(name))
        }
    }

    /**
     * Reads the configured model's notes and their cards into a snapshot.
     * @throws CollectionFailure on transport, protocol, or cancellation failure.
     */
    @Throws(CollectionFailure::class)
    fun read(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener = CollectionProgressListener.NONE,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): ReadResult {
        throwIfCancelled(cancellation)
        val key = keyProvider()

        progress.onProgress(CollectionProgress(CollectionProgress.Stage.FINDING_NOTE_TYPE))
        val modelId = resolveModelId(settings.modelName, key)

        throwIfCancelled(cancellation)
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.READING_NOTES))
        val noteIds = findIds(ProviderNotePolicy.modelSearch(settings.modelName), key, cancellation)
        val rawBrowserQueryNoteIds = browserQueryNoteIds(settings, key, cancellation)

        // Intersect raw browser-query matches with the configured model before
        // merging: a user's browser query is arbitrary and may match notes of any
        // note type, but Kani only syncs the configured model. AnkiDroid enforces
        // this by skipping cursor rows whose model id differs; the intersection is
        // the equivalent over an id-based provider.
        val configuredNoteIds = LinkedHashSet(noteIds)
        val browserQueryNoteIds = rawBrowserQueryNoteIds.filterTo(LinkedHashSet()) { it in configuredNoteIds }

        AnkiConnectReadPlanner.requireWithinIdCap(configuredNoteIds.size)
        val notes = readNotes(configuredNoteIds.toList(), modelId, settings, progress, cancellation, key)

        throwIfCancelled(cancellation)
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.SCANNING_CARDS))
        val cards = readCards(notes.rows.map { it.noteId }, progress, cancellation, key)

        val markedCards = cards.rows.map { card ->
            if (card.noteId in browserQueryNoteIds) card.withBrowserQueryMatched(true) else card
        }
        return ReadResult(
            snapshot = RecordsSyncModels.CollectionSnapshot(notes.rows.map { it.note }, markedCards),
            skipped = SkippedRows(notes.skipped, cards.skipped),
        )
    }

    /**
     * Wraps [read] with the capability set and source identity for sync.
     *
     * The active profile is resolved **before** any detail read, because the
     * profile is half the source key: the binding gate has to be able to reject a
     * profile switch before Kani spends a full collection read on — or worse,
     * mirrors — the wrong collection.
     */
    @Throws(CollectionFailure::class)
    fun readProviderCollection(
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener = CollectionProgressListener.NONE,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): ProviderCollectionSnapshot {
        throwIfCancelled(cancellation)
        val sourceKey = sourceKey(keyProvider())
        val result = read(settings, progress, cancellation)
        throwIfCancelled(cancellation)
        val snapshot = result.snapshot
        return ProviderCollectionSnapshot(
            snapshot = snapshot,
            // Deliberately no FSRS_MEMORY_STATE: AnkiConnect exposes no memory state.
            capabilities = setOf(
                CollectionCapability.READ_COLLECTION,
                CollectionCapability.LIST_NOTE_TYPES,
                CollectionCapability.SOURCE_IDENTITY,
            ),
            sourceIdentity = CollectionSourceIdentity.create(
                providerKind = CollectionProviderKind.ANKI_CONNECT,
                sourceKey = sourceKey,
                stableNoteIds = snapshot.notes.map(RecordsSyncModels.Note::noteId),
                stableCardIds = snapshot.cards.map(RecordsSyncModels.Card::cardId),
            ),
        )
    }

    /**
     * The endpoint paired with the active profile name. The endpoint alone cannot
     * identify a source: every profile on the machine answers on the same
     * loopback port, so an endpoint-only key would validate unchanged across a
     * profile switch. `CollectionSourceIdentity` digests the composed key under a
     * per-binding salt, so neither component is persisted or logged in the clear.
     */
    private fun sourceKey(key: String?): String {
        val result = resultOf(AnkiConnectRequests.getActiveProfile(key))
        val profile = when (result) {
            is AnkiConnectJson.Json.Str -> result.value
            AnkiConnectJson.Json.Null -> ""
            else -> throw protocolFailure("getActiveProfile")
        }
        if (profile.isBlank()) {
            throw CollectionFailure(
                CollectionFailureKind.INVALID_CONFIGURATION,
                "Anki has no collection open, so Kani cannot identify the source profile.",
            )
        }
        return AnkiConnectSourceKey.of(transport.endpointUrl(), profile)
    }

    private class ParsedNote(val noteId: Long, val note: RecordsSyncModels.Note)

    private class Batched<T>(val rows: List<T>, val skipped: Int)

    private fun resolveModelId(modelName: String, key: String?): Long {
        val models = AnkiConnectReads.namesAndIds(
            resultOf(AnkiConnectRequests.modelNamesAndIds(key)),
        ) ?: throw protocolFailure("modelNamesAndIds")
        return models[modelName] ?: throw CollectionFailure(
            CollectionFailureKind.INVALID_CONFIGURATION,
            "Anki has no note type named \"$modelName\".",
        )
    }

    private fun browserQueryNoteIds(
        settings: RecordsSyncModels.Settings,
        key: String?,
        cancellation: CollectionCancellation,
    ): Set<Long> {
        if (!settings.browserQueryImportEnabled()) return emptySet()
        val search = ProviderNotePolicy.browserQuerySearch(settings.normalizedBrowserQuery())
        return LinkedHashSet(findIds(search, key, cancellation))
    }

    private fun findIds(
        query: String,
        key: String?,
        cancellation: CollectionCancellation,
    ): List<Long> {
        throwIfCancelled(cancellation)
        val ids = AnkiConnectReads.ids(resultOf(AnkiConnectRequests.findNotes(query, key)))
            ?: throw protocolFailure("findNotes")
        AnkiConnectReadPlanner.requireWithinIdCap(ids.size)
        return ids
    }

    private fun readNotes(
        noteIds: List<Long>,
        configuredModelId: Long,
        settings: RecordsSyncModels.Settings,
        progress: CollectionProgressListener,
        cancellation: CollectionCancellation,
        key: String?,
    ): Batched<ParsedNote> = batched(
        ids = noteIds,
        stage = CollectionProgress.Stage.READING_NOTES,
        progress = progress,
        cancellation = cancellation,
    ) { batch ->
        val measured = measuredResultOf(AnkiConnectRequests.notesInfo(batch, key))
        val parsed = AnkiConnectReads.notesInfoIsolating(measured.result)
            ?: throw protocolFailure("notesInfo")
        val rows = parsed.rows.map { info ->
            ParsedNote(
                noteId = info.noteId,
                note = RecordsSyncModels.Note(
                    info.noteId,
                    // Every note read here came from the configured-model query
                    // (browser-query ids are intersected with it first), so the
                    // configured model id always applies.
                    configuredModelId,
                    info.modelName,
                    ProviderNotePolicy.selectRequiredFields(
                        info.fields.keys.toList(),
                        info.fields.values.toList(),
                        settings.requiredFields(),
                    ),
                    info.tags,
                ),
            )
        }
        Fetched(rows, parsed.skipped, measured.encodedBytes)
    }

    private fun readCards(
        noteIds: List<Long>,
        progress: CollectionProgressListener,
        cancellation: CollectionCancellation,
        key: String?,
    ): Batched<RecordsSyncModels.Card> {
        if (noteIds.isEmpty()) return Batched(emptyList(), 0)
        throwIfCancelled(cancellation)
        // findCards over the owning notes keeps card discovery in one bounded
        // query instead of one request per note.
        val query = noteIds.joinToString(" OR ") { "nid:$it" }
        val cardIds = AnkiConnectReads.ids(resultOf(AnkiConnectRequests.findCards(query, key)))
            ?: throw protocolFailure("findCards")
        AnkiConnectReadPlanner.requireWithinIdCap(cardIds.size)

        val detail = batched(
            ids = cardIds,
            stage = CollectionProgress.Stage.SCANNING_CARDS,
            progress = progress,
            cancellation = cancellation,
        ) { batch ->
            val measured = measuredResultOf(AnkiConnectRequests.cardsInfo(batch, key))
            val parsed = AnkiConnectReads.cardsInfoIsolating(measured.result)
                ?: throw protocolFailure("cardsInfo")
            val rows = parsed.rows
                // Every card here belongs to a configured-model note, so Kani's
                // front-template restriction applies to all of them.
                .filter { card -> AnkiConnectCardNormalization.isAcceptedConfiguredOrd(card.ord) }
                .map(::toSnapshotCard)
            Fetched(rows, parsed.skipped, measured.encodedBytes)
        }

        // Group by the owning note in note order for a deterministic snapshot,
        // matching AnkiDroidCardReader's per-note grouping.
        val byNote = detail.rows.groupBy(RecordsSyncModels.Card::noteId)
        val ordered = ArrayList<RecordsSyncModels.Card>(detail.rows.size)
        for (noteId in noteIds) {
            ordered.addAll(byNote[noteId].orEmpty())
        }
        return Batched(ordered, detail.skipped)
    }

    private fun toSnapshotCard(card: AnkiConnectReads.CardInfo): RecordsSyncModels.Card {
        val suspended = AnkiConnectCardNormalization.isSuspended(card.queue)
        return RecordsSyncModels.Card(
            card.cardId,
            card.noteId,
            card.ord.toInt(),
            // AnkiConnect reports a deck name, not a numeric deck id; the
            // snapshot's deckId/deckName both carry it, as AnkiDroid does with
            // its deck id.
            card.deckName,
            card.deckName,
            AnkiConnectCardNormalization.signed(card.queue),
            AnkiConnectCardNormalization.signed(card.type),
            AnkiConnectCardNormalization.signed(card.due),
            AnkiConnectCardNormalization.intervalDays(card.interval),
            AnkiConnectCardNormalization.counter(card.reps),
            AnkiConnectCardNormalization.counter(card.lapses),
            suspended,
            // No FSRS memory state over AnkiConnect: never fabricated.
            null,
            null,
            null,
        )
    }

    /**
     * Walks [ids] in bounded detail batches, adapting the next batch size from the
     * observed encoded size of the last one and checking cancellation before every
     * round trip. Progress is reported per batch with a known total.
     */
    private fun <I, R> batched(
        ids: List<I>,
        stage: CollectionProgress.Stage,
        progress: CollectionProgressListener,
        cancellation: CollectionCancellation,
        fetch: (List<I>) -> Fetched<R>,
    ): Batched<R> {
        val rows = ArrayList<R>(ids.size)
        var skipped = 0
        var completed = 0
        var batchSize = AnkiConnectReadPlanner.DEFAULT_START_BATCH
        var offset = 0
        while (offset < ids.size) {
            throwIfCancelled(cancellation)
            val batch = ids.subList(offset, minOf(offset + batchSize, ids.size))
            val fetched = fetch(batch)
            rows.addAll(fetched.rows)
            skipped += fetched.skipped
            offset += batch.size
            completed += batch.size
            progress.onProgress(CollectionProgress(stage, completed, ids.size))
            batchSize = AnkiConnectReadPlanner.adaptBatchSize(batch.size, fetched.observedBytes)
        }
        return Batched(rows, skipped)
    }

    /** One batch's parsed rows plus the encoded size that drives adaptation. */
    private class Fetched<R>(
        val rows: List<R>,
        val skipped: Int,
        val observedBytes: Long,
    )

    private fun protocolFailure(action: String): CollectionFailure =
        AnkiConnectStatusMapping.protocolFailure(action)

    private fun throwIfCancelled(cancellation: CollectionCancellation) {
        if (cancellation.isCancelled()) throw CollectionFailure.cancelled()
    }

    private fun resultOf(request: AnkiConnectEnvelope.Request): AnkiConnectJson.Json =
        measuredResultOf(request).result

    /**
     * Sends a `multi` request and returns one success result per nested action.
     * Every nested envelope is validated: a nested error or a wrong response
     * count fails the whole group rather than silently shifting results onto the
     * wrong action.
     */
    private fun multiResponsesOf(
        request: AnkiConnectEnvelope.Request,
        expected: Int,
    ): List<AnkiConnectJson.Json> {
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            is AnkiConnectTransport.Exchange.Failure -> throw AnkiConnectStatusMapping.transportFailure(exchange)
        }
        val nested = AnkiConnectEnvelope.parseMulti(body)
        if (nested.size != expected) throw protocolFailure(request.action)
        return nested.map { response ->
            when (response) {
                is AnkiConnectEnvelope.Response.Ok -> response.result
                is AnkiConnectEnvelope.Response.Failed -> throw AnkiConnectStatusMapping.failureFor(response.message)
                AnkiConnectEnvelope.Response.ProtocolError -> throw protocolFailure(request.action)
            }
        }
    }

    /** A success result plus the raw body size, used to adapt the batch size. */
    private class Measured(val result: AnkiConnectJson.Json, val encodedBytes: Long)

    private fun measuredResultOf(request: AnkiConnectEnvelope.Request): Measured =
        when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> when (
                val response = AnkiConnectEnvelope.parse(exchange.text)
            ) {
                is AnkiConnectEnvelope.Response.Ok ->
                    Measured(response.result, exchange.text.length.toLong())
                is AnkiConnectEnvelope.Response.Failed -> throw AnkiConnectStatusMapping.failureFor(response.message)
                AnkiConnectEnvelope.Response.ProtocolError -> throw protocolFailure(request.action)
            }
            is AnkiConnectTransport.Exchange.Failure -> throw AnkiConnectStatusMapping.transportFailure(exchange)
        }
}
