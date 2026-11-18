// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Pedrubik2000/kuma3-anki-core (rslib/src/rwkv/engine.rs,
// FeatureState and TorchIdRng), Copyright: Ankitects Pty Ltd and
// contributors, GNU AGPL version 3 or later.

package dev.bee.rwkv

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * One review row. Answer rows ([isQuery] false) carry the [ease] and update
 * the recurrent state; query rows hide the rating and only predict.
 *
 * [cardType] follows upstream's `RwkvReviewState`: 0 marks a card's learning
 * start, otherwise the revlog kind + 1 (1 learning, 2 review, 3 relearning).
 * An answer row with card type 0 always reads its elapsed time as unknown.
 */
data class RwkvReviewInput(
    val cardId: Long,
    val noteId: Long?,
    val deckId: Long?,
    val presetId: Long?,
    val isQuery: Boolean,
    val ease: Int?,
    val durationMillis: Long?,
    val cardType: Int?,
    val dayOffset: Long?,
    val elapsedDays: Long?,
    val elapsedSeconds: Long?,
) {
    fun asAnswer(answerEase: Int): RwkvReviewInput = copy(isQuery = false, ease = answerEase)
}

/** The kinds of id the model encodes. */
enum class RwkvIdKind(
    /** Stable persisted code. */
    val code: Int,
    internal val dims: Int,
) {
    CARD(0, 12),
    NOTE(1, 12),
    DECK(2, 8),
    PRESET(3, 8),
    ;

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun fromCode(code: Int): RwkvIdKind =
            entries.firstOrNull { it.code == code } ?: throw IOException("invalid RWKV id kind $code")
    }
}

/** An encoded id, e.g. `(NOTE, 42)`. */
data class RwkvIdKey(val kind: RwkvIdKind, val id: Long)

/**
 * The PyTorch MT19937 the ID encodings are drawn from. Each encoding value is
 * `(next_u32 mod 4) - 1.5`.
 */
class TorchIdRng private constructor(private val state: IntArray, private var index: Int) {
    fun copy(): TorchIdRng = TorchIdRng(state.copyOf(), index)

    internal fun copyFrom(other: TorchIdRng) {
        other.state.copyInto(state)
        index = other.index
    }

    internal fun encoding(dims: Int): FloatArray = FloatArray(dims) { ((nextU32() and 3).toFloat()) - 1.5f }

    internal fun nextU32(): Int {
        if (index >= TorchIdRngInitialState.LENGTH) {
            twist()
        }
        var value = state[index]
        index++
        value = value xor (value ushr 11)
        value = value xor ((value shl 7) and 0x9d2c5680.toInt())
        value = value xor ((value shl 15) and 0xefc60000.toInt())
        return value xor (value ushr 18)
    }

    private fun twist() {
        val length = TorchIdRngInitialState.LENGTH
        for (i in 0 until length) {
            val next = (i + 1) % length
            val twist = (state[i] and 0x80000000.toInt()) or (state[next] and 0x7fffffff)
            var value = state[(i + 397) % length] xor (twist ushr 1)
            if (twist and 1 != 0) {
                value = value xor 0x9908b0df.toInt()
            }
            state[i] = value
        }
        index = 0
    }

    fun contentEquals(other: TorchIdRng): Boolean = index == other.index && state.contentEquals(other.state)

    internal fun writeTo(buffer: ByteBuffer) {
        buffer.putInt(index)
        for (word in state) {
            buffer.putInt(word)
        }
    }

    companion object {
        const val INITIAL_INDEX: Int = 4
        internal const val BYTES: Int = 4 + 4 * TorchIdRngInitialState.LENGTH

        @JvmStatic
        fun initial(): TorchIdRng = TorchIdRng(TorchIdRngInitialState.WORDS.copyOf(), INITIAL_INDEX)

        /** A generator from an explicit MT19937 state (tests against the reference sequence). */
        internal fun fromState(words: IntArray, index: Int): TorchIdRng {
            require(words.size == TorchIdRngInitialState.LENGTH && index in 0..TorchIdRngInitialState.LENGTH)
            return TorchIdRng(words.copyOf(), index)
        }

        internal fun readFrom(buffer: ByteBuffer): TorchIdRng {
            val index = buffer.int
            if (index !in 0..TorchIdRngInitialState.LENGTH) {
                throw IOException("invalid RWKV id RNG index $index")
            }
            val state = IntArray(TorchIdRngInitialState.LENGTH) { buffer.int }
            return TorchIdRng(state, index)
        }
    }
}

