package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DictionaryStoreTest {
    private val missingKanjiInputs = arrayOf<String?>(null, " ", "not-kanji")

    @Test
    fun bundledDictionaryInstallsKanjiData() {
        val store = openStore()

        assertTrue(store.kanjiCount() > 1000)
    }

    @Test
    fun lookupReturnsRequestedLiteral() {
        val entry = lookupCoreKanji()

        assertEquals("日", entry.literal)
    }

    @Test
    fun lookupReturnsMeanings() {
        val entry = lookupCoreKanji()

        assertTrue(entry.meanings.contains("day"))
    }

    @Test
    fun lookupReturnsOnReadings() {
        val entry = lookupCoreKanji()

        assertTrue(entry.onReadings.contains("ニチ"))
    }

    @Test
    fun lookupReturnsKunReadings() {
        val entry = lookupCoreKanji()

        assertTrue(entry.kunReadings.contains("ひ"))
    }

    @Test
    fun lookupReturnsStrokeCount() {
        val entry = lookupCoreKanji()

        assertTrue(entry.strokeCount > 0)
    }

    @Test
    fun bundledDictionaryExposesRanks() {
        val store = openStore()

        val ranks: JitenKanjiRanks = store.jitenRanks()
        assertTrue(ranks.size() > 1000)
    }

    @Test
    fun bundledDictionaryRanksCoreKanji() {
        val ranks = openStore().jitenRanks()

        assertTrue((ranks.rankOf("日") ?: 0) > 0)
    }

    @Test
    fun bundledDictionaryExposesManifest() {
        val context: Context = ApplicationProvider.getApplicationContext()

        assertTrue(DictionaryStore.activeManifestText(context).contains("\"schema_version\""))
    }

    @Test
    fun lookupRejectsMissingKanji() {
        val store = DictionaryStore.open(ApplicationProvider.getApplicationContext())

        for (input in missingKanjiInputs) {
            assertNull(store.lookupKanji(input))
        }
    }

    @Test
    fun repeatedLookupServesCachedEntryInstance() {
        val store = openStore()

        val first = store.lookupKanji("日")
        val second = store.lookupKanji("日")

        assertNotNull(first)
        // The per-store entry cache returns the identical instance, proving the
        // second lookup never re-queried (or re-opened) the SQLite database.
        assertSame(first, second)
    }

    @Test
    fun repeatedMissLookupIsCachedAsNull() {
        val store = openStore()

        assertNull(store.lookupKanji("not-kanji"))
        assertNull(store.lookupKanji("not-kanji"))
    }

    @Test
    fun rankRangeQueryLoadsTopFiveThousandInStableOrder() {
        val store = openStore()
        val range = DictionaryLookup.JitenRankRange(1, 5_000)

        val page = store.kanjiByJitenRank(range, offset = 0, limit = 5_000)

        assertEquals(store.eligibleKanjiCount(range), page.totalEligible)
        assertTrue(page.entries.size > 1_000)
        assertTrue(page.entries.size <= 5_000)
        assertEquals(1, page.entries.first().jitenRank)
        assertTrue(page.entries.zipWithNext().all { (left, right) ->
            val leftRank = left.jitenRank ?: Int.MAX_VALUE
            val rightRank = right.jitenRank ?: Int.MAX_VALUE
            leftRank < rightRank || leftRank == rightRank && left.literal <= right.literal
        })
    }

    @Test
    fun rankRangeQueryPagesAndOptionallyAppendsUnrankedEntries() {
        val store = openStore()
        val rankedRange = DictionaryLookup.JitenRankRange(1, 10)
        val ranked = store.kanjiByJitenRank(rankedRange, offset = 0, limit = 3)
        val next = store.kanjiByJitenRank(rankedRange, offset = ranked.nextOffset!!, limit = 20)
        val withUnranked = store.kanjiByJitenRank(
            DictionaryLookup.JitenRankRange(1, 1, includeUnranked = true),
            offset = 0,
            limit = DictionaryLookup.MAX_KANJI_PAGE_SIZE,
        )

        assertEquals(3, ranked.entries.size)
        assertTrue(next.entries.isNotEmpty())
        assertTrue(withUnranked.entries.any { it.jitenRank == null })
        assertTrue(withUnranked.entries.takeWhile { it.jitenRank != null }.all { it.jitenRank == 1 })
        assertTrue(withUnranked.entries.dropWhile { it.jitenRank != null }.all { it.jitenRank == null })
    }

    private fun openStore(): DictionaryStore {
        return DictionaryStore.open(ApplicationProvider.getApplicationContext())
    }

    private fun lookupCoreKanji(): DictionaryLookup.KanjiEntry {
        val entry = openStore().lookupKanji("日")
        assertNotNull(entry)
        return entry!!
    }
}
