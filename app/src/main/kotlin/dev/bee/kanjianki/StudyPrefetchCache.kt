package dev.bee.kanjianki

import java.util.concurrent.ConcurrentHashMap

/**
 * Single-use prefetch cache for study session data. Populated on the Home
 * route's background load with data for the first N study items. Consumed
 * once on study entry; invalidated if the session is re-entered or the
 * sync epoch changes.
 *
 * Thread-safe: written from the IO thread during Home load, read from the
 * IO thread on study entry. The map is never read and written concurrently
 * for the same key because prefetch runs before study entry.
 */
internal class StudyPrefetchCache {
    private val cache = ConcurrentHashMap<PrefetchKey, PrefetchedItemData>()
    private var epoch: Long = 0L

    fun populate(items: List<PrefetchedItemData>, syncEpoch: Long) {
        cache.clear()
        epoch = syncEpoch
        for (item in items) {
            cache[PrefetchKey(item.kanji, item.rung)] = item
        }
    }

    fun consume(kanji: String, rung: String, syncEpoch: Long): PrefetchedItemData? {
        if (syncEpoch != epoch) {
            invalidate()
            return null
        }
        return cache.remove(PrefetchKey(kanji, rung))
    }

    fun invalidate() {
        cache.clear()
    }

    fun size(): Int = cache.size

    data class PrefetchKey(val kanji: String, val rung: String)
}

data class PrefetchedItemData(
    val kanji: String,
    val rung: String,
    val choicePool: List<Any>?,
    val readingPool: List<String>?,
)
