package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.SourceBindingRecordCodec
import dev.bee.kanjianki.syncapi.SourceBindingValidationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SqliteSourceBindingStoreTest {
    private lateinit var context: Context
    private lateinit var localStore: LocalStore
    private lateinit var bindingStore: SqliteSourceBindingStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        localStore = LocalStore(context)
        bindingStore = SqliteSourceBindingStore(localStore)
    }

    @After
    fun tearDown() {
        localStore.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun absentBindingLoadsAsNull() {
        assertNull(bindingStore.load())
        assertFalse(bindingStore.legacyAndroidMigrationEligible())
    }

    @Test
    fun bindingRoundTripsOnlyVersionedOpaqueFields() {
        val binding = fixture()

        bindingStore.save(binding)

        assertEquals(binding, bindingStore.load())
        val stored = settingsRows()
        assertEquals(SourceBindingRecordCodec.keys, stored.keys)
        assertFalse(stored.values.any { it.contains("Private profile") })
        assertFalse(stored.values.any { it == "123456789" })
    }

    @Test
    fun replacementAndClearAreAtomicRecordOperations() {
        bindingStore.save(fixture())
        val replacement = fixture(
            providerDigest = "c".repeat(64),
            validationState = SourceBindingValidationState.REVALIDATION_REQUIRED,
            validatedAt = 99L,
        )

        bindingStore.save(replacement)
        assertEquals(replacement, bindingStore.load())

        bindingStore.clear()
        assertNull(bindingStore.load())
        assertEquals(emptyMap<String, String>(), settingsRows())
    }

    @Test
    fun incompleteOrMalformedRecordsFailClosed() {
        putSetting(SourceBindingRecordCodec.KEY_VERSION, "1")
        assertThrows(IllegalArgumentException::class.java) { bindingStore.load() }

        bindingStore.clear()
        for ((key, value) in SourceBindingRecordCodec.encode(fixture())) {
            putSetting(key, value)
        }
        putSetting(SourceBindingRecordCodec.KEY_NOTE_ID_DIGESTS, "raw-note-id")
        assertThrows(IllegalArgumentException::class.java) { bindingStore.load() }
    }

    @Test
    fun legacyMigrationResultAtomicallyReplacesEligibilityMarker() {
        putSetting(
            SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION,
            SourceBindingMigrationRecord.ELIGIBLE,
        )
        val binding = fixture()

        bindingStore.saveLegacyMigrationResult(binding)

        assertEquals(binding, bindingStore.load())
        assertFalse(bindingStore.legacyAndroidMigrationEligible())
        assertFalse(
            settingsRows().containsKey(
                SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION,
            ),
        )
    }

    @Test
    fun failedLegacyMigrationSaveRollsBackBindingAndMarkerDeletion() {
        putSetting(
            SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION,
            SourceBindingMigrationRecord.ELIGIBLE,
        )
        localStore.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_source_binding
            BEFORE INSERT ON ${LocalStoreBase.TABLE_SETTINGS}
            WHEN NEW.key = '${SourceBindingRecordCodec.KEY_SOURCE_KEY_DIGEST}'
            BEGIN
                SELECT RAISE(ABORT, 'injected binding failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) {
            bindingStore.saveLegacyMigrationResult(fixture())
        }

        assertTrue(bindingStore.legacyAndroidMigrationEligible())
        assertNull(bindingStore.load())
        assertEquals(
            mapOf(
                SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION to
                    SourceBindingMigrationRecord.ELIGIBLE,
            ),
            settingsRows(),
        )
    }

    private fun settingsRows(): Map<String, String> {
        val rows = LinkedHashMap<String, String>()
        localStore.readableDatabase.query(
            LocalStoreBase.TABLE_SETTINGS,
            arrayOf("key", LocalStoreBase.COLUMN_VALUE),
            null,
            null,
            null,
            null,
            "key",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows[cursor.getString(0)] = cursor.getString(1)
            }
        }
        return rows
    }

    private fun putSetting(key: String, value: String) {
        localStore.writableDatabase.insertWithOnConflict(
            LocalStoreBase.TABLE_SETTINGS,
            null,
            ContentValues().apply {
                put("key", key)
                put(LocalStoreBase.COLUMN_VALUE, value)
                put(LocalStoreBase.COLUMN_UPDATED_AT, 1L)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun fixture(
        providerDigest: String = "a".repeat(64),
        validationState: SourceBindingValidationState = SourceBindingValidationState.VALIDATED,
        validatedAt: Long = 42L,
    ): PersistedSourceBinding =
        PersistedSourceBinding(
            version = PersistedSourceBinding.CURRENT_VERSION,
            providerKindDigest = providerDigest,
            sourceKeyDigest = "b".repeat(64),
            bindingSalt = "database-local-random-salt",
            noteIdDigests = listOf("d".repeat(64), "e".repeat(64)),
            cardIdDigests = listOf("f".repeat(64)),
            validationState = validationState,
            lastValidatedAtMillis = validatedAt,
        )
}
