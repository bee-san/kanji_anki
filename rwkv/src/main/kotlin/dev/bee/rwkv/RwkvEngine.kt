// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs,
// RwkvInference::review / predict_many / answer_intervals), Copyright:
// Ankitects Pty Ltd and contributors, GNU AGPL version 3 or later.

package dev.bee.rwkv

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The ids one card's reviews run under. The user stream is global. */
data class RwkvStreamIds(val card: Long, val deck: Long, val note: Long, val preset: Long) {
    internal fun forStream(stream: Int): Long =
        when (stream) {
            RwkvArchitecture.CARD -> card
            RwkvArchitecture.DECK -> deck
            RwkvArchitecture.NOTE -> note
            RwkvArchitecture.PRESET -> preset
            else -> USER_ID
        }

    companion object {
        const val USER_ID: Long = 0L
    }
}

/**
 * When a card was last answered (wall clock and scheduling day), how many
 * answers the model has seen for it, and the forgetting curve its last answer
 * produced, so recall can be read at any time without running the network.
 */
class RwkvCardHistory(
    val lastAnswerMillis: Long,
    val lastDayOffset: Long,
    val answers: Long,
    val curve: RwkvCurve,
) {
    fun recallAt(nowMillis: Long): Float =
        curve.predict(maxOf(0L, (nowMillis - lastAnswerMillis) / 1000L).toFloat())

    fun s90Days(maxIntervalDays: Int): Float? =
        RwkvIntervals.unroundedInterval(curve, RwkvIntervals.S90_TARGET_RETENTION, maxIntervalDays)

    fun encode(): ByteArray {
        val curves = RwkvArchitecture.NUM_CURVES
        val buffer = ByteBuffer.allocate(4 + 8 * 3 + 4 * 3 * curves).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putLong(lastAnswerMillis)
        buffer.putLong(lastDayOffset)
        buffer.putLong(answers)
        curve.weights.forEach(buffer::putFloat)
        curve.scales.forEach(buffer::putFloat)
        curve.decays.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun contentEquals(other: RwkvCardHistory): Boolean = encode().contentEquals(other.encode())

    companion object {
        private const val MAGIC: Int = 0x4b524831 // "KRH1"

        @JvmStatic
        @Throws(IOException::class)
        fun decode(bytes: ByteArray): RwkvCardHistory {
            try {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (buffer.int != MAGIC) {
                    throw IOException("invalid RWKV card history magic")
                }
                val lastAnswerMillis = buffer.long
                val lastDayOffset = buffer.long
                val answers = buffer.long
                val curves = RwkvArchitecture.NUM_CURVES
                val weights = FloatArray(curves) { buffer.float }
                val scales = FloatArray(curves) { buffer.float }
                val decays = FloatArray(curves) { buffer.float }
                if (buffer.hasRemaining()) {
                    throw IOException("trailing bytes in RWKV card history")
                }
                return RwkvCardHistory(lastAnswerMillis, lastDayOffset, answers, RwkvCurve(weights, scales, decays))
            } catch (error: java.nio.BufferUnderflowException) {
                throw IOException("RWKV card history ended early", error)
            }
        }
    }
}

/** Read/write access to the persisted model state one review touches. */
interface RwkvStateView : RwkvFeatureStore {
    fun moduleState(stream: Int, id: Long): RwkvModuleState?

    fun putModuleState(stream: Int, id: Long, state: RwkvModuleState)

    fun history(cardId: Long): RwkvCardHistory?

    fun putHistory(cardId: Long, history: RwkvCardHistory)
}

/** The whole collection's model state in memory: rebuilds, simulations and tests. */
class RwkvCollectionState(
    override val globals: RwkvGlobalFeatures = RwkvGlobalFeatures.initial(),
) : RwkvStateView {
    val streams: Array<MutableMap<Long, RwkvModuleState>> = Array(RwkvArchitecture.STREAMS) { HashMap() }
    val cards: MutableMap<Long, RwkvCardFeatures> = HashMap()
    val encodings: MutableMap<RwkvIdKey, FloatArray> = HashMap()
    val histories: MutableMap<Long, RwkvCardHistory> = HashMap()

    override fun moduleState(stream: Int, id: Long): RwkvModuleState? = streams[stream][id]

    override fun putModuleState(stream: Int, id: Long, state: RwkvModuleState) {
        streams[stream][id] = state
    }

    override fun card(cardId: Long): RwkvCardFeatures? = cards[cardId]

    override fun putCard(cardId: Long, features: RwkvCardFeatures) {
        cards[cardId] = features
    }

    override fun encoding(key: RwkvIdKey): FloatArray? = encodings[key]

    override fun putEncoding(key: RwkvIdKey, values: FloatArray) {
        encodings[key] = values
    }

    override fun history(cardId: Long): RwkvCardHistory? = histories[cardId]

    override fun putHistory(cardId: Long, history: RwkvCardHistory) {
        histories[cardId] = history
    }
}

/** One answer as the model sees it. */
data class RwkvAnswer(
    /** 1 again, 2 hard, 3 good, 4 easy. */
    val rating: Int,
    val durationMillis: Long?,
    val reviewedAtMillis: Long,
    /** The scheduling day of the review (any fixed epoch; only differences matter). */
    val dayOffset: Long,
    /** 1 learning, 2 review, 3 relearning; ignored for a card's first answer, which is always 0. */
    val reviewKind: Int,
    /** Overrides the elapsed time since the card's previous answer (synthetic histories). */
    val elapsedSecondsOverride: Long? = null,
    val elapsedDaysOverride: Long? = null,
) {
    init {
        require(rating in 1..4) { "RWKV rating must be 1..4, was $rating" }
    }
}

/** What an answer schedules. */
class RwkvAnswerOutcome(
    /** Whole days until the curve meets the target retention (`ceil`, at least one). */
    val intervalDays: Int,
    val unroundedIntervalDays: Float,
    /** Unrounded days until the curve meets 90% (FSRS's definition of stability). */
    val s90Days: Float,
    /** Whole days until 90% recall: the retention-independent promotion signal. */
    val promotionIntervalDays: Int,
    val buttonProbabilities: FloatArray,
    /** Recall the card's previous curve predicted at this answer, or null on a first answer. */
    val recallBefore: Float?,
)

/**
 * Applies answers to model state. Card and note states are compacted to
 * int8 after every answer, as upstream's default `int8` state precision; the
 * deck, preset and user streams stay f32.
 */
class RwkvEngine @JvmOverloads constructor(
    val model: RwkvModel,
    private val cardPrecision: RwkvStatePrecision = RwkvStatePrecision.INT8,
    private val notePrecision: RwkvStatePrecision = RwkvStatePrecision.INT8,
) {
    /**
     * Answers [answer] for the card [ids] and commits it into [view]. The
     * interval and S90 come from the four simulated answers' curves with the
     * grade order enforced (upstream's `predict_many` + `answer_intervals`),
     * read at the answered rating; the committed state is the answered row's.
     */
    fun answer(
        view: RwkvStateView,
        ids: RwkvStreamIds,
        answer: RwkvAnswer,
        targetRetention: Float,
        maxIntervalDays: Int,
    ): RwkvAnswerOutcome {
        val history = view.history(ids.card)
        val input = inputFor(ids, answer, history)
        val states = statesFor(view, ids)
        val steps = Array(4) { index -> model.step(RwkvFeatureBuilder.featuresFor(view, input.asAnswer(index + 1)), states) }
        val curves = Array(4) { steps[it].curve }
        val targets = FloatArray(4) { targetRetention }
        val intervals = RwkvIntervals.answerIntervals(curves, targets, maxIntervalDays, enforceGradeOrder = true)
        val s90s = RwkvIntervals.answerIntervals(
            curves,
            FloatArray(4) { RwkvIntervals.S90_TARGET_RETENTION },
            maxIntervalDays,
            enforceGradeOrder = true,
        )
        val chosen = steps[answer.rating - 1]
        val unrounded = intervals[answer.rating - 1] ?: maxIntervalDays.toFloat()
        val s90 = s90s[answer.rating - 1] ?: maxIntervalDays.toFloat()
        val recallBefore = history?.recallAt(answer.reviewedAtMillis)
        commit(view, ids, input.asAnswer(answer.rating), answer, history, chosen)
        return RwkvAnswerOutcome(
            intervalDays = RwkvIntervals.roundedDays(unrounded, maxIntervalDays),
            unroundedIntervalDays = unrounded,
            s90Days = s90,
            promotionIntervalDays = RwkvIntervals.roundedDays(s90, maxIntervalDays),
            buttonProbabilities = chosen.buttonProbabilities,
            recallBefore = recallBefore,
        )
    }

    /** Commits [answer] without computing intervals (history replay). */
    fun replay(view: RwkvStateView, ids: RwkvStreamIds, answer: RwkvAnswer) {
        val history = view.history(ids.card)
        val input = inputFor(ids, answer, history).asAnswer(answer.rating)
        val step = model.step(RwkvFeatureBuilder.featuresFor(view, input), statesFor(view, ids))
        commit(view, ids, input, answer, history, step)
    }

    /**
     * Gives a card a synthetic prior history (imported memory the model never
     * saw) without disturbing the collection's real context: the answers run
     * against a scratch copy of the global counters, and only the card and
     * note streams keep their updates. The deck, preset and user streams and
     * the day/today counters are left exactly as they were; the ID RNG keeps
     * its advance so encodings stay distinct.
     */
    fun warmCard(view: RwkvStateView, ids: RwkvStreamIds, answers: List<RwkvAnswer>) {
        if (answers.isEmpty()) {
            return
        }
        val real = view.globals
        val scratch = WarmView(view, real.copy())
        for (answer in answers) {
            replay(scratch, ids, answer)
        }
        val warmed = scratch.globals
        if (real.firstDayOffset == null) {
            real.firstDayOffset = warmed.firstDayOffset
        }
        val cardWasSeen = scratch.cardWasSeenBefore
        val features = view.card(ids.card)
        if (features != null) {
            if (!cardWasSeen) {
                real.cardCount += 1
            }
            view.putCard(
                ids.card,
                features.copy(lastNewCards = real.cardCount, lastI = real.reviewIndex - 1),
            )
        }
        real.rng.copyFrom(warmed.rng)
    }

    private fun inputFor(ids: RwkvStreamIds, answer: RwkvAnswer, history: RwkvCardHistory?): RwkvReviewInput {
        val first = history == null
        val elapsedSeconds = answer.elapsedSecondsOverride
            ?: history?.let { maxOf(0L, (answer.reviewedAtMillis - it.lastAnswerMillis) / 1000L) }
        val elapsedDays = answer.elapsedDaysOverride
            ?: history?.let { maxOf(0L, answer.dayOffset - it.lastDayOffset) }
        return RwkvReviewInput(
            cardId = ids.card,
            noteId = ids.note,
            deckId = ids.deck,
            presetId = ids.preset,
            isQuery = true,
            ease = null,
            durationMillis = answer.durationMillis,
            cardType = if (first) 0 else answer.reviewKind,
            dayOffset = answer.dayOffset,
            elapsedDays = if (first) null else elapsedDays,
            elapsedSeconds = if (first) null else elapsedSeconds,
        )
    }

    private fun statesFor(view: RwkvStateView, ids: RwkvStreamIds): Array<RwkvModuleState?> =
        Array(RwkvArchitecture.STREAMS) { stream -> view.moduleState(stream, ids.forStream(stream)) }

    private fun commit(
        view: RwkvStateView,
        ids: RwkvStreamIds,
        input: RwkvReviewInput,
        answer: RwkvAnswer,
        history: RwkvCardHistory?,
        step: RwkvStep,
    ) {
        RwkvFeatureBuilder.storeReview(view, input)
        for (stream in 0 until RwkvArchitecture.STREAMS) {
            val precision = when (stream) {
                RwkvArchitecture.CARD -> cardPrecision
                RwkvArchitecture.NOTE -> notePrecision
                else -> RwkvStatePrecision.F32
            }
            view.putModuleState(stream, ids.forStream(stream), RwkvStateCodec.quantize(step.next[stream], precision))
        }
        view.putHistory(
            ids.card,
            RwkvCardHistory(
                lastAnswerMillis = answer.reviewedAtMillis,
                lastDayOffset = answer.dayOffset,
                answers = (history?.answers ?: 0L) + 1,
                curve = step.curve,
            ),
        )
    }

    /** Precision a stream is persisted at. */
    fun precisionFor(stream: Int): RwkvStatePrecision =
        when (stream) {
            RwkvArchitecture.CARD -> cardPrecision
            RwkvArchitecture.NOTE -> notePrecision
            else -> RwkvStatePrecision.F32
        }

    /** Reads card/note writes through to [base]; keeps the shared streams' writes local. */
    private class WarmView(private val base: RwkvStateView, override val globals: RwkvGlobalFeatures) : RwkvStateView {
        private val shared = HashMap<Pair<Int, Long>, RwkvModuleState>()
        var cardWasSeenBefore: Boolean = false
            private set
        private var checkedCard = false

        override fun moduleState(stream: Int, id: Long): RwkvModuleState? =
            if (stream == RwkvArchitecture.CARD || stream == RwkvArchitecture.NOTE) {
                base.moduleState(stream, id)
            } else {
                shared[stream to id] ?: base.moduleState(stream, id)
            }

        override fun putModuleState(stream: Int, id: Long, state: RwkvModuleState) {
            if (stream == RwkvArchitecture.CARD || stream == RwkvArchitecture.NOTE) {
                base.putModuleState(stream, id, state)
            } else {
                shared[stream to id] = state
            }
        }

        override fun card(cardId: Long): RwkvCardFeatures? {
            val card = base.card(cardId)
            if (!checkedCard) {
                cardWasSeenBefore = card != null
                checkedCard = true
            }
            return card
        }

        override fun putCard(cardId: Long, features: RwkvCardFeatures) = base.putCard(cardId, features)

        override fun encoding(key: RwkvIdKey): FloatArray? = base.encoding(key)

        override fun putEncoding(key: RwkvIdKey, values: FloatArray) = base.putEncoding(key, values)

        override fun history(cardId: Long): RwkvCardHistory? = base.history(cardId)

        override fun putHistory(cardId: Long, history: RwkvCardHistory) = base.putHistory(cardId, history)
    }
}
