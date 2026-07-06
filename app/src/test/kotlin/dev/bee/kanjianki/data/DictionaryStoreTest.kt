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

    private fun openStore(): DictionaryStore {
        return DictionaryStore.open(ApplicationProvider.getApplicationContext())
    }

    private fun lookupCoreKanji(): DictionaryLookup.KanjiEntry {
        val entry = openStore().lookupKanji("日")
        assertNotNull(entry)
        return entry!!
    }
}
