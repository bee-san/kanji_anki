package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.history.KanjiTimelineEventDao
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.KanjiInventoryDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.similar.SimilarKanjiPairDao
import dev.bee.kanjianki.data.study.StudyItemDao
import dev.bee.kanjianki.domain.model.study.StudyKanjiRecoveryTimeline
import dev.bee.kanjianki.domain.repository.StudyKanjiDetailRepository

class RoomStudyKanjiDetailRepository internal constructor(
    private val dashboardRows: DashboardRowDao,
    private val kanjiExamples: KanjiExampleDao,
    private val kanjiInventory: KanjiInventoryDao,
    private val localSuspensions: LocalKanjiSuspensionDao,
    private val studyItems: StudyItemDao,
    private val timelineEvents: KanjiTimelineEventDao,
    private val similarKanjiPairs: SimilarKanjiPairDao,
    private val runInTransaction: suspend (suspend () -> StudyKanjiRecoveryTimeline) -> StudyKanjiRecoveryTimeline,
    private val exampleLimit: Int = RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT,
) : StudyKanjiDetailRepository {
    constructor(
        database: KaniRoomDatabase,
        exampleLimit: Int = RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT,
    ) : this(
        dashboardRows = database.dashboardRowDao(),
        kanjiExamples = database.kanjiExampleDao(),
        kanjiInventory = database.kanjiInventoryDao(),
        localSuspensions = database.localKanjiSuspensionDao(),
        studyItems = database.studyItemDao(),
        timelineEvents = database.kanjiTimelineEventDao(),
        similarKanjiPairs = database.similarKanjiPairDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
        exampleLimit = exampleLimit,
    )

    init {
        require(exampleLimit > 0) { "exampleLimit must be positive" }
    }

    override suspend fun timelineForKanji(
        kanji: String,
        eventLimit: Int,
    ): StudyKanjiRecoveryTimeline {
        val normalizedKanji = kanji.trim()
        if (normalizedKanji.isEmpty()) {
            return StudyKanjiRecoveryTimeline()
        }
        return runInTransaction {
            val locallySuspended = localSuspensions.get(normalizedKanji) != null
            val row = dashboardRows.get(normalizedKanji)
                ?.toDomain(kanjiExamples.listForKanji(normalizedKanji, exampleLimit))
            val inventory = kanjiInventory.get(normalizedKanji)
                ?.toDomain(suspended = locallySuspended)
            val item = studyItems.latestForKanji(normalizedKanji)
                ?.toDomain(hasSimilarKanji = similarKanjiPairs.listForKanji(normalizedKanji).isNotEmpty())
            val events = if (eventLimit <= 0) {
                emptyList()
            } else {
                timelineEvents.listLatestForKanji(normalizedKanji, eventLimit)
                    .asReversed()
                    .map { it.toDomain() }
            }
            StudyKanjiRecoveryTimeline(
                inventoryItem = inventory,
                currentRow = row,
                currentStudyItem = item,
                events = events,
            )
        }
    }
}
