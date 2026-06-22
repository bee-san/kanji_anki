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
        if (!sameStudyQueue(persisted, annotated)) {
            request.writer.replaceStudyItems(annotated)
        }
        return annotated
    }

    private fun sameStudyQueue(
        current: List<RecordsStudyModels.StudyItem>,
        annotated: List<RecordsStudyModels.StudyItem>,
    ): Boolean {
        if (current.size != annotated.size) {
            return false
        }
        for (i in current.indices) {
            if (!sameStudyItem(current[i], annotated[i])) {
                return false
            }
        }
        return true
    }

    private fun sameStudyItem(
        current: RecordsStudyModels.StudyItem,
        annotated: RecordsStudyModels.StudyItem,
    ): Boolean {
        return current.kanji == annotated.kanji &&
            current.state == annotated.state &&
            current.dueAtMillis == annotated.dueAtMillis &&
            current.stability == annotated.stability &&
            current.difficulty == annotated.difficulty &&
            current.totalReviews == annotated.totalReviews &&
            current.lapses == annotated.lapses &&
            current.learningStep == annotated.learningStep &&
            current.writingLevel == annotated.writingLevel &&
            current.recognitionStage == annotated.recognitionStage &&
            current.consecutiveFailedRecognitionDays == annotated.consecutiveFailedRecognitionDays &&
            current.lastFailedRecognitionDayMillis == annotated.lastFailedRecognitionDayMillis &&
            current.writingRemediationPending == annotated.writingRemediationPending &&
            current.suppressedByTaskType == annotated.suppressedByTaskType &&
            current.suppressedAtMillis == annotated.suppressedAtMillis &&
            current.matureIntervalDays == annotated.matureIntervalDays &&
            current.answerSignature == annotated.answerSignature &&
            current.activeToken == annotated.activeToken &&
            current.createdAtMillis == annotated.createdAtMillis &&
            sameTaskMemory(current.typingMeaningMemory, annotated.typingMeaningMemory) &&
            sameTaskMemory(current.meaningKanjiMemory, annotated.meaningKanjiMemory) &&
            sameTaskMemory(current.kanjiMeaningMemory, annotated.kanjiMeaningMemory) &&
            sameTaskMemory(current.fontMeaningMemory, annotated.fontMeaningMemory) &&
            sameTaskMemory(current.wordReadingMemory, annotated.wordReadingMemory) &&
            sameTaskMemory(current.writingRemediationMemory, annotated.writingRemediationMemory) &&
            current.rung == annotated.rung &&
            current.phase == annotated.phase &&
            current.realPassStreak == annotated.realPassStreak &&
            current.realAgainStreak == annotated.realAgainStreak &&
            current.lastRealReviewDueAtMillis == annotated.lastRealReviewDueAtMillis &&
            current.hasSimilarKanji == annotated.hasSimilarKanji &&
            sameTaskMemory(current.similarKanjiMemory, annotated.similarKanjiMemory)
    }

    private fun sameTaskMemory(
        current: RecordsStudyModels.TaskMemory,
        annotated: RecordsStudyModels.TaskMemory,
    ): Boolean {
        return current.encode() == annotated.encode()
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
