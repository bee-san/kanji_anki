package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository

class LoadNextStudySessionUseCase(
    private val studyQueueRepository: StudyQueueRepository,
    private val studyDashboardRepository: StudyDashboardRepository,
    private val selector: StudySessionSelector = StudySessionSelector(),
) {
    suspend operator fun invoke(request: LoadNextStudySessionRequest): StudySession? {
        val rows = studyDashboardRepository.listActive(request.dashboardLimit)
        val items = studyQueueRepository.listActive()
        val selected = selector.nextSession(
            NextSessionInput(
                items = items,
                rows = rows,
                nowMillis = request.nowMillis,
                studyAheadMillis = request.studyAheadMillis,
                allowedKanji = request.allowedKanji,
                ladderSettings = request.ladderSettings,
                newCardSortMode = request.newCardSortMode,
            ),
        ) ?: return null
        val claimedItem = studyQueueRepository.claimActiveToken(
            item = selected.item,
            token = selected.token,
        ) ?: return null
        val claimedToken = claimedItem.activeToken?.takeIf { it.isNotEmpty() } ?: return null
        val effectiveItem = selected.item.copy(activeToken = claimedToken)
        return selected.copy(
            item = effectiveItem,
            token = claimedToken,
            taskType = effectiveItem.rung.wireName,
            writingRequired = effectiveItem.rung == StudyRung.WRITE_KANJI,
        )
    }
}

data class LoadNextStudySessionRequest(
    val nowMillis: Long,
    val dashboardLimit: Int = DEFAULT_DASHBOARD_LIMIT,
    val studyAheadMillis: Long = 0L,
    val allowedKanji: Set<String>? = null,
    val ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
    val newCardSortMode: NewCardSortMode = NewCardSortMode.default,
) {
    init {
        require(nowMillis >= 0L) { "nowMillis must not be negative" }
        require(dashboardLimit > 0) { "dashboardLimit must be positive" }
    }

    companion object {
        const val DEFAULT_DASHBOARD_LIMIT = 120
    }
}
