package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import dev.bee.kanjianki.domain.scheduler.LoadNextStudySessionRequest
import dev.bee.kanjianki.domain.scheduler.LoadNextStudySessionUseCase
import dev.bee.kanjianki.domain.scheduler.StudySessionSelector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

class LegacyLoadNextStudySessionBridge(
    private val selector: StudySessionSelector = StudySessionSelector(),
) {
    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsSchedulerModels.StudySession? {
        val legacyItems = items.orEmpty()
        val legacyRows = rows.orEmpty()
        val domainItems = LegacyStudyMappers.toDomainItems(legacyItems)
        val domainRows = LegacyStudyMappers.toDomainRows(legacyRows)
        val useCase = LoadNextStudySessionUseCase(
            studyQueueRepository = InMemoryStudyQueueRepository(domainItems),
            studyDashboardRepository = InMemoryStudyDashboardRepository(domainRows),
            selector = selector,
        )
        val session = runBlocking {
            useCase(
                LoadNextStudySessionRequest(
                    nowMillis = nowMillis,
                    dashboardLimit = legacyRows.size.coerceAtLeast(1),
                    studyAheadMillis = studyAheadMillis,
                    allowedKanji = allowedKanji,
                    ladderSettings = LegacyStudyMappers.toDomain(settings, ladder),
                    newCardSortMode = LegacyStudyMappers.newCardSortMode(settings),
                ),
            )
        } ?: return null

        val original = originalItem(legacyItems, session.item)
        return RecordsSchedulerModels.StudySession(
            LegacyStudyMappers.toLegacy(original, session.item),
            legacyRows.firstOrNull { it.kanji == session.item.kanji },
            session.token,
            session.taskType,
            session.writingRequired,
            session.prompt,
        )
    }

    private fun originalItem(
        items: List<RecordsStudyModels.StudyItem>,
        selected: StudyQueueItem,
    ): RecordsStudyModels.StudyItem {
        return items.firstOrNull {
            it.kanji == selected.kanji && it.answerSignature == selected.answerSignature
        } ?: items.firstOrNull {
            it.kanji == selected.kanji
        } ?: throw IllegalStateException("Selected study item missing from legacy input: ${selected.kanji}")
    }

    private class InMemoryStudyQueueRepository(
        private val items: List<StudyQueueItem>,
    ) : StudyQueueRepository {
        override suspend fun listActive(): List<StudyQueueItem> = items

        override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> =
            items.filter { it.state == state }

        override suspend fun listAllForSeeding(): List<StudyQueueItem> = items

        override suspend fun replaceAllSeeded(items: List<StudyQueueItem>) = Unit

        override suspend fun updateReviewedItem(item: StudyQueueItem): Boolean = false

        override suspend fun dueCount(
            state: StudyItemState,
            nowMillis: Long,
        ): Int = items.count { it.state == state && it.dueAtMillis <= nowMillis }
    }

    private class InMemoryStudyDashboardRepository(
        private val rows: List<StudyDashboardRow>,
    ) : StudyDashboardRepository {
        override fun observeTop(limit: Int): Flow<List<StudyDashboardRow>> =
            flowOf(rows.take(limit))

        override fun observeActive(limit: Int): Flow<List<StudyDashboardRow>> =
            flowOf(rows.take(limit))

        override suspend fun listTop(limit: Int): List<StudyDashboardRow> =
            rows.take(limit)

        override suspend fun listActive(limit: Int): List<StudyDashboardRow> =
            rows.take(limit)

        override suspend fun get(kanji: String): StudyDashboardRow? =
            rows.firstOrNull { it.kanji == kanji }

        override suspend fun isLocallySuspended(kanji: String): Boolean = false

        override suspend fun setLocallySuspended(
            kanji: String,
            suspended: Boolean,
            nowMillis: Long,
        ): Boolean = false
    }
}
