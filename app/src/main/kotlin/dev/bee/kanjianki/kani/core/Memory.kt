package dev.bee.kanjianki.kani.core

import dev.bee.rwkv.RwkvAlgorithmInfo
import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvCardHistory
import dev.bee.rwkv.RwkvCollectionState
import dev.bee.rwkv.RwkvEngine
import dev.bee.rwkv.RwkvStreamIds
import java.nio.ByteBuffer
import java.security.MessageDigest

/** One row of Kani's answer log, as the memory model is fed it. */
data class LoggedAnswer(
    val id: Long,
    val kanji: String,
    val check: Check,
    /** 1 again, 2 hard, 3 good (see [MemoryRules.rating]). */
    val rating: Int,
    val latencyMs: Long,
    val atMillis: Long,
    val day: Int,
)

/**
 * How Kani's answers map onto the RWKV-Curve memory model (SM92, `:rwkv`).
 *
 * Each kanji is a note with two cards, its meaning check and its reading check; the check is
 * the deck. Every check answer is fed once, in log order. Sprints, mix-up cards, the reading
 * retry and writing are practice and are never fed.
 */
object MemoryRules {
    /** A solid kanji comes back for a check once the model puts its recall below this. */
    const val TARGET_RECALL = 0.9f

    /** Changes when the model, the weights or this mapping change; cached curves are then rebuilt. */
    val IDENTITY: String = "${RwkvAlgorithmInfo.stateIdentity()}/kani-v1"

    private const val AGAIN = 1
    private const val HARD = 2
    private const val GOOD = 3
    private const val REVIEW = 2
    private const val RELEARNING = 3

    /** Longest answer time fed to the model; a phone left on the check is not a slow recall. */
    private const val MAX_DURATION_MS = 60_000L

    /** Right and fast is Good, right but slow is Hard, a miss or "I don't know" is Again. */
    fun rating(correct: Boolean, latencyMs: Long): Int = when {
        !correct -> AGAIN
        latencyMs <= Progress.FAST_MS -> GOOD
        else -> HARD
    }

    fun ids(kanji: String, check: Check): RwkvStreamIds = RwkvStreamIds(
        card = cardId(kanji, check),
        deck = stableId("deck", check.name),
        note = stableId("note", kanji),
        preset = stableId("preset", "kani1"),
    )

    fun cardId(kanji: String, check: Check): Long = stableId("card", kanji, check.name)

    internal fun answer(a: LoggedAnswer, previousRating: Int?): RwkvAnswer = RwkvAnswer(
        rating = a.rating,
        durationMillis = a.latencyMs.coerceIn(0L, MAX_DURATION_MS),
        reviewedAtMillis = a.atMillis,
        dayOffset = a.day.toLong(),
        reviewKind = if (previousRating == AGAIN) RELEARNING else REVIEW,
    )

    /** First 63 bits of a SHA-256 of the length-prefixed parts. */
    private fun stableId(vararg parts: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("kani1".toByteArray())
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return ByteBuffer.wrap(digest.digest()).long and Long.MAX_VALUE
    }
}

/**
 * The model state for one process: answers go in strictly in log order, each exactly once.
 * The state is a derived cache of the answer log, so a fresh instance replays it from the start.
 */
class MemoryModel(private val engine: RwkvEngine) {
    private val state = RwkvCollectionState()
    private val lastRating = HashMap<Long, Int>()

    /** The id of the last answer fed (0 before any). */
    var fedThrough: Long = 0L
        private set

    /** Answers the model refused (a non-finite step); they are skipped, never retried. */
    var skipped: Int = 0
        private set

    fun feed(answer: LoggedAnswer) {
        require(answer.id > fedThrough) { "answer ${answer.id} fed out of order after $fedThrough" }
        val card = MemoryRules.cardId(answer.kanji, answer.check)
        try {
            engine.replay(state, MemoryRules.ids(answer.kanji, answer.check), MemoryRules.answer(answer, lastRating[card]))
            lastRating[card] = answer.rating
        } catch (_: IllegalStateException) {
            skipped++
        } catch (_: IllegalArgumentException) {
            skipped++
        }
        fedThrough = answer.id
    }

    fun history(kanji: String, check: Check): RwkvCardHistory? = state.history(MemoryRules.cardId(kanji, check))
}

/** Predicted recall per kanji and check, read from the model's cached curves. */
fun interface Recall {
    /** Null when the model has never seen that check of the kanji. */
    fun of(kanji: String, check: Check): Float?

    /** The weaker of the kanji's two checks, or null when neither has been answered. */
    fun weakest(kanji: String): Float? = listOfNotNull(of(kanji, Check.MEANING), of(kanji, Check.READING)).minOrNull()

    companion object {
        val NONE = Recall { _, _ -> null }
    }
}
