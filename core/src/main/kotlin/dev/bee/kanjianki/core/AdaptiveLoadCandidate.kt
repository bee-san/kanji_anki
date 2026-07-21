package dev.bee.kanjianki.core

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Scores dashboard rows as focus candidates for the adaptive load plan.
 *
 * [priorityScore] is an additive sum of the terms below. The listed ranges are
 * the *typical* magnitudes each term contributes — not a strict dominance
 * hierarchy: because the terms are simply added, a large value on a
 * nominally-lower term (e.g. an analyzer `weaknessScore` inflated by many
 * suspended examples) can outweigh a small `fsrsRisk`. The ordering is by
 * design a blend, and the comparators below break ties with discrete evidence.
 *
 * - `fsrsRisk` (typically 0..~135): retention risk from FSRS evidence on the
 *   kanji's Anki example cards. A card drifting below the 90% retrievability
 *   target, or stuck at high difficulty / low stability, is at risk of being
 *   forgotten, which makes it valuable to study today.
 * - `exposureBoost` (typically 0..80): the kanji keeps appearing in the user's
 *   real reading (external exposure tracker). Real-world encounters are direct
 *   evidence of value.
 * - `weaknessScore` (analyzer, typically 0..60 but uncapped): source evidence
 *   built by [KanjiAnalyzer] — suspended examples, missing mature support, and
 *   capped lapse/interval/FSRS pressure. It is counted exactly once here; this
 *   class must NOT re-add suspended-example or support-deficit terms on top of
 *   it (they are inside `weaknessScore` already).
 * - `frequencyValue` (0..24): how common the kanji is by Jiten frequency rank.
 *   This is the "value" axis of the Pareto claim — between two equally weak
 *   kanji, the one the user will meet more often is worth more.
 * - `kaniLapseScore * 2`: Kani-side lapse and writing evidence from the local
 *   study item, which the analyzer cannot see.
 *
 * Ordering additionally puts due-recovery candidates in a strictly earlier
 * tier and, within that tier, services the most overdue card first
 * (earliest-deadline-first). Priority signals never let a fresher due card
 * starve a long-overdue one.
 */
