package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class RoomLegacyStudyReadBridge @Inject constructor(
    private val studyDashboardRepository: StudyDashboardRepository,
    private val studyQueueRepository: StudyQueueRepository,
) {
    suspend fun activeSnapshot(
        dashboardLimit: Int = DEFAULT_DASHBOARD_LIMIT,
    ): RoomLegacyStudySnapshot {
        val rows = studyDashboardRepository.listActive(dashboardLimit.coerceAtLeast(0))
        val items = studyQueueRepository.listActive()
        return RoomLegacyStudySnapshot(
            rows = LegacyStudyMappers.toLegacyRows(rows),
            items = LegacyStudyMappers.toLegacyItems(items),
        )
    }

    @JvmOverloads
    fun activeSnapshotBlocking(
        dashboardLimit: Int = DEFAULT_DASHBOARD_LIMIT,
    ): RoomLegacyStudySnapshot = runBlocking {
        activeSnapshot(dashboardLimit)
    }

    companion object {
        const val DEFAULT_DASHBOARD_LIMIT = 120
    }
}

data class RoomLegacyStudySnapshot(
    val rows: List<RecordsImportModels.DashboardRow>,
    val items: List<RecordsStudyModels.StudyItem>,
)
