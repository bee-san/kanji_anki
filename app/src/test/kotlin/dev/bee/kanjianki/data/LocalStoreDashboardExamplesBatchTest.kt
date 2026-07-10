package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locks in that dashboard loading uses ordered, bounded example reads. The previous `IN` query
 * returned every matching database row before applying the per-kanji cap in Kotlin, making cold
 * startup proportional to the complete collection rather than the 120 rows visible on Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreDashboardExamplesBatchTest {
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
    fun boundedExamplesMatchPerKanjiReadsWithOrderingAndCap() {
        val rows = listOf(
            dashboardRow("見", exampleCount = 10),
            dashboardRow("書", exampleCount = 3),
            dashboardRow("読", exampleCount = 0),
        )
        val db = store.writableDatabase
        store.saveRows(db, rows, 1_000L)

        val batched = store.examplesForKanjiBatch(db, listOf("見", "書", "読", "未使用"))

        for (kanji in listOf("見", "書", "読")) {
            val perKanji = store.examplesForKanji(db, kanji)
            val fromBatch = batched[kanji] ?: emptyList()
            assertEquals(
                "batched example count mismatch for $kanji",
                perKanji.size,
                fromBatch.size,
            )
            assertEquals(
                "batched example order mismatch for $kanji",
                perKanji.map { it.expression },
                fromBatch.map { it.expression },
            )
        }

        // 8-example cap preserved even though 見 was saved with 10 examples.
        assertEquals(8, batched["見"]?.size)
        assertEquals(
            listOf("見1", "見3", "見5", "見7", "見9", "見0", "見2", "見4"),
            batched["見"]?.map { it.expression },
        )
        // Missing kanji simply have no entry.
        assertEquals(null, batched["未使用"])
    }

    @Test
    fun dashboardLoadMaterializesAtMostEightExamplesForEachOfIts120Headers() {
        val rows = (0..120).map { index ->
            dashboardRow(
                kanji = "字${index.toString().padStart(3, '0')}",
                exampleCount = if (index == 0) 500 else 9,
                weaknessScore = 1_000 - index,
                suspendedExampleCount = index % 3,
            )
        }
        val db = store.writableDatabase
        db.beginTransaction()
        try {
            store.saveRows(db, rows, 1_000L)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val loaded = store.dashboardRows()

        assertEquals(120, loaded.size)
        assertEquals((0 until 120).map { "字${it.toString().padStart(3, '0')}" }, loaded.map { it.kanji })
        assertTrue(loaded.all { it.examples.size == 8 })
        assertEquals(960, loaded.sumOf { it.examples.size })
        assertEquals(
            listOf("字0001", "字0003", "字0005", "字0007", "字0009", "字00011", "字00013", "字00015"),
            loaded.first().examples.map { it.expression },
        )
    }

    @Test
    fun freshSchemaHasDashboardOrderingIndexes() {
        val db = store.writableDatabase

        assertDashboardIndexes(db)
    }

    @Test
    fun migrationTwentyNineToThirtyAddsDashboardIndexesAndPreservesRows() {
        val db = SQLiteDatabase.create(null)
        db.execSQL(
            "CREATE TABLE ${LocalStoreBase.TABLE_DASHBOARD_ROWS} (" +
                "kanji TEXT PRIMARY KEY, weakness_score INTEGER NOT NULL, " +
                "suspended_example_count INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE ${LocalStoreBase.TABLE_KANJI_EXAMPLES} (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, source_type TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX idx_examples_kanji ON ${LocalStoreBase.TABLE_KANJI_EXAMPLES}(kanji)",
        )
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_DASHBOARD_ROWS} " +
                "(kanji, weakness_score, suspended_example_count) VALUES ('脱', 9, 2)",
        )
        db.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_KANJI_EXAMPLES} " +
                "(kanji, source_type) VALUES ('脱', 'active')",
        )

        store.onUpgrade(db, 29, 30)

        assertDashboardIndexes(db)
        assertEquals(1, countRows(db, LocalStoreBase.TABLE_DASHBOARD_ROWS))
        assertEquals(1, countRows(db, LocalStoreBase.TABLE_KANJI_EXAMPLES))
        // CREATE INDEX IF NOT EXISTS keeps the migration safe if startup retries it.
        store.onUpgrade(db, 29, 30)
        db.close()
    }

    private fun dashboardRow(
        kanji: String,
        exampleCount: Int,
        weaknessScore: Int = 5,
        suspendedExampleCount: Int = 0,
    ): RecordsImportModels.DashboardRow {
        val examples = List(exampleCount) { index ->
            RecordsImportModels.Example(
                if (index % 2 == 0) "active" else "suspended",
                (index + 1).toLong(),
                (100 + index).toLong(),
                "$kanji$index",
                "reading$index",
                "meaning$index",
                "sentence$index",
                false,
                0,
                index,
                index,
                null,
                null,
                null,
            )
        }
        return RecordsImportModels.DashboardRow(
            kanji,
            null,
            "meaning $kanji",
            "reading $kanji",
            "browser $kanji",
            weaknessScore,
            "reason",
            "reason text",
            exampleCount,
            suspendedExampleCount,
            0,
            examples,
        )
    }

    private fun assertDashboardIndexes(db: SQLiteDatabase) {
        assertNull(indexSql(db, "idx_examples_kanji"))
        assertEquals(
            "CREATE INDEX idx_dashboard_rows_priority ON " +
                "dashboard_rows(weakness_score DESC, suspended_example_count DESC, kanji ASC)",
            indexSql(db, "idx_dashboard_rows_priority"),
        )
        assertEquals(
            "CREATE INDEX idx_kanji_examples_ordered ON " +
                "kanji_examples(kanji, source_type DESC, id ASC)",
            indexSql(db, "idx_kanji_examples_ordered"),
        )
    }

    private fun indexSql(db: SQLiteDatabase, index: String): String? {
        return db.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(index),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    private fun countRows(db: SQLiteDatabase, table: String): Int {
        return db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }
}
