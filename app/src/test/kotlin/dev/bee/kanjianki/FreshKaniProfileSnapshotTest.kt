package dev.bee.kanjianki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreSchema
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FreshKaniProfileSnapshotTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun createsCurrentEmptySchemaWithoutTouchingApplicationDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationDatabase = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        val applicationDatabaseExisted = applicationDatabase.exists()
        val destination = File(temp.root, "fresh-profile.db")

        FreshKaniProfileSnapshot.create(context, destination)

        assertTrue(destination.isFile)
        SQLiteDatabase.openDatabase(
            destination.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            assertEquals(LocalStoreSchema.DB_VERSION, database.version)
            assertEquals(0, rowCount(database, LocalStoreBase.TABLE_SOURCE_NOTES))
            assertEquals(0, rowCount(database, LocalStoreBase.TABLE_STUDY_ITEMS))
            assertEquals(0, rowCount(database, LocalStoreBase.TABLE_REVIEW_LOG))
        }
        assertEquals(applicationDatabaseExisted, applicationDatabase.exists())
        assertFalse(
            context.cacheDir.listFiles().orEmpty().any { file ->
                file.name.startsWith("kani-fresh-profile-")
            },
        )
    }

    @Test
    fun refusesToReplaceAnExistingDestination() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val destination = temp.newFile("existing.db").apply {
            writeText("preserve")
        }

        assertThrows(IOException::class.java) {
            FreshKaniProfileSnapshot.create(context, destination)
        }

        assertEquals("preserve", destination.readText())
    }

    private fun rowCount(database: SQLiteDatabase, table: String): Int =
        database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
