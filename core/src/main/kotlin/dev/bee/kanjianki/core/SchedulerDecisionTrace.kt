package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections

class SchedulerDecisionTrace(
    operation: String?,
    @JvmField val nowMillis: Long,
    selected: SchedulerDecisionTraceCandidate?,
    candidates: List<SchedulerDecisionTraceCandidate>?,
    skipped: List<SchedulerDecisionTraceCandidate>?,
    transition: SchedulerReviewTransitionTrace?,
    fsrsCalls: List<SchedulerFsrsCallTrace>?,
) {
    @JvmField val operation: String = operation ?: ""
    @JvmField val selected: SchedulerDecisionTraceCandidate? = selected
    @JvmField val candidates: List<SchedulerDecisionTraceCandidate> = immutable(candidates)
    @JvmField val skipped: List<SchedulerDecisionTraceCandidate> = immutable(skipped)
    @JvmField val transition: SchedulerReviewTransitionTrace? = transition
    @JvmField val fsrsCalls: List<SchedulerFsrsCallTrace> = immutable(fsrsCalls)

    companion object {
        private fun <T> immutable(values: List<T>?): List<T> {
            return Collections.unmodifiableList(ArrayList(values ?: emptyList()))
        }
    }
}

class SchedulerDecisionTraceCandidate(
    kanji: String?,
    taskType: String?,
    @JvmField val rung: RecordsBase.LadderRung,
    @JvmField val phase: RecordsBase.SchedulerPhase,
    @JvmField val dueAtMillis: Long,
    reasonCodes: List<String>?,
    @JvmField val familyKey: String,
    @JvmField val weaknessScore: Int,
) {
    @JvmField val kanji: String = kanji ?: ""
    @JvmField val taskType: String = taskType ?: ""
    @JvmField val reasonCodes: List<String> = Collections.unmodifiableList(ArrayList(reasonCodes ?: emptyList()))
}

class SchedulerReviewTransitionTrace(
    rating: String?,
    @JvmField val beforeRung: RecordsBase.LadderRung,
    @JvmField val afterRung: RecordsBase.LadderRung,
    movementReason: String?,
    reasonCodes: List<String>?,
) {
    @JvmField val rating: String = rating ?: ""
    @JvmField val movementReason: String = movementReason ?: ""
    @JvmField val reasonCodes: List<String> = Collections.unmodifiableList(ArrayList(reasonCodes ?: emptyList()))
}

class SchedulerFsrsCallTrace(
    callType: String?,
    rating: String?,
    @JvmField val outputIntervalDays: Int,
) {
    @JvmField val callType: String = callType ?: ""
    @JvmField val rating: String = rating ?: ""
}

class SchedulerTracedReviewResult(
    @JvmField val result: RecordsSchedulerModels.ReviewResult,
    @JvmField val trace: SchedulerDecisionTrace,
)

object SchedulerTraceFormatter {
    @JvmStatic
    fun userExplanation(trace: SchedulerDecisionTrace?): String {
        val selected = trace?.selected ?: return "No study card is ready."
        return "Selected ${selected.kanji} for ${selected.taskType} (${selected.phase.wireName()})."
    }

    @JvmStatic
    fun developerExplanation(trace: SchedulerDecisionTrace?): String {
        if (trace == null) {
            return "scheduler_trace unavailable"
        }
        val parts = ArrayList<String>()
        parts.add("operation=${trace.operation}")
        parts.add("now=${trace.nowMillis}")
        trace.selected?.let {
            parts.add("selected=${it.kanji}:${it.taskType}:${it.reasonCodes.joinToString("|")}")
        }
        if (trace.skipped.isNotEmpty()) {
            parts.add("skipped=" + trace.skipped.joinToString(",") { it.kanji + ":" + it.reasonCodes.joinToString("|") })
        }
        trace.transition?.let {
            parts.add("transition=${it.beforeRung.wireName()}->${it.afterRung.wireName()}:${it.movementReason}:${it.reasonCodes.joinToString("|")}")
        }
        if (trace.fsrsCalls.isNotEmpty()) {
            parts.add("fsrs=" + trace.fsrsCalls.joinToString(",") { it.callType + ":" + it.rating + ":" + it.outputIntervalDays })
        }
        return parts.joinToString("; ")
    }
}
