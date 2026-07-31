package dev.bee.kanjianki.provider.ankiconnect

/**
 * The pure bounding/batching policy for AnkiConnect collection reads. `findNotes`
 * and `findCards` return complete ID arrays, so before fetching any detail Kani
 * enforces a hard ID-count cap and processes accepted IDs in bounded detail
 * batches. `notesInfo`/`cardsInfo` repeat rendered content and CSS, so the
 * batch size starts small and adapts downward by observed encoded byte size,
 * with 500 only as a hard ceiling. This holds the arithmetic with no I/O so the
 * limits are unit-testable.
 */
object AnkiConnectReadPlanner {
    /** Reject an ID response larger than this (planning bound). */
    const val MAX_ID_COUNT = 250_000

    /** Default starting detail-batch size before adaptation. */
    const val DEFAULT_START_BATCH = 100

    /** Hard ceiling on a detail batch, even if bytes stay small. */
    const val MAX_BATCH = 500

    /** Smallest batch the planner will shrink to. */
    const val MIN_BATCH = 10

    /** Target encoded bytes per detail batch used to adapt the size downward. */
    const val TARGET_BATCH_BYTES = 1_000_000L

    class OversizeIdResponseException(val count: Int, val cap: Int) :
        RuntimeException("AnkiConnect returned $count ids, exceeding the $cap cap")

    /**
     * Validates an ID-array size against [MAX_ID_COUNT].
     * @throws OversizeIdResponseException if too many ids were returned.
     */
    fun requireWithinIdCap(idCount: Int, cap: Int = MAX_ID_COUNT) {
        if (idCount > cap) throw OversizeIdResponseException(idCount, cap)
    }

    /**
     * Splits [ids] into batches of [batchSize], clamped to [MIN_BATCH]..[MAX_BATCH].
     * The final batch may be smaller. An empty input yields no batches.
     */
    fun <T> batches(ids: List<T>, batchSize: Int = DEFAULT_START_BATCH): List<List<T>> {
        val size = batchSize.coerceIn(MIN_BATCH, MAX_BATCH)
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(size)
    }

    /**
     * Adapts the next batch size from the last batch's observed encoded byte
     * size. If a batch of [lastBatchSize] rows encoded to [lastBatchBytes], scale
     * toward [TARGET_BATCH_BYTES] per batch, clamped to [MIN_BATCH]..[MAX_BATCH].
     * A zero-byte or zero-size observation keeps the size unchanged (clamped).
     */
    fun adaptBatchSize(
        lastBatchSize: Int,
        lastBatchBytes: Long,
        targetBytes: Long = TARGET_BATCH_BYTES,
    ): Int {
        if (lastBatchSize <= 0 || lastBatchBytes <= 0L) {
            return lastBatchSize.coerceIn(MIN_BATCH, MAX_BATCH)
        }
        val bytesPerRow = (lastBatchBytes.toDouble() / lastBatchSize).coerceAtLeast(1.0)
        val next = (targetBytes / bytesPerRow).toInt()
        return next.coerceIn(MIN_BATCH, MAX_BATCH)
    }
}
