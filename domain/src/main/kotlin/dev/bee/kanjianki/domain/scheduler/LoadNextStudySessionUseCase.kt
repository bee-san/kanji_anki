package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository

class LoadNextStudySessionUseCase(
    private val studyQueueRepository: StudyQueueRepository,
    private val studyDashboardRepository: StudyDashboardRepository,
    private val selector: StudySessionSelector = StudySessionSelector(),
) {
    suspend operator fun invoke(request: LoadNextStudySessionRequest): StudySession? {
        val rows = studyDashboardRepository.listTop(request.dashboardLimit)
        val items = activeStates.flatMap { studyQueueRepository.listByState(it) }
        return selector.nextSession(
            NextSessionInput(
                items = items,
                rows = rows,
                nowMillis = request.nowMillis,
                studyAheadMillis = request.studyAheadMillis,
                allowedKanji = request.allowedKanji,
                ladderSettings = request.ladderSettings,
                newCardSortMode = request.newCardSortMode,
            ),
        )
    }

    private companion object {
        val activeStates = listOf(
            StudyItemState.NEW,
            StudyItemState.LEARNING,
            StudyItemState.REVIEW,
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
