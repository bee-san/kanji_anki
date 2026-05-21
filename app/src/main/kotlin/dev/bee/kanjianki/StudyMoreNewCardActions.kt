package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsStudyModels

internal object StudyMoreNewCardActions {
    @JvmStatic
    fun applyAdmission(
        result: BridgeScheduler.ExtraNewCardsResult,
        writer: StudyItemWriter,
        selectedKanji: MutableList<String>,
        progressResetter: ProgressResetter,
        targetCounter: TargetCounter,
    ): AdmissionResult {
        if (!result.admittedAny()) {
            return AdmissionResult(false, result.admittedCount)
        }
        val seeded = writer.annotateSimilarKanjiAvailability(result.items)
        writer.replaceStudyItems(seeded)
        selectedKanji.clear()
        selectedKanji.addAll(result.admittedKanji)
        progressResetter.resetStudyRunProgress()
        targetCounter.setTargetCount(result.admittedCount)
        return AdmissionResult(true, result.admittedCount)
    }

    interface StudyItemWriter {
        fun annotateSimilarKanjiAvailability(items: List<RecordsStudyModels.StudyItem>): List<RecordsStudyModels.StudyItem>

        fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>)
    }

    fun interface ProgressResetter {
        fun resetStudyRunProgress()
    }

    fun interface TargetCounter {
        fun setTargetCount(targetCount: Int)
    }

    internal class AdmissionResult(
        private val admittedAny: Boolean,
        private val admittedCount: Int,
    ) {
        fun admittedAny(): Boolean = admittedAny

        fun admittedCount(): Int = admittedCount
    }
}
