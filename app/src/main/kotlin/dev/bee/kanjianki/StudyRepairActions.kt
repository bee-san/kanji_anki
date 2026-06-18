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

    @JvmStatic
    fun skipSimilarWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
        nowMillis: Long,
        skipper: SimilarWritingRepairSkipper,
        recorder: RepairOutcomeRecorder,
        marker: RepairTaskMarker,
    ): RepairCompletion {
        val saved = skipper.skipSimilarWritingRepair(repair.id, repair.activeToken, nowMillis)
        if (saved) {
            recorder.recordRepairOutcome(repair.repairKanji, false)
            marker.markStudyTaskCompleted(StudySessionTracker.similarRepairProgressKey(repair))
        }
        return RepairCompletion(saved, false)
    }

    fun interface SimilarWritingRepairWriter {
        fun saveSimilarWritingRepair(repair: RecordsImportModels.SimilarKanjiWritingRepair)
    }

    fun interface SimilarWritingRepairFinisher {
        fun finishSimilarWritingRepair(repairId: Long, activeToken: String?, passed: Boolean, nowMillis: Long): Boolean
    }

    fun interface SimilarWritingRepairSkipper {
        fun skipSimilarWritingRepair(repairId: Long, activeToken: String?, nowMillis: Long): Boolean
    }

    fun interface RepairOutcomeRecorder {
        fun recordRepairOutcome(kanji: String, passed: Boolean)
    }

    fun interface RepairTaskMarker {
        fun markStudyTaskCompleted(taskKey: String)
    }

    @JvmRecord
    internal data class ActiveRepair(
        val repair: RecordsImportModels.SimilarKanjiWritingRepair,
        val token: String,
        val progressKey: String,
        val studyTaskKey: String,
    )

    @JvmRecord
    internal data class RepairCompletion(
        val saved: Boolean,
        val passed: Boolean,
    )
}
