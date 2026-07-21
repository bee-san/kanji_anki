package dev.bee.kanjianki.core

import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decides how many kanji fit into today's focus and which ones they are.
 *
 * Auto mode uses a real Pareto criterion: it looks at how today's total
 * priority mass is distributed across ranked candidates and selects the head
 * of the curve that *maximizes* the Lorenz gap — its cumulative share of
 * priority mass minus the share an even spread would give it. Among heads the
 * max-gap prefix is the point of steepest over-concentration, so on a profile
 * like `[0.26, 0.30]` it picks the two-kanji head (gap peaks there), not the
 * single-kanji head. When no head clears [CONCENTRATION_GAP_THRESHOLD] the
 * priority is effectively spread evenly, so the plan falls back to the small
 * Pareto focus.
 *
 * The adjusted-target governor then shrinks the number under recent review
 * strain (misses, hard ratings, writing failures) and allows one extra kanji
 * on a steady streak, exactly as before.
 */
internal object AdaptiveLoadFocusPolicy {
    const val AUTO_PARETO_CAP: Int = 20

    /**
     * Minimum Lorenz gap (cumulative priority share minus even share) for a
     * head of the curve to count as concentrated. At 0.25, one of two
     * candidates must carry at least 3x the other's priority to be a focus
     * of one.
     */
    private const val CONCENTRATION_GAP_THRESHOLD = 0.25

    internal class AutoTarget(target: Int, @JvmField val concentrated: Boolean) {
        @JvmField val target: Int = max(1, target)
    }

    /**
     * Selects the auto focus size from the priority-mass distribution of the
     * non-due candidates. Due-recovery candidates always ride along on top of
     * the selected head. Candidates must already be sorted by
     * [AdaptiveLoadCandidate.AUTO_ORDER].
     */
    fun autoParetoTarget(candidates: List<AdaptiveLoadCandidate>): AutoTarget {
        var recoveryDue = 0
        val ranked = ArrayList<AdaptiveLoadCandidate>()
        for (candidate in candidates) {
            if (candidate.recoveryDue) {
                recoveryDue++
            } else {
                ranked.add(candidate)
            }
        }
        if (ranked.isEmpty()) {
            return AutoTarget(max(1, recoveryDue), false)
        }
        val fallback = min(ranked.size, AdaptiveLoadPlanner.targetCeiling(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT))
        val concentratedHead = concentratedHeadSize(ranked)
        if (concentratedHead <= 0) {
            return AutoTarget(clampAutoTarget(recoveryDue + fallback), false)
        }
        return AutoTarget(clampAutoTarget(recoveryDue + concentratedHead), true)
    }

    /**
     * Returns the size of the concentrated head of the priority curve, or 0
     * when the curve is flat, even, or has no positive mass. The head is the
     * prefix maximizing the Lorenz gap: its share of total priority mass
     * minus the share an even distribution would give it. The gap is compared
     * inclusively against [CONCENTRATION_GAP_THRESHOLD] (a gap of exactly the
     * threshold counts as concentrated).
     */
    private fun concentratedHeadSize(ranked: List<AdaptiveLoadCandidate>): Int {
        var total = 0.0
        for (candidate in ranked) {
            total += max(0.0, candidate.priorityScore)
        }
        if (total <= 0.0) {
            return 0
        }
        // A single candidate with positive priority is, by definition, the whole
        // concentrated head: one kanji carries all of today's priority. The Lorenz
        // loop below cannot reach it (it stops before the last index), so classify
        // it directly rather than fall back to the "spread evenly" copy.
        if (ranked.size == 1) {
            return 1
        }
        var cumulative = 0.0
        var bestGap = 0.0
        var bestHead = 0
        for (i in 0 until ranked.size - 1) {
            cumulative += max(0.0, ranked[i].priorityScore)
            val gap = cumulative / total - (i + 1).toDouble() / ranked.size
            if (gap > bestGap) {
                bestGap = gap
                bestHead = i + 1
            }
        }
        if (bestGap < CONCENTRATION_GAP_THRESHOLD) {
            return 0
        }
        return bestHead
    }

    private fun clampAutoTarget(value: Int): Int = max(1, min(AUTO_PARETO_CAP, value))

    fun adjustedTarget(
        ceiling: Int,
        stats: RecordsSchedulerModels.ReviewStats,
        currentStreakDays: Int,
        recoveryDue: Int,
    ): Int {
        val target = if (stats.total.coerceAtLeast(0) == 0) {
            min(3, ceiling)
        } else {
            max(1, (ceiling * 0.65f).roundToInt())
        }
        return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue)
    }

    fun adjustedAutoTarget(
        autoTarget: Int,
        ceiling: Int,
        stats: RecordsSchedulerModels.ReviewStats,
        currentStreakDays: Int,
        recoveryDue: Int,
    ): Int {
        val target = if (stats.total.coerceAtLeast(0) == 0) min(3, autoTarget) else autoTarget
        return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue)
    }

    private fun adjustedTargetFromBase(
        baseTarget: Int,
        ceiling: Int,
        stats: RecordsSchedulerModels.ReviewStats,
        currentStreakDays: Int,
        recoveryDue: Int,
    ): Int {
        var target = baseTarget
        val total = stats.total.coerceAtLeast(0)
        val missRate = if (total == 0) 0.0 else stats.again.coerceIn(0, total).toDouble() / total.toDouble()
        val hardRate = if (total == 0) 0.0 else stats.hard.coerceIn(0, total).toDouble() / total.toDouble()
        val writingFailureRate = stats.writingFailureRate()
        if (missRate >= 0.50) {
            target -= 2
        } else if (missRate >= 0.25) {
            target -= 1
        }
        if (hardRate >= 0.45) {
            target -= 1
        }
        if (writingFailureRate >= 0.30) {
            target -= 1
        }
        if (recoveryDue.coerceAtLeast(0) >= target) {
            target = max(1, target - 1)
        }
        if (total >= 3 &&
            currentStreakDays >= 3 &&
            missRate <= 0.10 &&
            hardRate <= 0.25 &&
            writingFailureRate <= 0.10
        ) {
            target += 1
        }
        return max(1, min(ceiling, target))
    }

    /**
     * Assembles the focus list: every due-recovery candidate first (already
     * aged most-overdue-first by the comparator), then the remaining slots by
     * priority order.
     */
    fun focusKanji(candidates: List<AdaptiveLoadCandidate>, displayTarget: Int): List<String> {
        val focus = LinkedHashSet<String>()
        addDueRecovery(candidates, focus, displayTarget)
        addByPriority(candidates, focus, displayTarget)
        return ArrayList(focus)
    }

    private fun addDueRecovery(candidates: List<AdaptiveLoadCandidate>, focus: LinkedHashSet<String>, displayTarget: Int) {
        for (candidate in candidates) {
            if (focus.size >= displayTarget) {
                return
            }
            if (candidate.recoveryDue) {
                focus.add(candidate.row.kanji)
            }
        }
    }

    private fun addByPriority(candidates: List<AdaptiveLoadCandidate>, focus: LinkedHashSet<String>, displayTarget: Int) {
        for (candidate in candidates) {
            if (focus.size >= displayTarget) {
                return
            }
            focus.add(candidate.row.kanji)
        }
    }
}
