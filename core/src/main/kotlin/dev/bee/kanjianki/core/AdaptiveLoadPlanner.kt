package dev.bee.kanjianki.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Plans today's adaptive focus set.
 *
 * The planner is a thin orchestrator: candidate scoring lives in
 * [AdaptiveLoadCandidate], focus sizing and Pareto mass selection in
 * [AdaptiveLoadFocusPolicy], and user-facing status text in
 * [AdaptiveLoadStatusFormatter].
 */
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
        val focusKanji = AdaptiveLoadFocusPolicy.focusKanji(
            candidates,
            min(inputs.itemCap, max(targetPlan.adjustedTarget, cappedRecoveryDue)),
        )
        val overflowDue = max(0, recoveryDue - focusKanji.size)
        val remaining = remainingCount(focusKanji, inputs.itemByKanji, inputs.studiedToday, inputs.nowMillis)
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            inputs.autoMode,
            inputs.workloadPercent,
            focusKanji.size,
            remaining,
            focusKanji,
            max(0, focusKanji.size - cappedRecoveryDue),
            false,
            statusFor(targetPlan, inputs, recoveryDue, overflowDue),
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
        val autoTarget: AdaptiveLoadFocusPolicy.AutoTarget?
    )

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

        private fun candidatesFor(inputs: PlanInputs): List<AdaptiveLoadCandidate> {
            val candidates = ArrayList<AdaptiveLoadCandidate>()
            for (row in inputs.rows) {
                candidates.add(
                    AdaptiveLoadCandidate(row, inputs.itemByKanji[row.kanji], inputs.nowMillis, inputs.settings, inputs.readingExposure),
                )
            }
            candidates.sortWith(if (inputs.autoMode) AdaptiveLoadCandidate.AUTO_ORDER else AdaptiveLoadCandidate.MANUAL_ORDER)
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
            candidates: List<AdaptiveLoadCandidate>,
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

        private fun targetPlanFor(candidates: List<AdaptiveLoadCandidate>, inputs: PlanInputs, recoveryDue: Int): TargetPlan {
            if (inputs.autoMode) {
                val autoTarget = AdaptiveLoadFocusPolicy.autoParetoTarget(candidates)
                val ceiling = min(min(candidates.size, AdaptiveLoadFocusPolicy.AUTO_PARETO_CAP), inputs.itemCap)
                val adjustedTarget = AdaptiveLoadFocusPolicy.adjustedAutoTarget(
                    autoTarget.target,
                    ceiling,
                    inputs.stats,
                    inputs.currentStreakDays,
                    recoveryDue,
                )
                return TargetPlan(ceiling, adjustedTarget, autoTarget)
            }
            val ceiling = min(targetCeiling(inputs.workloadPercent), inputs.itemCap)
            return TargetPlan(
                ceiling,
                AdaptiveLoadFocusPolicy.adjustedTarget(ceiling, inputs.stats, inputs.currentStreakDays, recoveryDue),
                null,
            )
        }

        private fun statusFor(targetPlan: TargetPlan, inputs: PlanInputs, recoveryDue: Int, overflowDue: Int): String {
            if (inputs.autoMode) {
                return AdaptiveLoadStatusFormatter.autoStatus(
                    targetPlan.adjustedTarget,
                    targetPlan.autoTarget!!,
                    inputs.stats,
                    recoveryDue,
                    overflowDue,
                )
            }
            return AdaptiveLoadStatusFormatter.manualStatus(
                inputs.workloadPercent,
                targetPlan.adjustedTarget,
                targetPlan.ceiling,
                inputs.stats,
                recoveryDue,
                overflowDue,
            )
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

        private fun recoveryDueCount(candidates: List<AdaptiveLoadCandidate>): Int {
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
                if (!studiedToday.contains(kanji) || AdaptiveLoadCandidate.isRecoveryDue(item, nowMillis)) {
                    remaining++
                }
            }
            return remaining
        }

        private fun kanjiList(candidates: List<AdaptiveLoadCandidate>): List<String> {
            val out = ArrayList<String>()
            for (candidate in candidates) {
                out.add(candidate.row.kanji)
            }
            return out
        }
    }
}
