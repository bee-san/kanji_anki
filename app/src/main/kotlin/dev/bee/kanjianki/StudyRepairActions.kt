package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels

internal object StudyRepairActions {
    @JvmStatic
    fun activateSimilarWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
        nowMillis: Long,
        writer: SimilarWritingRepairWriter,
    ): ActiveRepair {
        val token = StudyTokenFactory.studyItem("repair-${repair.id}", repair.activeToken)
        val activeRepair = repair.withToken(token, nowMillis)
        writer.saveSimilarWritingRepair(activeRepair)
        return ActiveRepair(
            activeRepair,
            token,
            StudySessionTracker.similarRepairProgressKey(activeRepair),
            StudySessionTracker.similarRepairStudyTaskKey(activeRepair),
        )
    }

    @JvmStatic
    fun completeSimilarWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
        rating: String?,
        nowMillis: Long,
        finisher: SimilarWritingRepairFinisher,
        recorder: RepairOutcomeRecorder,
        marker: RepairTaskMarker,
    ): RepairCompletion {
        val passed = MainActivityBase.RATING_AGAIN != rating
        val saved = finisher.finishSimilarWritingRepair(repair.id, repair.activeToken, passed, nowMillis)
        if (saved) {
            recorder.recordRepairOutcome(repair.repairKanji, passed)
        }
        if (saved && passed) {
            marker.markStudyTaskCompleted(StudySessionTracker.similarRepairProgressKey(repair))
        }
        return RepairCompletion(saved, passed)
    }

    fun interface SimilarWritingRepairWriter {
        fun saveSimilarWritingRepair(repair: RecordsImportModels.SimilarKanjiWritingRepair)
    }

    fun interface SimilarWritingRepairFinisher {
        fun finishSimilarWritingRepair(repairId: Long, activeToken: String?, passed: Boolean, nowMillis: Long): Boolean
    }

    fun interface RepairOutcomeRecorder {
        fun recordRepairOutcome(kanji: String, passed: Boolean)
    }

    fun interface RepairTaskMarker {
        fun markStudyTaskCompleted(taskKey: String)
    }

    internal class ActiveRepair(
        private val repair: RecordsImportModels.SimilarKanjiWritingRepair,
        private val token: String,
        private val progressKey: String,
        private val studyTaskKey: String,
    ) {
        fun repair(): RecordsImportModels.SimilarKanjiWritingRepair = repair

        fun token(): String = token

        fun progressKey(): String = progressKey

        fun studyTaskKey(): String = studyTaskKey
    }

    internal class RepairCompletion(
        private val saved: Boolean,
        private val passed: Boolean,
    ) {
        fun saved(): Boolean = saved

        fun passed(): Boolean = passed
    }
}
