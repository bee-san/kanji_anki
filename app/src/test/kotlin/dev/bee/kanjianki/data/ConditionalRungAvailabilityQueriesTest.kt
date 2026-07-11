package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConditionalRungAvailabilityQueriesTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        db = store.writableDatabase
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun targetedCapabilitiesMatchGlobalPredicatesForRequestedKanji() {
        listOf("A", "B", "C", "D", "E", "X", "Y").forEach(::insertInventory)
        insertSimilarPair("A", "B")
        insertSimilarPair("D", "missing-inventory")
        insertSimilarPair("X", "Y")

        insertUsage("A", "shared", 1)
        insertUsage("B", "shared", 2)
        insertUsage("C", "shared", 3)
        insertUsage("D", "pair", 4)
        insertUsage("E", "pair", 5)
        insertUsage("X", "unrelated", 6)
        insertPool("A", "shared")
        insertPool("A", "alternate")
        insertPool("D", "pair")
        insertPool("X", "unrelated")
        insertPool("X", "unrelated-alternate")

        insertExample("A", 1, reading = "", sentence = "")
        insertExample("A", 2, reading = "えー", sentence = "A sentence")
        insertExample("D", 3, reading = "でぃー", sentence = "  ")
        insertExample("X", 4, reading = "えっくす", sentence = "Unrelated sentence")

        val requested = setOf("A", "D", "not-present")

        assertEquals(
            store.kanjiWithSimilarNeighbors(db).intersect(requested),
            ConditionalRungAvailabilityQueries.similarKanji(db, requested),
        )
        assertEquals(
            store.kanjiWithKanjiReading(db).intersect(requested),
            ConditionalRungAvailabilityQueries.kanjiReading(db, requested),
        )
        assertEquals(
            store.kanjiWithReadingKanji(db).intersect(requested),
            ConditionalRungAvailabilityQueries.readingKanji(db, requested),
        )
        assertEquals(
            store.kanjiWithSentenceReading(db).intersect(requested),
            ConditionalRungAvailabilityQueries.sentenceReading(db, requested),
        )
        assertEquals(setOf("A"), ConditionalRungAvailabilityQueries.similarKanji(db, requested))
        assertEquals(setOf("A"), ConditionalRungAvailabilityQueries.kanjiReading(db, requested))
        assertEquals(setOf("A"), ConditionalRungAvailabilityQueries.readingKanji(db, requested))
        assertEquals(setOf("A"), ConditionalRungAvailabilityQueries.sentenceReading(db, requested))
    }

    @Test
    fun readingKanjiCountsDistinctGlobalKanjiInOneStatementPerChunk() {
        // Repeated evidence from two kanji is not enough, regardless of note count.
        insertUsage("A", "two-kanji", 1)
        insertUsage("A", "two-kanji", 2)
        insertUsage("B", "two-kanji", 3)
        insertUsage("B", "two-kanji", 4)

        // The third kanji is deliberately not requested: the >=3 predicate is global.
        insertUsage("C", "global-three", 5)
        insertUsage("D", "global-three", 6)
        insertUsage("E", "global-three", 7)

        assertEquals(
            setOf("C"),
            ConditionalRungAvailabilityQueries.readingKanji(db, listOf("A", "C")),
        )

        val sql = ConditionalRungAvailabilityQueries.readingKanjiSql(2)
        val normalizedSql = sql.uppercase(Locale.ROOT)
        assertTrue(normalizedSql.contains("WITH REQUESTED(KANJI) AS (VALUES (?),(?))"))
        assertTrue(normalizedSql.contains("JOIN KANJI_READING_USAGE"))
        assertTrue(normalizedSql.contains("HAVING COUNT(DISTINCT U.KANJI) >= 3"))
        assertFalse(normalizedSql.contains("LIMIT 3"))
        assertEquals(1, ConditionalRungAvailabilityQueries.readingKanjiStatementCount(400))
        assertEquals(2, ConditionalRungAvailabilityQueries.readingKanjiStatementCount(401))

        val details = ArrayList<String>()
        db.rawQuery("EXPLAIN QUERY PLAN $sql", arrayOf("A", "C")).use { cursor ->
            while (cursor.moveToNext()) {
                details.add(cursor.getString(3))
            }
        }
        assertTrue(
            details.joinToString("\n"),
            details.any { it.contains("SEARCH u USING INDEX idx_kanji_reading_usage_reading") },
        )
    }

    @Test
    fun invalidationDuringCapabilityLoadCannotRepublishOldGeneration() {
        val cache = ConditionalRungAvailabilityCache()
        val firstQueryStarted = CountDownLatch(1)
        val releaseFirstQuery = CountDownLatch(1)
        val queryCount = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<Set<String>> {
                cache.load(ConditionalRungCapability.READING_KANJI, listOf("A")) {
                    if (queryCount.incrementAndGet() == 1) {
                        firstQueryStarted.countDown()
                        check(releaseFirstQuery.await(5, TimeUnit.SECONDS))
                        setOf("A") // Result read from the pre-invalidation database generation.
                    } else {
                        emptySet() // Result from the post-invalidation generation.
                    }
                }
            }

            assertTrue(firstQueryStarted.await(5, TimeUnit.SECONDS))
            cache.invalidate()
            releaseFirstQuery.countDown()

            assertEquals(emptySet<String>(), result.get(5, TimeUnit.SECONDS))
            assertEquals(2, queryCount.get())
            assertEquals(1L, cache.generationForTest())

            // The negative result from the retried, current-generation query was cached. If the
            // stale positive publication won the race, this would either return A or query again.
            var queriedAgain = false
            assertEquals(
                emptySet<String>(),
                cache.load(ConditionalRungCapability.READING_KANJI, listOf("A")) {
                    queriedAgain = true
                    setOf("A")
                },
            )
            assertFalse(queriedAgain)
        } finally {
            releaseFirstQuery.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun capabilityCacheSurvivesReviewItemInvalidationAndClearsWithContent() {
        insertExample("A", 1, reading = "えー", sentence = "A sentence")
        store.saveStudyItem(studyItem("A", totalReviews = 0))
        store.saveStudyItem(studyItem("B", totalReviews = 0))

        assertTrue(store.studyItemsForKanji(listOf("A")).single().hasSentenceReading)
        assertFalse(store.studyItemsForKanji(listOf("B")).single().hasSentenceReading)

        // A review clears the study-item cache. Capability evidence is unchanged, so it should
        // come from the independent capability cache rather than rerunning all four sources.
        db.delete(LocalStoreBase.TABLE_KANJI_EXAMPLES, null, null)
        insertExample("B", 2, reading = "びー", sentence = "B sentence")
        store.saveStudyItem(studyItem("A", totalReviews = 1))
        store.saveStudyItem(studyItem("B", totalReviews = 1))
        assertTrue(store.studyItemsForKanji(listOf("A")).single().hasSentenceReading)
        assertFalse(store.studyItemsForKanji(listOf("B")).single().hasSentenceReading)

        // A sync/content invalidation clears both positive and negative capability entries.
        store.clearDashboardRowsCache()
        store.clearStudyItemsCache()
        assertFalse(store.studyItemsForKanji(listOf("A")).single().hasSentenceReading)
        assertTrue(store.studyItemsForKanji(listOf("B")).single().hasSentenceReading)
    }

    @Test
    fun batchedSentenceCapabilityUsesOrderedKanjiIndexForEachRequestedKey() {
        val details = ArrayList<String>()
        db.rawQuery(
            "EXPLAIN QUERY PLAN ${ConditionalRungAvailabilityQueries.sentenceReadingSql(3)}",
            arrayOf("A", "B", "C"),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                details.add(cursor.getString(3))
            }
        }

        assertTrue(details.joinToString("\n"), details.any { it.contains("idx_kanji_examples_ordered") })
    }

    private fun studyItem(kanji: String, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            1.0,
            2.0,
            totalReviews,
            0,
            0,
            0,
            "",
            1_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji-$totalReviews")
            .build()
    }

    private fun insertInventory(kanji: String) {
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_KANJI_INVENTORY} " +
                "(kanji, primary_meaning, readings, browser_search, search_text, source_count, " +
                "example_count, first_seen_at, last_seen_at) VALUES (?, '', '', '', '', 1, 1, 1, 1)",
            arrayOf(kanji),
        )
    }

    private fun insertSimilarPair(a: String, b: String) {
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS} " +
                "(kanji_a, kanji_b, source, first_seen_at, last_seen_at) VALUES (?, ?, 'test', 1, 1)",
            arrayOf(a, b),
        )
    }

    private fun insertUsage(kanji: String, reading: String, noteId: Long) {
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_KANJI_READING_USAGE} " +
                "(kanji, reading, expression, note_id, source_type, mature, lapses, interval_days) " +
                "VALUES (?, ?, ?, ?, 'active', 0, 0, 0)",
            arrayOf<Any>(kanji, reading, "$kanji-$reading", noteId),
        )
    }

    private fun insertPool(kanji: String, reading: String) {
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_KANJI_READING_POOL} (kanji, reading, attested) " +
                "VALUES (?, ?, 1)",
            arrayOf(kanji, reading),
        )
    }

    private fun insertExample(
        kanji: String,
        id: Long,
        reading: String,
        sentence: String,
    ) {
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_KANJI_EXAMPLES} " +
                "(kanji, source_type, card_id, note_id, expression, reading, meaning, sentence, mature, lapses) " +
                "VALUES (?, 'active', ?, ?, ?, ?, '', ?, 0, 0)",
            arrayOf<Any>(kanji, id, id, "$kanji-$id", reading, sentence),
        )
    }
}
