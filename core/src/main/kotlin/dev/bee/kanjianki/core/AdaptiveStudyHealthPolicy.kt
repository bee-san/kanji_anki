package dev.bee.kanjianki.core

import java.util.Collections
import java.util.EnumMap
import java.util.LinkedHashMap

/**
 * Summarises the persisted routing-v2 state without inventing another queue or
 * replaying review history. Core ownership is a distribution; repair and risk
 * values are overlays, so an item in repair still belongs to exactly one core.
 */
object AdaptiveStudyHealthPolicy {
    const val STUCK_REPAIR_ATTEMPTS: Int = 6

    @JvmStatic
    fun summarize(
        items: List<ItemEvidence?>?,
        escalationThreshold: Int,
    ): Metric {
        val accumulator = Accumulator(escalationThreshold.coerceAtLeast(1))
        items.orEmpty().forEach(accumulator::add)
        return accumulator.metric()
    }

    @JvmStatic
    fun summarizeStudyItems(
        items: List<RecordsStudyModels.StudyItem?>?,
        escalationThreshold: Int,
    ): Metric = summarize(
        items.orEmpty().map { item ->
            item?.let {
                ItemEvidence(
                    state = it.state,
                    phase = it.phase,
                    routingVersion = it.routingVersion,
                    adaptiveRouteStateJson = it.adaptiveRouteStateJson,
                    contextualReadingConsecutivePasses = it.wordReadingMemory.consecutivePasses,
                )
            }
        },
        escalationThreshold,
    )

    class ItemEvidence(
        state: String?,
        phase: RecordsBase.SchedulerPhase?,
        routingVersion: Int,
        adaptiveRouteStateJson: String?,
        contextualReadingConsecutivePasses: Int,
    ) {
        @JvmField val state: String = state.orEmpty()
        @JvmField val phase: RecordsBase.SchedulerPhase = phase ?: RecordsBase.SchedulerPhase.NEW_LEARNING
        @JvmField val routingVersion: Int = routingVersion.coerceAtLeast(0)
        @JvmField val adaptiveRouteStateJson: String = adaptiveRouteStateJson.orEmpty()
        @JvmField val contextualReadingConsecutivePasses: Int = contextualReadingConsecutivePasses.coerceAtLeast(0)
    }

    class Metric internal constructor(
        coreCounts: Map<CoreSkill, Int>,
        activeRepairsByTask: Map<String, Int>,
        activeRepairsByFailure: Map<FailureKind, Int>,
        @JvmField val totalAdaptiveItems: Int,
        @JvmField val contextualCompleteCount: Int,
        @JvmField val activeRepairCount: Int,
        @JvmField val revalidationPendingCount: Int,
        @JvmField val recentCoreMissCount: Int,
        @JvmField val escalationRiskCount: Int,
        @JvmField val stuckRepairCount: Int,
        @JvmField val malformedStateCount: Int,
    ) {
        @JvmField val coreCounts: Map<CoreSkill, Int> = immutableCoreCounts(coreCounts)
        @JvmField val activeRepairsByTask: Map<String, Int> = immutablePositiveStringCounts(activeRepairsByTask)
        @JvmField val activeRepairsByFailure: Map<FailureKind, Int> = immutableFailureCounts(activeRepairsByFailure)

        fun countFor(skill: CoreSkill?): Int = coreCounts[skill] ?: 0

        fun repairCountFor(taskType: String?): Int = activeRepairsByTask[taskType] ?: 0

        fun failureCountFor(kind: FailureKind?): Int = activeRepairsByFailure[kind] ?: 0

        companion object {
            @JvmStatic
            fun empty(): Metric = Metric(
                coreCounts = emptyMap(),
                activeRepairsByTask = emptyMap(),
                activeRepairsByFailure = emptyMap(),
                totalAdaptiveItems = 0,
                contextualCompleteCount = 0,
                activeRepairCount = 0,
                revalidationPendingCount = 0,
                recentCoreMissCount = 0,
                escalationRiskCount = 0,
                stuckRepairCount = 0,
                malformedStateCount = 0,
            )
        }
    }

