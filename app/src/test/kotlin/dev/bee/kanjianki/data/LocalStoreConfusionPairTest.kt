package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.ConfusionPairMiner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreConfusionPairTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun freshInstallReviewLogHasRungColumnDefaultingToSimilarKanji() {
        val db = store.writableDatabase
        assertTrue(columnExists(db, "similar_kanji_review_log", "rung"))

        db.execSQL(
            "INSERT INTO similar_kanji_review_log (target_kanji, choice_signature, selected_kanji, correct, reviewed_at) " +
                "VALUES ('拉', 'sig', '提', 0, 1000)",
        )
        assertEquals(listOf("similar_kanji"), rungValues(db))
    }

    @Test
    fun migrationToTwentyFourAddsRungAndPreservesRows() {
        val db = SQLiteDatabase.create(null)
        db.execSQL(
            "CREATE TABLE similar_kanji_review_log (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "target_kanji TEXT NOT NULL, choice_signature TEXT NOT NULL, selected_kanji TEXT NOT NULL, " +
                "correct INTEGER NOT NULL, reviewed_at INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO similar_kanji_review_log (target_kanji, choice_signature, selected_kanji, correct, reviewed_at) " +
                "VALUES ('拉', 'sig', '提', 0, 1000)",
        )

        store.onUpgrade(db, 23, 24)

        assertTrue(columnExists(db, "similar_kanji_review_log", "rung"))
        assertEquals(listOf("similar_kanji"), rungValues(db))
        db.rawQuery("SELECT target_kanji, selected_kanji, correct, reviewed_at FROM similar_kanji_review_log", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("拉", it.getString(0))
            assertEquals("提", it.getString(1))
            assertEquals(0, it.getInt(2))
            assertEquals(1000L, it.getLong(3))
        }
        db.close()
    }

    @Test
    fun recordChoiceReviewLogPersistsMeaningRungPicks() {
        store.recordChoiceReviewLog("拉", "sig", "提", false, RecordsBase.LadderRung.MEANING_KANJI.wireName(), 1_000L)
        store.recordChoiceReviewLog("拉", "sig", "拉", true, RecordsBase.LadderRung.MEANING_KANJI.wireName(), 2_000L)
        store.recordChoiceReviewLog("", "sig", "提", false, "meaning_kanji", 3_000L)
        store.recordChoiceReviewLog("拉", "sig", "", false, "meaning_kanji", 4_000L)

        val rows = reviewLogRows(store.readableDatabase)
        assertEquals(2, rows.size)
        assertEquals(RowSnapshot("拉", "提", 0, "meaning_kanji"), rows[0])
        assertEquals(RowSnapshot("拉", "拉", 1, "meaning_kanji"), rows[1])
    }

    @Test
    fun submitSimilarChoiceStampsSimilarKanjiRung() {
        val card = RecordsImportModels.SimilarKanjiChoiceCard("拉", "pull", listOf("拉", "提"), "拉\t提")

        store.submitSimilarChoice(card, "提", 1_000L, false)

        val rows = reviewLogRows(store.readableDatabase)
        assertEquals(1, rows.size)
        assertEquals(RowSnapshot("拉", "提", 0, "similar_kanji"), rows[0])
    }

    @Test
    fun rebuildSimilarKanjiPairsMinesUserConfusionPairsAndUnlocksHasSimilarKanji() {
        val now = 200L * 24L * 60L * 60L * 1000L
        seedInventory(listOf("拉", "提", "謎"), now)
        store.recordChoiceReviewLog("拉", "sig", "提", false, "meaning_kanji", now - 1_000L)
        store.recordChoiceReviewLog("提", "sig", "拉", false, "similar_kanji", now - 2_000L)
        // Wrong picks against kanji outside the local inventory never mine.
        store.recordChoiceReviewLog("拉", "sig", "外", false, "meaning_kanji", now - 1_000L)
        store.recordChoiceReviewLog("拉", "sig", "外", false, "meaning_kanji", now - 2_000L)

        store.rebuildSimilarKanjiPairs(SimilarKanjiIndex.empty(), now)

        val pairs = store.allLocalSimilarPairs()
        assertEquals(1, pairs.size)
        assertEquals(ConfusionPairMiner.SOURCE_USER_CONFUSION, pairs[0].source)
        assertEquals(setOf("拉", "提"), setOf(pairs[0].kanjiA, pairs[0].kanjiB))
        assertTrue(store.hasSimilarLocalPair("拉", "提"))

        val annotated = store.annotateSimilarKanjiAvailability(
            listOf(studyItem("拉"), studyItem("提"), studyItem("謎")),
        )
        assertTrue(annotated.first { it.kanji == "拉" }.hasSimilarKanji)
        assertTrue(annotated.first { it.kanji == "提" }.hasSimilarKanji)
        assertFalse(annotated.first { it.kanji == "謎" }.hasSimilarKanji)
    }

    @Test
    fun rebuildKeepsMinedPairsAlongsideStaticPairsAcrossResyncs() {
        val now = 200L * 24L * 60L * 60L * 1000L
        seedInventory(listOf("拉", "提", "謎"), now)
        store.recordChoiceReviewLog("拉", "sig", "謎", false, "meaning_kanji", now - 1_000L)
        store.recordChoiceReviewLog("拉", "sig", "謎", false, "meaning_kanji", now - 2_000L)
        val staticIndex = SimilarKanjiIndex.parseTsv(java.io.StringReader("拉\t提\tkiku:wk-visually-similar\n"))

        store.rebuildSimilarKanjiPairs(staticIndex, now)
        store.rebuildSimilarKanjiPairs(staticIndex, now + 1_000L)

        val sources = store.allLocalSimilarPairs().map { it.source }.sorted()
        assertEquals(listOf("kiku:wk-visually-similar", ConfusionPairMiner.SOURCE_USER_CONFUSION), sources)
    }

    @Test
    fun choiceWrongPickCountsAggregateRecentWrongPicksByTarget() {
        val now = 200L * 24L * 60L * 60L * 1000L
        store.recordChoiceReviewLog("拉", "sig", "提", false, "meaning_kanji", now - 1_000L)
        store.recordChoiceReviewLog("拉", "sig", "提", false, "similar_kanji", now - 2_000L)
        store.recordChoiceReviewLog("拉", "sig", "謎", false, "similar_kanji", now - 3_000L)
        store.recordChoiceReviewLog("拉", "sig", "拉", true, "similar_kanji", now - 4_000L)
        store.recordChoiceReviewLog("拉", "sig", "腕", false, "similar_kanji", now - 100L * 24L * 60L * 60L * 1000L)

        val counts = store.choiceWrongPickCounts(now)

        assertEquals(mapOf("拉" to mapOf("提" to 2, "謎" to 1)), counts)
    }

    @Test
    fun rebuildPrunesWrongPickRowsOlderThanMiningWindow() {
        val now = 200L * 24L * 60L * 60L * 1000L
        val windowStart = ConfusionPairMiner.windowStartMillis(now)
        seedInventory(listOf("拉", "提"), now)
        // Two rows inside the window and one row that predates it.
        store.recordChoiceReviewLog("拉", "sig", "提", false, "meaning_kanji", now - 1_000L)
        store.recordChoiceReviewLog("拉", "sig", "提", false, "similar_kanji", now - 2_000L)
        store.recordChoiceReviewLog("拉", "sig", "提", false, "meaning_kanji", windowStart - 1_000L)
        assertEquals(3, reviewLogRows(store.readableDatabase).size)

        store.rebuildSimilarKanjiPairs(SimilarKanjiIndex.empty(), now)

        // The out-of-window row is deleted; the two in-window rows survive.
        val remaining = reviewLogRows(store.readableDatabase)
        assertEquals(2, remaining.size)
        store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM similar_kanji_review_log WHERE reviewed_at < ?",
            arrayOf(windowStart.toString()),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    private fun seedInventory(kanji: List<String>, nowMillis: Long) {
        val db = store.writableDatabase
        for (glyph in kanji) {
            db.execSQL(
                "INSERT INTO kanji_inventory (kanji, primary_meaning, readings, browser_search, search_text, " +
                    "source_count, example_count, first_seen_at, last_seen_at) VALUES (?, ?, '', '', ?, 1, 1, ?, ?)",
                arrayOf<Any>(glyph, "meaning-$glyph", glyph, nowMillis, nowMillis),
            )
        }
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
    }

    private data class RowSnapshot(
        val target: String,
        val selected: String,
        val correct: Int,
        val rung: String,
    )

    private fun reviewLogRows(db: SQLiteDatabase): List<RowSnapshot> {
        val out = ArrayList<RowSnapshot>()
        db.rawQuery(
            "SELECT target_kanji, selected_kanji, correct, rung FROM similar_kanji_review_log ORDER BY reviewed_at ASC",
            null,
        ).use {
            while (it.moveToNext()) {
                out.add(RowSnapshot(it.getString(0), it.getString(1), it.getInt(2), it.getString(3)))
            }
        }
        return out
    }

    private fun rungValues(db: SQLiteDatabase): List<String> {
        val out = ArrayList<String>()
        db.rawQuery("SELECT rung FROM similar_kanji_review_log ORDER BY id ASC", null).use {
            while (it.moveToNext()) {
                out.add(it.getString(0))
            }
        }
        return out
    }

    private fun columnExists(db: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == columnName) {
                    return true
                }
            }
            false
        }
    }
}
