package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        assertTrue(databaseFile.createNewFile())

        val result = WidgetLocalStoreReader.read(context) { "opened" }

        assertSame(WidgetStoreRead.Corrupt, result)
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

    private fun cleanDatabasePath() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        context.getDatabasePath(LocalStoreSchema.DB_NAME).deleteRecursively()
    }
}
