package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Derives the initial ladder rung, phase and FSRS memory for a newly admitted
 * study item from the Anki evidence already carried on its dashboard row.
 *
 * Core principle: do not re-teach a kanji the learner already reads in context.
 *
 * - A kanji supported by a mature active card that was never suspended
 *   ([isAlreadyReadInContext]) is seeded straight into the review phase at the
 *   top rung, so it is validated once against a real FSRS interval derived from
 *   Anki's own memory state instead of climbing the whole ladder from scratch.
 * - Everything else keeps the conservative new-learning start at the default
 *   rung, but its initial difficulty is still primed from Anki evidence so a
 *   known-hard kanji is paced appropriately if it later lapses.
 *
 * The strong-evidence band is deliberately narrow: queue admission already
 * skips rows whose mature support has met the retirement threshold (default 2),
 * so this only fires for kanji that are read in context but not yet fully
 * supported (typically exactly one mature active example, no suspensions).
 */
object AdmissionEvidencePolicy {
    private const val MIN_SEED_STABILITY = 1.0
    private const val MAX_SEED_STABILITY = 36_500.0
    private const val PLACEHOLDER_STABILITY = 0.4
    private const val DEFAULT_DIFFICULTY = 5.0
    private const val MIN_DIFFICULTY = 1.0
    private const val MAX_DIFFICULTY = 10.0
    private const val MAX_LAPSE_DIFFICULTY_BONUS = 5

    class Seed internal constructor(
        @JvmField val rung: RecordsBase.LadderRung,
        @JvmField val phase: RecordsBase.SchedulerPhase,
        @JvmField val state: String,
        @JvmField val stability: Double,
        @JvmField val difficulty: Double,
    ) {
        @JvmField val matureIntervalDays: Int = if (state == StudyLadderRules.STATE_REVIEW) {
            min(Int.MAX_VALUE.toLong(), stability.roundToInt().toLong().coerceAtLeast(0L)).toInt()
        } else {
            0
        }

        fun isReviewSeed(): Boolean = state == StudyLadderRules.STATE_REVIEW
    }

    @JvmStatic
    fun seedFor(
        row: RecordsImportModels.DashboardRow?,
        ladder: RecordsBase.StudyLadderSettings?,
        settings: RecordsSyncModels.Settings?,
    ): Seed {
        val safeLadder = StudyLadderRules.safeLadder(ladder)
        val safeSettings = settings ?: RecordsSyncModels.Settings.kikuDefaults()
        val difficulty = evidenceDifficulty(row)
        if (row != null && isAlreadyReadInContext(row)) {
            return Seed(
                safeLadder.highestRung(RecordsBase.RungAvailability.none()),
                RecordsBase.SchedulerPhase.REVIEW,
                StudyLadderRules.STATE_REVIEW,
                evidenceStability(row, safeSettings),
                difficulty,
            )
        }
        return Seed(
            safeLadder.startingRung(RecordsBase.RungAvailability.none()),
            RecordsBase.SchedulerPhase.NEW_LEARNING,
            StudyLadderRules.STATE_NEW,
            PLACEHOLDER_STABILITY,
            difficulty,
        )
    }

    /**
     * Strong evidence: the kanji appears in at least one mature active card and
     * was never suspended, so the learner demonstrably reads it in a mature word
     * context. Such a kanji is validated once at the top rung rather than
     * drilled up the whole ladder.
     */
    @JvmStatic
    fun isAlreadyReadInContext(row: RecordsImportModels.DashboardRow): Boolean {
        return row.suspendedExampleCount == 0 && row.matureSupportCount >= 1
    }

    private fun evidenceStability(
        row: RecordsImportModels.DashboardRow,
        settings: RecordsSyncModels.Settings,
    ): Double {
        var best = 0.0
        for (example in row.examples) {
            if (RecordsBase.SOURCE_SUSPENDED == example.sourceType) {
                continue
            }
            val fromFsrs = example.fsrsStability
            val candidate = if (fromFsrs != null && fromFsrs.isFinite() && fromFsrs > 0.0) {
                fromFsrs
            } else {
                example.intervalDays.toDouble()
            }
            best = max(best, candidate)
        }
        if (best <= 0.0) {
            best = settings.matureDays.toDouble()
        }
        return min(MAX_SEED_STABILITY, max(MIN_SEED_STABILITY, best))
    }

    private fun evidenceDifficulty(row: RecordsImportModels.DashboardRow?): Double {
        if (row == null) {
            return DEFAULT_DIFFICULTY
        }
        var hardest: Double? = null
        var maxLapses = 0
        for (example in row.examples) {
            val difficulty = example.fsrsDifficulty
            if (difficulty != null && difficulty.isFinite()) {
                hardest = if (hardest == null) difficulty else max(hardest, difficulty)
            }
            maxLapses = max(maxLapses, example.lapses)
        }
        val raw = hardest ?: (DEFAULT_DIFFICULTY + min(MAX_LAPSE_DIFFICULTY_BONUS, maxLapses).toDouble())
        return min(MAX_DIFFICULTY, max(MIN_DIFFICULTY, raw))
    }
}
