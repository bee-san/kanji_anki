package dev.bee.kanjianki.data.repository

import androidx.room.withTransaction
import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import dev.bee.kanjianki.data.study.LearningRepeatDao
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomStudyDashboardRepository internal constructor(
    private val dashboardRows: DashboardRowDao,
    private val kanjiExamples: KanjiExampleDao,
    private val localSuspensions: LocalKanjiSuspensionDao,
    private val learningRepeats: LearningRepeatDao,
    private val runInTransaction: suspend (suspend () -> Boolean) -> Boolean = { block -> block() },
    private val exampleLimit: Int = DEFAULT_EXAMPLE_LIMIT,
) : StudyDashboardRepository {
    constructor(
        database: KaniRoomDatabase,
        exampleLimit: Int = DEFAULT_EXAMPLE_LIMIT,
    ) : this(
        dashboardRows = database.dashboardRowDao(),
        kanjiExamples = database.kanjiExampleDao(),
        localSuspensions = database.localKanjiSuspensionDao(),
        learningRepeats = database.learningRepeatDao(),
        runInTransaction = { block -> database.withTransaction { block() } },
        exampleLimit = exampleLimit,
    )

    init {
        require(exampleLimit > 0) { "exampleLimit must be positive" }
    }

    override fun observeTop(limit: Int): Flow<List<StudyDashboardRow>> =
        dashboardRows.observeTop(limit).map { rows ->
            rows.map { it.toDomainWithExamples() }
        }

    override fun observeActive(limit: Int): Flow<List<StudyDashboardRow>> =
        combine(dashboardRows.observeAllOrdered(), localSuspensions.observeAll()) { rows, suspensions ->
            rows.activeRows(limit, suspensions).map { it.toDomainWithExamples() }
        }

    override suspend fun listTop(limit: Int): List<StudyDashboardRow> =
        dashboardRows.listTop(limit).map { it.toDomainWithExamples() }

    override suspend fun listActive(limit: Int): List<StudyDashboardRow> =
        dashboardRows.listAllOrdered()
            .activeRows(limit, localSuspensions.listAll())
            .map { it.toDomainWithExamples() }

    override suspend fun get(kanji: String): StudyDashboardRow? =
        dashboardRows.get(kanji)?.toDomainWithExamples()

    override suspend fun isLocallySuspended(kanji: String): Boolean {
        val safeKanji = normalizedKanji(kanji)
        return safeKanji.isNotEmpty() && localSuspensions.get(safeKanji) != null
    }

    override suspend fun setLocallySuspended(
        kanji: String,
        suspended: Boolean,
        nowMillis: Long,
    ): Boolean {
        val safeKanji = normalizedKanji(kanji)
        if (safeKanji.isEmpty()) {
            return false
        }
        return runInTransaction {
            if (suspended) {
                localSuspensions.upsert(
                    LocalKanjiSuspensionEntity(
                        kanji = safeKanji,
                        suspendedAt = nowMillis.coerceAtLeast(0L),
                    ),
                )
                learningRepeats.deleteForKanji(safeKanji)
            } else {
                localSuspensions.delete(safeKanji)
            }
            true
        }
    }

    private suspend fun DashboardRowEntity.toDomainWithExamples(): StudyDashboardRow =
        toDomain(kanjiExamples.listForKanji(kanji, exampleLimit))

    private fun List<DashboardRowEntity>.activeRows(
        limit: Int,
        suspensions: List<LocalKanjiSuspensionEntity>,
    ): List<DashboardRowEntity> {
        if (limit <= 0) {
            return emptyList()
        }
        val suspendedKanji = suspensions.mapTo(mutableSetOf()) { it.kanji }
        return filterNot { suspendedKanji.contains(it.kanji) }.take(limit)
    }

    companion object {
        const val DEFAULT_EXAMPLE_LIMIT = 8

        private fun normalizedKanji(kanji: String): String = kanji.trim()
    }
}
