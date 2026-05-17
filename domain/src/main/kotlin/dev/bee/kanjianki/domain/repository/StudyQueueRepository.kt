package dev.bee.kanjianki.domain.repository

import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem

interface StudyQueueRepository {
    suspend fun listActive(): List<StudyQueueItem>

    suspend fun listByState(state: StudyItemState): List<StudyQueueItem>

    suspend fun listAllForSeeding(): List<StudyQueueItem>

    suspend fun replaceAllSeeded(items: List<StudyQueueItem>)

    suspend fun updateReviewedItem(item: StudyQueueItem): Boolean

    suspend fun dueCount(
        state: StudyItemState,
        nowMillis: Long,
    ): Int
}
