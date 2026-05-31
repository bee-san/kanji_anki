package dev.bee.kanjianki.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.LocalDayPolicy
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
