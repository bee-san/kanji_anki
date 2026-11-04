package dev.bee.kanjianki.application

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.FinishLegacyRepairCommand
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.ReviewCommitResult
import dev.bee.kanjianki.data.ReviewTaskTiming
import dev.bee.kanjianki.data.ReviewTokenQuery
import dev.bee.kanjianki.data.ReviewTokenStatus
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SkipLegacyRepairCommand
import dev.bee.kanjianki.data.StudyChoiceDataSnapshot
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import dev.bee.kanjianki.data.StudyRecoveryQuery
import dev.bee.kanjianki.data.StudyRecoveryStatus
import dev.bee.kanjianki.data.StudyRepository

/** Portable Study persistence orchestration and repository error mapping. */
class StudyUseCases(
    private val repository: StudyRepository,
) {
    suspend fun loadQueue(nowMillis: Long): StudyQueueSnapshot =
        repository.loadQueue(nowMillis).valueOrThrow("load study queue")

    suspend fun loadAllItems(): List<RecordsStudyModels.StudyItem> =
        repository.loadAllItems().valueOrThrow("load all study items")

    suspend fun loadItems(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> =
        repository.loadItems(kanji).valueOrThrow("load study items")

    suspend fun replaceQueue(
        items: List<RecordsStudyModels.StudyItem>,
        baseline: List<RecordsStudyModels.StudyItem>? = null,
    ) {
        repository.replaceQueue(StudyQueueWriteCommand(items, baseline))
            .valueOrThrow("replace study queue")
    }

    suspend fun annotateCapabilities(
        items: List<RecordsStudyModels.StudyItem>,
    ): List<RecordsStudyModels.StudyItem> =
        repository.annotateCapabilities(items).valueOrThrow("annotate study capabilities")

    suspend fun saveItem(item: RecordsStudyModels.StudyItem) {
        repository.saveItem(item).valueOrThrow("save study item")
    }

    suspend fun recordTaskTiming(timing: ReviewTaskTiming): Boolean =
        repository.recordTaskTiming(timing).valueOrThrow("record study task timing")

    suspend fun commitReview(command: ReviewCommitCommand): ReviewCommitResult =
        repository.commitReview(command).valueOrThrow("commit review")

    suspend fun undoLastReview(snapshot: AppliedReviewSnapshot): Boolean =
        repository.undoLastReview(snapshot).valueOrThrow("undo review")

    suspend fun loadQueueVersion(): Long? =
        repository.loadQueueVersion().valueOrThrow("load study queue version")

    suspend fun reviewTokenStatus(query: ReviewTokenQuery): ReviewTokenStatus =
        repository.reviewTokenStatus(query).valueOrThrow("load review token status")

    suspend fun recoveryStatus(query: StudyRecoveryQuery): StudyRecoveryStatus =
        repository.recoveryStatus(query).valueOrThrow("load study recovery status")

    suspend fun loadChoiceData(
        kanji: String,
        nowMillis: Long,
    ): StudyChoiceDataSnapshot =
        repository.loadChoiceData(kanji, nowMillis).valueOrThrow("load study choice data")

    suspend fun loadDueSimilarChoice(
        targetKanji: String,
        nowMillis: Long,
    ): RecordsImportModels.SimilarKanjiChoiceCard? =
        repository.loadDueSimilarChoice(targetKanji, nowMillis)
            .valueOrThrow("load due similar choice")

    suspend fun loadDueLegacyWritingRepairs(
        nowMillis: Long,
    ): List<RecordsImportModels.SimilarKanjiWritingRepair> =
        repository.loadDueLegacyWritingRepairs(nowMillis)
            .valueOrThrow("load legacy writing repairs")

    suspend fun saveLegacyWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
    ) {
        repository.saveLegacyWritingRepair(repair).valueOrThrow("save legacy writing repair")
    }

    suspend fun finishLegacyWritingRepair(command: FinishLegacyRepairCommand): Boolean =
        repository.finishLegacyWritingRepair(command).valueOrThrow("finish legacy writing repair")

    suspend fun skipLegacyWritingRepair(command: SkipLegacyRepairCommand): Boolean =
        repository.skipLegacyWritingRepair(command).valueOrThrow("skip legacy writing repair")

    suspend fun loadMnemonic(kanji: String): String =
        repository.loadMnemonic(kanji).valueOrThrow("load study mnemonic")

    suspend fun saveMnemonic(command: SaveMnemonicCommand) {
        repository.saveMnemonic(command).valueOrThrow("save study mnemonic")
    }
}
