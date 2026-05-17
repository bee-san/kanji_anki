package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.KaniRoomDatabase
import dev.bee.kanjianki.data.inventory.DashboardRowDao
import dev.bee.kanjianki.data.inventory.DashboardRowEntity
import dev.bee.kanjianki.data.inventory.KanjiExampleDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionDao
import dev.bee.kanjianki.data.inventory.LocalKanjiSuspensionEntity
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomStudyDashboardRepository internal constructor(
    private val dashboardRows: DashboardRowDao,
    private val kanjiExamples: KanjiExampleDao,
    private val localSuspensions: LocalKanjiSuspensionDao,
    private val exampleLimit: Int = DEFAULT_EXAMPLE_LIMIT,
) : StudyDashboardRepository {
    constructor(
        database: KaniRoomDatabase,
        exampleLimit: Int = DEFAULT_EXAMPLE_LIMIT,
    ) : this(
        database.dashboardRowDao(),
        database.kanjiExampleDao(),
        database.localKanjiSuspensionDao(),
        exampleLimit,
    )

    init {
        require(exampleLimit > 0) { "exampleLimit must be positive" }
    }

    override fun observeTop(limit: Int): Flow<List<StudyDashboardRow>> =
        dashboardRows.observeTop(limit).map { rows ->
            rows.map { it.toDomainWithExamples() }
        }

    override fun observeActive(limit: Int): Flow<List<StudyDashboardRow>> =
        combine(dashboardRows.observeTop(limit), localSuspensions.observeAll()) { rows, suspensions ->
            rows.withoutSuspended(suspensions).map { it.toDomainWithExamples() }
        }

    override suspend fun listTop(limit: Int): List<StudyDashboardRow> =
        dashboardRows.listTop(limit).map { it.toDomainWithExamples() }

    override suspend fun listActive(limit: Int): List<StudyDashboardRow> =
        dashboardRows.listTop(limit)
            .withoutSuspended(localSuspensions.listAll())
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
        if (suspended) {
            localSuspensions.upsert(
                LocalKanjiSuspensionEntity(
                    kanji = safeKanji,
                    suspendedAt = nowMillis.coerceAtLeast(0L),
                ),
            )
        } else {
            localSuspensions.delete(safeKanji)
        }
        return true
    }

    private suspend fun DashboardRowEntity.toDomainWithExamples(): StudyDashboardRow =
        toDomain(kanjiExamples.listForKanji(kanji, exampleLimit))

    private fun List<DashboardRowEntity>.withoutSuspended(
        suspensions: List<LocalKanjiSuspensionEntity>,
    ): List<DashboardRowEntity> {
        val suspendedKanji = suspensions.mapTo(mutableSetOf()) { it.kanji }
        return filterNot { suspendedKanji.contains(it.kanji) }
    }

    private companion object {
        const val DEFAULT_EXAMPLE_LIMIT = 8

        fun normalizedKanji(kanji: String): String = kanji.trim()
    }
}
