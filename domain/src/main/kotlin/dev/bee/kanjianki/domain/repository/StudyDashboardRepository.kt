package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import kotlinx.coroutines.flow.Flow

interface StudyDashboardRepository {
    fun observeTop(limit: Int): Flow<List<StudyDashboardRow>>

    suspend fun get(kanji: String): StudyDashboardRow?
}
