package dev.bee.kanjianki.core

import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvEngine
import dev.bee.rwkv.RwkvStateView
import dev.bee.rwkv.RwkvStreamIds
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * How Kani's memories map onto RWKV's five streams:
 *
 * - card: one per (kanji, core skill, signature meaning). A same-meaning
 *   signature reshuffle keeps the card; a genuine meaning change (which also
 *   resets Kani's memories) starts a new one.
 * - note: the kanji, so recognition and contextual reading are siblings the
 *   way an Anki note's cards are.
 * - deck: the core skill.
 * - preset: one for all of Kani; the user stream is global.
 */
object RwkvStreamKeys {
    @JvmStatic
    fun idsFor(kanji: String, core: CoreSkill, answerSignature: String): RwkvStreamIds =
        RwkvStreamIds(
            card = cardId(kanji, core, answerSignature),
            deck = stableId("deck", core.wireName()),
            note = stableId("note", kanji),
            preset = stableId("preset", "kani"),
        )

    @JvmStatic
    fun cardId(kanji: String, core: CoreSkill, answerSignature: String): Long =
        stableId("card", kanji, core.wireName(), StudyQueueSeeder.signatureMeaning(answerSignature))

    /** A positive 63-bit id from SHA-256 of the length-prefixed parts. */
    internal fun stableId(vararg parts: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return ByteBuffer.wrap(digest.digest()).long and Long.MAX_VALUE
    }
}

/** Scheduling days for the model: only differences between them matter. */
object RwkvDays {
    /**
     * The model day of a review from the local day start the review log
     * persists (`review_day_start`). Noon-anchoring keeps 23- and 25-hour
     * daylight-saving days on distinct consecutive integers.
     */
    @JvmStatic
    fun fromDayStart(localDayStartMillis: Long): Long =
        Math.floorDiv(localDayStartMillis + HALF_DAY_MILLIS, MemoryReviewResult.DAY_MILLIS)

    private const val HALF_DAY_MILLIS: Long = 12L * 60L * 60L * 1000L
}

/** One answer [RwkvMemoryModel] fed to the model, as the review log persists it for replay. */
class RwkvFedAnswer(val ids: RwkvStreamIds, val answer: RwkvAnswer)

/**
 * The production memory model: RWKV-Curve over persisted collection state.
 *
 * Answers are applied to [view] immediately; with a snapshot view the caller
 * persists the view's delta in the review transaction. [durationMillis] is
 * the answered card's active time (null in simulations), and [dayOffsetOf]
 * must give the same day the review log will record for the review.
 * [singlePass] trades the four-answer grade-order search for one forward pass
 * per answer; only long simulations (forecasts) set it.
 */
class RwkvMemoryModel @JvmOverloads constructor(
    private val engine: RwkvEngine,
    private val view: RwkvStateView,
    private val dayOffsetOf: (Long) -> Long,
    private val durationMillis: Long? = null,
    private val maxIntervalDays: Int = MAX_INTERVAL_DAYS,
    private val singlePass: Boolean = false,
) : KaniMemoryModel {
    private val fed = ArrayList<RwkvFedAnswer>()

    /** The answers fed so far, in order. */
    val fedAnswers: List<RwkvFedAnswer> get() = fed

    override fun review(request: MemoryReviewRequest): MemoryReviewResult {
        val ids = RwkvStreamKeys.idsFor(request.kanji, request.core, request.answerSignature)
        val answer = answerFor(request, ratingCode(request.rating))
        val target = retention(request.targetRetention)
        val outcome = if (singlePass) {
            engine.answerSinglePass(view, ids, answer, target, maxIntervalDays)
        } else {
            engine.answer(view, ids, answer, target, maxIntervalDays)
        }
        fed += RwkvFedAnswer(ids, answer)
        return result(outcome.s90Days, request.previousDifficulty, outcome.intervalDays, outcome.promotionIntervalDays)
    }

    override fun previewFirstGood(request: MemoryReviewRequest): MemoryReviewResult {
        val ids = RwkvStreamKeys.idsFor(request.kanji, request.core, request.answerSignature)
        val answer = answerFor(request, GOOD)
        val outcome = engine.preview(view, ids, answer, retention(request.targetRetention), maxIntervalDays)
        return result(outcome.s90Days, request.previousDifficulty, outcome.intervalDays, outcome.promotionIntervalDays)
    }

    private fun answerFor(request: MemoryReviewRequest, rating: Int): RwkvAnswer =
        RwkvAnswer(
            rating = rating,
            durationMillis = durationMillis,
            reviewedAtMillis = request.reviewedAtMillis,
            dayOffset = dayOffsetOf(request.reviewedAtMillis),
            reviewKind = request.kind.rwkvReviewKind,
        )

    private fun result(s90Days: Float, difficulty: Double, intervalDays: Int, promotionDays: Int): MemoryReviewResult =
        MemoryReviewResult(
            stability = s90Days.toDouble(),
            difficulty = difficulty,
            intervalMillis = intervalDays.toLong() * MemoryReviewResult.DAY_MILLIS,
            promotionIntervalMillis = promotionDays.toLong() * MemoryReviewResult.DAY_MILLIS,
        )

    companion object {
        const val MAX_INTERVAL_DAYS: Int = 36_500
        private const val GOOD = 3

        /** Kani's wire rating as RWKV's ease; anything unknown is Again, as the scheduler treats it. */
        @JvmStatic
        fun ratingCode(rating: String?): Int =
            when (StudyRatings.normalize(rating)) {
                StudyRatings.HARD -> 2
                StudyRatings.GOOD -> 3
                StudyRatings.EASY -> 4
                else -> 1
            }

        private fun retention(target: Double): Float =
            if (target.isNaN()) 0.9f else target.coerceIn(0.01, 0.99).toFloat()
    }
}
