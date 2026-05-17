package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomStudyDashboardRepository(
    database: KaniRoomDatabase,
    private val exampleLimit: Int = DEFAULT_EXAMPLE_LIMIT,
) : StudyDashboardRepository {
    private val dashboardRows = database.dashboardRowDao()
    private val kanjiExamples = database.kanjiExampleDao()

    init {
        require(exampleLimit > 0) { "exampleLimit must be positive" }
    }

    override fun observeTop(limit: Int): Flow<List<StudyDashboardRow>> =
        dashboardRows.observeTop(limit).map { rows ->
            rows.map { it.toDomainWithExamples() }
        }

    override suspend fun listTop(limit: Int): List<StudyDashboardRow> =
        dashboardRows.listTop(limit).map { it.toDomainWithExamples() }

    override suspend fun get(kanji: String): StudyDashboardRow? =
        dashboardRows.get(kanji)?.toDomainWithExamples()

    private suspend fun DashboardRowEntity.toDomainWithExamples(): StudyDashboardRow =
        toDomain(kanjiExamples.listForKanji(kanji, exampleLimit))

    private companion object {
        const val DEFAULT_EXAMPLE_LIMIT = 8
    }
}
