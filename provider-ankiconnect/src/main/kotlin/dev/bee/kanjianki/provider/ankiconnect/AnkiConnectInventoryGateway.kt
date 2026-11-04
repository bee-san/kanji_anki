package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionInventoryConsumer
import dev.bee.kanjianki.syncapi.CollectionInventoryGateway
import dev.bee.kanjianki.syncapi.CollectionInventoryNote
import dev.bee.kanjianki.syncapi.CollectionInventoryResult
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionSourceStatus

/**
 * Collection-wide aggregate note scan over AnkiConnect, for Missing Kanji
 * analysis. This is deliberately **not** the configured-model sync path: it
 * spans every note type, returns no snapshot, and is never mirrored or persisted
 * as collection history.
 *
 * Four properties are load-bearing, and each is a place this could go wrong:
 *
 * - **Aggregate-only.** Each note's raw fields are handed to the consumer one
 *   row at a time and then dropped. This gateway accumulates nothing but counts,
 *   so a 100k-note collection costs one bounded batch of resident field text
 *   rather than the whole collection. The caller
 *   ([dev.bee.kanjianki.core.AnkiKanjiInventoryCollector]) keeps only kanji
 *   literals.
 * - **ID discovery is not streaming, and this class does not pretend it is.**
 *   `findNotes` answers with the complete ID array in one response, so the only
 *   protection available at that step is the hard cap
 *   ([AnkiConnectReadPlanner.requireWithinIdCap]); streaming starts at the
 *   *detail* stage. An oversize collection fails with an actionable error rather
 *   than being silently truncated.
 * - **No model enumeration per note.** `notesInfo` already reports each note's
 *   model name, so the model count is derived from what the scan actually saw
 *   plus `modelNamesAndIds` for the collection-level count. AnkiConnect exposes
 *   no numeric model id on `notesInfo`, so [CollectionInventoryNote.modelId] is
 *   resolved from the name map and falls back to `0` for a model the map does
 *   not contain (a note type created between the two calls).
 * - **Malformed rows are isolated, cancellation is honored per batch.** The same
 *   rules the configured-model read follows, so a damaged collection produces a
 *   warning-thresholded count rather than a failed scan.
 */
class AnkiConnectInventoryGateway(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
) : CollectionInventoryGateway {
    private val handshake = AnkiConnectHandshake(transport)

    /**
     * Whether this Anki can serve an inventory scan. Inventory needs only the
     * required read actions, so a ready handshake is sufficient; the capability
     * is reported separately from [AnkiConnectGateway.status] because a caller
     * may have one and not the other.
     */
    override fun status(): CollectionSourceStatus =
        when (val result = handshake.run(keyProvider())) {
            is AnkiConnectHandshake.Status.Ready ->
                CollectionSourceStatus(
                    CollectionAvailability.READY,
                    setOf(CollectionCapability.COLLECTION_INVENTORY),
                    "Connected to Anki (API v${result.version}).",
                )
            else -> CollectionSourceStatus(
                AnkiConnectStatusMapping.availabilityFor(result),
                emptySet(),
                AnkiConnectStatusMapping.messageFor(result),
            )
        }

    @Throws(CollectionFailure::class)
    override fun scan(
        consumer: CollectionInventoryConsumer,
        progress: CollectionProgressListener,
        cancellation: CollectionCancellation,
    ): CollectionInventoryResult {
        throwIfCancelled(cancellation)
        val key = keyProvider()
        progress.onProgress(CollectionProgress(CollectionProgress.Stage.READING_INVENTORY))

        val modelIdsByName = AnkiConnectReads.namesAndIds(
            resultOf(AnkiConnectRequests.modelNamesAndIds(key)),
        ) ?: throw protocolFailure("modelNamesAndIds")

        throwIfCancelled(cancellation)
        // The collection-wide query. `deck:*` matches every note in every deck,
        // including notes in filtered decks, which is what a collection-wide
        // inventory means; an empty query is rejected by AnkiConnect.
        val noteIds = AnkiConnectReads.ids(
            resultOf(AnkiConnectRequests.findNotes(COLLECTION_WIDE_QUERY, key)),
        ) ?: throw protocolFailure("findNotes")
        AnkiConnectReadPlanner.requireWithinIdCap(noteIds.size)

        var notesRead = 0
        var skippedNotes = 0
        var batchSize = AnkiConnectReadPlanner.DEFAULT_START_BATCH
        var offset = 0
        while (offset < noteIds.size) {
            throwIfCancelled(cancellation)
            val batch = noteIds.subList(offset, minOf(offset + batchSize, noteIds.size))
            val measured = measuredResultOf(AnkiConnectRequests.notesInfo(batch, key))
            val parsed = AnkiConnectReads.notesInfoIsolating(measured.result)
                ?: throw protocolFailure("notesInfo")
            for (info in parsed.rows) {
                // Cancellation is checked per row as well as per batch: a batch
                // of 500 rendered notes is enough work to be worth interrupting.
                throwIfCancelled(cancellation)
                consumer.onNote(
                    CollectionInventoryNote(
                        noteId = info.noteId,
                        modelId = modelIdsByName[info.modelName] ?: UNKNOWN_MODEL_ID,
                        modelName = info.modelName,
                        fieldNames = info.fields.keys.toList(),
                        fields = info.fields.values.toList(),
                    ),
                )
            }
            notesRead += parsed.rows.size
            skippedNotes += parsed.skipped
            offset += batch.size
            progress.onProgress(
                CollectionProgress(
                    CollectionProgress.Stage.READING_INVENTORY,
                    notesRead + skippedNotes,
                    noteIds.size,
                ),
            )
            batchSize = AnkiConnectReadPlanner.adaptBatchSize(batch.size, measured.encodedBytes)
        }

        return CollectionInventoryResult(
            notesRead = notesRead,
            skippedNotes = skippedNotes,
            modelCount = modelIdsByName.size,
            // AnkiConnect has no direct-SQL equivalent; every scan goes through
            // the provider's own search, so the mode is never DIRECT_PAGED.
            queryMode = CollectionInventoryResult.QueryMode.PROVIDER_SEARCH,
        )
    }

    private fun throwIfCancelled(cancellation: CollectionCancellation) {
        if (cancellation.isCancelled()) throw CollectionFailure.cancelled()
    }

    private fun resultOf(request: AnkiConnectEnvelope.Request): AnkiConnectJson.Json =
        measuredResultOf(request).result

    private class Measured(val result: AnkiConnectJson.Json, val encodedBytes: Long)

    private fun measuredResultOf(request: AnkiConnectEnvelope.Request): Measured =
        when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> when (
                val response = AnkiConnectEnvelope.parse(exchange.text)
            ) {
                is AnkiConnectEnvelope.Response.Ok ->
                    Measured(response.result, exchange.text.length.toLong())
                is AnkiConnectEnvelope.Response.Failed ->
                    throw AnkiConnectStatusMapping.failureFor(response.message)
                AnkiConnectEnvelope.Response.ProtocolError -> throw protocolFailure(request.action)
            }
            is AnkiConnectTransport.Exchange.Failure ->
                throw AnkiConnectStatusMapping.transportFailure(exchange)
        }

    private fun protocolFailure(action: String): CollectionFailure =
        AnkiConnectStatusMapping.protocolFailure(action)

    companion object {
        /** Every note in every deck. AnkiConnect rejects an empty query. */
        const val COLLECTION_WIDE_QUERY = "deck:*"

        /** Used when `notesInfo` names a model `modelNamesAndIds` did not list. */
        const val UNKNOWN_MODEL_ID = 0L
    }
}