    private class Accumulator(private val escalationThreshold: Int) {
        private val coreCounts = EnumMap<CoreSkill, Int>(CoreSkill::class.java)
        private val repairTasks = LinkedHashMap<String, Int>()
        private val repairFailures = EnumMap<FailureKind, Int>(FailureKind::class.java)
        private var total = 0
        private var contextualComplete = 0
        private var activeRepairs = 0
        private var revalidations = 0
        private var recentMisses = 0
        private var escalationRisks = 0
        private var stuckRepairs = 0
        private var malformedStates = 0

        init {
            CoreSkill.entries.forEach { coreCounts[it] = 0 }
            FailureKind.entries.forEach { repairFailures[it] = 0 }
        }

        fun add(item: ItemEvidence?) {
            if (item == null || item.state == StudyLadderRules.STATE_RETIRED ||
                item.routingVersion < AdaptiveStudyItemPolicy.ROUTING_VERSION
            ) {
                return
            }
            val route = AdaptiveRouteStateCodec.decode(item.adaptiveRouteStateJson)
            if (route == null) {
                malformedStates += 1
                return
            }
            total += 1
            coreCounts.increment(route.activeCore)
            val repairTask = route.activeRepairTask()
            val repairActive = repairTask != null
            if (repairActive) {
                activeRepairs += 1
                repairTasks.increment(repairTask)
                val failure = route.recurringFailure ?: route.answerEvidence?.failureKind ?: FailureKind.UNKNOWN
                repairFailures.increment(failure)
                val escalationFloor = (escalationThreshold - 1).coerceAtLeast(1)
                if (route.recurringFailureCount >= escalationFloor || route.activeRepairTasks.size > 1) {
                    escalationRisks += 1
                }
                if (route.repairAttemptCount >= STUCK_REPAIR_ATTEMPTS) {
                    stuckRepairs += 1
                }
            }
            if (route.revalidationPending) {
                revalidations += 1
            }
            if (repairActive || route.revalidationPending) {
                recentMisses += 1
            }
            if (route.activeCore == CoreSkill.CONTEXTUAL_READING &&
                !repairActive &&
                !route.revalidationPending &&
                item.phase == RecordsBase.SchedulerPhase.REVIEW &&
                route.contextualReadingReviewCount > 0 &&
                item.contextualReadingConsecutivePasses > 0
            ) {
                contextualComplete += 1
            }
        }

        fun metric(): Metric = Metric(
            coreCounts = coreCounts,
            activeRepairsByTask = repairTasks,
            activeRepairsByFailure = repairFailures,
            totalAdaptiveItems = total,
            contextualCompleteCount = contextualComplete,
            activeRepairCount = activeRepairs,
            revalidationPendingCount = revalidations,
            recentCoreMissCount = recentMisses,
            escalationRiskCount = escalationRisks,
            stuckRepairCount = stuckRepairs,
            malformedStateCount = malformedStates,
        )
    }

    private fun <K> MutableMap<K, Int>.increment(key: K) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun immutableCoreCounts(counts: Map<CoreSkill, Int>): Map<CoreSkill, Int> {
        val normalized = EnumMap<CoreSkill, Int>(CoreSkill::class.java)
        CoreSkill.entries.forEach { normalized[it] = (counts[it] ?: 0).coerceAtLeast(0) }
        return Collections.unmodifiableMap(normalized)
    }

    private fun immutableFailureCounts(counts: Map<FailureKind, Int>): Map<FailureKind, Int> {
        val normalized = EnumMap<FailureKind, Int>(FailureKind::class.java)
        FailureKind.entries.forEach { normalized[it] = (counts[it] ?: 0).coerceAtLeast(0) }
        return Collections.unmodifiableMap(normalized)
    }

    private fun immutablePositiveStringCounts(counts: Map<String, Int>): Map<String, Int> {
        val normalized = LinkedHashMap<String, Int>()
        counts.entries
            .filter { it.key.isNotBlank() && it.value > 0 }
            .sortedBy { it.key }
            .forEach { normalized[it.key] = it.value }
        return Collections.unmodifiableMap(normalized)
    }
}
