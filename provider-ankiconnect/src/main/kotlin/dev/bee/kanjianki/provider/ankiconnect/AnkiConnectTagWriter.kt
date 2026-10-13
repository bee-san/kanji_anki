package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.CollectionCancellation

/**
 * Kani's only routine write to a user's Anki collection over AnkiConnect: adding
 * one of its own note tags. Everything about this class exists to make that write
 * safe to run automatically after a sync.
 *
 * **One note per action.** AnkiConnect's `addTags` accepts a note-id array, and
 * sending one action for the whole set would be a single round trip — but a
 * failure would then be attributed to the whole set, and Kani could not tell
 * which notes were tagged. Since the caller retries untagged notes on the next
 * sync, an unattributable failure means either re-tagging notes that already have
 * the tag or dropping notes that never got it. So each note gets its own action,
 * and each action's outcome is recorded separately.
 *
 * **Bounded `multi` batches, every nested envelope inspected.** One action per
 * note would be one round trip per note. `multi` collapses them, at the cost that
 * a partial failure arrives as a mixed array rather than an exception: some
 * nested envelopes succeed and some carry an `error`. A caller that checked only
 * the outer envelope would read a wholly-failed batch as a success. This class
 * inspects every nested envelope, and treats a short, long, or unparseable array
 * as *all notes in the batch failed* rather than guessing which position belongs
 * to which note.
 *
 * **A tag write is never allowed to fail a sync.** Transport loss, an API-key
 * error, a protocol error, and a cancelled run all resolve to "these notes are
 * not tagged yet", which the caller retries later. `addTags` is idempotent in
 * Anki — re-tagging an already-tagged note is a no-op — so a retry after an
 * ambiguous outcome cannot double-tag. That idempotence is why this writer does
 * not need AnkiDroid's read-modify-write cycle: `AnkiDroidRepairedTagging` has to
 * read the `tags` column and rewrite it whole, which would clobber a concurrent
 * edit if it did not merge; AnkiConnect's `addTags` merges server-side.
 *
 * Nothing here can change scheduling state. The only actions it sends are
 * `addTags` and `multi`, both on the [AnkiConnectActions] allowlist, and the only
 * tags it writes are Kani's own.
 */
class AnkiConnectTagWriter(
    private val transport: AnkiConnectTransport,
    private val keyProvider: () -> String? = { null },
    private val batchSize: Int = AnkiConnectReadPlanner.MAX_MULTI_ACTIONS,
) {
    /** Which notes a tag write reached, and which the caller should retry. */
    data class Outcome(
        val tagged: Set<Long>,
        val failed: Set<Long>,
    ) {
        val requested: Int get() = tagged.size + failed.size
    }

    /**
     * Adds [tag] to every note in [noteIds], reporting per-note success.
     *
     * Never throws for a provider problem: an unreachable Anki, a rejected key, a
     * malformed response, or a cancelled run all come back as failed note ids.
     * Cancellation stops before the next batch, so the notes already tagged stay
     * reported as tagged — they really are.
     */
    fun addTag(
        tag: String,
        noteIds: Set<Long>,
        cancellation: CollectionCancellation = CollectionCancellation.NONE,
    ): Outcome {
        require(tag.isNotBlank()) { "tag must not be blank" }
        // A non-positive note id is not a note; it would make AnkiConnect fail an
        // otherwise healthy batch, so it is dropped before the wire.
        val requested = noteIds.filterTo(sortedSetOf()) { it > 0L }
        if (requested.isEmpty()) return Outcome(emptySet(), emptySet())

        val tagged = linkedSetOf<Long>()
        val failed = linkedSetOf<Long>()
        val key = keyProvider()
        for (batch in requested.chunked(batchSize.coerceIn(1, AnkiConnectReadPlanner.MAX_MULTI_ACTIONS))) {
            if (cancellation.isCancelled()) {
                // Not an error: the remaining notes are simply still untagged.
                failed.addAll(requested.filter { it !in tagged && it !in failed })
                break
            }
            val results = sendBatch(tag, batch, key)
            batch.forEachIndexed { index, noteId ->
                if (results[index]) tagged += noteId else failed += noteId
            }
        }
        return Outcome(tagged, failed)
    }

    /**
     * Sends one bounded `multi` of one-note `addTags` actions and reports one
     * boolean per note, positionally.
     *
     * Any shape Kani cannot map position-for-position onto [batch] — a transport
     * failure, an outer error, a nested count mismatch, an unparseable nested
     * envelope — fails the whole batch. Guessing an alignment is the one outcome
     * worse than a retry: it would report notes as tagged that are not.
     */
    private fun sendBatch(tag: String, batch: List<Long>, key: String?): List<Boolean> {
        val allFailed = List(batch.size) { false }
        val request = AnkiConnectRequests.addTagsMulti(batch, tag, key)
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            is AnkiConnectTransport.Exchange.Failure -> return allFailed
        }
        val nested = AnkiConnectEnvelope.parseMulti(body)
        if (nested.size != batch.size) return allFailed
        return nested.map { response ->
            when (response) {
                // `addTags` reports success as a null result; any non-error
                // envelope means Anki accepted the tag for that note.
                is AnkiConnectEnvelope.Response.Ok -> true
                is AnkiConnectEnvelope.Response.Failed -> false
                AnkiConnectEnvelope.Response.ProtocolError -> false
            }
        }
    }
}
