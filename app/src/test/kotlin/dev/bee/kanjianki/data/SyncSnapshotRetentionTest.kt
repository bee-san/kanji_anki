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
    fun pendingSnapshotDoesNotDisplaceNewestSuccessfulRetentionSlot() {
        val totalSuccessful = SyncSnapshotRetentionPolicy.KEEP_LATEST + 3L
        for (syncId in 1..totalSuccessful) {
            insertSyncRun(syncId, finishedAt = syncId * 1_000L)
            insertCardSnapshot(syncId, cardId = syncId, noteId = syncId, intervalDays = 1)
            insertNoteSnapshot(syncId, noteId = syncId, kanji = "痛")
        }
        val pendingId = 10_000L
        insertSyncRun(pendingId, finishedAt = 20_000L, status = LocalStoreBase.STATUS_PENDING)
        insertCardSnapshot(pendingId, cardId = pendingId, noteId = pendingId, intervalDays = 1)
        insertNoteSnapshot(pendingId, noteId = pendingId, kanji = "待")

        localStore.pruneSupersededSnapshots(db)

        val remaining = distinctSyncIds(TABLE_SYNC_CARD_SNAPSHOTS)
        val expectedNewestSuccesses =
            (totalSuccessful - SyncSnapshotRetentionPolicy.KEEP_LATEST + 1..totalSuccessful).toSet()
        assertTrue(remaining.containsAll(expectedNewestSuccesses))
        assertTrue("pending cleanup belongs to successful finalization", remaining.contains(pendingId))
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

    @Test
    fun pendingSnapshotCannotBecomeImpactBaseline() {
        insertSyncRun(1L, finishedAt = 1_000L)
        insertCardSnapshot(1L, cardId = 100L, noteId = 10L, intervalDays = 10)
        insertNoteSnapshot(1L, noteId = 10L, kanji = "痛")
        insertKanjiSnapshot(1L, "痛", weakness = 80, active = 1, mature = 0)

        insertSyncRun(2L, finishedAt = 1_500L, status = LocalStoreBase.STATUS_PENDING)
        insertCardSnapshot(2L, cardId = 100L, noteId = 10L, intervalDays = 1)
        insertNoteSnapshot(2L, noteId = 10L, kanji = "痛")
        insertKanjiSnapshot(2L, "痛", weakness = 99, active = 1, mature = 0)

        insertSyncRun(3L, finishedAt = 3_000L)
        insertCardSnapshot(3L, cardId = 100L, noteId = 10L, intervalDays = 40)
        insertNoteSnapshot(3L, noteId = 10L, kanji = "痛")
        insertKanjiSnapshot(3L, "痛", weakness = 30, active = 1, mature = 3)
        insertStudyItem("痛", createdAt = 1_200L)

        val row = KanjiImpactReportStore(localStore).report(db).rows.single { it.kanji == "痛" }

        assertEquals(3, row.baselineMatureCards)
        assertEquals(3, row.currentMatureCards)
    }

    @Test
    fun onlySuccessOwnedSuspendedImportsBecomeImpactCandidates() {
        insertSyncRun(1L, finishedAt = 1_000L)
        insertSyncRun(2L, finishedAt = 2_000L, status = LocalStoreBase.STATUS_PENDING)
        insertSyncRun(3L, finishedAt = 3_000L, status = "failed")
        insertSyncRun(4L, finishedAt = 4_000L)
        for (kanji in listOf("成", "待", "失")) {
            insertKanjiSnapshot(4L, kanji, weakness = 0, active = 0)
        }
        insertSuspendedImport("成", firstImportedAt = 500L, lastSeenSyncId = 1L)
        insertSuspendedImport("待", firstImportedAt = 500L, lastSeenSyncId = 2L)
        insertSuspendedImport("失", firstImportedAt = 500L, lastSeenSyncId = 3L)

        val report = KanjiImpactReportStore(localStore).report(db)

        assertEquals(listOf("成"), report.rows.map { it.kanji })
    }

    @Test
    fun pendingAndFailedSuspendedImportsCannotMoveImpactBaseline() {
        for (syncId in 1L..3L) {
            insertSyncRun(syncId, finishedAt = syncId * 1_000L)
            for (kanji in listOf("成", "待", "失")) {
                insertKanjiSnapshot(
                    syncId,
                    kanji,
                    weakness = 1,
                    active = 1,
                    mature = syncId.toInt(),
                )
            }
        }
        insertSyncRun(4L, finishedAt = 4_000L, status = LocalStoreBase.STATUS_PENDING)
        insertSyncRun(5L, finishedAt = 5_000L, status = "failed")
        for (kanji in listOf("成", "待", "失")) {
            insertStudyItem(kanji, createdAt = 2_500L)
        }
        insertSuspendedImport("成", firstImportedAt = 1_500L, lastSeenSyncId = 2L)
        insertSuspendedImport("待", firstImportedAt = 1_500L, lastSeenSyncId = 4L)
        insertSuspendedImport("失", firstImportedAt = 1_500L, lastSeenSyncId = 5L)

        val rows = KanjiImpactReportStore(localStore).report(db).rows.associateBy { it.kanji }

        assertEquals(2, rows.getValue("成").baselineMatureCards)
        assertEquals(3, rows.getValue("待").baselineMatureCards)
        assertEquals(3, rows.getValue("失").baselineMatureCards)
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

    private fun insertSyncRun(
        id: Long,
        finishedAt: Long,
        status: String = LocalStoreBase.STATUS_SUCCESS,
    ) {
        db.execSQL(
            "INSERT INTO sync_runs " +
                "(id, started_at, finished_at, status, active_notes_count, active_cards_count, suspended_cards_archived_count, " +
                "suspended_kanji_imported_count, deleted_notes_count, deleted_cards_count, error_code, error_message, removal_message) " +
                "VALUES (?, ?, ?, ?, 0, 0, 0, 0, 0, 0, NULL, NULL, NULL)",
            arrayOf<Any>(id, finishedAt - 100L, finishedAt, status),
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

    private fun insertKanjiSnapshot(
        syncId: Long,
        kanji: String,
        weakness: Int,
        active: Int,
        mature: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, fsrs_stability_avg, fsrs_difficulty_avg, fsrs_retrievability_avg, weakness_score, " +
                "reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, ?, ?, ?, 0, ?, 10.0, 0, 5, NULL, NULL, NULL, ?, '', ?, 0)",
            arrayOf<Any>(syncId, syncId * 1_000L, kanji, active, mature, weakness, active),
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

    private fun insertSuspendedImport(
        kanji: String,
        firstImportedAt: Long,
        lastSeenSyncId: Long,
    ) {
        db.execSQL(
            "INSERT INTO suspended_imports " +
                "(kanji, jiten_rank, rank_known, cutoff_used, first_imported_at, last_seen_sync_id) " +
                "VALUES (?, NULL, 0, 2500, ?, ?)",
            arrayOf<Any>(kanji, firstImportedAt, lastSeenSyncId),
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