/**
 * The collection-wide feature counters. Mutable: [RwkvFeatureBuilder.storeReview]
 * advances them in place, so callers copy before speculative work.
 */
class RwkvGlobalFeatures(
    var firstDayOffset: Long?,
    var previousDayOffset: Long?,
    var today: Long,
    var todayReviews: Long,
    var todayNewCards: Long,
    var reviewIndex: Long,
    /** Distinct cards stored so far (upstream's `card_set.len()`). */
    var cardCount: Long,
    val rng: TorchIdRng,
) {
    fun copy(): RwkvGlobalFeatures =
        RwkvGlobalFeatures(
            firstDayOffset,
            previousDayOffset,
            today,
            todayReviews,
            todayNewCards,
            reviewIndex,
            cardCount,
            rng.copy(),
        )

    fun contentEquals(other: RwkvGlobalFeatures): Boolean =
        firstDayOffset == other.firstDayOffset &&
            previousDayOffset == other.previousDayOffset &&
            today == other.today &&
            todayReviews == other.todayReviews &&
            todayNewCards == other.todayNewCards &&
            reviewIndex == other.reviewIndex &&
            cardCount == other.cardCount &&
            rng.contentEquals(other.rng)

    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(4 + 2 * 9 + 5 * 8 + TorchIdRng.BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        putOptional(buffer, firstDayOffset)
        putOptional(buffer, previousDayOffset)
        buffer.putLong(today)
        buffer.putLong(todayReviews)
        buffer.putLong(todayNewCards)
        buffer.putLong(reviewIndex)
        buffer.putLong(cardCount)
        rng.writeTo(buffer)
        return buffer.array()
    }

    companion object {
        private const val MAGIC: Int = 0x4b524731 // "KRG1"

        @JvmStatic
        fun initial(): RwkvGlobalFeatures = RwkvGlobalFeatures(null, null, -1, 0, 0, 0, 0, TorchIdRng.initial())

        @JvmStatic
        @Throws(IOException::class)
        fun decode(bytes: ByteArray): RwkvGlobalFeatures {
            try {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                if (buffer.int != MAGIC) {
                    throw IOException("invalid RWKV global features magic")
                }
                val decoded = RwkvGlobalFeatures(
                    firstDayOffset = getOptional(buffer),
                    previousDayOffset = getOptional(buffer),
                    today = buffer.long,
                    todayReviews = buffer.long,
                    todayNewCards = buffer.long,
                    reviewIndex = buffer.long,
                    cardCount = buffer.long,
                    rng = TorchIdRng.readFrom(buffer),
                )
                if (buffer.hasRemaining()) {
                    throw IOException("trailing bytes in RWKV global features")
                }
                return decoded
            } catch (error: java.nio.BufferUnderflowException) {
                throw IOException("RWKV global features ended early", error)
            }
        }

        private fun putOptional(buffer: ByteBuffer, value: Long?) {
            buffer.put(if (value == null) 0 else 1)
            buffer.putLong(value ?: 0L)
        }

        private fun getOptional(buffer: ByteBuffer): Long? {
            val tag = buffer.get().toInt()
            val value = buffer.long
            return when (tag) {
                0 -> null
                1 -> value
                else -> throw IOException("invalid RWKV optional tag")
            }
        }
    }
}

/** Per-card feature counters; present once the card's first answer was stored. */
data class RwkvCardFeatures(
    val lastNewCards: Long,
    val lastI: Long,
    val firstDayOffset: Long,
    val elapsedDaysCumulative: Long,
    val elapsedSecondsCumulative: Long,
) {
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(4 + 5 * 8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putLong(lastNewCards)
        buffer.putLong(lastI)
        buffer.putLong(firstDayOffset)
        buffer.putLong(elapsedDaysCumulative)
        buffer.putLong(elapsedSecondsCumulative)
        return buffer.array()
    }

    companion object {
        private const val MAGIC: Int = 0x4b524331 // "KRC1"

        @JvmStatic
        @Throws(IOException::class)
        fun decode(bytes: ByteArray): RwkvCardFeatures {
            if (bytes.size != 4 + 5 * 8) {
                throw IOException("invalid RWKV card features length ${bytes.size}")
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != MAGIC) {
                throw IOException("invalid RWKV card features magic")
            }
            return RwkvCardFeatures(buffer.long, buffer.long, buffer.long, buffer.long, buffer.long)
        }
    }
}

