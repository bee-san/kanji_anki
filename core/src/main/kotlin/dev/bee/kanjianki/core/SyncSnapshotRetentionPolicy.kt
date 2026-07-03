package dev.bee.kanjianki.core

/**
 * Retention policy for the heavy per-note/per-card sync snapshot tables
 * (`sync_card_snapshots`, `sync_note_snapshots`). These hold a full copy of every
 * note and card (including `fields_json`) for every successful sync, so without
 * pruning they grow without bound (~millions of rows/year on a large collection
 * with daily auto-sync).
 *
 * The kanji impact report only needs two card/note snapshot sync_ids per kanji:
 * the per-kanji baseline (resolved through the small, fully retained
 * `sync_kanji_snapshots` aggregate) and the latest sync. The baseline a kanji
 * resolves to is either its first-ever snapshot or the snapshot at/around its
 * first study signal, which in practice is the earliest retained sync. So we keep:
 *
 *  - the earliest successful sync (global baseline anchor), and
 *  - the most recent [keepLatest] successful syncs (covers "latest" plus a small
 *    buffer for in-flight report reads and near-term baselines),
 *
 * and prune the card/note snapshots for every other superseded sync_id. The
 * per-kanji aggregate table is retained long-term and is unaffected.
 */
object SyncSnapshotRetentionPolicy {
    /** Number of most-recent successful syncs whose card/note snapshots are retained. */
    const val KEEP_LATEST: Int = 8

    /**
     * Given the successful sync_ids that currently have card/note snapshots (in any
     * order), return the sync_ids whose card/note snapshots should be pruned.
     *
     * Retains the earliest sync_id plus the newest [keepLatest] sync_ids; everything
     * in between is pruned. Returns an empty list when nothing needs pruning.
     */
    @JvmStatic
    @JvmOverloads
    fun snapshotSyncIdsToPrune(existingSyncIds: Collection<Long>, keepLatest: Int = KEEP_LATEST): List<Long> {
        if (existingSyncIds.isEmpty()) {
            return emptyList()
        }
        val sorted = existingSyncIds.toSortedSet().toList()
        val keep = LinkedHashSet<Long>()
        // Global baseline anchor: the earliest retained sync.
        keep.add(sorted.first())
        // Newest keepLatest syncs.
        val effectiveKeep = if (keepLatest < 1) 1 else keepLatest
        val fromIndex = maxOf(0, sorted.size - effectiveKeep)
        for (index in fromIndex until sorted.size) {
            keep.add(sorted[index])
        }
        return sorted.filter { !keep.contains(it) }
    }
}
