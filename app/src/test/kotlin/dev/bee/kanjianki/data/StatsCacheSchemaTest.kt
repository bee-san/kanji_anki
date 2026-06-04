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
    fun dbVersionIsTwentyTwo() {
        assertEquals(22, LocalStoreSchema.DB_VERSION)
    }

    @Test
    fun createInitialTablesCreatesStatsCacheTables() {
        val store = LocalStore(context)
        val db = store.writableDatabase

        assertStatsCacheTablesExist(db)
        assertEquals(1L, statsSourceVersion(db))
        store.close()
    }

    @Test
    fun migrationToTwentyTwoCreatesStatsCacheTables() {
        val store = LocalStore(context)
        val db = SQLiteDatabase.create(null)

        store.onUpgrade(db, 21, 22)

        assertStatsCacheTablesExist(db)
        assertEquals(1L, statsSourceVersion(db))
        db.close()
        store.close()
    }

    private fun assertStatsCacheTablesExist(db: SQLiteDatabase) {
        assertTrue(tableExists(db, "stats_cache_state"))
        assertTrue(tableExists(db, "stats_screen_cache"))
    }

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName),
        ).use { cursor -> cursor.moveToFirst() }
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
}
