package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.StudyLadderRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreInventoryIntegrationTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        store.writableDatabase
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    private fun insertInventoryItem(kanji: String, meaning: String, readings: String = "test") {
        val values = ContentValues().apply {
            put("kanji", kanji)
            put("primary_meaning", meaning)
            put("readings", readings)
            put("browser_search", kanji)
            put("search_text", "$kanji $meaning $readings")
            put("source_count", 1)
            put("example_count", 1)
            put("first_seen_at", System.currentTimeMillis())
            put("last_seen_at", System.currentTimeMillis())
        }
        store.writableDatabase.insertWithOnConflict(
            LocalStoreBase.TABLE_KANJI_INVENTORY, null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Test
    fun searchEmptyQueryReturnsAll() {
        insertInventoryItem("水", "water")
        insertInventoryItem("火", "fire")
        val results = store.searchKanjiInventory("")
        assertEquals(2, results.size)
    }

    @Test
    fun searchNullQueryReturnsAll() {
        insertInventoryItem("水", "water")
        val results = store.searchKanjiInventory(null)
        assertEquals(1, results.size)
    }

    @Test
    fun searchPartialMatchOnMeaning() {
        insertInventoryItem("水", "water")
        insertInventoryItem("火", "fire")
        val results = store.searchKanjiInventory("wat")
        assertEquals(1, results.size)
        assertEquals("水", results[0].kanji)
    }

    @Test
    fun searchExactMatchOnKanji() {
        insertInventoryItem("水", "water")
        insertInventoryItem("火", "fire")
        val results = store.searchKanjiInventory("水")
        assertEquals(1, results.size)
        assertEquals("水", results[0].kanji)
    }

    @Test
    fun searchSpecialCharacterPercentEscaped() {
        insertInventoryItem("水", "100% water")
        insertInventoryItem("火", "pure fire")
        val results = store.searchKanjiInventory("%")
        assertEquals(1, results.size)
        assertEquals("水", results[0].kanji)
    }

    @Test
    fun searchSpecialCharacterUnderscoreEscaped() {
        insertInventoryItem("水", "under_score")
        insertInventoryItem("火", "no match")
        val results = store.searchKanjiInventory("_")
        assertEquals(1, results.size)
        assertEquals("水", results[0].kanji)
    }

    @Test
    fun searchSpecialCharacterBackslashEscaped() {
        insertInventoryItem("水", "back\\slash")
        insertInventoryItem("火", "plain")
        val results = store.searchKanjiInventory("\\")
        assertEquals(1, results.size)
        assertEquals("水", results[0].kanji)
    }

    @Test
    fun searchQuotesDoNotCrash() {
        insertInventoryItem("水", "it's \"water\"")
        val results = store.searchKanjiInventory("\"water\"")
        assertEquals(1, results.size)
    }

    @Test
    fun searchEmptyInventoryReturnsEmpty() {
        val results = store.searchKanjiInventory("anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun searchCaseInsensitive() {
        insertInventoryItem("水", "Water")
        val results = store.searchKanjiInventory("water")
        assertEquals(1, results.size)
    }

    @Test
    fun searchOnlySimilarKanjiFilterWhenNoPairs() {
        insertInventoryItem("水", "water")
        val results = store.searchKanjiInventory(null, true)
        assertTrue(results.isEmpty())
    }

    @Test
    fun studyQueueSearchFiltersSchedulerMembershipBeforeLimitAndExactRestore() {
        repeat(301) { index ->
            insertInventoryItem("字$index", "unadmitted $index")
        }
        insertInventoryItem("龍", "dragon")
        insertStudyItem("龍", StudyLadderRules.STATE_NEW)
        insertInventoryItem("亀", "turtle")
        insertStudyItem("亀", StudyLadderRules.STATE_RETIRED)

        val queue = store.searchStudyQueueInventory("", false, includeLocallySuspended = false)

        assertEquals(listOf("龍"), queue.map { it.kanji })
        assertTrue(store.searchStudyQueueInventory("亀", false, false).isEmpty())
    }

    @Test
    fun studyQueueManagementIncludesSuspensionsWithoutLeakingUnadmittedRows() {
        insertInventoryItem("水", "water")
        insertStudyItem("水", StudyLadderRules.STATE_NEW)
        insertInventoryItem("火", "fire")
        store.setKanjiLocallySuspended("火", true, 1_000L)
        insertInventoryItem("木", "tree")

        val queue = store.searchStudyQueueInventory("", false, includeLocallySuspended = false)
        val management = store.searchStudyQueueInventory("", false, includeLocallySuspended = true)

        assertEquals(setOf("水"), queue.map { it.kanji }.toSet())
        assertEquals(setOf("水", "火"), management.map { it.kanji }.toSet())
        assertTrue(management.first { it.kanji == "火" }.suspended)
    }

    @Test
    fun timelineForKanjiWithNoEventsReturnsEmptyTimeline() {
        val timeline = store.timelineForKanji("水")
        assertNotNull(timeline)
        assertTrue(timeline.events.isEmpty())
    }

    @Test
    fun multipleItemsReturnInOrder() {
        insertInventoryItem("水", "water")
        insertInventoryItem("火", "fire")
        insertInventoryItem("木", "tree")
        val results = store.searchKanjiInventory("")
        assertEquals(3, results.size)
    }

    @Test
    fun concurrentInsertAndSearchDoesNotThrow() {
        insertInventoryItem("水", "water")
        val thread = Thread {
            val threadStore = LocalStore(context)
            try {
                val values = ContentValues().apply {
                    put("kanji", "火")
                    put("primary_meaning", "fire")
                    put("readings", "ひ")
                    put("browser_search", "火")
                    put("search_text", "火 fire ひ")
                    put("source_count", 1)
                    put("example_count", 1)
                    put("first_seen_at", System.currentTimeMillis())
                    put("last_seen_at", System.currentTimeMillis())
                }
                threadStore.writableDatabase.insertWithOnConflict(
                    LocalStoreBase.TABLE_KANJI_INVENTORY, null, values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            } finally {
                threadStore.close()
            }
        }
        thread.start()
        val results = store.searchKanjiInventory("")
        assertNotNull(results)
        thread.join()
    }

    private fun insertStudyItem(kanji: String, state: String) {
        val values = ContentValues().apply {
            put(LocalStoreBase.COLUMN_KANJI, kanji)
            put(LocalStoreBase.COLUMN_STATE, state)
            put("due_at", 0L)
            put("stability", 0.4)
            put("difficulty", 5.0)
            put("total_reviews", 0)
            put("lapses", 0)
            put("learning_step", 0)
            put("writing_level", 0)
            put(LocalStoreBase.COLUMN_ANSWER_SIGNATURE, "signature-$kanji")
            put(LocalStoreBase.COLUMN_CREATED_AT, 0L)
        }
        store.writableDatabase.insertWithOnConflict(
            LocalStoreBase.TABLE_STUDY_ITEMS,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        store.clearStudyItemsCache()
    }
}
