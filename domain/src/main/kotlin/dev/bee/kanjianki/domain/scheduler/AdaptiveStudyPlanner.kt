package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AdaptiveStudyPlanner {
    fun plan(request: AdaptiveStudyPlanRequest? = null): AdaptiveStudyPlan {
        val inputs = request ?: AdaptiveStudyPlanRequest(
            workloadPolicy = AdaptiveWorkloadPolicy.manual(DEFAULT_WORKLOAD_PERCENT),
        )
        val candidates = candidatesFor(inputs)
        if (candidates.isEmpty()) {
            return emptyPlan(inputs)
        }
        if (inputs.allKanjiMode) {
            return allKanjiPlan(candidates, inputs)
        }

        val recoveryDue = candidates.count { it.recoveryDue }
        val targetPlan = targetPlanFor(candidates, inputs, recoveryDue)
        val cappedRecoveryDue = min(recoveryDue, inputs.workloadPolicy.maxItems)
        val focusKanji = focusKanji(
            candidates = candidates,
            displayTarget = min(
                inputs.workloadPolicy.maxItems,
                max(targetPlan.adjustedTarget, cappedRecoveryDue),
            ),
        )
        val remaining = remainingCount(
            focusKanji = focusKanji,
            itemByKanji = inputs.itemByKanji,
            studiedToday = inputs.studiedToday,
            nowMillis = inputs.nowMillis,
        )
        return AdaptiveStudyPlan(
            autoMode = inputs.workloadPolicy.mode.isAuto,
            workloadPercent = inputs.workloadPolicy.workloadPercent,
            targetCount = focusKanji.size,
            remainingCount = remaining,
            focusKanji = focusKanji,
            newAdmissionLimit = max(0, focusKanji.size - cappedRecoveryDue),
            allKanjiMode = false,
            status = statusFor(targetPlan, inputs, recoveryDue),
        )
    }

    private fun candidatesFor(inputs: AdaptiveStudyPlanRequest): List<Candidate> {
        val comparator = if (inputs.workloadPolicy.mode.isAuto) {
            AUTO_CANDIDATE_ORDER
        } else {
            CANDIDATE_ORDER
        }
        return inputs.rows.map { row ->
            Candidate(
                row = row,
                item = inputs.itemByKanji[row.kanji],
                nowMillis = inputs.nowMillis,
                settings = inputs.settings,
            )
        }.sortedWith(comparator)
    }

    private fun emptyPlan(inputs: AdaptiveStudyPlanRequest): AdaptiveStudyPlan = AdaptiveStudyPlan(
        autoMode = inputs.workloadPolicy.mode.isAuto,
        workloadPercent = inputs.workloadPolicy.workloadPercent,
        targetCount = 0,
        remainingCount = 0,
        focusKanji = emptyList(),
        newAdmissionLimit = 0,
        allKanjiMode = inputs.allKanjiMode,
        status = "No current problem kanji.",
    )

    private fun allKanjiPlan(
        candidates: List<Candidate>,
        inputs: AdaptiveStudyPlanRequest,
    ): AdaptiveStudyPlan {
        var focus = candidates.map { it.row.kanji }
        var allIncluded = true
        if (inputs.workloadPolicy.maxItems != Int.MAX_VALUE && focus.size > inputs.workloadPolicy.maxItems) {
            focus = focus.take(inputs.workloadPolicy.maxItems)
            allIncluded = false
        }
        val remaining = remainingCount(
            focusKanji = focus,
            itemByKanji = inputs.itemByKanji,
            studiedToday = inputs.studiedToday,
            nowMillis = inputs.nowMillis,
        )
        return AdaptiveStudyPlan(
            autoMode = false,
            workloadPercent = inputs.workloadPolicy.workloadPercent,
            targetCount = focus.size,
            remainingCount = remaining,
            focusKanji = focus,
            newAdmissionLimit = focus.size,
            allKanjiMode = allIncluded,
            status = if (allIncluded) {
                "All current problem kanji are available today."
            } else {
                "All kanji mode is capped to today's maximum."
            },
        )
    }

    private fun targetPlanFor(
        candidates: List<Candidate>,
        inputs: AdaptiveStudyPlanRequest,
        recoveryDue: Int,
    ): TargetPlan {
        if (inputs.workloadPolicy.mode.isAuto) {
            val autoTarget = autoParetoTarget(candidates)
            val ceiling = min(min(candidates.size, AUTO_PARETO_CAP), inputs.workloadPolicy.maxItems)
            val adjusted = adjustedAutoTarget(
                autoTarget = autoTarget.target,
                ceiling = ceiling,
                stats = inputs.recentStats,
                currentStreakDays = inputs.currentStreakDays,
                recoveryDue = recoveryDue,
            )
            return TargetPlan(ceiling, adjusted, autoTarget)
        }
        val ceiling = min(targetCeiling(inputs.workloadPolicy.workloadPercent), inputs.workloadPolicy.maxItems)
        return TargetPlan(
            ceiling = ceiling,
            adjustedTarget = adjustedTarget(
                ceiling = ceiling,
                stats = inputs.recentStats,
                currentStreakDays = inputs.currentStreakDays,
                recoveryDue = recoveryDue,
            ),
            autoTarget = null,
        )
    }

    private fun focusKanji(
        candidates: List<Candidate>,
        displayTarget: Int,
    ): List<String> {
        val focus = linkedSetOf<String>()
        for (candidate in candidates) {
            if (focus.size >= displayTarget) {
                return focus.toList()
            }
            if (candidate.recoveryDue) {
                focus.add(candidate.row.kanji)
            }
        }
        for (candidate in candidates) {
            if (focus.size >= displayTarget) {
                return focus.toList()
            }
            focus.add(candidate.row.kanji)
        }
        return focus.toList()
    }

    private fun statusFor(
        targetPlan: TargetPlan,
        inputs: AdaptiveStudyPlanRequest,
        recoveryDue: Int,
    ): String = if (inputs.workloadPolicy.mode.isAuto) {
        autoStatusFor(targetPlan.adjustedTarget, requireNotNull(targetPlan.autoTarget), inputs.recentStats, recoveryDue)
    } else {
        manualStatusFor(
            workloadPercent = inputs.workloadPolicy.workloadPercent,
            target = targetPlan.adjustedTarget,
            ceiling = targetPlan.ceiling,
            stats = inputs.recentStats,
            recoveryDue = recoveryDue,
        )
    }

    private data class TargetPlan(
        val ceiling: Int,
        val adjustedTarget: Int,
        val autoTarget: AutoTarget?,
    )

    private data class Candidate(
        val row: StudyDashboardRow,
        val item: StudyQueueItem?,
        val nowMillis: Long,
        val settings: AdaptiveStudySettings,
    ) {
        val recoveryDue: Boolean = recoveryDue(item, nowMillis)
        val fsrsRisk: Double = fsrsRisk(row, settings)
        val suspendedCount: Int = row.suspendedExampleCount
        val lapseScore: Int = lapseScore(row, item)
        val supportDeficit: Int = max(0, settings.matureSupportThreshold - row.matureSupportCount)
        val priorityScore: Double = row.weaknessScore +
            fsrsRisk +
            suspendedCount * 8.0 +
            lapseScore * 2.0 +
            supportDeficit * 4.0
    }

    private data class AutoTarget(
        val target: Int,
        val dropFound: Boolean,
    ) {
        init {
            require(target >= 1) { "target must be positive" }
        }
    }

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_MANUAL = "manual"
        const val DEFAULT_WORKLOAD_PERCENT = 20
        const val DEFAULT_WORKLOAD_MODE = MODE_AUTO
        const val DEFAULT_MAX_ITEMS = 5
        const val MIN_MAX_ITEMS = 1
        const val MAX_MAX_ITEMS = 20
        private const val AUTO_PARETO_CAP = 20

        fun normalizeWorkloadMode(mode: String?): String =
            AdaptiveWorkloadMode.fromSetting(mode).settingValue

        fun isAutoMode(mode: String?): Boolean =
            AdaptiveWorkloadMode.fromSetting(mode).isAuto

        fun snapWorkloadPercent(value: Int): Int {
            val clamped = value.coerceIn(0, 100)
            if (clamped == 100) {
                return 100
            }
            return (clamped / 5.0f).roundToInt()
                .times(5)
                .coerceIn(0, 95)
        }

        fun targetCeiling(workloadPercent: Int): Int {
            val snapped = snapWorkloadPercent(workloadPercent)
            if (snapped >= 100) {
                return Int.MAX_VALUE
            }
            return (1 + snapped / 5).coerceIn(1, 20)
        }

        fun normalizeMaxItems(value: Int): Int =
            value.coerceIn(MIN_MAX_ITEMS, MAX_MAX_ITEMS)

        fun workloadLabel(workloadPercent: Int): String {
            val snapped = snapWorkloadPercent(workloadPercent)
            return when {
                snapped <= 0 -> "Very little"
                snapped <= 20 -> "Pareto"
                snapped <= 50 -> "Balanced"
                snapped < 100 -> "More"
                else -> "All kanji"
            }
        }

        private val CANDIDATE_ORDER: Comparator<Candidate> =
            compareBy<Candidate> { if (it.recoveryDue) 0 else 1 }
                .thenByDescending { it.fsrsRisk }
                .thenByDescending { it.suspendedCount }
                .thenByDescending { it.lapseScore }
                .thenByDescending { it.supportDeficit }
                .thenByDescending { it.row.weaknessScore }
                .thenBy { it.row.kanji }

        private val AUTO_CANDIDATE_ORDER: Comparator<Candidate> =
            compareBy<Candidate> { if (it.recoveryDue) 0 else 1 }
                .thenByDescending { it.priorityScore }
                .then(CANDIDATE_ORDER)

        private fun adjustedTarget(
            ceiling: Int,
            stats: AdaptiveReviewStats,
            currentStreakDays: Int,
            recoveryDue: Int,
        ): Int {
            val target = if (stats.total == 0) {
                min(3, ceiling)
            } else {
                max(1, (ceiling * 0.65f).roundToInt())
            }
            return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue)
        }

        private fun adjustedAutoTarget(
            autoTarget: Int,
            ceiling: Int,
            stats: AdaptiveReviewStats,
            currentStreakDays: Int,
            recoveryDue: Int,
        ): Int {
            val target = if (stats.total == 0) min(3, autoTarget) else autoTarget
            return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue)
        }

        private fun adjustedTargetFromBase(
            baseTarget: Int,
            ceiling: Int,
            stats: AdaptiveReviewStats,
            currentStreakDays: Int,
            recoveryDue: Int,
        ): Int {
            var target = baseTarget
            val missRate = if (stats.total == 0) 0.0 else stats.again / stats.total.toDouble()
            val hardRate = if (stats.total == 0) 0.0 else stats.hard / stats.total.toDouble()
            val writingFailureRate = stats.writingFailureRate()
            when {
                missRate >= 0.50 -> target -= 2
                missRate >= 0.25 -> target -= 1
            }
            if (hardRate >= 0.45) {
                target -= 1
            }
            if (writingFailureRate >= 0.30) {
                target -= 1
            }
            if (recoveryDue >= target) {
                target = max(1, target - 1)
            }
            if (
                stats.total >= 3 &&
                currentStreakDays >= 3 &&
                missRate <= 0.10 &&
                hardRate <= 0.25 &&
                writingFailureRate <= 0.10
            ) {
                target += 1
            }
            return target.coerceIn(1, ceiling)
        }

        private fun autoParetoTarget(candidates: List<Candidate>): AutoTarget {
            val recoveryDue = candidates.count { it.recoveryDue }
            val ranked = candidates.filterNot { it.recoveryDue }
            if (ranked.isEmpty()) {
                return AutoTarget(max(1, recoveryDue), dropFound = false)
            }

            val fallback = min(ranked.size, targetCeiling(DEFAULT_WORKLOAD_PERCENT))
            val top = ranked.first().priorityScore
            if (top <= 0.0) {
                return AutoTarget(min(AUTO_PARETO_CAP, recoveryDue + fallback), dropFound = false)
            }
            val absoluteDrop = max(4.0, top * 0.15)
            val scanLimit = min(ranked.size - 1, max(0, AUTO_PARETO_CAP - recoveryDue - 1))
            for (index in 0 until scanLimit) {
                val current = ranked[index].priorityScore
                val next = ranked[index + 1].priorityScore
                val drop = current - next
                if (next <= current * 0.70 && drop >= absoluteDrop) {
                    return AutoTarget(max(1, min(AUTO_PARETO_CAP, recoveryDue + index + 1)), true)
                }
            }
            return AutoTarget(max(1, min(AUTO_PARETO_CAP, recoveryDue + fallback)), false)
        }

        private fun remainingCount(
            focusKanji: List<String>,
            itemByKanji: Map<String, StudyQueueItem>,
            studiedToday: Set<String>,
            nowMillis: Long,
        ): Int = focusKanji.count { kanji ->
            !studiedToday.contains(kanji) || recoveryDue(itemByKanji[kanji], nowMillis)
        }

        private fun manualStatusFor(
            workloadPercent: Int,
            target: Int,
            ceiling: Int,
            stats: AdaptiveReviewStats,
            recoveryDue: Int,
        ): String = when {
            workloadPercent <= 0 ->
                "Very little work today: one focused kanji unless recovery is already due."
            stats.total == 0 ->
                "Pareto focus starts small until Kani has review history."
            recoveryDue >= target ->
                "Due recovery fills today's focus, so new kanji wait."
            target >= ceiling ->
                "Recent reviews are steady, so Kani can use the full focus range."
            else ->
                "Adaptive focus is set from recent misses, hard ratings, and writing results."
        }

        private fun autoStatusFor(
            target: Int,
            autoTarget: AutoTarget,
            stats: AdaptiveReviewStats,
            recoveryDue: Int,
        ): String = when {
            recoveryDue >= target ->
                "Due recovery fills today's auto Pareto focus, so new kanji wait."
            stats.total == 0 && autoTarget.dropFound ->
                "Auto Pareto found today's drop-off, then starts small until Kani has review history."
            stats.total == 0 ->
                "Auto Pareto starts small until Kani has review history."
            !autoTarget.dropFound ->
                "Auto Pareto did not find a sharp drop-off, so Kani uses the small Pareto focus."
            target < autoTarget.target ->
                "Auto Pareto found today's drop-off, then recent review strain lowered the focus."
            target > autoTarget.target ->
                "Auto Pareto found today's drop-off and your steady streak allows one extra kanji."
            else ->
                "Auto Pareto uses today's problem-kanji drop-off."
        }

        private fun recoveryDue(
            item: StudyQueueItem?,
            nowMillis: Long,
        ): Boolean {
            if (item == null || item.state == StudyItemState.RETIRED) {
                return false
            }
            if (item.state == StudyItemState.LEARNING) {
                return true
            }
            return item.totalReviews > 0 && item.dueAtMillis <= nowMillis
        }

        private fun lapseScore(
            row: StudyDashboardRow,
            item: StudyQueueItem?,
        ): Int {
            var score = if (item == null) {
                0
            } else {
                item.lapses * 3 + max(0, 3 - item.writingLevel)
            }
            for (example in row.examples) {
                score += example.lapses
            }
            return score
        }

        private fun fsrsRisk(
            row: StudyDashboardRow,
            settings: AdaptiveStudySettings,
        ): Double {
            var best = 0.0
            for (example in row.examples) {
                var risk = 0.0
                val retrievability = normalizedRetrievability(example.fsrsRetrievability)
                if (retrievability != null) {
                    risk += max(0.0, 0.90 - retrievability) * 120.0
                }
                if (example.fsrsDifficulty != null) {
                    risk += max(0.0, example.fsrsDifficulty - 5.0) * 5.0
                }
                if (example.fsrsStability != null) {
                    if (example.reps >= 5 && example.fsrsStability < settings.matureDays) {
                        risk += (settings.matureDays - example.fsrsStability) * 1.4
                    } else if (example.mature && example.fsrsStability >= settings.matureDays * 2.0) {
                        risk -= 8.0
                    }
                } else if (example.reps >= 8 && example.intervalDays < settings.matureDays) {
                    risk += min(16.0, (settings.matureDays - example.intervalDays) * 0.6)
                }
                best = max(best, risk)
            }
            return best
        }

        private fun normalizedRetrievability(value: Double?): Double? = when {
            value == null -> null
            value < 0.0 -> null
            value > 1.0 && value <= 100.0 -> value / 100.0
            value > 1.0 -> null
            else -> value
        }
    }
}