/** Little-endian f32 encoding of an id encoding vector. */
object RwkvEncodingCodec {
    @JvmStatic
    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return buffer.array()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(kind: RwkvIdKind, bytes: ByteArray): FloatArray {
        if (bytes.size != kind.dims * 4) {
            throw IOException("invalid RWKV $kind encoding length ${bytes.size}")
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(kind.dims) { buffer.float }
    }
}

/** Where the feature builder reads and writes per-card counters and id encodings. */
interface RwkvFeatureStore {
    val globals: RwkvGlobalFeatures

    fun card(cardId: Long): RwkvCardFeatures?

    fun putCard(cardId: Long, features: RwkvCardFeatures)

    fun encoding(key: RwkvIdKey): FloatArray?

    fun putEncoding(key: RwkvIdKey, values: FloatArray)
}

/** Builds the 92 model inputs and advances the feature counters, as upstream's `FeatureState`. */
object RwkvFeatureBuilder {
    const val ID_PLACEHOLDER: Long = 314_159_265_358_979_323L
    private const val SECONDS_PER_DAY: Long = 86_400L
    private val DAY_OFFSET_ENCODE_PERIODS = floatArrayOf(3f, 7f, 30f, 100f, 365f, 3650f, 36500f)

    private const val ELAPSED_DAYS_MEAN = 1.51f
    private const val ELAPSED_DAYS_STD = 1.62f
    private const val ELAPSED_DAYS_CUMULATIVE_MEAN = 2.14f
    private const val ELAPSED_DAYS_CUMULATIVE_STD = 2.25f
    private const val ELAPSED_SECONDS_MEAN = 9.96f
    private const val ELAPSED_SECONDS_STD = 5.21f
    private const val ELAPSED_SECONDS_CUMULATIVE_MEAN = 10.86f
    private const val ELAPSED_SECONDS_CUMULATIVE_STD = 5.8f
    private const val DURATION_MEAN = 8.9f
    private const val DURATION_STD = 1.07f
    private const val DIFF_NEW_CARDS_MEAN = 2.945f
    private const val DIFF_NEW_CARDS_STD = 2.011f
    private const val DIFF_REVIEWS_MEAN = 4.64f
    private const val DIFF_REVIEWS_STD = 2.59f
    private const val CUM_NEW_CARDS_TODAY_MEAN = 2.55f
    private const val CUM_NEW_CARDS_TODAY_STD = 1.41f
    private const val CUM_REVIEWS_TODAY_MEAN = 4.59f
    private const val CUM_REVIEWS_TODAY_STD = 1.30f

    @JvmStatic
    fun idKeys(input: RwkvReviewInput): Array<RwkvIdKey> =
        arrayOf(
            RwkvIdKey(RwkvIdKind.CARD, input.cardId),
            RwkvIdKey(RwkvIdKind.NOTE, input.noteId ?: (ID_PLACEHOLDER + input.cardId)),
            RwkvIdKey(RwkvIdKind.DECK, input.deckId ?: ID_PLACEHOLDER),
            RwkvIdKey(RwkvIdKind.PRESET, input.presetId ?: ID_PLACEHOLDER),
        )

