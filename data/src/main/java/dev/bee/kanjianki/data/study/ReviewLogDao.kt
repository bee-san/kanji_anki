package dev.bee.kanjianki.data.study

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReviewLogDao {
    @Query("SELECT * FROM review_log WHERE kanji = :kanji ORDER BY reviewed_at DESC, id DESC")
    suspend fun listForKanji(kanji: String): List<ReviewLogEntity>

    @Query("SELECT * FROM review_log WHERE reviewed_at >= :fromMillis ORDER BY reviewed_at ASC, id ASC")
    suspend fun listSince(fromMillis: Long): List<ReviewLogEntity>

    @Query(
        """
        SELECT
            COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN rating = 'again' THEN 1 ELSE 0 END), 0) AS again_count,
            COALESCE(SUM(CASE WHEN rating = 'hard' THEN 1 ELSE 0 END), 0) AS hard_count,
            COALESCE(SUM(CASE WHEN rating = 'easy' THEN 1 ELSE 0 END), 0) AS easy_count,
            COALESCE(SUM(CASE WHEN rating NOT IN ('again', 'hard', 'easy') THEN 1 ELSE 0 END), 0) AS good_count,
            COALESCE(SUM(CASE WHEN writing_required = 1 THEN 1 ELSE 0 END), 0) AS writing_required_count,
            COALESCE(
                SUM(
                    CASE
                        WHEN writing_required = 1 AND writing_passed = 0 AND manual_override = 0 THEN 1
                        ELSE 0
                    END
                ),
                0
            ) AS writing_failed_count
        FROM review_log
        WHERE reviewed_at >= :fromMillis
        """,
    )
    suspend fun reviewStatsSince(fromMillis: Long): ReviewStatsAggregate

    @Query("SELECT DISTINCT kanji FROM review_log WHERE reviewed_at >= :fromMillis")
    suspend fun distinctKanjiSince(fromMillis: Long): List<String>

    @Query(
        """
        SELECT
            review_day_start AS day_start,
            COUNT(*) AS review_count,
            MAX(reviewed_at) AS last_reviewed_at
        FROM review_log
        WHERE review_day_start > 0
        GROUP BY review_day_start
        ORDER BY review_day_start DESC
        """,
    )
    suspend fun listReviewDaysDescending(): List<ReviewDayAggregate>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: ReviewLogEntity): Long
}

data class ReviewStatsAggregate(
    @ColumnInfo(name = "total")
    val total: Int,
    @ColumnInfo(name = "again_count")
    val again: Int,
    @ColumnInfo(name = "hard_count")
    val hard: Int,
    @ColumnInfo(name = "good_count")
    val good: Int,
    @ColumnInfo(name = "easy_count")
    val easy: Int,
    @ColumnInfo(name = "writing_required_count")
    val writingRequired: Int,
    @ColumnInfo(name = "writing_failed_count")
    val writingFailed: Int,
)

data class ReviewDayAggregate(
    @ColumnInfo(name = "day_start")
    val dayStart: Long,
    @ColumnInfo(name = "review_count")
    val reviewCount: Int,
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long,
)
