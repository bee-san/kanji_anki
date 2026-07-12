package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreIntegrityMigrationTest {
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
    fun upgradeFromThirtyAddsIntegrityColumnsWithSafeDefaults() {
        val db = SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE study_items (kanji TEXT PRIMARY KEY)")
            db.execSQL("CREATE TABLE review_log (token TEXT UNIQUE)")

            store.onUpgrade(db, 30, 31)

            assertColumns(
                db,
                "study_items",
                "scheduler_revision",
                "routing_version",
                "adaptive_route_state_json",
            )
            assertColumns(
                db,
                "review_log",
                "core_skill",
                "failure_cause",
                "evidence_source",
                "selected_answer",
                "correct_answer",
                "answer_evidence_json",
            )

            db.execSQL("INSERT INTO study_items (kanji) VALUES ('痛')")
            db.rawQuery(
                "SELECT scheduler_revision, routing_version, adaptive_route_state_json FROM study_items",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("", cursor.getString(2))
            }
        } finally {
            db.close()
        }
    }

    private fun assertColumns(db: SQLiteDatabase, table: String, vararg expected: String) {
        val names = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                names.add(cursor.getString(nameIndex))
            }
        }
        for (column in expected) {
            assertTrue("Missing $table.$column", names.contains(column))
        }
    }
}
