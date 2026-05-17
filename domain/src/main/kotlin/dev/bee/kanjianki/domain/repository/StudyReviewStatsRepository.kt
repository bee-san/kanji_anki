package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats

data class StudyStreak(
    val currentDays: Int,
    val bestDays: Int,
    val studiedToday: Boolean,
    val reviewsToday: Int,
    val lastStudyAtMillis: Long,
) {
    companion object {
        val empty = StudyStreak(
            currentDays = 0,
            bestDays = 0,
            studiedToday = false,
            reviewsToday = 0,
            lastStudyAtMillis = 0L,
        )
    }
}

interface StudyReviewStatsRepository {
    suspend fun reviewStatsSince(sinceMillis: Long): AdaptiveReviewStats

    suspend fun studiedKanjiSince(sinceMillis: Long): Set<String>

    suspend fun studyStreak(nowMillis: Long): StudyStreak

    suspend fun currentStreakDays(nowMillis: Long): Int =
        studyStreak(nowMillis).currentDays
}
