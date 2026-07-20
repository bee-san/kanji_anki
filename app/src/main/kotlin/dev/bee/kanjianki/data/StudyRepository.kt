package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.KanjiReadingChoicePlanner
import dev.bee.kanjianki.core.ReadingKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StoreResult

/** Persistence capabilities owned by the Study route and its coordinators. */
interface StudyRepository {
    suspend fun loadQueue(nowMillis: Long): StoreResult<StudyQueueSnapshot>

    suspend fun loadItems(kanji: Collection<String>): StoreResult<List<RecordsStudyModels.StudyItem>>

    suspend fun replaceQueue(command: StudyQueueWriteCommand): StoreResult<Unit>

    suspend fun annotateCapabilities(
        items: List<RecordsStudyModels.StudyItem>,
    ): StoreResult<List<RecordsStudyModels.StudyItem>>

    /** The complete token insert, revision CAS, evidence, timing, and stats commit. */
    suspend fun commitReview(command: ReviewCommitCommand): StoreResult<ReviewCommitResult>

    suspend fun undoLastReview(snapshot: AppliedReviewSnapshot): StoreResult<Boolean>

    suspend fun reviewTokenStatus(query: ReviewTokenQuery): StoreResult<ReviewTokenStatus>

    suspend fun recoveryStatus(query: StudyRecoveryQuery): StoreResult<StudyRecoveryStatus>

    suspend fun loadChoiceData(
        kanji: String,
        nowMillis: Long,
    ): StoreResult<StudyChoiceDataSnapshot>

    suspend fun loadDueSimilarChoice(
        targetKanji: String,
        nowMillis: Long,
    ): StoreResult<RecordsImportModels.SimilarKanjiChoiceCard?>

    suspend fun loadDueLegacyWritingRepairs(
        nowMillis: Long,
    ): StoreResult<List<RecordsImportModels.SimilarKanjiWritingRepair>>

    suspend fun saveLegacyWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
    ): StoreResult<Unit>

    suspend fun finishLegacyWritingRepair(command: FinishLegacyRepairCommand): StoreResult<Boolean>

    suspend fun skipLegacyWritingRepair(command: SkipLegacyRepairCommand): StoreResult<Boolean>

    suspend fun loadMnemonic(kanji: String): StoreResult<String>
}

data class StudyQueueSnapshot(
    val activeRows: List<RecordsImportModels.DashboardRow>,
    val studyItems: List<RecordsStudyModels.StudyItem>,
    val locallySuspendedKanji: Set<String>,
    val latestSuccessfulSyncAtMillis: Long?,
    val studyLadder: RecordsBase.StudyLadderSettings,
    val schedulerParameters: RecordsSchedulerModels.SchedulerParameters,
    val schedulerFsrsWeights: List<Double>?,
    val learningSteps: RecordsSchedulerModels.LearningStepSettings,
    val adaptiveWorkload: AdaptiveWorkloadSnapshot,
    val studyAheadMinutes: Int,
    val studyStreak: StudyStreakSnapshot,
    val recentReviewStats: RecordsSchedulerModels.ReviewStats,
    val studiedKanjiToday: Set<String>,
    val dueLegacyWritingRepairs: List<RecordsImportModels.SimilarKanjiWritingRepair>,
)

data class StudyQueueWriteCommand(
    val items: List<RecordsStudyModels.StudyItem>,
    val baseline: List<RecordsStudyModels.StudyItem>? = null,
)

data class ReviewTokenQuery(
    val token: String,
    val kanji: String,
    val taskType: String,
    val answerSignature: String,
)

data class ReviewTokenStatus(
    val consumed: Boolean,
    val matchesReview: Boolean,
)

data class StudyRecoveryQuery(
    val review: ReviewTokenQuery,
    val repairId: Long? = null,
    val repairAttemptsBefore: Int = 0,
    val repairPassed: Boolean = false,
)

data class StudyRecoveryStatus(
    val token: ReviewTokenStatus,
    val legacyRepairFinished: Boolean,
)

data class StudyChoiceDataSnapshot(
    val kanjiReadingUsages: List<KanjiReadingChoicePlanner.Usage>,
    val kanjiReadingPool: List<KanjiReadingChoicePlanner.PoolReading>,
    val readingKanjiUsages: List<ReadingKanjiChoicePlanner.TargetUsage>,
    val readingKanjiCandidates: Map<String, List<ReadingKanjiChoicePlanner.Candidate>>,
    val activeRows: List<RecordsImportModels.DashboardRow>,
    val inventory: List<RecordsImportModels.KanjiInventoryItem>,
    val similarPairs: List<RecordsImportModels.SimilarKanjiPair>,
    val wrongPickCounts: Map<String, Map<String, Int>>,
)

data class FinishLegacyRepairCommand(
    val repairId: Long,
    val token: String,
    val passed: Boolean,
    val finishedAtMillis: Long,
)

data class SkipLegacyRepairCommand(
    val repairId: Long,
    val token: String,
    val skippedAtMillis: Long,
)
