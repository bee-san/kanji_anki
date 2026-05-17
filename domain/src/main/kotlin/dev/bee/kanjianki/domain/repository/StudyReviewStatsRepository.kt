package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats

interface StudyReviewStatsRepository {
    suspend fun reviewStatsSince(sinceMillis: Long): AdaptiveReviewStats

    suspend fun studiedKanjiSince(sinceMillis: Long): Set<String>

    suspend fun currentStreakDays(nowMillis: Long): Int
}
