package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreSuppressionMigrationTest {
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
    fun upgradeToTwentyFiveClearsStaleSuppressionFlags() {
        val db = store.writableDatabase
        insertStudyItem(db, "裂", "word_reading", 1234L)
        insertStudyItem(db, "語", "", 0L)

        store.onUpgrade(db, 24, 25)

        assertEquals("" to 0L, suppressionFor(db, "裂"))
        assertEquals("" to 0L, suppressionFor(db, "語"))
    }

    private fun insertStudyItem(
        db: SQLiteDatabase,
        kanji: String,
        suppressedByTaskType: String,
        suppressedAt: Long,
    ) {
        db.execSQL(
            "INSERT INTO study_items (kanji, state, due_at, stability, difficulty, total_reviews, lapses, " +
                "learning_step, writing_level, suppressed_by_task_type, suppressed_at, created_at) " +
                "VALUES (?, 'review', 1000, 1.0, 5.0, 1, 0, 0, 0, ?, ?, 1000)",
            arrayOf<Any>(kanji, suppressedByTaskType, suppressedAt),
        )
    }

    private fun suppressionFor(db: SQLiteDatabase, kanji: String): Pair<String, Long> {
        db.rawQuery(
            "SELECT suppressed_by_task_type, suppressed_at FROM study_items WHERE kanji = ?",
            arrayOf(kanji),
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Missing study item for $kanji" }
            return cursor.getString(0) to cursor.getLong(1)
        }
    }
}