internal class AdaptiveLoadCandidate(
    @JvmField val row: RecordsImportModels.DashboardRow,
    item: RecordsStudyModels.StudyItem?,
    nowMillis: Long,
    settings: RecordsSyncModels.Settings,
    exposure: ReadingExposureModels.ExposureIndex,
) {
    @JvmField val recoveryDue: Boolean = isRecoveryDue(item, nowMillis)

    @JvmField val overdueMillis: Long = overdueMillis(item, nowMillis, recoveryDue)

    @JvmField val exposureBoost: Double = exposure.priorityBoost(row.kanji)

    @JvmField val fsrsRisk: Double = fsrsRisk(row, settings)

    @JvmField val frequencyValue: Double = frequencyValue(row.jitenRank)

    @JvmField val kaniLapseScore: Int = kaniLapseScore(item)

    @JvmField val suspendedCount: Int = row.suspendedExampleCount

    @JvmField val supportDeficit: Int = max(
        0,
        settings.matureSupportThreshold.coerceAtLeast(0) - row.matureSupportCount.coerceAtLeast(0),
    )

    @JvmField val priorityScore: Double = fsrsRisk +
        exposureBoost +
        row.weaknessScore +
        frequencyValue +
        kaniLapseScore * 2.0

    companion object {
        private const val FREQUENCY_VALUE_MAX = 24.0
        private const val FREQUENCY_RANK_HORIZON = 4000.0
        private const val MIN_FSRS_DIFFICULTY = 1.0
        private const val MAX_FSRS_DIFFICULTY = 10.0

        /**
         * Manual-mode ordering: due tier first, most overdue first inside the
         * tier, then retention risk, then the composite priority score, then
         * discrete evidence tiebreakers, ending on the kanji for determinism.
         */
        @JvmStatic
        val MANUAL_ORDER: Comparator<AdaptiveLoadCandidate> =
            compareBy<AdaptiveLoadCandidate> { if (it.recoveryDue) 0 else 1 }
                .thenByDescending { it.overdueMillis }
                .thenByDescending { it.fsrsRisk }
                .thenByDescending { it.priorityScore }
                .thenByDescending { it.exposureBoost }
                .thenByDescending { it.suspendedCount }
                .thenByDescending { it.kaniLapseScore }
                .thenByDescending { it.supportDeficit }
                .thenByDescending { it.row.weaknessScore }
                .thenBy { it.row.kanji }

        /**
         * Auto-mode ordering: same due tier and aging rules, then the
         * composite priority score (the quantity the Pareto mass selection
         * reasons about), falling back to the manual order for ties.
         */
        @JvmStatic
        val AUTO_ORDER: Comparator<AdaptiveLoadCandidate> =
            compareBy<AdaptiveLoadCandidate> { if (it.recoveryDue) 0 else 1 }
                .thenByDescending { it.overdueMillis }
                .thenByDescending { it.priorityScore }
                .then(MANUAL_ORDER)

        /**
         * A Kani study item needs recovery only once its due time has arrived:
         * a mid-learning card whose step delay has elapsed, or a reviewed
         * card whose FSRS due time has passed. A learning card scheduled a few
         * minutes out (e.g. a card just answered `Again` in the session that
         * ended) is NOT recovery-due until that step delay elapses, so the
         * home counts read 0 for a while after finishing a session. The
         * learning clause deliberately omits the `totalReviews > 0` guard the
         * reviewed clause uses: a card abandoned mid-learning with no persisted
         * review yet must still count once it is past due.
         */
        @JvmStatic
        fun isRecoveryDue(item: RecordsStudyModels.StudyItem?, nowMillis: Long): Boolean {
            if (item == null || StudyLadderRules.STATE_RETIRED == item.state) {
                return false
            }
            if (StudyLadderRules.STATE_LEARNING == item.state) {
                return item.dueAtMillis <= nowMillis
            }
            return item.totalReviews > 0 && item.dueAtMillis <= nowMillis
        }

        private fun overdueMillis(item: RecordsStudyModels.StudyItem?, nowMillis: Long, recoveryDue: Boolean): Long {
            if (!recoveryDue || item == null) {
                return 0L
            }
            return nonNegativeDifference(nowMillis, item.dueAtMillis)
        }

        /**
         * Zipf-style value of a kanji from its Jiten frequency rank: rank 1
         * scores [FREQUENCY_VALUE_MAX], decaying logarithmically to zero at
         * rank [FREQUENCY_RANK_HORIZON]. Unknown ranks score zero.
         */
        @JvmStatic
        fun frequencyValue(rank: Int?): Double {
            if (rank == null || rank < 1) {
                return 0.0
            }
            val normalized = ln(rank.toDouble()) / ln(FREQUENCY_RANK_HORIZON)
            return FREQUENCY_VALUE_MAX * max(0.0, 1.0 - normalized)
        }

        /**
         * Kani-side evidence only: local item lapses and a writing-skill
         * deficit. Anki example lapses are deliberately excluded because the
         * analyzer already prices them into the row's weakness score.
         */
        @JvmStatic
        fun kaniLapseScore(item: RecordsStudyModels.StudyItem?): Int {
            if (item == null) {
                return 0
            }
            return saturatingAddNonNegative(
                saturatingMultiplyNonNegative(item.lapses, 3),
                3 - item.writingLevel.coerceIn(0, 3),
            )
        }

        private fun fsrsRisk(row: RecordsImportModels.DashboardRow, settings: RecordsSyncModels.Settings): Double {
            var best = 0.0
            for (example in row.examples) {
                best = max(best, exampleRisk(example, settings))
            }
            return best
        }

        private fun exampleRisk(example: RecordsImportModels.Example, settings: RecordsSyncModels.Settings): Double {
            var risk = 0.0
            val matureDays = settings.matureDays.coerceAtLeast(0)
            val intervalDays = example.intervalDays.coerceAtLeast(0)
            val reps = example.reps.coerceAtLeast(0)
            val retrievability = normalizedRetrievability(example.fsrsRetrievability)
            if (retrievability != null) {
                risk += max(0.0, 0.90 - retrievability) * 120.0
            }
            val difficulty = example.fsrsDifficulty
                ?.takeIf { it.isFinite() }
                ?.coerceIn(MIN_FSRS_DIFFICULTY, MAX_FSRS_DIFFICULTY)
            if (difficulty != null) {
                risk += max(0.0, difficulty - 5.0) * 5.0
            }
            val stability = example.fsrsStability?.takeIf { it.isFinite() && it > 0.0 }
            if (stability != null) {
                if (reps >= 5 && stability < matureDays.toDouble()) {
                    risk += (matureDays.toDouble() - stability) * 1.4
                } else if (example.mature && stability >= matureDays.toDouble() * 2.0) {
                    risk -= 8.0
                }
            } else if (reps >= 8 && intervalDays < matureDays) {
                risk += min(16.0, (matureDays.toDouble() - intervalDays.toDouble()) * 0.6)
            }
            return risk
        }

        private fun normalizedRetrievability(value: Double?): Double? {
            if (value == null || !value.isFinite() || value < 0.0) {
                return null
            }
            if (value > 1.0 && value <= 100.0) {
                return value / 100.0
            }
            if (value > 1.0) {
                return null
            }
            return value
        }
    }
}
