package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.RoomStudyQueueMutationGate
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy
import dev.bee.kanjianki.data.StudyQueueMutationGate
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.data.study.StudyItemEntity
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.repository.StudyQueueRepository

class RoomStudyQueueRepository internal constructor(
    private val studyItems: StudyItemDao,
    private val similarKanjiPairs: SimilarKanjiPairDao,
    private val studyQueueMutationGate: StudyQueueMutationGate,
    private val ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val claimInTransaction: suspend (suspend () -> StudyItemEntity?) -> StudyItemEntity?,
) : StudyQueueRepository {
    constructor(database: KaniRoomDatabase) : this(
        database = database,
        studyQueueMutationGate = RoomStudyQueueMutationGate(),
        ownershipPolicy = RoomStudyRuntimeOwnershipPolicy.DISABLED,
    )

    constructor(
        database: KaniRoomDatabase,
        studyQueueMutationGate: StudyQueueMutationGate,
        ownershipPolicy: RoomStudyRuntimeOwnershipPolicy,
    ) : this(
        studyItems = database.studyItemDao(),
        similarKanjiPairs = database.similarKanjiPairDao(),
        studyQueueMutationGate = studyQueueMutationGate,
        ownershipPolicy = ownershipPolicy,
        runInTransaction = { block -> database.withTransaction { block() } },
        claimInTransaction = { block -> database.withTransaction { block() } },
    )

    override suspend fun listActive(): List<StudyQueueItem> =
        if (ownershipPolicy.canReadStudyRuntimeFromRoom()) {
            studyItems.listByStates(activeStateWireNames).toDomainWithSimilarAvailability()
        } else {
            emptyList()
        }

    override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> =
        if (ownershipPolicy.canReadStudyRuntimeFromRoom()) {
            studyItems.listByState(state.wireName).toDomainWithSimilarAvailability()
        } else {
            emptyList()
        }

    override suspend fun listAllForSeeding(): List<StudyQueueItem> =
        if (ownershipPolicy.canReadStudyRuntimeFromRoom()) {
            studyItems.listAll().toDomainWithSimilarAvailability()
        } else {
            emptyList()
        }

    override suspend fun replaceAllSeeded(items: List<StudyQueueItem>) {
        if (!ownershipPolicy.canWriteStudyRuntimeToRoom()) {
            return
        }
        studyQueueMutationGate.mutate {
            runInTransaction {
                studyItems.deleteAll()
                if (items.isNotEmpty()) {
                    studyItems.upsertAll(items.map { it.toEntity() })
                }
            }
        }
    }

    override suspend fun claimActiveToken(
        kanji: String,
        answerSignature: String,
        token: String,
    ): StudyQueueItem? {
        if (!ownershipPolicy.canReadStudyRuntimeFromRoom() || !ownershipPolicy.canWriteStudyRuntimeToRoom()) {
            return null
        }
        val safeToken = token.takeIf { it.isNotEmpty() } ?: return null
        val claimed = studyQueueMutationGate.mutate {
            claimInTransaction {
                val current = studyItems.get(kanji, answerSignature) ?: return@claimInTransaction null
                if (current.state !in activeStateWireNames) {
                    return@claimInTransaction null
                }
                val existingToken = current.activeToken?.takeIf { it.isNotEmpty() }
                val updated = if (existingToken == null) {
                    current.copy(activeToken = safeToken)
                } else {
                    current
                }
                if (updated != current) {
                    studyItems.upsert(updated)
                }
                updated
            }
        } ?: return null
        val withSimilar = similarKanjiPairs.kanjiWithSimilarNeighbors().toSet()
        return claimed.toDomain(hasSimilarKanji = withSimilar.contains(claimed.kanji))
    }

    override suspend fun updateReviewedItem(item: StudyQueueItem): Boolean {
        if (!ownershipPolicy.canWriteStudyRuntimeToRoom()) {
            return false
        }
        var updated = false
        studyQueueMutationGate.mutate {
            runInTransaction {
                val current = studyItems.get(item.kanji, item.answerSignature) ?: return@runInTransaction
                studyItems.upsert(current.withReviewUpdate(item))
                updated = true
            }
        }
        return updated
    }

    override suspend fun dueCount(
        state: StudyItemState,
        nowMillis: Long,
    ): Int =
        if (ownershipPolicy.canReadStudyRuntimeFromRoom()) {
            studyItems.dueCount(state.wireName, nowMillis)
        } else {
            0
        }

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
