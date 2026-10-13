package dev.bee.kanjianki.syncdomain

import java.util.Locale
import java.util.regex.Pattern

class ProviderCardPolicy private constructor() {
    @JvmRecord
    data class FsrsMemoryState(
        val stability: Double?,
        val difficulty: Double?,
        val retrievability: Double?,
    )

    companion object {
        private const val STABILITY = "stability"
        private const val DIFFICULTY = "difficulty"
        private const val RETRIEVABILITY = "retrievability"
        private val EMPTY = FsrsMemoryState(null, null, null)
        private val FSRS_DATA_VALUE = Pattern.compile(
            "(?i)['\"]?(stability|difficulty|retrievability|s|d|r)['\"]?\\s*[:=]\\s*['\"]?([^\\s,'\"{}\\[\\]]+)"
        )

        /**
         * True when the card is suspended.
         *
         * The rule is *any* negative queue, not Anki's narrower `queue == -1`. A
         * user-buried or sibling-buried card is not active study material either,
         * and treating only `-1` as suspended would let a buried card count as
         * mature support for a kanji the learner is not actually being shown.
         */
        @JvmStatic
        fun isSuspendedQueue(rawQueue: Long): Boolean = rawQueue < 0L

        /**
         * A configured-model card is kept only at template ordinal 0, Kani's front
         * template. Accepting a reverse or extra template would let one note supply
         * two independent-looking passes for the same kanji.
         */
        @JvmStatic
        fun isAcceptedTemplateOrd(rawTemplateOrd: Long): Boolean = rawTemplateOrd == 0L

        /**
         * Converts Anki's raw `ivl` into the whole days the snapshot carries.
         *
         * Anki stores a **negative** `ivl` to mean seconds, for sub-day learning
         * cards. Passed through unchanged that becomes a negative day count, and
         * maturity arithmetic compares day counts against a positive threshold — so
         * a card answered minutes ago would sort as further from mature than one at
         * a genuine long interval, and any consumer that sums intervals would be
         * pulled downward by it. Sub-day therefore floors to 0.
         */
        @JvmStatic
        fun intervalDays(rawInterval: Long): Int {
            if (rawInterval <= 0L) return 0
            return rawInterval.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        /**
         * Clamps a provider counter (`reps`, `lapses`) into the snapshot's `Int`
         * field. Negative counters are not meaningful, so they floor at 0; the top
         * saturates rather than wrapping, because a wrapped count would read as a
         * *negative* number of lapses.
         */
        @JvmStatic
        fun counter(rawValue: Long): Int =
            rawValue.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        /**
         * Clamps a signed provider value (`queue`, `due`) into the snapshot's `Int`
         * field. Unlike [counter] the sign is preserved: `queue` uses negatives for
         * suspended and buried, and `due` is relative in some queues.
         */
        @JvmStatic
        fun signed(rawValue: Long): Int =
            rawValue.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

        @JvmStatic
        fun shouldReportCardProgress(scanned: Int, total: Int): Boolean {
            if (scanned <= 0 || scanned == total || total <= 100) {
                return true
            }
            if (scanned <= 10) {
                return true
            }
            return scanned % (if (total <= 1000) 10 else 50) == 0
        }

        @JvmStatic
        fun fsrsMemoryState(
            fsrsStability: String?,
            legacyStability: String?,
            fsrsDifficulty: String?,
            legacyDifficulty: String?,
            fsrsRetrievability: String?,
            legacyRetrievability: String?,
            serializedData: String?,
        ): FsrsMemoryState {
            val stability = firstFiniteDouble(fsrsStability, legacyStability)
            val difficulty = firstFiniteDouble(fsrsDifficulty, legacyDifficulty)
            val retrievability = firstFiniteDouble(fsrsRetrievability, legacyRetrievability)
            if (stability != null || difficulty != null || retrievability != null) {
                return FsrsMemoryState(stability, difficulty, retrievability)
            }
            return parseFsrsData(serializedData)
        }

        @JvmStatic
        fun parseFsrsData(data: String?): FsrsMemoryState {
            if (data == null || data.trim().isEmpty()) {
                return EMPTY
            }
            var stability: Double? = null
            var difficulty: Double? = null
            var retrievability: Double? = null
            val matcher = FSRS_DATA_VALUE.matcher(data)
            while (matcher.find()) {
                val value = parseDouble(matcher.group(2)) ?: continue
                when (matcher.group(1).lowercase(Locale.ROOT)) {
                    STABILITY, "s" -> stability = value
                    DIFFICULTY, "d" -> difficulty = value
                    else -> retrievability = value
                }
            }
            return FsrsMemoryState(stability, difficulty, retrievability)
        }

        @JvmStatic
        fun parseDouble(value: String?): Double? {
            return value?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        }

        private fun firstFiniteDouble(firstValue: String?, fallbackValue: String?): Double? {
            val value = parseDouble(trim(firstValue))
            return value ?: parseDouble(trim(fallbackValue))
        }

        private fun trim(value: String?): String? = value?.trim()
    }
}
