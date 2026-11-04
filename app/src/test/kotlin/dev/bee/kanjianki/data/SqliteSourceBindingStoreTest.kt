package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.SourceBindingRecordCodec
import dev.bee.kanjianki.syncapi.SourceBindingResetScope
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

    @Test
    fun explicitRebindAtomicallyClearsProviderStateAndPreservesKaniProgress() {
        seedProviderProjectionAndKaniProgress()
        putSetting(REPAIRED_HANDOFF_SETTING_KEY, "修")
        val replacement = fixture(
            providerDigest = "c".repeat(64),
            validatedAt = 99L,
        )

        bindingStore.saveExplicitRecoveryResult(
            replacement,
            SourceBindingResetScope.PROVIDER_PROJECTIONS_AND_WRITE_RECEIPTS,
        )

        assertEquals(replacement, bindingStore.load())
        assertEquals(0, rowCount(LocalStoreBase.TABLE_SOURCE_NOTES))
        assertEquals(0, rowCount(LocalStoreBase.TABLE_DASHBOARD_ROWS))
        assertEquals(0, rowCount(LocalStoreBase.TABLE_MISSING_KANJI_EXPORTS))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_SUSPENDED_IMPORTS))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_SUSPENDED_SOURCES))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_SUSPENDED_ARCHIVE))
        assertNull(suspendedArchiveRestoredAt())
        assertEquals(1, rowCount(LocalStoreBase.TABLE_STUDY_ITEMS))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_REVIEW_LOG))
        assertFalse(settingsRows().containsKey(REPAIRED_HANDOFF_SETTING_KEY))
    }

    @Test
    fun failedExplicitRebindRollsBackProviderResetBindingAndMarker() {
        val original = fixture()
        bindingStore.save(original)
        seedProviderProjectionAndKaniProgress()
        putSetting(
            SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION,
            SourceBindingMigrationRecord.ELIGIBLE,
        )
        localStore.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_explicit_source_binding
            BEFORE INSERT ON ${LocalStoreBase.TABLE_SETTINGS}
            WHEN NEW.key = '${SourceBindingRecordCodec.KEY_SOURCE_KEY_DIGEST}'
            BEGIN
                SELECT RAISE(ABORT, 'injected explicit binding failure');
            END
            """.trimIndent(),
        )

        assertThrows(SQLiteException::class.java) {
            bindingStore.saveExplicitRecoveryResult(
                fixture(providerDigest = "c".repeat(64), validatedAt = 99L),
                SourceBindingResetScope.PROVIDER_PROJECTIONS_AND_WRITE_RECEIPTS,
            )
        }

        assertEquals(original, bindingStore.load())
        assertTrue(bindingStore.legacyAndroidMigrationEligible())
        assertEquals(1, rowCount(LocalStoreBase.TABLE_SOURCE_NOTES))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_DASHBOARD_ROWS))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_MISSING_KANJI_EXPORTS))
        assertEquals(9L, suspendedArchiveRestoredAt())
        assertEquals(1, rowCount(LocalStoreBase.TABLE_STUDY_ITEMS))
        assertEquals(1, rowCount(LocalStoreBase.TABLE_REVIEW_LOG))
    }

    private fun seedProviderProjectionAndKaniProgress() {
        val database = localStore.writableDatabase
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SOURCE_NOTES} " +
                "(note_id, model_name, expression, reading, meaning, sentence, " +
                "fields_json, tags, last_seen_sync_id) " +
                "VALUES (1, 'Model', '修理', 'しゅうり', 'repair', '', '{}', '', 1)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_DASHBOARD_ROWS} " +
                "(kanji, jiten_rank, primary_meaning, reading, browser_search, weakness_score, " +
                "reason_code, reason_text, active_example_count, suspended_example_count, " +
                "mature_support_count, rebuilt_at) " +
                "VALUES ('修', 100, 'repair', 'しゅう', '修', 10, 'weak', 'weak', 1, 0, 0, 1)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_MISSING_KANJI_EXPORTS} " +
                "(literal, destination_key, exported_at, external_note_id) " +
                "VALUES ('修', 'old-provider', 1, 99)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SUSPENDED_IMPORTS} " +
                "(kanji, jiten_rank, rank_known, cutoff_used, first_imported_at, last_seen_sync_id) " +
                "VALUES ('修', 100, 1, 2500, 1, 1)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SUSPENDED_SOURCES} " +
                "(kanji, card_id, note_id, expression, reading, meaning, sentence, sync_id) " +
                "VALUES ('修', 2, 1, '修理', 'しゅうり', 'repair', '', 1)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SUSPENDED_ARCHIVE} " +
                "(card_id, note_id, deck_name, model_name, expression, reading, meaning, " +
                "sentence, fields_json, archived_at, archived_sync_id, restored_at) " +
                "VALUES (2, 1, 'Deck', 'Model', '修理', 'しゅうり', 'repair', '', '{}', 1, 1, 9)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_STUDY_ITEMS} " +
                "(kanji, state, due_at, stability, difficulty, total_reviews, lapses, " +
                "learning_step, writing_level, answer_signature, created_at) " +
                "VALUES ('修', 'review', 100, 5.0, 4.0, 8, 1, 0, 2, '修理', 1)",
        )
        database.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_REVIEW_LOG} " +
                "(kanji, token, rating, writing_required, writing_passed, manual_override, " +
                "reviewed_at, answer_signature) " +
                "VALUES ('修', 'review-token', 'good', 0, 0, 0, 1, '修理')",
        )
    }

    private fun rowCount(table: String): Int =
        localStore.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun suspendedArchiveRestoredAt(): Long? =
        localStore.readableDatabase.rawQuery(
            "SELECT restored_at FROM ${LocalStoreBase.TABLE_SUSPENDED_ARCHIVE} WHERE card_id=2",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getLong(0)
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