data class AdaptiveStudyPlanRequest(
    val rows: List<StudyDashboardRow> = emptyList(),
    val items: List<StudyQueueItem> = emptyList(),
    val recentStats: AdaptiveReviewStats = AdaptiveReviewStats(),
    val currentStreakDays: Int = 0,
    val studiedToday: Set<String> = emptySet(),
    val workloadPolicy: AdaptiveWorkloadPolicy =
        AdaptiveWorkloadPolicy.fromSettings(
            AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT,
            AdaptiveStudyPlanner.DEFAULT_WORKLOAD_MODE,
            AdaptiveStudyPlanner.DEFAULT_MAX_ITEMS,
        ),
    val nowMillis: Long = 0L,
    val settings: AdaptiveStudySettings = AdaptiveStudySettings(),
) {
    internal val itemByKanji: Map<String, StudyQueueItem> = items.associateBy { it.kanji }

    internal val allKanjiMode: Boolean
        get() = !workloadPolicy.mode.isAuto &&
            workloadPolicy.workloadPercent >= 100
}

data class AdaptiveStudyPlan(
    val autoMode: Boolean,
    val workloadPercent: Int,
    val targetCount: Int,
    val remainingCount: Int,
    val focusKanji: List<String>,
    val newAdmissionLimit: Int,
    val allKanjiMode: Boolean,
    val status: String,
) {
    fun focusComplete(): Boolean = !allKanjiMode && targetCount > 0 && remainingCount <= 0
}

