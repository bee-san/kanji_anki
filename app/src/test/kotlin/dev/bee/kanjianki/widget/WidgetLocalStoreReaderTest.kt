package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.KaniTestDatabase
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLocalStoreReaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanDatabasePath()
    }

    @After
    fun tearDown() {
        cleanDatabasePath()
    }

    @Test
    fun missingPathReturnsNotSetUpWithoutCreatingDatabase() {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)

        val result = WidgetLocalStoreReader.read(context) { "opened" }

        assertSame(WidgetStoreRead.NotSetUp, result)
        assertFalse(databaseFile.exists())
    }

    @Test
    fun directoryAtDatabasePathReturnsNotSetUpWithoutReplacingDirectory() {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        assertTrue(databaseFile.mkdirs())

        val result = WidgetLocalStoreReader.read(context) { "opened" }

        assertSame(WidgetStoreRead.NotSetUp, result)
        assertTrue(databaseFile.isDirectory)
    }

    @Test
    fun halfCreatedFileReturnsCorruptWithoutRepairingIt() {
        val databaseFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        databaseFile.parentFile?.mkdirs()
        // Establish the precondition rather than assert that cleanup produced it. This
        // previously did `assertTrue(databaseFile.createNewFile())`, which fails when the
        // path already exists — and many tests in this module share one database path
        // inside the same Robolectric sandbox, so that assertion reported *leftover state*
        // as a broken widget reader. It failed twice in CI's full-suite run while passing
        // in isolation, the second time because deleting the chain reordered the suite.
        // The subject is what the reader does with a zero-length file, so the test makes
        // one and gets on with it.
        databaseFile.delete()
        databaseFile.createNewFile()
        assertTrue("the fixture must be a zero-length file", databaseFile.isFile)
        assertEquals(0L, databaseFile.length())

        val result = WidgetLocalStoreReader.read(context) { "opened" }

        assertSame(WidgetStoreRead.Corrupt, result)
        // Still zero-length: the reader reports corruption rather than repairing in place,
        // which is the actual contract here.
        assertEquals(0L, databaseFile.length())
    }

    @Test
    fun validStoreReturnsBlockValue() {
        LocalStore(context).use { store ->
            store.putStringSetting("widget_reader_test", "ready")
        }

        val result = WidgetLocalStoreReader.read(context) { store ->
            store.getStringSetting("widget_reader_test", null)
        }

        assertEquals(WidgetStoreRead.Ready("ready"), result)
    }

    /**
     * Clears the database path through the shared helper.
     *
     * `KaniTestDatabase.delete` removes the database *and* SQLite's `-journal`/`-wal`/`-shm`
     * sidecars, which is what this test needs and what a bare `deleteDatabase` misses. Kept
     * as one helper rather than a local copy because many tests in this module share the one
     * database path in the same Robolectric sandbox.
     */
    private fun cleanDatabasePath() {
        KaniTestDatabase.delete(context)
        context.getDatabasePath(LocalStoreSchema.DB_NAME).deleteRecursively()
    }
}
