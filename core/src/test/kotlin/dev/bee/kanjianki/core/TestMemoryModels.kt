package dev.bee.kanjianki.core

import dev.bee.rwkv.RwkvCollectionState
import dev.bee.rwkv.RwkvEngine
import dev.bee.rwkv.RwkvModel
import dev.bee.rwkv.RwkvWeights
import java.io.File

/** Memory models and schedulers for tests. */
internal object TestMemoryModels {
    /** The FSRS-6 test double: closed-form intervals for policy tests. */
    fun fsrs(): KaniMemoryModel = FsrsTestMemoryModel()

    fun fsrsScheduler(): BridgeScheduler = BridgeScheduler.withMemoryModel(fsrs())

    private val rwkvModel: RwkvModel by lazy {
        val path = System.getProperty("kani.rwkv.weights") ?: error("kani.rwkv.weights is not set (run through Gradle)")
        RwkvModel.load(RwkvWeights.parse(File(path).readBytes()))
    }

    val rwkvEngine: RwkvEngine by lazy { RwkvEngine(rwkvModel) }

    /** The production RWKV model over a fresh in-memory collection, on UTC days. */
    fun rwkv(state: RwkvCollectionState = RwkvCollectionState()): RwkvMemoryModel =
        RwkvMemoryModel(rwkvEngine, state, { millis -> Math.floorDiv(millis, MemoryReviewResult.DAY_MILLIS) })

    fun rwkvScheduler(state: RwkvCollectionState = RwkvCollectionState()): BridgeScheduler =
        BridgeScheduler.withMemoryModel(rwkv(state))
}

/**
 * Keeps the stored memory and schedules every review answer [reviewIntervalMillis]
 * ahead; graduations and first-Good previews get one day. Learning steps are
 * observed only. Records the requests it saw.
 */
internal open class FixedIntervalMemoryModel(
    private val reviewIntervalMillis: Long,
    private val promotionIntervalMillis: Long = reviewIntervalMillis,
    private val graduationIntervalMillis: Long = BridgeScheduler.DAY,
) : KaniMemoryModel {
    val requests = ArrayList<MemoryReviewRequest>()
    val elapsedDays: Int get() = requests.lastOrNull { it.kind == MemoryAnswerKind.REVIEW }?.elapsedDays ?: -1

    override fun review(request: MemoryReviewRequest): MemoryReviewResult {
        requests += request
        return when (request.kind) {
            MemoryAnswerKind.REVIEW -> MemoryReviewResult(
                request.previousStability,
                request.previousDifficulty,
                reviewIntervalMillis,
                promotionIntervalMillis,
            )
            MemoryAnswerKind.NEW_GRADUATION, MemoryAnswerKind.RELEARNING_GRADUATION ->
                MemoryReviewResult(request.previousStability, request.previousDifficulty, graduationIntervalMillis)
            MemoryAnswerKind.NEW_LEARNING_STEP, MemoryAnswerKind.RELEARNING_STEP ->
                MemoryReviewResult(request.previousStability, request.previousDifficulty, 0L)
        }
    }

    override fun previewFirstGood(request: MemoryReviewRequest): MemoryReviewResult =
        MemoryReviewResult(request.previousStability, request.previousDifficulty, graduationIntervalMillis)
}