    /** The model inputs for [input]. Reads [store] only: unseen ids are previewed on a copy of the RNG. */
    @JvmStatic
    fun featuresFor(store: RwkvFeatureStore, input: RwkvReviewInput): FloatArray {
        val globals = store.globals
        val card = store.card(input.cardId)
        val elapsedSeconds = elapsedSeconds(input)
        val elapsedDays = elapsedDays(input, elapsedSeconds)
        val elapsedDaysCumulative = (card?.elapsedDaysCumulative ?: 0L) + elapsedDays
        val elapsedSecondsCumulative = (card?.elapsedSecondsCumulative ?: 0L) + elapsedSeconds

        val rawDayOffset = input.dayOffset ?: 0L
        val dayOffset = globals.firstDayOffset?.let { rawDayOffset - it } ?: 0L
        val dayOffsetFirst = card?.firstDayOffset ?: dayOffset
        val previousDayOffset = globals.previousDayOffset ?: 0L
        val diffNewCards = if (card == null) 0L else globals.cardCount - card.lastNewCards
        val diffReviews = if (card == null) 0L else max(globals.reviewIndex - card.lastI - 1, 0L)

        var todayNewCards = globals.todayNewCards
        var todayReviews = globals.todayReviews
        if (dayOffset != globals.today) {
            todayNewCards = 0
            todayReviews = -1
        }
        todayReviews += 1
        if (card == null) {
            todayNewCards += 1
        }

        val features = FloatArray(RwkvArchitecture.CARD_FEATURES)
        var at = 0
        features[at++] = scale(logElapsed(elapsedDays), ELAPSED_DAYS_MEAN, ELAPSED_DAYS_STD)
        features[at++] = scale(logElapsed(elapsedDaysCumulative), ELAPSED_DAYS_CUMULATIVE_MEAN, ELAPSED_DAYS_CUMULATIVE_STD)
        features[at++] = scale(logElapsed(elapsedSeconds), ELAPSED_SECONDS_MEAN, ELAPSED_SECONDS_STD)
        features[at++] = cyclicSin(elapsedSeconds, SECONDS_PER_DAY)
        features[at++] = cyclicCos(elapsedSeconds, SECONDS_PER_DAY)
        features[at++] = scale(logElapsed(elapsedSecondsCumulative), ELAPSED_SECONDS_CUMULATIVE_MEAN, ELAPSED_SECONDS_CUMULATIVE_STD)
        features[at++] = cyclicSin(elapsedSecondsCumulative, SECONDS_PER_DAY)
        features[at++] = cyclicCos(elapsedSecondsCumulative, SECONDS_PER_DAY)
        features[at++] = input.durationMillis?.let { scale(ln(10f + it.toFloat()), DURATION_MEAN, DURATION_STD) } ?: 0f

        val rating = input.ease ?: 0
        for (ease in 1..4) {
            features[at++] = if (!input.isQuery && rating == ease) 1f else 0f
        }
        features[at++] = if (input.noteId == null) 1f else 0f
        features[at++] = if (input.deckId == null) 1f else 0f
        features[at++] = if (input.presetId == null) 1f else 0f
        // ln(ln(e + Δ)) is NaN for Δ < 1 - e. Upstream histories are time-sorted
        // so Δ >= 0 always holds there; a device clock or timezone moving back
        // must not poison the shared user stream, so a negative gap reads as 0.
        features[at++] = RwkvMath.lnLnE(maxOf(0L, dayOffset - previousDayOffset).toFloat())
        features[at++] = (Math.floorMod(dayOffset, 7L).toFloat() - 3f) / 3f
        features[at++] = scale(ln(3f + diffNewCards.toFloat()), DIFF_NEW_CARDS_MEAN, DIFF_NEW_CARDS_STD)
        features[at++] = scale(ln(3f + diffReviews.toFloat()), DIFF_REVIEWS_MEAN, DIFF_REVIEWS_STD)
        features[at++] = scale(ln(3f + todayNewCards.toFloat()), CUM_NEW_CARDS_TODAY_MEAN, CUM_NEW_CARDS_TODAY_STD)
        features[at++] = scale(ln(3f + todayReviews.toFloat()), CUM_REVIEWS_TODAY_MEAN, CUM_REVIEWS_TODAY_STD)
        features[at++] = if (input.isQuery) 0f else (input.cardType ?: 0).toFloat() - 2f
        features[at++] = if (input.isQuery) 1f else 0f

        var previewRng: TorchIdRng? = null
        for (key in idKeys(input)) {
            val encoding = store.encoding(key) ?: run {
                val rng = previewRng ?: globals.rng.copy().also { previewRng = it }
                rng.encoding(key.kind.dims)
            }
            encoding.copyInto(features, at)
            at += encoding.size
        }

        for (period in DAY_OFFSET_ENCODE_PERIODS) {
            val phase = 2f * RwkvMath.PI_F / period
            val day = Math.floorMod(dayOffset, period.toLong()).toFloat()
            val firstDay = Math.floorMod(dayOffsetFirst, period.toLong()).toFloat()
            features[at++] = sin(phase * day)
            features[at++] = cos(phase * day)
            features[at++] = sin(phase * firstDay)
            features[at++] = cos(phase * firstDay)
        }
        check(at == RwkvArchitecture.CARD_FEATURES) { "built $at RWKV features" }
        return features
    }

