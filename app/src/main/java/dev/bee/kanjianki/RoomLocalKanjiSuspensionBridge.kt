package dev.bee.kanjianki

import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class RoomLocalKanjiSuspensionBridge @Inject constructor(
    private val studyDashboardRepository: StudyDashboardRepository,
    private val ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
) {
    suspend fun setLocallySuspended(
        kanji: String?,
        suspended: Boolean,
        nowMillis: Long,
    ): Boolean {
        val safeKanji = kanji?.trim().orEmpty()
        if (safeKanji.isEmpty()) {
            return false
        }
        check(ownershipPolicy.canWriteStudyRuntimeToRoom()) {
            "Room local suspension writes require completed legacy reset/migration or active double-write ownership."
        }
        return studyDashboardRepository.setLocallySuspended(
            kanji = safeKanji,
            suspended = suspended,
            nowMillis = nowMillis,
        )
    }

    fun setLocallySuspendedBlocking(
        kanji: String?,
        suspended: Boolean,
        nowMillis: Long,
    ): Boolean = runBlocking {
        setLocallySuspended(kanji, suspended, nowMillis)
    }
}
