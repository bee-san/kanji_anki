package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.SyncSnapshotRetentionPolicy
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
class SyncSnapshotRetentionTest {
    private lateinit var context: Context
    private lateinit var localStore: LocalStore
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        localStore = LocalStore(context)
        db = localStore.writableDatabase
        localStore.createHistoricalSyncTables(db)
    }

    @After
    fun tearDown() {
        localStore.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun pruningRemovesSupersededCardAndNoteSnapshotsButKeepsRetentionWindow() {
        val totalSyncs = SyncSnapshotRetentionPolicy.KEEP_LATEST + 5L
        for (syncId in 1..totalSyncs) {
            insertSyncRun(syncId, finishedAt = syncId * 1_000L)
            insertCardSnapshot(syncId, cardId = 100L, noteId = 10L, intervalDays = syncId.toInt())
            insertNoteSnapshot(syncId, noteId = 10L, kanji = "痛")
            insertKanjiSnapshot(syncId, "痛", weakness = 80 - syncId.toInt(), active = 1)
        }

        localStore.pruneSupersededSnapshots(db)

        val remainingCardSyncIds = distinctSyncIds(TABLE_SYNC_CARD_SNAPSHOTS)
        val remainingNoteSyncIds = distinctSyncIds(TABLE_SYNC_NOTE_SNAPSHOTS)

        // Earliest sync retained as global baseline.
        assertTrue(remainingCardSyncIds.contains(1L))
        assertTrue(remainingNoteSyncIds.contains(1L))
        // Newest KEEP_LATEST syncs retained.
        val newestKept = (totalSyncs - SyncSnapshotRetentionPolicy.KEEP_LATEST + 1..totalSyncs).toList()
        assertTrue(remainingCardSyncIds.containsAll(newestKept))
        // Middle syncs pruned.
        for (middle in 2 until (totalSyncs - SyncSnapshotRetentionPolicy.KEEP_LATEST + 1)) {
            assertFalse("card sync $middle should be pruned", remainingCardSyncIds.contains(middle))
            assertFalse("note sync $middle should be pruned", remainingNoteSyncIds.contains(middle))
        }
        // Per-kanji aggregate table is fully retained.
        assertEquals(totalSyncs.toInt(), distinctSyncIds(TABLE_SYNC_KANJI_SNAPSHOTS).size)
    }

    @Test
    fun impactReportStillComputesSameCardDeltaAfterPruning() {
        val totalSyncs = SyncSnapshotRetentionPolicy.KEEP_LATEST + 5L
        for (syncId in 1..totalSyncs) {
            insertSyncRun(syncId, finishedAt = syncId * 1_000L)
            // Same card_id across syncs; interval grows so baseline vs latest differ.
            insertCardSnapshot(syncId, cardId = 100L, noteId = 10L, intervalDays = syncId.toInt() * 5)
            insertNoteSnapshot(syncId, noteId = 10L, kanji = "痛")
            insertKanjiSnapshot(syncId, "痛", weakness = 80 - syncId.toInt(), active = 1)
        }
        // A study signal so the baseline resolves to the earliest retained sync.
        insertStudyItem("痛", createdAt = 1L)
        insertReview("痛", reviewedAt = 1_500L)

        val before = KanjiImpactReportStore(localStore).report(db)
        localStore.pruneSupersededSnapshots(db)
        val after = KanjiImpactReportStore(localStore).report(db)

        assertEquals(before.rows.size, after.rows.size)
        assertTrue(after.rows.isNotEmpty())
        val beforeRow = before.rows.first { it.kanji == "痛" }
        val afterRow = after.rows.first { it.kanji == "痛" }
        assertEquals(beforeRow.bucket, afterRow.bucket)
        assertEquals(beforeRow.currentCardCount, afterRow.currentCardCount)
        assertEquals(beforeRow.reviewCount, afterRow.reviewCount)
    }

    private fun distinctSyncIds(table: String): Set<Long> {
        val ids = HashSet<Long>()
        db.rawQuery("SELECT DISTINCT sync_id FROM $table", null).use {
            while (it.moveToNext()) {
                ids.add(it.getLong(0))
            }
        }
        return ids
    }

    private fun insertSyncRun(id: Long, finishedAt: Long) {
        db.execSQL(
            "INSERT INTO sync_runs " +
                "(id, started_at, finished_at, status, active_notes_count, active_cards_count, suspended_cards_archived_count, " +
                "suspended_kanji_imported_count, deleted_notes_count, deleted_cards_count, error_code, error_message, removal_message) " +
                "VALUES (?, ?, ?, 'success', 0, 0, 0, 0, 0, 0, NULL, NULL, NULL)",
            arrayOf<Any>(id, finishedAt - 100L, finishedAt),
        )
    }

    private fun insertCardSnapshot(syncId: Long, cardId: Long, noteId: Long, intervalDays: Int) {
        db.execSQL(
            "INSERT INTO sync_card_snapshots " +
                "(sync_id, started_at, finished_at, card_id, note_id, deck_id, deck_name, model_id, model_name, ord, queue, " +
                "type, due, interval_days, reps, lapses, suspended, fsrs_stability, fsrs_difficulty, fsrs_retrievability, mature) " +
                "VALUES (?, ?, ?, ?, ?, '1', 'Deck', 0, 'Model', 0, 2, 2, 0, ?, ?, 0, 0, 1.0, 5.0, 0.9, ?)",
            arrayOf<Any>(
                syncId,
                syncId * 1_000L - 100L,
                syncId * 1_000L,
                cardId,
                noteId,
                intervalDays,
                syncId.toInt(),
                if (intervalDays >= 21) 1 else 0,
            ),
        )
    }

    private fun insertNoteSnapshot(syncId: Long, noteId: Long, kanji: String) {
        db.execSQL(
            "INSERT INTO sync_note_snapshots " +
                "(sync_id, finished_at, note_id, model_id, model_name, deck_ids, deck_names, expression, reading, meaning, " +
                "sentence, tags, fields_json, extracted_kanji) " +
                "VALUES (?, ?, ?, 0, 'Model', '1', 'Deck', ?, ?, 'pain', '', '', '{}', ?)",
            arrayOf<Any>(syncId, syncId * 1_000L, noteId, kanji, kanji, kanji),
        )
    }

    private fun insertKanjiSnapshot(syncId: Long, kanji: String, weakness: Int, active: Int) {
        db.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, fsrs_stability_avg, fsrs_difficulty_avg, fsrs_retrievability_avg, weakness_score, " +
                "reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, ?, ?, ?, 0, 0, 10.0, 0, 5, NULL, NULL, NULL, ?, '', ?, 0)",
            arrayOf<Any>(syncId, syncId * 1_000L, kanji, active, weakness, active),
        )
    }

    private fun insertStudyItem(kanji: String, createdAt: Long) {
        db.execSQL(
            "INSERT INTO study_items " +
                "(kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, " +
                "rung, phase, real_pass_streak, real_again_streak, mature_interval_days, active_token, created_at) " +
                "VALUES (?, 'review', 0, 1.0, 5.0, 0, 0, 0, 0, 'kanji_meaning', 'review', 3, 0, 22, '', ?)",
            arrayOf<Any>(kanji, createdAt),
        )
    }

    private fun insertReview(kanji: String, reviewedAt: Long) {
        db.execSQL(
            "INSERT INTO review_log " +
                "(kanji, token, rating, writing_required, writing_passed, manual_override, reviewed_at, review_day_start) " +
                "VALUES (?, ?, 'good', 1, 1, 0, ?, 0)",
            arrayOf<Any>(kanji, "$kanji-$reviewedAt", reviewedAt),
        )
    }

    private companion object {
        const val TABLE_SYNC_CARD_SNAPSHOTS = "sync_card_snapshots"
        const val TABLE_SYNC_NOTE_SNAPSHOTS = "sync_note_snapshots"
        const val TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots"
    }
}
