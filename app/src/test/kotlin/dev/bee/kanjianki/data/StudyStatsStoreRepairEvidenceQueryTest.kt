package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyStatsStoreRepairEvidenceQueryTest {
    private lateinit var context: Context
    private lateinit var localStore: LocalStore
    private lateinit var db: SQLiteDatabase
    private lateinit var statsStore: StudyStatsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        localStore = LocalStore(context)
        db = localStore.writableDatabase
        statsStore = StudyStatsStore(localStore)
    }

    @After
    fun tearDown() {
        localStore.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun kanjiRepairEvidenceReturnsImprovingAndActiveNoReviewCandidates() {
        insertStudyItem("弱", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 4, 1, 14)
        insertStudyItem("未", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.NEW_LEARNING, 0, 0, 0)

        insertReview("弱", "good", 1_000L)
        insertReview("弱", "hard", 2_000L)
        insertReview("弱", "good", 3_000L)

        insertSnapshot(1L, 500L, "弱", weakness = 90, mature = 0, active = 1, reps = 5)
        insertSnapshot(2L, 900L, "弱", weakness = 70, mature = 0, active = 1, reps = 6)
        insertSnapshot(3L, 4_000L, "弱", weakness = 50, mature = 2, active = 1, reps = 7)
        insertSnapshot(4L, 5_000L, "弱", weakness = 40, mature = 3, active = 1, reps = 8)

        val evidence = statsStore.kanjiRepairEvidence()

        assertEquals(2, evidence.size)

        val improving = evidence[0]
        assertEquals("弱", improving.kanji)
        assertEquals(KanjiRepairEvidencePolicy.Status.IMPROVING, improving.status)
        assertEquals("improved_weakness_after_reviews", improving.reason)
        assertEquals(70, improving.beforeWeakness ?: -1)
        assertEquals(40, improving.afterWeakness ?: -1)
        assertEquals(0, improving.beforeMatureSupport ?: -1)
        assertEquals(3, improving.afterMatureSupport ?: -1)
        assertEquals(3, improving.kaniReviews)
        assertEquals(0, improving.writingFailures)
        assertEquals(2_000L, improving.lastMistakeAtMillis)
        assertEquals(5_000L, improving.lastSyncAtMillis)
        assertEquals(0.84, improving.confidence, 0.0001)

        val noReview = evidence[1]
        assertEquals("未", noReview.kanji)
        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, noReview.status)
        assertEquals("no_kani_reviews", noReview.reason)
        assertEquals(0, noReview.kaniReviews)
        assertNull(noReview.beforeWeakness)
        assertNull(noReview.afterWeakness)
        assertEquals(0.05, noReview.confidence, 0.0001)
    }

    @Test
    fun kanjiRepairEvidenceReturnsNoPostReviewSyncWhenNoLaterSnapshotExists() {
        insertStudyItem("止", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 2, 0, 30)

        insertReview("止", "good", 1_000L)
        insertReview("止", "hard", 2_000L)
        insertReview("止", "good", 3_000L)

        insertSnapshot(1L, 500L, "止", weakness = 95, mature = 0, active = 1, reps = 4)
        insertSnapshot(2L, 900L, "止", weakness = 80, mature = 0, active = 1, reps = 5)
        insertSnapshot(3L, 2_500L, "止", weakness = 78, mature = 1, active = 1, reps = 6)

        val evidence = statsStore.kanjiRepairEvidence()

        assertEquals(1, evidence.size)

        val item = evidence[0]
        assertEquals("止", item.kanji)
        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, item.status)
        assertEquals("no_post_review_sync", item.reason)
        assertEquals(3, item.kaniReviews)
        assertEquals(80, item.beforeWeakness ?: -1)
        assertNull(item.afterWeakness)
        assertEquals(2_500L, item.lastSyncAtMillis)
        assertEquals(0.10, item.confidence, 0.0001)
    }

    @Test
    fun retiredRepairsLast30DaysCountsOnlyRecentRetiredTimelineEvents() {
        val now = 100L * 24L * 60L * 60L * 1000L
        val day = 24L * 60L * 60L * 1000L
        insertTimelineEvent("弱", now - day, "retired", "recent-retire")
        insertTimelineEvent("未", now - 29 * day, "retired", "edge-retire")
        insertTimelineEvent("古", now - 31 * day, "retired", "old-retire")
        insertTimelineEvent("止", now - day, "reopened", "recent-reopen")

        assertEquals(2, statsStore.retiredRepairsLast30Days(now))
    }

    @Test
    fun retiredRepairsLast30DaysReturnsZeroWhenNoTimelineEvents() {
        assertEquals(0, statsStore.retiredRepairsLast30Days(1_000_000L))
    }

    private fun insertTimelineEvent(
        kanji: String,
        occurredAt: Long,
        eventType: String,
        dedupeKey: String,
    ) {
        db.execSQL(
            "INSERT INTO kanji_timeline_events " +
                "(kanji, occurred_at, event_type, title, detail, source_expression, source_reading, rating, " +
                "writing_required, writing_passed, manual_override, weakness_score, mature_support_count, sync_id, dedupe_key) " +
                "VALUES (?, ?, ?, 'title', 'detail', '', '', '', 0, 0, 0, NULL, NULL, NULL, ?)",
            arrayOf<Any>(kanji, occurredAt, eventType, dedupeKey),
        )
    }

    private fun insertSnapshot(
        syncId: Long,
        finishedAt: Long,
        kanji: String,
        weakness: Int,
        mature: Int,
        active: Int,
        reps: Int,
    ) {
        db.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, fsrs_stability_avg, fsrs_difficulty_avg, fsrs_retrievability_avg, weakness_score, " +
                "reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, ?, ?, ?, 0, ?, 10.0, 0, ?, NULL, NULL, NULL, ?, '', ?, 0)",
            arrayOf<Any>(syncId, finishedAt, kanji, active, mature, reps, weakness, active),
        )
    }

    private fun insertReview(
        kanji: String,
        rating: String,
        reviewedAt: Long,
        writingRequired: Int = 1,
        writingPassed: Int = 1,
        manualOverride: Int = 0,
    ) {
        db.execSQL(
            "INSERT INTO review_log " +
                "(kanji, token, rating, writing_required, writing_passed, manual_override, reviewed_at, review_day_start) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(kanji, "$kanji-$reviewedAt-$rating-$writingPassed-$manualOverride", rating, writingRequired, writingPassed, manualOverride, reviewedAt),
        )
    }

    private fun insertStudyItem(
        kanji: String,
        rung: RecordsBase.LadderRung,
        phase: RecordsBase.SchedulerPhase,
        passStreak: Int,
        againStreak: Int,
        matureIntervalDays: Int,
    ) {
        db.execSQL(
            "INSERT INTO study_items " +
                "(kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, " +
                "rung, phase, real_pass_streak, real_again_streak, mature_interval_days, active_token, created_at) " +
                "VALUES (?, 'review', 0, 1.0, 5.0, 0, 0, 0, 0, ?, ?, ?, ?, ?, '', 1)",
            arrayOf<Any>(kanji, rung.wireName(), phase.wireName(), passStreak, againStreak, matureIntervalDays),
        )
    }
}
