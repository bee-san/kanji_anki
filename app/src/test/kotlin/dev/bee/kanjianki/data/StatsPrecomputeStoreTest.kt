package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsPrecomputeStoreTest {
    private lateinit var context: Context
    private lateinit var localStore: LocalStore
    private lateinit var db: SQLiteDatabase
    private lateinit var cacheStore: StatsCacheStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        localStore = LocalStore(context)
        db = localStore.writableDatabase
        cacheStore = StatsCacheStore(localStore)
    }

    @After
    fun tearDown() {
        localStore.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun refreshWritesFreshCacheMatchingDirectQueries() {
        val generatedAtMillis = 1_700_000_000_000L
        val startDay = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(generatedAtMillis), -(STATS_REVIEW_DAY_SUMMARY_LIMIT - 1))
        seedRepresentativeStatsInputs()
        seedReviewDaySummaryInputs(startDay)
        cacheStore.markDirty(db)
        val directOutcome = StudyStatsStore(localStore).kaniOutcomeStats()
        val directImpact = KanjiImpactReportStore(localStore).report()
        val directStudyImpact = StudyStatsStore(localStore).studyImpactStats()
        val directRecentMistakes = StudyStatsStore(localStore).recentMistakes(STATS_RECENT_MISTAKE_LIMIT)
        val directStreak = StudyStatsStore(localStore).studyStreak(generatedAtMillis)
        val directTaskTime = StudyStatsStore(localStore).studyTaskTimeStats(generatedAtMillis)
        val directRepairEvidence = StudyStatsStore(localStore).kanjiRepairEvidence()
        val directTaskTypes = StudyStatsQueries(localStore, db).taskTypeDaySummaries(generatedAtMillis, STATS_REVIEW_DAY_SUMMARY_LIMIT)
        val directCumulative = StudyStatsQueries(localStore, db).cumulativeKanjiPracticed()

        StatsPrecomputeStore(localStore).refresh(db, generatedAtMillis = generatedAtMillis)

        val cached = cacheStore.readFresh(db, nowMillis = generatedAtMillis)
        assertNotNull(cached)
        cached!!
        assertEquals(generatedAtMillis, cached.generatedAtMillis)
        assertOutcomeStatsEquals(directOutcome, cached.outcomeStats)
        assertImpactReportEquals(directImpact, cached.impactReport)
        assertStudyImpactStatsEquals(directStudyImpact, cached.studyImpactStats)
        assertRecentMistakesEquals(directRecentMistakes, cached.recentMistakes)
        assertStudyStreakEquals(directStreak, cached.studyStreak)
        assertStudyTaskTimeStatsEquals(directTaskTime, cached.studyTaskTimeStats)
        assertRepairEvidenceEquals(directRepairEvidence, cached.kanjiRepairEvidence)
        assertEquals(directTaskTypes, cached.taskTypeDaySummaries)
        assertEquals(directCumulative, cached.cumulativeKanjiPracticed)
        assertEquals(mapOf("徴" to mapOf("微" to 2)), cached.wrongPickCounts)
        assertEquals(mapOf("微" to "minute", "徴" to "sign"), cached.confusionMeanings)
        assertEquals(STATS_CACHE_FORMAT_VERSION, cached.cacheFormatVersion)
        assertEquals(STATS_REVIEW_DAY_SUMMARY_LIMIT, cached.reviewDaySummaries.size)
        assertEquals(
            StatsCacheStore.ReviewDaySummarySnapshot(startDay, 1, 0, 0, 1, 0, 1, 0),
            cached.reviewDaySummaries[0],
        )
        assertEquals(
            StatsCacheStore.ReviewDaySummarySnapshot(LocalDayPolicy.moveLocalDays(startDay, 2), 1, 1, 0, 0, 0, 1, 1),
            cached.reviewDaySummaries[2],
        )
        assertEquals(
            StatsCacheStore.ReviewDaySummarySnapshot(LocalDayPolicy.moveLocalDays(startDay, 4), 3, 0, 0, 2, 1, 2, 0),
            cached.reviewDaySummaries[4],
        )
        assertEquals(
            StatsCacheStore.ReviewDaySummarySnapshot(LocalDayPolicy.moveLocalDays(startDay, STATS_REVIEW_DAY_SUMMARY_LIMIT - 1), 1, 0, 1, 0, 0, 0, 0),
            cached.reviewDaySummaries[STATS_REVIEW_DAY_SUMMARY_LIMIT - 1],
        )
        assertTrue("fixture should exercise weak-kanji improvement", cached.outcomeStats.weakKanjiImproved.improvedCount > 0)
        assertTrue("fixture should exercise ladder aggregate", cached.outcomeStats.ladderHealth.totalActiveItems > 0)
        assertTrue("fixture should exercise impact rows", cached.impactReport.rows.isNotEmpty())
    }

    @Test
    fun refreshZeroFillsMissingReviewDaysWithinBoundedWindow() {
        val generatedAtMillis = 1_700_000_000_000L
        val startDay = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(generatedAtMillis), -(STATS_REVIEW_DAY_SUMMARY_LIMIT - 1))
        seedReviewDaySummaryInputs(startDay)
        cacheStore.markDirty(db)

        StatsPrecomputeStore(localStore).refresh(db, generatedAtMillis = generatedAtMillis)

        val cached = cacheStore.readFresh(db, nowMillis = generatedAtMillis)
        assertNotNull(cached)
        cached!!
        assertEquals(STATS_REVIEW_DAY_SUMMARY_LIMIT, cached.reviewDaySummaries.size)
        assertEquals(
            StatsCacheStore.ReviewDaySummarySnapshot(LocalDayPolicy.moveLocalDays(startDay, 1), 0, 0, 0, 0, 0, 0, 0),
            cached.reviewDaySummaries[1],
        )
        assertEquals(
            StatsCacheStore.ReviewDaySummarySnapshot(LocalDayPolicy.moveLocalDays(startDay, 2), 1, 1, 0, 0, 0, 1, 1),
            cached.reviewDaySummaries[2],
        )
    }

    @Test
    fun refreshDoesNotClearPreviousCacheWhenComputationThrows() {
        val previous = StatsCacheStore.Snapshot(
            StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric(2, 0.8, 0.4, emptyList()),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                StudyStatsStore.LadderHealthMetric.empty(),
            ),
            KanjiImpactAnalyzer.Report(1, 0, 0, emptyList()),
            1_111L,
            cacheStore.currentSourceVersion(db),
            StudyStatsStore.StudyImpactStats(0, 0, 0, 0, 0, 0),
            emptyList(),
            StudyStatsStore.StudyStreak(0, 0, false, 0, 0L),
            StudyStatsStore.StudyTaskTimeStats(0L, 0L, 0),
            STATS_CACHE_FORMAT_VERSION,
        )
        cacheStore.write(db, previous)
        val throwing = object : StatsPrecomputeStore.Computations {
            override fun outcomeStats(db: SQLiteDatabase): StudyStatsStore.KaniOutcomeStats {
                return StudyStatsStore.KaniOutcomeStats.empty()
            }

            override fun impactReport(db: SQLiteDatabase): KanjiImpactAnalyzer.Report {
                throw IllegalStateException("boom")
            }
        }

        try {
            StatsPrecomputeStore(localStore, throwing).refresh(db, generatedAtMillis = 2_222L)
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }

        val latest = cacheStore.readLatest(db)
        assertNotNull(latest)
        latest!!
        assertEquals(1_111L, latest.generatedAtMillis)
        assertEquals(2, latest.outcomeStats.weakKanjiImproved.improvedCount)
        assertEquals(1, latest.impactReport.helpedCount)
    }

    private fun seedRepresentativeStatsInputs() {
        insertSyncRun(1, 1_000L)
        insertSyncRun(2, 3_000L)
        insertKanjiSnapshot(1, 1_000L, "痛", weakness = 82, mature = 1, active = 2, reps = 10, lapses = 4)
        insertKanjiSnapshot(2, 3_000L, "痛", weakness = 44, mature = 3, active = 3, reps = 20, lapses = 1)
        insertKanjiSnapshot(1, 1_000L, "弱", weakness = 76, mature = 0, active = 1, reps = 5, lapses = 2)
        insertKanjiSnapshot(2, 3_000L, "弱", weakness = 52, mature = 2, active = 2, reps = 10, lapses = 1)
        insertReview("痛", "good", 1_500L)
        insertReview("痛", "again", 1_700L)
        insertReview("弱", "good", 1_600L)
        insertReview("弱", "good", 1_800L)
        insertStudyItem("痛", RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW, 3, 0, 22)
        insertStudyItem("弱", RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW, 0, 2, 1)
        insertConfusionFixture()
    }

    private fun seedReviewDaySummaryInputs(startDay: Long) {
        val day2 = LocalDayPolicy.moveLocalDays(startDay, 2)
        val day4 = LocalDayPolicy.moveLocalDays(startDay, 4)
        val day89 = LocalDayPolicy.moveLocalDays(startDay, STATS_REVIEW_DAY_SUMMARY_LIMIT - 1)
        insertReview("日", "good", startDay + 1_000L, dayStart = startDay, writingRequired = true, writingPassed = true)
        insertReview("月", "again", day2 + 2_000L, dayStart = day2, writingRequired = true, writingPassed = false, manualOverride = false)
        insertReview("火", "good", day4 + 3_000L, dayStart = day4, writingRequired = true, writingPassed = true)
        insertReview("水", "good", day4 + 4_000L, dayStart = day4, writingRequired = true, writingPassed = false, manualOverride = true)
        insertReview("木", "easy", day4 + 5_000L, dayStart = day4, writingRequired = false, writingPassed = true)
        insertReview("金", "hard", day89 + 6_000L, dayStart = day89, writingRequired = false, writingPassed = true)
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

    private fun insertKanjiSnapshot(
        syncId: Long,
        finishedAt: Long,
        kanji: String,
        weakness: Int,
        mature: Int,
        active: Int,
        reps: Int,
        lapses: Int,
    ) {
        db.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, fsrs_stability_avg, fsrs_difficulty_avg, fsrs_retrievability_avg, weakness_score, " +
                "reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, ?, ?, ?, 0, ?, 10.0, ?, ?, NULL, NULL, NULL, ?, '', ?, 0)",
            arrayOf<Any>(syncId, finishedAt, kanji, active, mature, lapses, reps, weakness, active),
        )
    }

    private fun insertReview(
        kanji: String,
        rating: String,
        reviewedAt: Long,
        dayStart: Long = 0L,
        writingRequired: Boolean = true,
        writingPassed: Boolean = true,
        manualOverride: Boolean = false,
        taskType: String = "kanji_meaning",
    ) {
        db.execSQL(
            "INSERT INTO review_log " +
                "(kanji, token, rating, writing_required, writing_passed, manual_override, reviewed_at, review_day_start, task_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                kanji,
                "$kanji-$reviewedAt",
                rating,
                if (writingRequired) 1 else 0,
                if (writingPassed) 1 else 0,
                if (manualOverride) 1 else 0,
                reviewedAt,
                dayStart,
                taskType,
            ),
        )
    }

    private fun insertConfusionFixture() {
        db.execSQL(
            "INSERT INTO kanji_inventory (kanji, primary_meaning, readings, browser_search, search_text, source_count, example_count, first_seen_at, last_seen_at) VALUES " +
                "('徴', 'sign', '', '', '', 1, 1, 1, 1), ('微', 'minute', '', '', '', 1, 1, 1, 1)",
        )
        val now = 1_700_000_000_000L
        repeat(2) { index ->
            db.execSQL(
                "INSERT INTO similar_kanji_review_log (target_kanji, choice_signature, selected_kanji, correct, reviewed_at, rung) VALUES (?, ?, ?, 0, ?, 'similar_kanji')",
                arrayOf<Any>("徴", "fixture-$index", "微", now - index),
            )
        }
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

    private fun assertOutcomeStatsEquals(expected: StudyStatsStore.KaniOutcomeStats, actual: StudyStatsStore.KaniOutcomeStats) {
        assertEquals(expected.weakKanjiImproved.improvedCount, actual.weakKanjiImproved.improvedCount)
        assertEquals(expected.weakKanjiImproved.averageBeforeWeakness, actual.weakKanjiImproved.averageBeforeWeakness, 0.0001)
        assertEquals(expected.weakKanjiImproved.averageAfterWeakness, actual.weakKanjiImproved.averageAfterWeakness, 0.0001)
        assertEquals(expected.matureSupportGained.gainedSupportCount, actual.matureSupportGained.gainedSupportCount)
        assertEquals(expected.matureSupportGained.matureSupportGained, actual.matureSupportGained.matureSupportGained)
        assertEquals(expected.matureSupportGained.firstSupportCount, actual.matureSupportGained.firstSupportCount)
        assertEquals(expected.ladderHealth.totalActiveItems, actual.ladderHealth.totalActiveItems)
        assertEquals(expected.ladderHealth.promotionReadyCount, actual.ladderHealth.promotionReadyCount)
        assertEquals(expected.ladderHealth.demotionRiskCount, actual.ladderHealth.demotionRiskCount)
        assertEquals(expected.ladderHealth.demotionReadyCount, actual.ladderHealth.demotionReadyCount)
        for (rung in RecordsBase.LadderRung.values()) {
            assertEquals(expected.ladderHealth.countFor(rung), actual.ladderHealth.countFor(rung))
        }
    }

    private fun assertImpactReportEquals(expected: KanjiImpactAnalyzer.Report, actual: KanjiImpactAnalyzer.Report) {
        assertEquals(expected.helpedCount, actual.helpedCount)
        assertEquals(expected.notHelpingCount, actual.notHelpingCount)
        assertEquals(expected.needsMoreCardsCount, actual.needsMoreCardsCount)
        assertEquals(expected.rows.size, actual.rows.size)
        expected.rows.zip(actual.rows).forEach { (expectedRow, actualRow) ->
            assertEquals(expectedRow.kanji, actualRow.kanji)
            assertEquals(expectedRow.bucket, actualRow.bucket)
            assertEquals(expectedRow.reviewCount, actualRow.reviewCount)
            assertEquals(expectedRow.currentCardCount, actualRow.currentCardCount)
            assertEquals(expectedRow.retentionDelta, actualRow.retentionDelta, 0.0001)
            assertEquals(expectedRow.difficultyDelta, actualRow.difficultyDelta, 0.0001)
        }
    }

    private fun assertStudyImpactStatsEquals(expected: StudyStatsStore.StudyImpactStats, actual: StudyStatsStore.StudyImpactStats) {
        assertEquals(expected.totalReviews, actual.totalReviews)
        assertEquals(expected.distinctReviewedKanji, actual.distinctReviewedKanji)
        assertEquals(expected.writingRequired, actual.writingRequired)
        assertEquals(expected.writingPassed, actual.writingPassed)
        assertEquals(expected.writingFailed, actual.writingFailed)
        assertEquals(expected.manualOverrides, actual.manualOverrides)
    }

    private fun assertRecentMistakesEquals(expected: List<StudyStatsStore.RecentMistake>, actual: List<StudyStatsStore.RecentMistake>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedMistake, actualMistake) ->
            assertEquals(expectedMistake.kanji, actualMistake.kanji)
            assertEquals(expectedMistake.rating, actualMistake.rating)
            assertEquals(expectedMistake.reviewedAtMillis, actualMistake.reviewedAtMillis)
        }
    }

    private fun assertStudyStreakEquals(expected: StudyStatsStore.StudyStreak, actual: StudyStatsStore.StudyStreak) {
        assertEquals(expected.currentDays, actual.currentDays)
        assertEquals(expected.bestDays, actual.bestDays)
        assertEquals(expected.studiedToday, actual.studiedToday)
        assertEquals(expected.reviewsToday, actual.reviewsToday)
        assertEquals(expected.lastStudyAtMillis, actual.lastStudyAtMillis)
    }

    private fun assertRepairEvidenceEquals(
        expected: List<StudyStatsStore.KanjiRepairEvidence>,
        actual: List<StudyStatsStore.KanjiRepairEvidence>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedRow, actualRow) ->
            assertEquals(expectedRow.kanji, actualRow.kanji)
            assertEquals(expectedRow.status, actualRow.status)
            assertEquals(expectedRow.reason, actualRow.reason)
            assertEquals(expectedRow.explanation, actualRow.explanation)
            assertEquals(expectedRow.beforeWeakness, actualRow.beforeWeakness)
            assertEquals(expectedRow.afterWeakness, actualRow.afterWeakness)
            assertEquals(expectedRow.beforeMatureSupport, actualRow.beforeMatureSupport)
            assertEquals(expectedRow.afterMatureSupport, actualRow.afterMatureSupport)
            assertEquals(expectedRow.kaniReviews, actualRow.kaniReviews)
            assertEquals(expectedRow.writingFailures, actualRow.writingFailures)
            assertEquals(expectedRow.lastMistakeAtMillis, actualRow.lastMistakeAtMillis)
            assertEquals(expectedRow.lastSyncAtMillis, actualRow.lastSyncAtMillis)
            assertEquals(expectedRow.confidence, actualRow.confidence, 0.0001)
            assertEquals(expectedRow.confidenceReason, actualRow.confidenceReason)
        }
    }

    private fun assertStudyTaskTimeStatsEquals(
        expected: StudyStatsStore.StudyTaskTimeStats,
        actual: StudyStatsStore.StudyTaskTimeStats,
    ) {
        assertEquals(expected.todayMillis, actual.todayMillis)
        assertEquals(expected.lastSevenDaysMillis, actual.lastSevenDaysMillis)
        assertEquals(expected.answeredTasks, actual.answeredTasks)
    }
}
