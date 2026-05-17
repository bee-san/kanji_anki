package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.repository.StudyRuntimeSnapshotRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class RoomLegacyStudyReadBridge @Inject constructor(
    private val studyRuntimeSnapshotRepository: StudyRuntimeSnapshotRepository,
    private val ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
) {
    suspend fun activeSnapshot(
        dashboardLimit: Int = DEFAULT_DASHBOARD_LIMIT,
    ): RoomLegacyStudySnapshot {
        check(ownershipPolicy.canReadStudyRuntimeFromRoom()) {
            "Room study reads require completed legacy reset/migration or active double-write ownership."
        }
        val snapshot = studyRuntimeSnapshotRepository.activeSnapshot(dashboardLimit.coerceAtLeast(0))
        return RoomLegacyStudySnapshot(
            rows = LegacyStudyMappers.toLegacyRows(snapshot.rows),
            items = LegacyStudyMappers.toLegacyItems(snapshot.items),
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
