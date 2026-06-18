package dev.bee.kanjianki.core

import java.util.LinkedHashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AdaptiveLoadPlanner {
    fun plan(request: PlanRequest?): RecordsSchedulerModels.AdaptiveLoadPlan {
        return planInternal(PlanInputs.from(request))
    }

    private fun planInternal(inputs: PlanInputs): RecordsSchedulerModels.AdaptiveLoadPlan {
        val candidates = candidatesFor(inputs)
        if (candidates.isEmpty()) {
            return emptyPlan(inputs)
        }

        if (inputs.allKanjiMode()) {
            return allKanjiPlan(candidates, inputs)
        }

        val recoveryDue = recoveryDueCount(candidates)
        val targetPlan = targetPlanFor(candidates, inputs, recoveryDue)
        val cappedRecoveryDue = min(recoveryDue, inputs.itemCap)
        val focusKanji = focusKanji(candidates, min(inputs.itemCap, max(targetPlan.adjustedTarget, cappedRecoveryDue)))
        val remaining = remainingCount(focusKanji, inputs.itemByKanji, inputs.studiedToday, inputs.nowMillis)
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            inputs.autoMode,
            inputs.workloadPercent,
            focusKanji.size,
            remaining,
            focusKanji,
            max(0, focusKanji.size - cappedRecoveryDue),
            false,
            statusFor(targetPlan, inputs, recoveryDue)
        )
    }

    enum class WorkloadMode(private val settingValue: String) {
        AUTO(MODE_AUTO),
        MANUAL(MODE_MANUAL);

        fun settingValue(): String = settingValue

        fun isAuto(): Boolean = this == AUTO

        companion object {
            @JvmStatic
            fun fromSetting(mode: String?): WorkloadMode {
                return if (MODE_MANUAL == mode) MANUAL else AUTO
            }
        }
    }

    class WorkloadPolicy private constructor(
        private val mode: WorkloadMode?,
        workloadPercent: Int,
        maxItems: Int
    ) {
        private val workloadPercent: Int = snapWorkloadPercent(workloadPercent)
        private val maxItems: Int = if (maxItems == Int.MAX_VALUE) Int.MAX_VALUE else normalizeMaxItems(maxItems)

        fun mode(): WorkloadMode = mode ?: WorkloadMode.AUTO

        fun workloadPercent(): Int = workloadPercent

        fun maxItems(): Int = maxItems

        companion object {
            @JvmStatic
            fun of(mode: WorkloadMode?, workloadPercent: Int, maxItems: Int): WorkloadPolicy {
                return WorkloadPolicy(mode, workloadPercent, maxItems)
            }

            @JvmStatic
            fun manual(workloadPercent: Int): WorkloadPolicy {
                return WorkloadPolicy(WorkloadMode.MANUAL, workloadPercent, Int.MAX_VALUE)
            }

            @JvmStatic
            fun fromSettings(workloadPercent: Int, workloadMode: String?, maxItems: Int): WorkloadPolicy {
                return of(WorkloadMode.fromSetting(workloadMode), workloadPercent, maxItems)
            }
        }
    }

    class PlanRequest private constructor(builder: Builder) {
        private val rows: List<RecordsImportModels.DashboardRow>? = builder.rows
        private val items: List<RecordsStudyModels.StudyItem>? = builder.items
        internal val recentStats: RecordsSchedulerModels.ReviewStats? = builder.recentStats
        internal val currentStreakDays: Int = builder.currentStreakDays
        internal val studiedToday: Set<String>? = builder.studiedToday
        internal val workloadPolicy: WorkloadPolicy? = builder.workloadPolicy
        private val nowMillis: Long = builder.nowMillis
        internal val settings: RecordsSyncModels.Settings? = builder.settings
        internal val readingExposure: ReadingExposureModels.ExposureIndex? = builder.readingExposure

        fun rows(): List<RecordsImportModels.DashboardRow>? = rows

        fun items(): List<RecordsStudyModels.StudyItem>? = items

        fun nowMillis(): Long = nowMillis

        class Builder private constructor(
            val rows: List<RecordsImportModels.DashboardRow>?,
            val items: List<RecordsStudyModels.StudyItem>?,
            val recentStats: RecordsSchedulerModels.ReviewStats?,
            val currentStreakDays: Int,
            val studiedToday: Set<String>?,
            workloadPolicy: WorkloadPolicy?,
            nowMillis: Long
        ) {
            var workloadPolicy: WorkloadPolicy? = workloadPolicy
                private set
            var nowMillis: Long = nowMillis
                private set
            var settings: RecordsSyncModels.Settings? = RecordsSyncModels.Settings.kikuDefaults()
                private set
            var readingExposure: ReadingExposureModels.ExposureIndex? = ReadingExposureModels.ExposureIndex.EMPTY
                private set

            fun workloadPolicy(workloadPolicy: WorkloadPolicy?): Builder {
                this.workloadPolicy = workloadPolicy
                return this
            }

            fun nowMillis(nowMillis: Long): Builder {
                this.nowMillis = nowMillis
                return this
            }

            fun settings(settings: RecordsSyncModels.Settings?): Builder {
                this.settings = settings
                return this
            }

            fun readingExposure(readingExposure: ReadingExposureModels.ExposureIndex?): Builder {
                this.readingExposure = readingExposure ?: ReadingExposureModels.ExposureIndex.EMPTY
                return this
            }

            fun build(): PlanRequest = PlanRequest(this)

            companion object {
                fun create(
                    rows: List<RecordsImportModels.DashboardRow>?,
                    items: List<RecordsStudyModels.StudyItem>?,
                    recentStats: RecordsSchedulerModels.ReviewStats?,
                    currentStreakDays: Int,
                    studiedToday: Set<String>?,
                    workloadPolicy: WorkloadPolicy?,
                    nowMillis: Long
                ): Builder {
                    return Builder(rows, items, recentStats, currentStreakDays, studiedToday, workloadPolicy, nowMillis)
                }
            }
        }

        companion object {
            @JvmStatic
            fun builder(
                rows: List<RecordsImportModels.DashboardRow>?,
                items: List<RecordsStudyModels.StudyItem>?,
                recentStats: RecordsSchedulerModels.ReviewStats?,
                currentStreakDays: Int,
                studiedToday: Set<String>?,
                workloadPolicy: WorkloadPolicy?,
                nowMillis: Long
            ): Builder {
                return Builder.create(rows, items, recentStats, currentStreakDays, studiedToday, workloadPolicy, nowMillis)
            }
        }

    }

    private class PlanInputs private constructor(request: PlanRequest) {
        val rows: List<RecordsImportModels.DashboardRow>
        val stats: RecordsSchedulerModels.ReviewStats
        val studiedToday: Set<String>
        val currentStreakDays: Int
        val workloadPercent: Int
        val autoMode: Boolean
        val itemCap: Int
        val nowMillis: Long
        val settings: RecordsSyncModels.Settings
        val readingExposure: ReadingExposureModels.ExposureIndex
        val itemByKanji: Map<String, RecordsStudyModels.StudyItem>

        init {
            val policy = request.workloadPolicy ?: WorkloadPolicy.manual(DEFAULT_WORKLOAD_PERCENT)
            rows = request.rows() ?: emptyList()
            stats = request.recentStats ?: RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0)
            studiedToday = request.studiedToday ?: emptySet()
            currentStreakDays = request.currentStreakDays
            workloadPercent = policy.workloadPercent()
            autoMode = policy.mode().isAuto()
            itemCap = policy.maxItems()
            nowMillis = request.nowMillis()
            settings = request.settings ?: RecordsSyncModels.Settings.kikuDefaults()
            readingExposure = request.readingExposure ?: ReadingExposureModels.ExposureIndex.EMPTY
            itemByKanji = itemIndex(request.items())
        }

        fun allKanjiMode(): Boolean = !autoMode && workloadPercent >= 100

        companion object {
            fun from(request: PlanRequest?): PlanInputs {
                val safeRequest = request ?: PlanRequest.builder(
                    null,
                    null,
                    null,
                    0,
                    null,
                    WorkloadPolicy.manual(DEFAULT_WORKLOAD_PERCENT),
                    0L
                ).build()
                return PlanInputs(safeRequest)
            }

            private fun itemIndex(items: List<RecordsStudyModels.StudyItem>?): Map<String, RecordsStudyModels.StudyItem> {
                val itemByKanji = HashMap<String, RecordsStudyModels.StudyItem>()
                for (item in items ?: emptyList()) {
                    itemByKanji[item.kanji] = item
                }
                return itemByKanji
            }
        }
    }

    private class TargetPlan(
        val ceiling: Int,
        val adjustedTarget: Int,
        val autoTarget: AutoTarget?
    )

    private class Candidate(
        val row: RecordsImportModels.DashboardRow,
        item: RecordsStudyModels.StudyItem?,
        nowMillis: Long,
        settings: RecordsSyncModels.Settings,
        exposure: ReadingExposureModels.ExposureIndex,
    ) {
        val recoveryDue: Boolean = recoveryDue(item, nowMillis)
        val exposureBoost: Double = exposure.priorityBoost(row.kanji)
        val fsrsRisk: Double = fsrsRisk(row, settings)
        val suspendedCount: Int = row.suspendedExampleCount
        val lapseScore: Int = lapseScore(row, item)
        val supportDeficit: Int = max(0, settings.matureSupportThreshold - row.matureSupportCount)
        val priorityScore: Double = row.weaknessScore +
            exposureBoost +
            fsrsRisk +
            suspendedCount * 8.0 +
            lapseScore * 2.0 +
            supportDeficit * 4.0

        companion object {
            private fun lapseScore(row: RecordsImportModels.DashboardRow, item: RecordsStudyModels.StudyItem?): Int {
                var score = if (item == null) 0 else item.lapses * 3 + max(0, 3 - item.writingLevel)
                for (example in row.examples) {
                    score += example.lapses
                }
                return score
            }

            private fun fsrsRisk(row: RecordsImportModels.DashboardRow, settings: RecordsSyncModels.Settings): Double {
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

            private fun normalizedRetrievability(value: Double?): Double? {
                if (value == null || value < 0.0) {
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

    private class AutoTarget(target: Int, val dropFound: Boolean) {
        val target: Int = max(1, target)
    }

    companion object {
        const val SETTING_KEY: String = "adaptive_load_work_percent"
        const val MODE_SETTING_KEY: String = "adaptive_load_mode"
        const val MODE_AUTO: String = "auto"
        const val MODE_MANUAL: String = "manual"
        const val DEFAULT_WORKLOAD_PERCENT: Int = 20
        const val DEFAULT_WORKLOAD_MODE: String = MODE_AUTO
        const val DEFAULT_MAX_ITEMS: Int = 5
        const val MIN_MAX_ITEMS: Int = 1
        const val MAX_MAX_ITEMS: Int = 20
        private const val AUTO_PARETO_CAP = 20

        private val CANDIDATE_ORDER: Comparator<Candidate> = compareBy<Candidate> { if (it.recoveryDue) 0 else 1 }
            .thenByDescending { it.fsrsRisk }
            .thenByDescending { it.priorityScore }
            .thenByDescending { it.exposureBoost }
            .thenByDescending { it.suspendedCount }
            .thenByDescending { it.lapseScore }
            .thenByDescending { it.supportDeficit }
            .thenByDescending { it.row.weaknessScore }
            .thenBy { it.row.kanji }

        private val AUTO_CANDIDATE_ORDER: Comparator<Candidate> = compareBy<Candidate> { if (it.recoveryDue) 0 else 1 }
            .thenByDescending { it.priorityScore }
            .then(CANDIDATE_ORDER)

        private fun candidatesFor(inputs: PlanInputs): List<Candidate> {
            val candidates = ArrayList<Candidate>()
            for (row in inputs.rows) {
                candidates.add(Candidate(row, inputs.itemByKanji[row.kanji], inputs.nowMillis, inputs.settings, inputs.readingExposure))
            }
            candidates.sortWith(if (inputs.autoMode) AUTO_CANDIDATE_ORDER else CANDIDATE_ORDER)
            return candidates
        }

        private fun emptyPlan(inputs: PlanInputs): RecordsSchedulerModels.AdaptiveLoadPlan {
            return RecordsSchedulerModels.AdaptiveLoadPlan(
                inputs.autoMode,
                inputs.workloadPercent,
                0,
                0,
                emptyList(),
                0,
                inputs.allKanjiMode(),
                "No current problem kanji."
            )
        }

        private fun allKanjiPlan(
            candidates: List<Candidate>,
            inputs: PlanInputs
        ): RecordsSchedulerModels.AdaptiveLoadPlan {
            var focus = kanjiList(candidates)
            var allIncluded = true
            if (inputs.itemCap != Int.MAX_VALUE && focus.size > inputs.itemCap) {
                focus = ArrayList(focus.subList(0, inputs.itemCap))
                allIncluded = false
            }
            val remaining = remainingCount(focus, inputs.itemByKanji, inputs.studiedToday, inputs.nowMillis)
            return RecordsSchedulerModels.AdaptiveLoadPlan(
                false,
                inputs.workloadPercent,
                focus.size,
                remaining,
                focus,
                focus.size,
                allIncluded,
                if (allIncluded) {
                    "All current problem kanji are available today."
                } else {
                    "All kanji mode is capped to today's maximum."
                }
            )
        }

        private fun targetPlanFor(candidates: List<Candidate>, inputs: PlanInputs, recoveryDue: Int): TargetPlan {
            if (inputs.autoMode) {
                val autoTarget = autoParetoTarget(candidates)
                val ceiling = min(min(candidates.size, AUTO_PARETO_CAP), inputs.itemCap)
                val adjustedTarget = adjustedAutoTarget(autoTarget.target, ceiling, inputs.stats, inputs.currentStreakDays, recoveryDue)
                return TargetPlan(ceiling, adjustedTarget, autoTarget)
            }
            val ceiling = min(targetCeiling(inputs.workloadPercent), inputs.itemCap)
            return TargetPlan(ceiling, adjustedTarget(ceiling, inputs.stats, inputs.currentStreakDays, recoveryDue), null)
        }

        private fun focusKanji(candidates: List<Candidate>, displayTarget: Int): List<String> {
            val focus = LinkedHashSet<String>()
            addDueRecovery(candidates, focus, displayTarget)
            addByPriority(candidates, focus, displayTarget)
            return ArrayList(focus)
        }

        private fun addDueRecovery(candidates: List<Candidate>, focus: LinkedHashSet<String>, displayTarget: Int) {
            for (candidate in candidates) {
                if (focus.size >= displayTarget) {
                    return
                }
                if (candidate.recoveryDue) {
                    focus.add(candidate.row.kanji)
                }
            }
        }

        private fun addByPriority(candidates: List<Candidate>, focus: LinkedHashSet<String>, displayTarget: Int) {
            for (candidate in candidates) {
                if (focus.size >= displayTarget) {
                    return
                }
                focus.add(candidate.row.kanji)
            }
        }

        private fun statusFor(targetPlan: TargetPlan, inputs: PlanInputs, recoveryDue: Int): String {
            if (inputs.autoMode) {
                return autoStatusFor(targetPlan.adjustedTarget, targetPlan.autoTarget!!, inputs.stats, recoveryDue)
            }
            return statusFor(inputs.workloadPercent, targetPlan.adjustedTarget, targetPlan.ceiling, inputs.stats, recoveryDue)
        }

        @JvmStatic
        fun normalizeWorkloadMode(mode: String?): String = WorkloadMode.fromSetting(mode).settingValue()

        @JvmStatic
        fun isAutoMode(mode: String?): Boolean = WorkloadMode.fromSetting(mode).isAuto()

        @JvmStatic
        fun snapWorkloadPercent(value: Int): Int {
            val clamped = max(0, min(100, value))
            if (clamped == 100) {
                return 100
            }
            return max(0, min(95, (clamped / 5.0f).roundToInt() * 5))
        }

        @JvmStatic
        fun targetCeiling(workloadPercent: Int): Int {
            val snapped = snapWorkloadPercent(workloadPercent)
            if (snapped >= 100) {
                return Int.MAX_VALUE
            }
            return max(1, min(20, 1 + snapped / 5))
        }

        @JvmStatic
        fun normalizeMaxItems(value: Int): Int = max(MIN_MAX_ITEMS, min(MAX_MAX_ITEMS, value))

        @JvmStatic
        fun workloadLabel(workloadPercent: Int): String {
            val snapped = snapWorkloadPercent(workloadPercent)
            if (snapped <= 0) {
                return "Very little"
            }
            if (snapped <= 20) {
                return "Pareto"
            }
            if (snapped <= 50) {
                return "Balanced"
            }
            if (snapped < 100) {
                return "More"
            }
            return "All kanji"
        }

        private fun adjustedTarget(
            ceiling: Int,
            stats: RecordsSchedulerModels.ReviewStats,
            currentStreakDays: Int,
            recoveryDue: Int
        ): Int {
            val target = if (stats.total == 0) min(3, ceiling) else max(1, (ceiling * 0.65f).roundToInt())
            return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue)
        }

        private fun adjustedAutoTarget(
            autoTarget: Int,
            ceiling: Int,
            stats: RecordsSchedulerModels.ReviewStats,
            currentStreakDays: Int,
            recoveryDue: Int
        ): Int {
            val target = if (stats.total == 0) min(3, autoTarget) else autoTarget
            return adjustedTargetFromBase(target, ceiling, stats, currentStreakDays, recoveryDue)
        }

        private fun adjustedTargetFromBase(
            baseTarget: Int,
            ceiling: Int,
            stats: RecordsSchedulerModels.ReviewStats,
            currentStreakDays: Int,
            recoveryDue: Int
        ): Int {
            var target = baseTarget
            val missRate = if (stats.total == 0) 0.0 else stats.again.toDouble() / stats.total.toDouble()
            val hardRate = if (stats.total == 0) 0.0 else stats.hard.toDouble() / stats.total.toDouble()
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
            if (recoveryDue >= target) {
                target = max(1, target - 1)
            }
            if (stats.total >= 3 &&
                currentStreakDays >= 3 &&
                missRate <= 0.10 &&
                hardRate <= 0.25 &&
                writingFailureRate <= 0.10
            ) {
                target += 1
            }
            return max(1, min(ceiling, target))
        }

        private fun autoParetoTarget(candidates: List<Candidate>): AutoTarget {
            val recoveryDue = recoveryDueCount(candidates)
            val ranked = ArrayList<Candidate>()
            for (candidate in candidates) {
                if (!candidate.recoveryDue) {
                    ranked.add(candidate)
                }
            }
            if (ranked.isEmpty()) {
                return AutoTarget(max(1, recoveryDue), false)
            }

            val fallback = min(ranked.size, targetCeiling(DEFAULT_WORKLOAD_PERCENT))
            val top = ranked[0].priorityScore
            if (top <= 0.0) {
                return AutoTarget(min(AUTO_PARETO_CAP, recoveryDue + fallback), false)
            }
            val absoluteDrop = max(4.0, top * 0.15)
            val scanLimit = min(ranked.size - 1, max(0, AUTO_PARETO_CAP - recoveryDue - 1))
            for (i in 0 until scanLimit) {
                val current = ranked[i].priorityScore
                val next = ranked[i + 1].priorityScore
                val drop = current - next
                if (next <= current * 0.70 && drop >= absoluteDrop) {
                    return AutoTarget(max(1, min(AUTO_PARETO_CAP, recoveryDue + i + 1)), true)
                }
            }
            return AutoTarget(max(1, min(AUTO_PARETO_CAP, recoveryDue + fallback)), false)
        }

        private fun recoveryDueCount(candidates: List<Candidate>): Int {
            var count = 0
            for (candidate in candidates) {
                if (candidate.recoveryDue) {
                    count++
                }
            }
            return count
        }

        private fun remainingCount(
            focusKanji: List<String>,
            itemByKanji: Map<String, RecordsStudyModels.StudyItem>,
            studiedToday: Set<String>,
            nowMillis: Long
        ): Int {
            var remaining = 0
            for (kanji in focusKanji) {
                val item = itemByKanji[kanji]
                if (!studiedToday.contains(kanji) || recoveryDue(item, nowMillis)) {
                    remaining++
                }
            }
            return remaining
        }

        private fun kanjiList(candidates: List<Candidate>): List<String> {
            val out = ArrayList<String>()
            for (candidate in candidates) {
                out.add(candidate.row.kanji)
            }
            return out
        }

        private fun statusFor(
            workloadPercent: Int,
            target: Int,
            ceiling: Int,
            stats: RecordsSchedulerModels.ReviewStats,
            recoveryDue: Int
        ): String {
            if (workloadPercent <= 0) {
                return "Very little work today: one focused kanji unless recovery is already due."
            }
            if (stats.total == 0) {
                return "Pareto focus starts small until Kani has review history."
            }
            if (recoveryDue >= target) {
                return "Due recovery fills today's focus, so new kanji wait."
            }
            if (target >= ceiling) {
                return "Recent reviews are steady, so Kani can use the full focus range."
            }
            return "Adaptive focus is set from recent misses, hard ratings, and writing results."
        }

        private fun autoStatusFor(
            target: Int,
            autoTarget: AutoTarget,
            stats: RecordsSchedulerModels.ReviewStats,
            recoveryDue: Int
        ): String {
            if (recoveryDue >= target) {
                return "Due recovery fills today's auto Pareto focus, so new kanji wait."
            }
            if (stats.total == 0) {
                return if (autoTarget.dropFound) {
                    "Auto Pareto found today's drop-off, then starts small until Kani has review history."
                } else {
                    "Auto Pareto starts small until Kani has review history."
                }
            }
            if (!autoTarget.dropFound) {
                return "Auto Pareto did not find a sharp drop-off, so Kani uses the small Pareto focus."
            }
            if (target < autoTarget.target) {
                return "Auto Pareto found today's drop-off, then recent review strain lowered the focus."
            }
            if (target > autoTarget.target) {
                return "Auto Pareto found today's drop-off and your steady streak allows one extra kanji."
            }
            return "Auto Pareto uses today's problem-kanji drop-off."
        }

        private fun recoveryDue(item: RecordsStudyModels.StudyItem?, nowMillis: Long): Boolean {
            if (item == null || "retired" == item.state) {
                return false
            }
            if ("learning" == item.state) {
                return true
            }
            return item.totalReviews > 0 && item.dueAtMillis <= nowMillis
        }
    }
}
