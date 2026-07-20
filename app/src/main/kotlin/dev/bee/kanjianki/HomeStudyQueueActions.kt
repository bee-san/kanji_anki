package dev.bee.kanjianki

import dev.bee.kanjianki.core.DurableStudyItemRetentionPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyItemComparators

internal object HomeStudyQueueActions {
    @JvmStatic
    fun studyQueue(
        request: StudyQueueRequest,
        currentItems: List<RecordsStudyModels.StudyItem>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        val planItems = currentItems ?: request.reader.studyItems()
        if (!request.persist) {
            return planItems
        }
        val effectivePlan = request.plan ?: request.planProvider.adaptivePlan(
            request.rows,
            planItems,
            request.nowMillis,
        )
        val seeded = request.seeder.seedQueue(
            request.rows,
            planItems,
            request.settingsProvider.settings(),
            request.nowMillis,
            request.dayStartProvider.startOfDay(request.nowMillis),
            effectivePlan,
            request.ladderProvider.studyLadderSettings(),
        )
        val annotatedSeeded = request.writer.annotateSimilarKanjiAvailability(seeded)
        // Study callers supply the active-row subset, and activeDashboardRows is a
        // deliberately capped UI read. Preserve durable families outside that scope;
        // only the full sync/analyzer reconciliation may explicitly retire them.
        val persisted = if (currentItems == null) planItems else request.reader.studyItems()
        val retained = DurableStudyItemRetentionPolicy.retainUnseeded(annotatedSeeded, persisted)
        if (!StudyItemComparators.sameStudyItemsIgnoringOrder(persisted, retained)) {
            // Pass the exact pre-seed baseline, even though it is scoped. The
            // transaction re-reads the full durable state, merges reviews that
            // landed after this seed input was read, and retains out-of-cap rows.
            request.writer.replaceStudyItems(annotatedSeeded, planItems)
        }
        return annotatedSeeded
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

        fun replaceStudyItems(
            items: List<RecordsStudyModels.StudyItem>,
            baseline: List<RecordsStudyModels.StudyItem>,
        )
    }
}
