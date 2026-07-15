package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StudyPrefetchCacheTest {

    @Test
    fun populateStoresItems() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning"), item("火", "word_reading")), syncEpoch =1L)
        assertEquals(2, cache.size())
    }

    @Test
    fun consumeReturnsPopulatedItem() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning")), syncEpoch =1L)
        val result = cache.consume("水", "kanji_meaning", syncEpoch = 1L)
        assertNotNull(result)
        assertEquals("水", result!!.kanji)
    }

    @Test
    fun consumeRemovesItemSingleUse() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning")), syncEpoch =1L)
        cache.consume("水", "kanji_meaning", syncEpoch = 1L)
        val second = cache.consume("水", "kanji_meaning", syncEpoch = 1L)
        assertNull(second)
    }

    @Test
    fun consumeWithWrongEpochInvalidatesAndReturnsNull() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning")), syncEpoch =1L)
        val result = cache.consume("水", "kanji_meaning", syncEpoch = 2L)
        assertNull(result)
        assertEquals(0, cache.size())
    }

    @Test
    fun consumeMissingKeyReturnsNull() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning")), syncEpoch =1L)
        val result = cache.consume("火", "word_reading", syncEpoch = 1L)
        assertNull(result)
    }

    @Test
    fun invalidateClearsAll() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning"), item("火", "word_reading")), syncEpoch =1L)
        cache.invalidate()
        assertEquals(0, cache.size())
    }

    @Test
    fun repopulateReplacesOldEntries() {
        val cache = StudyPrefetchCache()
        cache.populate(listOf(item("水", "kanji_meaning")), syncEpoch =1L)
        cache.populate(listOf(item("火", "word_reading")), syncEpoch =2L)
        assertNull(cache.consume("水", "kanji_meaning", syncEpoch = 2L))
        assertNotNull(cache.consume("火", "word_reading", syncEpoch = 2L))
    }

    private fun item(kanji: String, rung: String) = PrefetchedItemData(
        kanji = kanji,
        rung = rung,
        choicePool = null,
        readingPool = null,
    )
}
