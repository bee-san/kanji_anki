package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
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
class LocalStoreMnemonicNotesTest {
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
    fun freshInstallCreatesMnemonicNotesTable() {
        assertTrue(tableExists(store.writableDatabase, LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES))
    }

    @Test
    fun migrationFromThirtyOneCreatesTablePreservesRowsAndIsIdempotent() {
        val db = SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO settings (key, value) VALUES ('sentinel', 'kept')")

            store.onUpgrade(db, 31, 32)

            assertTrue(tableExists(db, LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES))
            db.rawQuery("SELECT value FROM settings WHERE key='sentinel'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kept", cursor.getString(0))
            }
            store.onUpgrade(db, 31, 32)
            assertTrue(tableExists(db, LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES))
        } finally {
            db.close()
        }
    }

    @Test
    fun saveReadOverwriteAndReopenPreserveInternalNewlines() {
        store.saveKanjiMnemonicNote(" 裂 ", "  splits open\n  like a shell  ", 1_000L)

        assertEquals("splits open\n  like a shell", store.kanjiMnemonicNote("裂"))
        assertEquals(1_000L, updatedAt("裂"))

        store.saveKanjiMnemonicNote("裂", "new\nassociation", 2_000L)
        assertEquals("new\nassociation", store.kanjiMnemonicNote(" 裂 "))
        assertEquals(2_000L, updatedAt("裂"))

        store.close()
        store = LocalStore(context)
        assertEquals("new\nassociation", store.kanjiMnemonicNote("裂"))
    }

    @Test
    fun blankSaveDeletesExistingNoteAndInvalidKeysAreIgnored() {
        store.saveKanjiMnemonicNote("裂", "shell story", 1_000L)
        store.saveKanjiMnemonicNote("裂", " \n\t ", 2_000L)
        store.saveKanjiMnemonicNote("   ", "must not be stored", 3_000L)
        store.saveKanjiMnemonicNote(null, "must not be stored", 4_000L)
        store.saveKanjiMnemonicNote("裂開", "must not be stored", 5_000L)
        store.saveKanjiMnemonicNote("A", "must not be stored", 6_000L)
        store.saveKanjiMnemonicNote("あ", "must not be stored", 7_000L)

        assertEquals("", store.kanjiMnemonicNote("裂"))
        assertEquals("", store.kanjiMnemonicNote(" "))
        assertEquals("", store.kanjiMnemonicNote("裂開"))
        assertEquals("", store.kanjiMnemonicNote("A"))
        assertEquals("", store.kanjiMnemonicNote("あ"))
        assertEquals(0, mnemonicNoteCount())
    }

    @Test
    fun successfulSyncInventoryRebuildDoesNotEraseMnemonicNote() {
        store.saveKanjiMnemonicNote("裂", "shell story", 1_000L)

        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList<RecordsImportModels.SuspendedImport>(),
            emptyList<RecordsImportModels.DashboardRow>(),
            RecordsSyncModels.Settings.kikuDefaults(),
            2_000L,
            3_000L,
            null,
        )

        assertEquals("shell story", store.kanjiMnemonicNote("裂"))
        assertFalse(tableIsEmpty(LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES))
    }

    private fun updatedAt(kanji: String): Long {
        return store.readableDatabase.rawQuery(
            "SELECT updated_at FROM ${LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES} WHERE kanji=?",
            arrayOf(kanji),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun mnemonicNoteCount(): Int {
        return store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${LocalStoreBase.TABLE_KANJI_MNEMONIC_NOTES}",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private fun tableIsEmpty(table: String): Boolean {
        return store.readableDatabase.rawQuery("SELECT 1 FROM $table LIMIT 1", null).use { cursor ->
            !cursor.moveToFirst()
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }
    }
}
