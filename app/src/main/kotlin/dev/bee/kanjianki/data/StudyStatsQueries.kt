package dev.bee.kanjianki.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecentMistakePolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyImpactPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.core.StudyTaskTimingPolicy
import dev.bee.kanjianki.sync.SyncSettings
import kotlin.math.max

internal class StudyStatsQueries(
    private val store: LocalStore,
    private val database: SQLiteDatabase? = null,
) {
    fun studyTaskTimeStats(nowMillis: Long): StudyStatsStore.StudyTaskTimeStats {
        val window = StudyTaskTimingPolicy.windowFor(nowMillis)
        val cursor = db().rawQuery(
            "SELECT " +
                "COALESCE(SUM(CASE WHEN answered_at>=? THEN active_elapsed_ms ELSE 0 END), 0) AS today_elapsed, " +
                "COALESCE(SUM(active_elapsed_ms), 0) AS week_elapsed, " +
                "COUNT(*) AS week_tasks " +
                "FROM study_task_log WHERE answered_at>=? AND answered_at<?",
            arrayOf(
                window.todayStartMillis.toString(),
                window.sevenDayStartMillis.toString(),
                window.tomorrowStartMillis.toString()
            )
        )
        cursor.use {
            it.moveToFirst()
            return StudyStatsStore.StudyTaskTimeStats(
                it.getLong(0),
                it.getLong(1),
                it.getInt(2)
            )
        }
    }

    fun recentMistakes(limit: Int): List<StudyStatsStore.RecentMistake> {
        val cursor = db().query(
            TABLE_REVIEW_LOG,
            arrayOf(COLUMN_KANJI, COLUMN_RATING, COLUMN_REVIEWED_AT),
            "rating IN (?, ?)",
            RecentMistakePolicy.mistakeRatings(),
            null,
            null,
            "reviewed_at DESC, id DESC",
            RecentMistakePolicy.boundedLimit(limit).toString()
        )
        val mistakes = ArrayList<StudyStatsStore.RecentMistake>()
        cursor.use {
            while (it.moveToNext()) {
                mistakes.add(
                    StudyStatsStore.RecentMistake(
                        string(it, COLUMN_KANJI),
                        string(it, COLUMN_RATING),
                        longValue(it, COLUMN_REVIEWED_AT)
                    )
                )
            }
        }
        return mistakes
    }

    fun studyStreak(nowMillis: Long): StudyStatsStore.StudyStreak {
        val today = localDayStart(nowMillis)
        val studyDays = studyDays(today)
        val streak = StudyStreakPolicy.summarize(
            studyDays.days,
            today,
            studyDays.reviewsToday,
            studyDays.lastStudyAt
        )
        return StudyStatsStore.StudyStreak(
            streak.currentDays,
            streak.bestDays,
            streak.studiedToday,
            streak.reviewsToday,
            streak.lastStudyAtMillis
        )
    }

    fun reviewDaySummaries(nowMillis: Long, days: Int): List<StatsCacheStore.ReviewDaySummarySnapshot> {
        if (days <= 0) {
            return emptyList()
        }
        val startDay = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -(days - 1))
        val endDayExclusive = LocalDayPolicy.nextLocalDayStart(nowMillis)
        val summariesByDay = mutableMapOf<Long, StatsCacheStore.ReviewDaySummarySnapshot>()
        val cursor = db().rawQuery(
            "SELECT review_day_start, COUNT(*) AS total, " +
                "COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count, " +
                "COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count, " +
                "COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count, " +
                "COALESCE(SUM(CASE WHEN rating NOT IN ('again', 'hard', 'easy') THEN 1 ELSE 0 END), 0) AS good_count, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count " +
                "FROM $TABLE_REVIEW_LOG WHERE review_day_start>=? AND review_day_start<? GROUP BY review_day_start ORDER BY review_day_start ASC",
            arrayOf(startDay.toString(), endDayExclusive.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                val dayStart = it.getLong(0)
                summariesByDay[dayStart] = StatsCacheStore.ReviewDaySummarySnapshot(
                    dayStartMillis = dayStart,
                    total = it.getInt(1),
                    again = it.getInt(2),
                    hard = it.getInt(3),
                    good = it.getInt(5),
                    easy = it.getInt(4),
                    writingRequired = it.getInt(6),
                    writingFailed = it.getInt(7),
                )
            }
        }
        return (0 until days).map { index ->
            val dayStart = LocalDayPolicy.moveLocalDays(startDay, index)
            summariesByDay[dayStart] ?: StatsCacheStore.ReviewDaySummarySnapshot(dayStart, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    fun studyImpactStats(): StudyStatsStore.StudyImpactStats {
        val cursor = db().rawQuery(
            "SELECT " +
                "COUNT(*) AS total_reviews, " +
                "COUNT(DISTINCT kanji) AS distinct_kanji, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=1 THEN 1 ELSE 0 END), 0) AS writing_passed_count, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count, " +
                "COALESCE(SUM(CASE WHEN manual_override=1 THEN 1 ELSE 0 END), 0) AS manual_override_count " +
                "FROM $TABLE_REVIEW_LOG",
            null
        )
        cursor.use {
            it.moveToFirst()
            return StudyStatsStore.StudyImpactStats(
                it.getInt(0),
                it.getInt(1),
                it.getInt(2),
                it.getInt(3),
                it.getInt(4),
                it.getInt(5)
            )
        }
    }

    fun kaniOutcomeStats(): StudyStatsStore.KaniOutcomeStats {
        val promotionDays = max(1, ladderPromotionIntervalDays())
        val failStreak = max(1, ladderDemotionFailStreak())
        return StudyStatsStore.calculateKaniOutcomeStats(
            outcomeEvidence(db()),
            ladderItems(db()),
            promotionDays,
            failStreak
        )
    }

    fun kanjiRepairEvidenceInputs(): List<KanjiRepairEvidencePolicy.Input> {
        val db = db()
        val candidates = repairEvidenceCandidates(db)
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<KanjiRepairEvidencePolicy.Input>(candidates.size)
        for (kanji in candidates) {
            out.add(repairEvidenceInput(db, kanji))
        }
        out.sortWith(
            compareByDescending<KanjiRepairEvidencePolicy.Input> { it.lastReviewAtMillis() }
                .thenBy { it.kanji() }
        )
        return out
    }

    fun reviewStatsSince(sinceMillis: Long): RecordsSchedulerModels.ReviewStats {
        val cursor = db().rawQuery(
            "SELECT " +
                "COUNT(*) AS total, " +
                "COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count, " +
                "COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count, " +
                "COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count, " +
                "COALESCE(SUM(CASE WHEN rating NOT IN ('again', 'hard', 'easy') THEN 1 ELSE 0 END), 0) AS good_count, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count " +
                "FROM $TABLE_REVIEW_LOG WHERE reviewed_at>=?",
            arrayOf(sinceMillis.toString())
        )
        cursor.use {
            it.moveToFirst()
            return RecordsSchedulerModels.ReviewStats(
                it.getInt(0),
                it.getInt(1),
                it.getInt(2),
                it.getInt(4),
                it.getInt(3),
                it.getInt(5),
                it.getInt(6)
            )
        }
    }

    fun studiedKanjiSince(sinceMillis: Long): Set<String> {
        val cursor = db().query(
            true,
            TABLE_REVIEW_LOG,
            arrayOf(COLUMN_KANJI),
            "reviewed_at>=?",
            arrayOf(sinceMillis.toString()),
            null,
            null,
            null,
            null
        )
        val kanji = HashSet<String>()
        cursor.use {
            while (it.moveToNext()) {
                kanji.add(string(it, COLUMN_KANJI))
            }
        }
        return kanji
    }

    private fun db(): SQLiteDatabase = database ?: store.readableDatabase

    private fun outcomeEvidence(db: SQLiteDatabase): List<StudyStatsStore.OutcomeEvidence> {
        val cursor = db.rawQuery(
            "SELECT rw.kanji, " +
                "(SELECT s.weakness_score FROM $TABLE_SYNC_KANJI_SNAPSHOTS s WHERE s.kanji=rw.kanji AND s.finished_at<rw.first_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS before_weakness, " +
                "(SELECT s.mature_support_count FROM $TABLE_SYNC_KANJI_SNAPSHOTS s WHERE s.kanji=rw.kanji AND s.finished_at<rw.first_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS before_support, " +
                "(SELECT s.weakness_score FROM $TABLE_SYNC_KANJI_SNAPSHOTS s WHERE s.kanji=rw.kanji AND s.finished_at>rw.last_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS after_weakness, " +
                "(SELECT s.mature_support_count FROM $TABLE_SYNC_KANJI_SNAPSHOTS s WHERE s.kanji=rw.kanji AND s.finished_at>rw.last_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS after_support " +
                "FROM (SELECT kanji, MIN(reviewed_at) AS first_reviewed_at, MAX(reviewed_at) AS last_reviewed_at " +
                "FROM $TABLE_REVIEW_LOG WHERE kanji<>'' GROUP BY kanji) rw",
            null
        )
        val out = ArrayList<StudyStatsStore.OutcomeEvidence>()
        cursor.use {
            while (it.moveToNext()) {
                val kanji = string(it, COLUMN_KANJI)
                out.add(
                    StudyStatsStore.OutcomeEvidence(
                        kanji,
                        outcomeSnapshot(it, 1, 2),
                        outcomeSnapshot(it, 3, 4)
                    )
                )
            }
        }
        return out
    }

    private fun ladderItems(db: SQLiteDatabase): List<StudyStatsStore.LadderItemEvidence> {
        val cursor = db.query(
            TABLE_STUDY_ITEMS,
            arrayOf(
                LocalStoreBase.COLUMN_STATE,
                LocalStoreBase.COLUMN_RUNG,
                LocalStoreBase.COLUMN_PHASE,
                LocalStoreBase.COLUMN_REAL_PASS_STREAK,
                LocalStoreBase.COLUMN_REAL_AGAIN_STREAK,
                LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS
            ),
            "state<>?",
            arrayOf(STATE_RETIRED),
            null,
            null,
            null
        )
        val out = ArrayList<StudyStatsStore.LadderItemEvidence>()
        cursor.use {
            while (it.moveToNext()) {
                out.add(
                    StudyStatsStore.LadderItemEvidence(
                        string(it, LocalStoreBase.COLUMN_STATE),
                        RecordsBase.LadderRung.fromWireName(string(it, LocalStoreBase.COLUMN_RUNG)),
                        RecordsBase.SchedulerPhase.fromWireName(string(it, LocalStoreBase.COLUMN_PHASE)),
                        integer(it, LocalStoreBase.COLUMN_REAL_PASS_STREAK),
                        integer(it, LocalStoreBase.COLUMN_REAL_AGAIN_STREAK),
                        integer(it, LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS)
                    )
                )
            }
        }
        return out
    }

    private data class RepairEvidenceSummary(
        val kaniReviews: Int,
        val postReviewSamples: Int,
        val writingFailures: Int,
        val lastMistakeAtMillis: Long,
        val firstReviewAtMillis: Long,
        val lastReviewAtMillis: Long,
        val lastSyncAtMillis: Long,
    )

    private fun repairEvidenceCandidates(db: SQLiteDatabase): List<String> {
        val cursor = db.rawQuery(
            "SELECT kanji FROM $TABLE_STUDY_ITEMS WHERE state<>? " +
                "UNION SELECT DISTINCT kanji FROM $TABLE_REVIEW_LOG WHERE kanji<>'' " +
                "ORDER BY kanji ASC",
            arrayOf(STATE_RETIRED)
        )
        val candidates = ArrayList<String>()
        cursor.use {
            while (it.moveToNext()) {
                candidates.add(string(it, COLUMN_KANJI))
            }
        }
        return candidates
    }

    private fun repairEvidenceInput(db: SQLiteDatabase, kanji: String): KanjiRepairEvidencePolicy.Input {
        val summary = repairEvidenceSummary(db, kanji)
        val before = repairEvidenceSnapshotBefore(db, kanji, summary.firstReviewAtMillis)
        val after = repairEvidenceSnapshotAfter(db, kanji, summary.lastReviewAtMillis)
        return KanjiRepairEvidencePolicy.Input(
            kanji,
            before,
            after,
            summary.kaniReviews,
            summary.postReviewSamples,
            summary.writingFailures,
            summary.lastMistakeAtMillis,
            summary.firstReviewAtMillis,
            summary.lastReviewAtMillis,
            summary.lastSyncAtMillis,
            repairEvidenceLadder(db, kanji)
        )
    }

    private fun repairEvidenceSummary(db: SQLiteDatabase, kanji: String): RepairEvidenceSummary {
        val mistakeRatings = RecentMistakePolicy.mistakeRatings()
        val cursor = db.rawQuery(
            "SELECT " +
                "COUNT(*) AS kani_reviews, " +
                "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failures, " +
                "COALESCE(MAX(CASE WHEN rating IN (?, ?) THEN reviewed_at ELSE 0 END), 0) AS last_mistake_at, " +
                "COALESCE(MIN(reviewed_at), 0) AS first_review_at, " +
                "COALESCE(MAX(reviewed_at), 0) AS last_review_at " +
                "FROM $TABLE_REVIEW_LOG WHERE kanji=?",
            arrayOf(mistakeRatings[0], mistakeRatings[1], kanji)
        )
        cursor.use {
            it.moveToFirst()
            val firstReviewAtMillis = it.getLong(3)
            val lastReviewAtMillis = it.getLong(4)
            return RepairEvidenceSummary(
                kaniReviews = it.getInt(0),
                postReviewSamples = repairEvidencePostReviewSamples(db, kanji, lastReviewAtMillis),
                writingFailures = it.getInt(1),
                lastMistakeAtMillis = it.getLong(2),
                firstReviewAtMillis = firstReviewAtMillis,
                lastReviewAtMillis = lastReviewAtMillis,
                lastSyncAtMillis = repairEvidenceLastSyncAtMillis(db, kanji),
            )
        }
    }

    private fun repairEvidenceLastSyncAtMillis(db: SQLiteDatabase, kanji: String): Long {
        val cursor = db.rawQuery(
            "SELECT COALESCE(MAX(finished_at), 0) FROM $TABLE_SYNC_KANJI_SNAPSHOTS WHERE kanji=?",
            arrayOf(kanji)
        )
        cursor.use {
            it.moveToFirst()
            return it.getLong(0)
        }
    }

    private fun repairEvidencePostReviewSamples(db: SQLiteDatabase, kanji: String, lastReviewAtMillis: Long): Int {
        if (lastReviewAtMillis <= 0L) {
            return 0
        }
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SYNC_KANJI_SNAPSHOTS WHERE kanji=? AND finished_at>?",
            arrayOf(kanji, lastReviewAtMillis.toString())
        )
        cursor.use {
            it.moveToFirst()
            return it.getInt(0)
        }
    }

    private fun repairEvidenceSnapshotBefore(
        db: SQLiteDatabase,
        kanji: String,
        firstReviewAtMillis: Long,
    ): KanjiRepairEvidencePolicy.Snapshot? {
        if (firstReviewAtMillis <= 0L) {
            return null
        }
        return repairEvidenceSnapshot(db, kanji, "<", firstReviewAtMillis)
    }

    private fun repairEvidenceSnapshotAfter(
        db: SQLiteDatabase,
        kanji: String,
        lastReviewAtMillis: Long,
    ): KanjiRepairEvidencePolicy.Snapshot? {
        if (lastReviewAtMillis <= 0L) {
            return null
        }
        return repairEvidenceSnapshot(db, kanji, ">", lastReviewAtMillis)
    }

    private fun repairEvidenceSnapshot(
        db: SQLiteDatabase,
        kanji: String,
        comparator: String,
        boundaryMillis: Long,
    ): KanjiRepairEvidencePolicy.Snapshot? {
        val cursor = db.rawQuery(
            "SELECT weakness_score, mature_support_count, finished_at, active_example_count, suspended_example_count, reason_code " +
                "FROM $TABLE_SYNC_KANJI_SNAPSHOTS WHERE kanji=? AND finished_at${comparator}? ORDER BY finished_at DESC, sync_id DESC LIMIT 1",
            arrayOf(kanji, boundaryMillis.toString())
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return KanjiRepairEvidencePolicy.Snapshot(
                it.getInt(0),
                it.getInt(1),
                it.getLong(2),
                it.getInt(3),
                it.getInt(4),
                stringOrNull(it, 5)
            )
        }
    }

    private fun repairEvidenceLadder(db: SQLiteDatabase, kanji: String): KanjiRepairEvidencePolicy.Ladder? {
        val cursor = db.query(
            TABLE_STUDY_ITEMS,
            arrayOf(
                LocalStoreBase.COLUMN_RUNG,
                LocalStoreBase.COLUMN_PHASE,
                LocalStoreBase.COLUMN_REAL_PASS_STREAK,
                LocalStoreBase.COLUMN_REAL_AGAIN_STREAK,
                LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS,
            ),
            "kanji=? AND state<>?",
            arrayOf(kanji, STATE_RETIRED),
            null,
            null,
            null,
            "1"
        )
        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return KanjiRepairEvidencePolicy.Ladder(
                RecordsBase.LadderRung.fromWireName(string(it, LocalStoreBase.COLUMN_RUNG)),
                RecordsBase.SchedulerPhase.fromWireName(string(it, LocalStoreBase.COLUMN_PHASE)),
                integer(it, LocalStoreBase.COLUMN_REAL_PASS_STREAK),
                integer(it, LocalStoreBase.COLUMN_REAL_AGAIN_STREAK),
                integer(it, LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS)
            )
        }
    }

    private fun stringOrNull(cursor: Cursor, columnIndex: Int): String? {
        return if (cursor.isNull(columnIndex)) null else cursor.getString(columnIndex)
    }

    private fun studyDays(today: Long): StudyDays {
        val cursor = db().rawQuery(
            "SELECT review_day_start, COUNT(*) AS review_count, MAX(reviewed_at) AS last_reviewed_at " +
                "FROM $TABLE_REVIEW_LOG WHERE review_day_start>0 " +
                "GROUP BY review_day_start ORDER BY review_day_start DESC",
            null
        )
        val days = ArrayList<Long>()
        var reviewsToday = 0
        var lastStudyAt = 0L
        cursor.use {
            while (it.moveToNext()) {
                val day = it.getLong(0)
                if (lastStudyAt == 0L) {
                    lastStudyAt = it.getLong(2)
                }
                if (day == today) {
                    reviewsToday = it.getInt(1)
                }
                days.add(day)
            }
        }
        return StudyDays(days, reviewsToday, lastStudyAt)
    }

    private fun ladderPromotionIntervalDays(): Int {
        return store.getIntSetting(
            SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
            RecordsSyncModels.Settings.kikuDefaults().ladderPromotionIntervalDays
        )
    }

    private fun ladderDemotionFailStreak(): Int {
        return store.getIntSetting(
            SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
            realDueReviewsToMove()
        )
    }

    private fun realDueReviewsToMove(): Int {
        return store.getIntSetting(
            SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
            RecordsSyncModels.Settings.kikuDefaults().realDueReviewsToMove
        )
    }

    private data class StudyDays(
        val days: List<Long>,
        val reviewsToday: Int,
        val lastStudyAt: Long,
    )

    private companion object {
        const val TABLE_REVIEW_LOG = "review_log"
        const val TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots"
        const val TABLE_STUDY_ITEMS = "study_items"
        const val COLUMN_KANJI = "kanji"
        const val COLUMN_RATING = "rating"
        const val COLUMN_REVIEWED_AT = "reviewed_at"
        const val STATE_RETIRED = "retired"

        fun outcomeSnapshot(
            cursor: Cursor,
            weaknessColumnIndex: Int,
            supportColumnIndex: Int,
        ): StudyStatsStore.OutcomeSnapshot? {
            if (cursor.isNull(weaknessColumnIndex) || cursor.isNull(supportColumnIndex)) {
                return null
            }
            return StudyStatsStore.OutcomeSnapshot(
                cursor.getInt(weaknessColumnIndex),
                cursor.getInt(supportColumnIndex)
            )
        }

        fun localDayStart(millis: Long): Long = LocalDayPolicy.localDayStart(millis)

        fun string(cursor: Cursor, column: String): String {
            return cursor.getString(cursor.getColumnIndexOrThrow(column))
        }

        fun longValue(cursor: Cursor, column: String): Long {
            return cursor.getLong(cursor.getColumnIndexOrThrow(column))
        }

        fun integer(cursor: Cursor, column: String): Int {
            return cursor.getInt(cursor.getColumnIndexOrThrow(column))
        }
    }
}
