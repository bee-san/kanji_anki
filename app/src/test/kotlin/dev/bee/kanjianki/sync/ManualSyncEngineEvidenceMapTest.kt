package dev.bee.kanjianki.sync

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
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
class ManualSyncEngineEvidenceMapTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var db: SQLiteDatabase
    private lateinit var engine: ManualSyncEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        db = store.writableDatabase
        engine = ManualSyncEngine(
            context,
            store,
            UnusedGateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        )
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun evidenceMapContainsStatusesOnlyForActiveRowKanji() {
        seedImprovingEvidence("弱")
        seedImprovingEvidence("他")
        insertStudyItem("未")

        val map = engine.repairEvidenceStatusByKanji(listOf(row("弱"), row("未")))

        assertEquals(KanjiRepairEvidencePolicy.Status.IMPROVING, map["弱"])
        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, map["未"])
        assertFalse(map.containsKey("他"))
        assertEquals(2, map.size)
    }

    @Test
    fun evidenceMapIsEmptyForEmptyRows() {
        seedImprovingEvidence("弱")

        assertTrue(engine.repairEvidenceStatusByKanji(emptyList()).isEmpty())
    }

    private fun seedImprovingEvidence(kanji: String) {
        insertStudyItem(kanji)
        insertReview(kanji, "good", 1_000L)
        insertReview(kanji, "hard", 2_000L)
        insertReview(kanji, "good", 3_000L)
        insertSnapshot(1L, 900L, kanji, weakness = 70, mature = 0)
        insertSnapshot(2L, 4_000L, kanji, weakness = 50, mature = 2)
        insertSnapshot(3L, 5_000L, kanji, weakness = 40, mature = 3)
    }

    private fun insertStudyItem(kanji: String) {
        db.execSQL(
            "INSERT INTO study_items " +
                "(kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, " +
                "rung, phase, real_pass_streak, real_again_streak, mature_interval_days, active_token, created_at) " +
                "VALUES (?, 'review', 0, 1.0, 5.0, 0, 0, 0, 0, 'kanji_meaning', 'review', 0, 0, 0, '', 1)",
            arrayOf<Any>(kanji),
        )
    }

    private fun insertReview(kanji: String, rating: String, reviewedAt: Long) {
        db.execSQL(
            "INSERT INTO review_log " +
                "(kanji, token, rating, writing_required, writing_passed, manual_override, reviewed_at, review_day_start) " +
                "VALUES (?, ?, ?, 1, 1, 0, ?, 0)",
            arrayOf<Any>(kanji, "$kanji-$reviewedAt-$rating", rating, reviewedAt),
        )
    }

    private fun insertSnapshot(syncId: Long, finishedAt: Long, kanji: String, weakness: Int, mature: Int) {
        db.execSQL(
            "INSERT OR IGNORE INTO sync_runs " +
                "(id, started_at, finished_at, status, active_notes_count, active_cards_count, " +
                "suspended_cards_archived_count, suspended_kanji_imported_count, deleted_notes_count, deleted_cards_count, " +
                "error_code, error_message, removal_message) VALUES (?, ?, ?, 'success', 0, 0, 0, 0, 0, 0, NULL, NULL, '')",
            arrayOf<Any>(syncId, finishedAt - 1L, finishedAt),
        )
        db.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, fsrs_stability_avg, fsrs_difficulty_avg, fsrs_retrievability_avg, weakness_score, " +
                "reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, ?, ?, 1, 0, ?, 10.0, 0, 5, NULL, NULL, NULL, ?, '', 1, 0)",
            arrayOf<Any>(syncId, finishedAt, kanji, mature, weakness),
        )
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 30, "reason", "reason text", 1, 0, 0,
            ArrayList<RecordsImportModels.Example>(),
        )
    }

    private class UnusedGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw AnkiDroidGateway.SyncFailure.permanent("not used in this test")
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not used in this test")
        }
    }
}
