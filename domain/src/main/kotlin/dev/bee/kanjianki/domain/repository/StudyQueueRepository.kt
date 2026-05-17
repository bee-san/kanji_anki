package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem

interface StudyQueueRepository {
    suspend fun listActive(): List<StudyQueueItem>

    suspend fun listByState(state: StudyItemState): List<StudyQueueItem>

    suspend fun dueCount(
        state: StudyItemState,
        nowMillis: Long,
    ): Int
}
