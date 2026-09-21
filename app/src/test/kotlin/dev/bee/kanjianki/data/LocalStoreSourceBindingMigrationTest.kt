package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class LocalStoreSourceBindingMigrationTest {
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
    fun v34MigrationMarksSuccessfulExistingMirrorAsLegacyAndroidCandidate() {
        insertSyncRun(LocalStoreBase.STATUS_SUCCESS)
        insertSourceNote(1L)

        store.onUpgrade(store.writableDatabase, 33, 34)

        assertTrue(SqliteSourceBindingStore(store).legacyAndroidMigrationEligible())
    }

    @Test
    fun v34MigrationDoesNotMarkMirrorWithoutSuccessfulSync() {
        insertSyncRun("retryable_error")
        insertSourceNote(1L)

        store.onUpgrade(store.writableDatabase, 33, 34)

        assertFalse(SqliteSourceBindingStore(store).legacyAndroidMigrationEligible())
    }

    @Test
    fun v34MigrationDoesNotMarkEmptyDatabaseEvenWithSuccessfulHistory() {
        insertSyncRun(LocalStoreBase.STATUS_SUCCESS)

        store.onUpgrade(store.writableDatabase, 33, 34)

        assertFalse(SqliteSourceBindingStore(store).legacyAndroidMigrationEligible())
    }

    @Test
    fun committedMirrorEvidenceUsesUnsignedLowestSixtyFourIds() {
        val noteIds = listOf(-1L, Long.MIN_VALUE) + (0L..70L)
        for (noteId in noteIds.reversed()) {
            insertSourceNote(noteId)
        }
        val cardIds = listOf(-1L, Long.MIN_VALUE, Long.MAX_VALUE, 1L, 0L)
        for (cardId in cardIds) {
            insertSourceCard(cardId)
        }

        val evidence = store.collectionMirrorIdentityEvidence()

        assertEquals((0L..63L).toList(), evidence.stableNoteIds)
        assertEquals(
            listOf(0L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -1L),
            evidence.stableCardIds,
        )
    }

    @Test
    fun profileEmptinessIgnoresFailedSyncAttemptsButDetectsKaniProgress() {
        assertTrue(store.isEmptyKaniProfile())

        insertSyncRun("retryable_error")
        assertTrue(store.isEmptyKaniProfile())

        store.writableDatabase.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_STUDY_ITEMS} " +
                "(kanji, state, due_at, stability, difficulty, total_reviews, lapses, " +
                "learning_step, writing_level, created_at) " +
                "VALUES ('字', 'review', 1, 2.0, 3.0, 4, 0, 0, 0, 1)",
        )
        assertFalse(store.isEmptyKaniProfile())
    }

    private fun insertSyncRun(status: String) {
        store.writableDatabase.insertOrThrow(
            LocalStoreBase.TABLE_SYNC_RUNS,
            null,
            ContentValues().apply {
                put("started_at", 1L)
                put("finished_at", 2L)
                put("status", status)
                put("active_notes_count", 1)
                put("active_cards_count", 1)
                put("suspended_cards_archived_count", 0)
                put("suspended_kanji_imported_count", 0)
                put("deleted_notes_count", 0)
                put("deleted_cards_count", 0)
            },
        )
    }

    private fun insertSourceNote(noteId: Long) {
        store.writableDatabase.insertOrThrow(
            LocalStoreBase.TABLE_SOURCE_NOTES,
            null,
            ContentValues().apply {
                put("note_id", noteId)
                put("model_name", "Kiku")
                put("expression", "字")
                put("reading", "じ")
                put("meaning", "character")
                put("sentence", "")
                put("fields_json", "{}")
                put("tags", "")
                put("last_seen_sync_id", 1L)
            },
        )
    }

    private fun insertSourceCard(cardId: Long) {
        store.writableDatabase.insertOrThrow(
            LocalStoreBase.TABLE_SOURCE_CARDS,
            null,
            ContentValues().apply {
                put("card_id", cardId)
                put("note_id", 1L)
                put("deck_name", "Kiku")
                put("ord", 0)
                put("queue", 2)
                put("type", 2)
                put("due", 1)
                put("interval_days", 1)
                put("reps", 1)
                put("lapses", 0)
                putNull("fsrs_stability")
                putNull("fsrs_difficulty")
                putNull("fsrs_retrievability")
                put("last_seen_sync_id", 1L)
            },
        )
    }
}
