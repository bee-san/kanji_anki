package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.repository.StudyQueueRepository

class RoomStudyQueueRepository(
    database: KaniRoomDatabase,
) : StudyQueueRepository {
    private val studyItems = database.studyItemDao()

    override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> =
        studyItems.listByState(state.wireName).map { it.toDomain() }

    override suspend fun dueCount(
        state: StudyItemState,
        nowMillis: Long,
    ): Int = studyItems.dueCount(state.wireName, nowMillis)
}