    /** Commits an answer row: assigns its id encodings and advances the counters. */
    @JvmStatic
    fun storeReview(store: RwkvFeatureStore, input: RwkvReviewInput) {
        val globals = store.globals
        for (key in idKeys(input)) {
            if (store.encoding(key) == null) {
                store.putEncoding(key, globals.rng.encoding(key.kind.dims))
            }
        }

        val elapsedSeconds = elapsedSeconds(input)
        val elapsedDays = elapsedDays(input, elapsedSeconds)
        val previous = store.card(input.cardId)

        val rawDayOffset = input.dayOffset ?: 0L
        val dayOffset = globals.firstDayOffset?.let { rawDayOffset - it } ?: 0L
        if (globals.firstDayOffset == null) {
            globals.firstDayOffset = rawDayOffset
        }
        if (dayOffset != globals.today) {
            globals.today = dayOffset
            globals.todayNewCards = 0
            globals.todayReviews = -1
        }
        globals.todayReviews += 1
        var firstDayOffset = previous?.firstDayOffset ?: 0L
        if (previous == null) {
            globals.todayNewCards += 1
            globals.cardCount += 1
            firstDayOffset = dayOffset
        }
        globals.previousDayOffset = dayOffset
        store.putCard(
            input.cardId,
            RwkvCardFeatures(
                lastNewCards = globals.cardCount,
                lastI = globals.reviewIndex,
                firstDayOffset = firstDayOffset,
                elapsedDaysCumulative = (previous?.elapsedDaysCumulative ?: 0L) + elapsedDays,
                elapsedSecondsCumulative = (previous?.elapsedSecondsCumulative ?: 0L) + elapsedSeconds,
            ),
        )
        globals.reviewIndex += 1
    }

    internal fun elapsedSeconds(input: RwkvReviewInput): Long {
        if (creationElapsedIsQueryOnly(input)) {
            return -1
        }
        return input.elapsedSeconds ?: input.elapsedDays?.let { it * SECONDS_PER_DAY } ?: -1
    }

    internal fun elapsedDays(input: RwkvReviewInput, elapsedSeconds: Long): Long {
        if (creationElapsedIsQueryOnly(input)) {
            return -1
        }
        return input.elapsedDays ?: if (elapsedSeconds >= 0) elapsedSeconds / SECONDS_PER_DAY else -1
    }

    private fun creationElapsedIsQueryOnly(input: RwkvReviewInput): Boolean =
        !input.isQuery && input.ease != null && input.cardType == 0

    private fun scale(value: Float, mean: Float, std: Float): Float = (value - mean) / std

    private fun logElapsed(x: Long): Float = if (x == -1L) 0f else ln(1f + 1e-5f + x.toFloat())

    private fun cyclicSin(value: Long, period: Long): Float =
        sin(Math.floorMod(value, period).toFloat() * 2f * RwkvMath.PI_F / period.toFloat())

    private fun cyclicCos(value: Long, period: Long): Float =
        cos(Math.floorMod(value, period).toFloat() * 2f * RwkvMath.PI_F / period.toFloat())
}

/** An in-memory [RwkvFeatureStore] over the whole history, for replays and tests. */
class RwkvMemoryFeatureStore(override val globals: RwkvGlobalFeatures = RwkvGlobalFeatures.initial()) : RwkvFeatureStore {
    val cards: MutableMap<Long, RwkvCardFeatures> = HashMap()
    val encodings: MutableMap<RwkvIdKey, FloatArray> = HashMap()

    override fun card(cardId: Long): RwkvCardFeatures? = cards[cardId]

    override fun putCard(cardId: Long, features: RwkvCardFeatures) {
        cards[cardId] = features
    }

    override fun encoding(key: RwkvIdKey): FloatArray? = encodings[key]

    override fun putEncoding(key: RwkvIdKey, values: FloatArray) {
        encodings[key] = values
    }
}
