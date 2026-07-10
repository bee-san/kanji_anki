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
class StatsCacheSchemaTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun dbVersionIsTwentyEight() {
        assertEquals(28, LocalStoreSchema.DB_VERSION)
    }

    @Test
    fun createInitialTablesCreatesStatsCacheTables() {
        val store = LocalStore(context)
        val db = store.writableDatabase

        assertStatsCacheTablesExist(db)
        assertStatsScreenCacheHasCacheFormatVersionColumn(db)
        assertEquals(1L, statsSourceVersion(db))
        store.close()
    }

    @Test
    fun migrationToTwentyThreeAddsStatsCacheFormatVersionColumn() {
        val store = LocalStore(context)
        val db = SQLiteDatabase.create(null)

        createLegacyStatsCacheTables(db)
        db.execSQL(
            "INSERT INTO stats_screen_cache (id, source_version, generated_at, outcome_json, impact_report_json) VALUES (1, 1, 123, '{}', '{}')"
        )

        store.onUpgrade(db, 22, 23)

        assertStatsCacheTablesExist(db)
        assertStatsScreenCacheHasCacheFormatVersionColumn(db)
        assertEquals(1, statsScreenCacheCacheFormatVersion(db))
        assertEquals(1L, statsSourceVersion(db))
        db.close()
        store.close()
    }

    private fun createLegacyStatsCacheTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE stats_cache_state (key TEXT PRIMARY KEY, value INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE stats_screen_cache (id INTEGER PRIMARY KEY CHECK (id = 1), source_version INTEGER NOT NULL, generated_at INTEGER NOT NULL, outcome_json TEXT NOT NULL, impact_report_json TEXT NOT NULL)"
        )
        db.execSQL("INSERT INTO stats_cache_state (key, value) VALUES ('stats_source_version', 1)")
    }

    private fun assertStatsCacheTablesExist(db: SQLiteDatabase) {
        assertTrue(tableExists(db, "stats_cache_state"))
        assertTrue(tableExists(db, "stats_screen_cache"))
    }

    private fun assertStatsScreenCacheHasCacheFormatVersionColumn(db: SQLiteDatabase) {
        assertTrue(columnExists(db, "stats_screen_cache", "cache_format_version"))
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName),
        ).use { cursor -> cursor.moveToFirst() }
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

    private fun statsSourceVersion(db: SQLiteDatabase): Long {
        return db.rawQuery(
            "SELECT value FROM stats_cache_state WHERE key='stats_source_version'",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun statsScreenCacheCacheFormatVersion(db: SQLiteDatabase): Int {
        return db.rawQuery(
            "SELECT cache_format_version FROM stats_screen_cache WHERE id=1",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }
}
