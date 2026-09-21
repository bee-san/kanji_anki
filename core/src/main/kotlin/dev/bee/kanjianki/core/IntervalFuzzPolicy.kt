package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic interval fuzz for adaptive core reviews.
 *
 * FSRS rounds every interval to a whole day, so kanji admitted together with
 * similar histories stay due on the same day forever and the daily load stays
 * lumpy. Anki spreads them with a small random fuzz; Kani uses the same bands
 * but derives the offset from a stable hash of the item and its review ordinal,
 * so a retried or replayed review produces the same schedule and goldens stay
 * reproducible.
 *
 * Bands (Anki `fuzz_range`): below 2.5 days none; [2.5, 7) ±15%; [7, 20) ±10%;
 * 20 and above ±5%. The half-width is at least one day, the result never drops
 * below two days, and a fuzzed interval never exceeds [maximumIntervalDays].
 */
object IntervalFuzzPolicy {
    private const val MIN_FUZZ_INTERVAL_DAYS = 2.5
    private const val MIN_FUZZED_RESULT_DAYS = 2

    @JvmStatic
    fun fuzzedIntervalDays(
        intervalDays: Int,
        kanji: String?,
        coreSkill: CoreSkill,
        reviewOrdinal: Int,
        maximumIntervalDays: Int = Int.MAX_VALUE,
    ): Int {
        if (intervalDays < MIN_FUZZ_INTERVAL_DAYS) {
            return intervalDays
        }
        val halfWidth = max(1.0, intervalDays * factorFor(intervalDays))
        val low = max(MIN_FUZZED_RESULT_DAYS, (intervalDays - halfWidth).roundToInt())
        val high = min(maximumIntervalDays.coerceAtLeast(low), (intervalDays + halfWidth).roundToInt())
        if (high <= low) {
            return low
        }
        val span = (high - low + 1).toLong()
        val offset = (unitInterval(kanji, coreSkill, reviewOrdinal) * span).toLong().coerceIn(0L, span - 1L)
        return (low + offset).toInt()
    }

    /** Anki's fuzz bands. */
    @JvmStatic
    fun factorFor(intervalDays: Int): Double = when {
        intervalDays < MIN_FUZZ_INTERVAL_DAYS -> 0.0
        intervalDays < 7 -> 0.15
        intervalDays < 20 -> 0.10
        else -> 0.05
    }

    /** Stable pseudo-uniform value in [0, 1) for this item and review ordinal. */
    @JvmStatic
    fun unitInterval(kanji: String?, coreSkill: CoreSkill, reviewOrdinal: Int): Double {
        var hash = -7046029254386353131L xor reviewOrdinal.toLong()
        val key = (kanji ?: "") + "\u0000" + coreSkill.wireName()
        for (index in key.indices) {
            hash = java.lang.Long.rotateLeft(hash xor key[index].code.toLong(), 27) * 1099511628211L
        }
        hash = hash xor (hash ushr 33)
        hash *= -49064778989728563L
        hash = hash xor (hash ushr 29)
        return (hash ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}
