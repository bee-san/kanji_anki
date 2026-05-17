package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.repository.StudyRuntimeSnapshot
import dev.bee.kanjianki.domain.repository.StudyRuntimeSnapshotRepository

class RoomStudyRuntimeSnapshotRepository internal constructor(
    private val dashboardRows: DashboardRowDao,
    private val kanjiExamples: KanjiExampleDao,
    private val localSuspensions: LocalKanjiSuspensionDao,
    private val studyItems: StudyItemDao,
    private val similarKanjiPairs: SimilarKanjiPairDao,
    private val runInTransaction: suspend (suspend () -> StudyRuntimeSnapshot) -> StudyRuntimeSnapshot,
    private val exampleLimit: Int = RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT,
) : StudyRuntimeSnapshotRepository {
    constructor(
        database: KaniRoomDatabase,
        exampleLimit: Int = RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT,
    ) : this(
        dashboardRows = database.dashboardRowDao(),
        kanjiExamples = database.kanjiExampleDao(),
        localSuspensions = database.localKanjiSuspensionDao(),
        studyItems = database.studyItemDao(),
        similarKanjiPairs = database.similarKanjiPairDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
        exampleLimit = exampleLimit,
    )

    init {
        require(exampleLimit > 0) { "exampleLimit must be positive" }
    }

    override suspend fun activeSnapshot(dashboardLimit: Int): StudyRuntimeSnapshot =
        runInTransaction {
            val suspendedKanji = localSuspensions.listAll().mapTo(mutableSetOf()) { it.kanji }
            val rows = dashboardRows.listTop(dashboardLimit.coerceAtLeast(0))
                .filterNot { suspendedKanji.contains(it.kanji) }
                .map { row -> row.toDomain(kanjiExamples.listForKanji(row.kanji, exampleLimit)) }
            val itemRows = studyItems.listByStates(activeStateWireNames)
            val withSimilar = if (itemRows.isEmpty()) {
                emptySet()
            } else {
                similarKanjiPairs.kanjiWithSimilarNeighbors().toSet()
            }
            val items = itemRows.map { item ->
                item.toDomain(hasSimilarKanji = withSimilar.contains(item.kanji))
            }
            StudyRuntimeSnapshot(rows = rows, items = items)
        }

    private companion object {
        val activeStateWireNames = listOf(
            StudyItemState.NEW.wireName,
            StudyItemState.LEARNING.wireName,
            StudyItemState.REVIEW.wireName,
        )
    }
}
