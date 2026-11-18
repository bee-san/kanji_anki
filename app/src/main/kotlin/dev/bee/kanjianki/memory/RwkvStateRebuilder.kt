package dev.bee.kanjianki.memory

import dev.bee.kanjianki.core.RwkvHistoryBackfill
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.RwkvStateStore
import dev.bee.rwkv.RwkvCollectionState
import dev.bee.rwkv.RwkvEngine

/**
 * Rebuilds the RWKV state from the ordered history: classifies any review
 * rows written before v34, replays every fed answer and seed burst in
 * sequence order into memory, then publishes the result in one transaction
 * guarded by the state-version compare-and-swap (retrying if a writer won).
 */
internal class RwkvStateRebuilder(private val store: LocalStore, private val engine: RwkvEngine) {
    fun rebuild(progress: (done: Int, total: Int) -> Unit) {
        classifyHistory(progress)
        repeat(MAX_ATTEMPTS) {
            val version = store.rwkvMeta().stateVersion
            val total = store.rwkvHistoryEventCount()
            val state = RwkvCollectionState()
            var done = 0
            progress(0, total)
            store.forEachRwkvHistoryEvent { event ->
                when (event) {
                    is RwkvStateStore.HistoryEvent.Review -> engine.replay(state, event.ids, event.answer)
                    is RwkvStateStore.HistoryEvent.Warm -> engine.warmCard(state, event.burst.ids, event.burst.answers)
                }
                done++
                if (done % PROGRESS_EVERY == 0) {
                    progress(done, total)
                }
            }
            if (store.publishRwkvCollection(state, version)) {
                progress(total, total)
                return
            }
        }
        throw IllegalStateException("RWKV state kept changing while it was being rebuilt")
    }

    private fun classifyHistory(progress: (Int, Int) -> Unit) {
        val total = store.rwkvUnclassifiedRowCount()
        if (total == 0) {
            return
        }
        val backfill = RwkvHistoryBackfill().apply { markSeen(store.rwkvFedCardIds()) }
        var done = 0
        while (true) {
            val classified = store.classifyRwkvHistoryBatch(backfill, CLASSIFY_BATCH)
            if (classified == 0) {
                return
            }
            done += classified
            progress(done, total)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val CLASSIFY_BATCH = 500
        const val PROGRESS_EVERY = 200
    }
}
