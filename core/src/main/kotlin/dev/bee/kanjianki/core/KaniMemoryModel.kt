package dev.bee.kanjianki.core

/**
 * The long-term memory model behind the scheduler.
 *
 * The scheduler calls [review] exactly once for every answer that exercises a
 * core memory (legacy rung answers, including learning and relearning steps,
 * and adaptive core/revalidation answers). Repair appearances never call it.
 * Only the interval-bearing kinds ([MemoryAnswerKind.schedulesFromResult])
 * take their due date from the result; learning steps keep their configured
 * step delays and only let the model observe the answer.
 */
interface KaniMemoryModel {
    fun review(request: MemoryReviewRequest): MemoryReviewResult

    /**
     * The schedule a first Good would give [request]'s memory, without
     * recording any answer: a newly promoted core's first interval.
     */
    fun previewFirstGood(request: MemoryReviewRequest): MemoryReviewResult
}

enum class MemoryAnswerKind(
    /** RWKV's review kind: 1 learning, 2 review, 3 relearning. */
    val rwkvReviewKind: Int,
    val schedulesFromResult: Boolean,
) {
    NEW_LEARNING_STEP(1, false),
    RELEARNING_STEP(3, false),
    NEW_GRADUATION(1, true),
    RELEARNING_GRADUATION(3, true),
    REVIEW(2, true),
}

class MemoryReviewRequest(
    val kanji: String,
    val core: CoreSkill,
    /** The item's answer signature; its meaning part distinguishes a genuinely new card. */
    val answerSignature: String,
    /** A [StudyRatings] wire value; anything unknown counts as Again. */
    val rating: String,
    val kind: MemoryAnswerKind,
    val reviewedAtMillis: Long,
    val targetRetention: Double,
    /** The stored memory before the answer (opaque to RWKV; carried for compatibility). */
    val previousStability: Double,
    val previousDifficulty: Double,
    /** Whole days since the memory's last review, as the scheduler measures it. */
    val elapsedDays: Int,
)

class MemoryReviewResult(
    /** Days until recall falls to 90% (FSRS's definition of stability). */
    val stability: Double,
    /** Carried for downgrade compatibility; RWKV passes the previous value through. */
    val difficulty: Double,
    val intervalMillis: Long,
    /** The interval at a fixed 0.90 retention: the retention-independent promotion signal. */
    val promotionIntervalMillis: Long = intervalMillis,
) {
    fun intervalDays(): Int = (intervalMillis / DAY_MILLIS).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun promotionIntervalDays(): Int =
        (promotionIntervalMillis / DAY_MILLIS).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    companion object {
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}

/**
 * The model of a scheduler that is not allowed to apply reviews: queue
 * seeding, session selection and due counts never consult memory, so they
 * run without loading model state. Applying a review through it is a bug.
 */
object UnboundMemoryModel : KaniMemoryModel {
    override fun review(request: MemoryReviewRequest): MemoryReviewResult =
        throw IllegalStateException("BridgeScheduler has no memory model bound; use BridgeScheduler.withMemoryModel")

    override fun previewFirstGood(request: MemoryReviewRequest): MemoryReviewResult =
        throw IllegalStateException("BridgeScheduler has no memory model bound; use BridgeScheduler.withMemoryModel")
}
