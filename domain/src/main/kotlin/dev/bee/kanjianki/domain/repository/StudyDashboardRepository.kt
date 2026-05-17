package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import kotlinx.coroutines.flow.Flow

interface StudyDashboardRepository {
    fun observeTop(limit: Int): Flow<List<StudyDashboardRow>>

    fun observeActive(limit: Int): Flow<List<StudyDashboardRow>>

    suspend fun listTop(limit: Int): List<StudyDashboardRow>

    suspend fun listActive(limit: Int): List<StudyDashboardRow>

    suspend fun get(kanji: String): StudyDashboardRow?

    suspend fun isLocallySuspended(kanji: String): Boolean

    suspend fun setLocallySuspended(
        kanji: String,
        suspended: Boolean,
        nowMillis: Long,
    ): Boolean
}
