package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyQueueItem

data class StudyRuntimeSnapshot(
    val rows: List<StudyDashboardRow>,
    val items: List<StudyQueueItem>,
)

interface StudyRuntimeSnapshotRepository {
    suspend fun activeSnapshot(dashboardLimit: Int): StudyRuntimeSnapshot
}
