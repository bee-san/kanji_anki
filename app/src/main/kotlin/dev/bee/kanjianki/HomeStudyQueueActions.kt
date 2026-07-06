package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels

internal object HomeStudyQueueActions {
    @JvmStatic
    fun studyQueue(
        request: StudyQueueRequest,
        currentItems: List<RecordsStudyModels.StudyItem>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        val current = currentItems ?: request.reader.studyItems()
        if (!request.persist) {
            return current
        }
        val effectivePlan = request.plan ?: request.planProvider.adaptivePlan(
            request.rows,
            current,
            request.nowMillis,
        )
        val seeded = request.seeder.seedQueue(
            request.rows,
            current,
            request.settingsProvider.settings(),
            request.nowMillis,
            request.dayStartProvider.startOfDay(request.nowMillis),
            effectivePlan,
            request.ladderProvider.studyLadderSettings(),
        )
        val annotated = request.writer.annotateSimilarKanjiAvailability(seeded)
        val persisted = if (currentItems == null) {
            current
        } else {
            request.reader.studyItems()
        }
        if (!StudyItemComparators.sameStudyQueue(persisted, annotated)) {
            request.writer.replaceStudyItems(annotated)
        }
        return annotated
    }

    @JvmRecord
    internal data class StudyQueueRequest(
        val rows: List<RecordsImportModels.DashboardRow>,
        val nowMillis: Long,
        val persist: Boolean,
        val plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        val reader: StudyItemsReader,
        val settingsProvider: StudySettingsProvider,
        val dayStartProvider: DayStartProvider,
        val ladderProvider: StudyLadderProvider,
        val planProvider: AdaptivePlanProvider,
        val seeder: StudyQueueSeeder,
        val writer: StudyItemsWriter,
    )

    fun interface StudyItemsReader {
        fun studyItems(): List<RecordsStudyModels.StudyItem>
    }

    fun interface StudySettingsProvider {
        fun settings(): RecordsSyncModels.Settings
    }

    fun interface DayStartProvider {
        fun startOfDay(nowMillis: Long): Long
    }

    fun interface StudyLadderProvider {
        fun studyLadderSettings(): RecordsBase.StudyLadderSettings
    }

    fun interface AdaptivePlanProvider {
        fun adaptivePlan(
            rows: List<RecordsImportModels.DashboardRow>,
            currentItems: List<RecordsStudyModels.StudyItem>,
            nowMillis: Long,
        ): RecordsSchedulerModels.AdaptiveLoadPlan
    }

    fun interface StudyQueueSeeder {
        fun seedQueue(
            rows: List<RecordsImportModels.DashboardRow>,
            currentItems: List<RecordsStudyModels.StudyItem>,
            settings: RecordsSyncModels.Settings,
            nowMillis: Long,
            startOfDayMillis: Long,
            plan: RecordsSchedulerModels.AdaptiveLoadPlan,
            ladder: RecordsBase.StudyLadderSettings,
        ): List<RecordsStudyModels.StudyItem>
    }

    interface StudyItemsWriter {
        fun annotateSimilarKanjiAvailability(items: List<RecordsStudyModels.StudyItem>): List<RecordsStudyModels.StudyItem>

        fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>)
    }
}
