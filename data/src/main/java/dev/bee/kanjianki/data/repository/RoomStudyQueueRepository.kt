package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.repository.StudyQueueRepository

class RoomStudyQueueRepository(
    database: KaniRoomDatabase,
) : StudyQueueRepository {
    private val studyItems = database.studyItemDao()
    private val similarKanjiPairs = database.similarKanjiPairDao()

    override suspend fun listActive(): List<StudyQueueItem> =
        studyItems.listByStates(activeStateWireNames).toDomainWithSimilarAvailability()

    override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> =
        studyItems.listByState(state.wireName).toDomainWithSimilarAvailability()

    override suspend fun dueCount(
        state: StudyItemState,
        nowMillis: Long,
    ): Int = studyItems.dueCount(state.wireName, nowMillis)

    private suspend fun List<StudyItemEntity>.toDomainWithSimilarAvailability(): List<StudyQueueItem> {
        if (isEmpty()) {
            return emptyList()
        }
        val withSimilar = similarKanjiPairs.kanjiWithSimilarNeighbors().toSet()
        return map { it.toDomain(hasSimilarKanji = withSimilar.contains(it.kanji)) }
    }

    private companion object {
        val activeStateWireNames = listOf(
            StudyItemState.NEW.wireName,
            StudyItemState.LEARNING.wireName,
            StudyItemState.REVIEW.wireName,
        )
    }
}