data class AdaptiveReviewStats(
    val total: Int = 0,
    val again: Int = 0,
    val hard: Int = 0,
    val good: Int = 0,
    val easy: Int = 0,
    val writingRequired: Int = 0,
    val writingFailed: Int = 0,
) {
    fun writingFailureRate(): Double =
        if (writingRequired == 0) 0.0 else writingFailed / writingRequired.toDouble()
}

data class AdaptiveStudySettings(
    val matureDays: Int = 21,
    val matureSupportThreshold: Int = 2,
)

enum class AdaptiveWorkloadMode(
    val settingValue: String,
) {
    AUTO(AdaptiveStudyPlanner.MODE_AUTO),
    MANUAL(AdaptiveStudyPlanner.MODE_MANUAL);

    val isAuto: Boolean
        get() = this == AUTO

    companion object {
        fun fromSetting(mode: String?): AdaptiveWorkloadMode =
            if (mode == AdaptiveStudyPlanner.MODE_MANUAL) MANUAL else AUTO
    }
}

data class AdaptiveWorkloadPolicy(
    val mode: AdaptiveWorkloadMode,
    val workloadPercent: Int,
    val maxItems: Int,
) {
    companion object {
        fun of(
            mode: AdaptiveWorkloadMode?,
            workloadPercent: Int,
            maxItems: Int,
        ): AdaptiveWorkloadPolicy = AdaptiveWorkloadPolicy(
            mode = mode ?: AdaptiveWorkloadMode.AUTO,
            workloadPercent = AdaptiveStudyPlanner.snapWorkloadPercent(workloadPercent),
            maxItems = if (maxItems == Int.MAX_VALUE) {
                Int.MAX_VALUE
            } else {
                AdaptiveStudyPlanner.normalizeMaxItems(maxItems)
            },
        )

        fun manual(workloadPercent: Int): AdaptiveWorkloadPolicy =
            of(AdaptiveWorkloadMode.MANUAL, workloadPercent, Int.MAX_VALUE)

        fun fromSettings(
            workloadPercent: Int,
            workloadMode: String?,
            maxItems: Int,
        ): AdaptiveWorkloadPolicy = of(
            AdaptiveWorkloadMode.fromSetting(workloadMode),
            workloadPercent,
            maxItems,
        )
    }
}
