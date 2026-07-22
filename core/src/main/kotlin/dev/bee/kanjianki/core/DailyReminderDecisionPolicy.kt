package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.Locale

enum class ReminderFamily {
    DUE,
    STREAK,
    SYNC,
}

data class DailyReminderDecisionRequest(
    val plan: DailyStudyPlan,
    val nowMillis: Long,
    val quietHoursStartMinuteOfDay: Int? = null,
    val quietHoursLeadMinutes: Int = 60,
    val dismissedFamiliesToday: Set<ReminderFamily> = emptySet(),
    val dueRemindersShownToday: Int = 0,
    val streakRemindersShownToday: Int = 0,
    val syncRemindersShownToday: Int = 0,
    val dueReminderCapPerDay: Int = 2,
    val streakReminderCapPerDay: Int = 1,
    val syncReminderCapPerDay: Int = 1,
)

data class DailyReminderDecision(
    val shouldSchedule: Boolean,
    val family: ReminderFamily?,
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
    val reasonIds: List<String>,
    val humanReason: String,
)

object DailyReminderDecisionPolicy {
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MILLIS_PER_MINUTE = 60_000L

    @JvmStatic
    fun decide(request: DailyReminderDecisionRequest): DailyReminderDecision {
        val nowMillis = request.nowMillis.coerceAtLeast(0L)
        val action = effectiveAction(request.plan)
        val family = familyFor(action)
        val title = titleFor(action)
        val baseReasons = mutableListOf<String>().apply {
            addAll(request.plan.reasons.filter { it.isNotBlank() })
            if (isEmpty()) {
                add(fallbackReason(action, request.plan))
            }
        }
        val reasonIds = mutableListOf<String>().apply {
            addAll(reasonIdsFor(action, request.plan))
        }

        if (family == null) {
            return decision(
                shouldSchedule = false,
                family = null,
                triggerAtMillis = 0L,
                title = title,
                body = bodyFor(action, request.plan, 0L),
                reasonIds = reasonIds,
                baseReasons = baseReasons,
            )
        }

        if (family in request.dismissedFamiliesToday) {
            reasonIds += "reminder:dismissed-today"
            baseReasons += "dismissed today"
            return decision(
                shouldSchedule = false,
                family = family,
                triggerAtMillis = 0L,
                title = title,
                body = bodyFor(action, request.plan, 0L),
                reasonIds = reasonIds,
                baseReasons = baseReasons,
            )
        }

        if (capReached(family, request)) {
            reasonIds += capReasonId(family)
            baseReasons += capHumanReason(family)
            return decision(
                shouldSchedule = false,
                family = family,
                triggerAtMillis = 0L,
                title = title,
                body = bodyFor(action, request.plan, 0L),
                reasonIds = reasonIds,
                baseReasons = baseReasons,
            )
        }

        val plannedTriggerAtMillis = maxOf(nowMillis, request.plan.nextUsefulReminderAtMillis)
        val triggerAtMillis = adjustForQuietHours(
            nowMillis = nowMillis,
            plannedTriggerAtMillis = plannedTriggerAtMillis,
            quietHoursStartMinuteOfDay = request.quietHoursStartMinuteOfDay,
            quietHoursLeadMinutes = request.quietHoursLeadMinutes,
        )
        if (quietHoursSoon(nowMillis, plannedTriggerAtMillis, request.quietHoursStartMinuteOfDay, request.quietHoursLeadMinutes)) {
            reasonIds += "reminder:quiet-hours-soon"
            baseReasons += "quiet hours soon"
        }

        return decision(
            shouldSchedule = true,
            family = family,
            triggerAtMillis = triggerAtMillis,
            title = title,
            body = bodyFor(action, request.plan, triggerAtMillis),
            reasonIds = reasonIds,
            baseReasons = baseReasons,
        )
    }

    private fun decision(
        shouldSchedule: Boolean,
        family: ReminderFamily?,
        triggerAtMillis: Long,
        title: String,
        body: String,
        reasonIds: List<String>,
        baseReasons: List<String>,
    ): DailyReminderDecision {
        return DailyReminderDecision(
            shouldSchedule = shouldSchedule,
            family = family,
            triggerAtMillis = triggerAtMillis,
            title = title,
            body = body,
            reasonIds = reasonIds.distinct(),
            humanReason = baseReasons.joinToString(" · "),
        )
    }

    private fun effectiveAction(plan: DailyStudyPlan): RecommendedAction {
        return when (plan.recommendedAction) {
            RecommendedAction.STUDY_NOW -> {
                when {
                    plan.dueNow > 0 || plan.newProblemKanjiAvailable > 0 -> RecommendedAction.STUDY_NOW
                    plan.dueLater > 0 -> RecommendedAction.WAIT_UNTIL_LATER
                    else -> RecommendedAction.NOTHING_USEFUL_NOW
                }
            }
            RecommendedAction.STUDY_ONCE_FOR_STREAK -> {
                if (plan.streakStatus == StreakStatus.SAFE) {
                    RecommendedAction.NOTHING_USEFUL_NOW
                } else {
                    RecommendedAction.STUDY_ONCE_FOR_STREAK
                }
            }
            RecommendedAction.WAIT_UNTIL_LATER -> {
                if (plan.dueLater > 0) {
                    RecommendedAction.WAIT_UNTIL_LATER
                } else {
                    RecommendedAction.NOTHING_USEFUL_NOW
                }
            }
            RecommendedAction.SYNC_FIRST -> {
                if (plan.syncStatus == SyncStatus.CURRENT) {
                    RecommendedAction.NOTHING_USEFUL_NOW
                } else {
                    RecommendedAction.SYNC_FIRST
                }
            }
            RecommendedAction.NOTHING_USEFUL_NOW -> RecommendedAction.NOTHING_USEFUL_NOW
        }
    }

    private fun familyFor(action: RecommendedAction): ReminderFamily? {
        return when (action) {
            RecommendedAction.STUDY_NOW,
            RecommendedAction.WAIT_UNTIL_LATER,
                -> ReminderFamily.DUE

            RecommendedAction.STUDY_ONCE_FOR_STREAK -> ReminderFamily.STREAK
            RecommendedAction.SYNC_FIRST -> ReminderFamily.SYNC
            RecommendedAction.NOTHING_USEFUL_NOW -> null
        }
    }

    private fun titleFor(action: RecommendedAction): String {
        return when (action) {
            RecommendedAction.STUDY_NOW -> "Study now"
            RecommendedAction.STUDY_ONCE_FOR_STREAK -> "Keep your streak"
            RecommendedAction.WAIT_UNTIL_LATER -> "Study later"
            RecommendedAction.SYNC_FIRST -> "Sync Kani"
            RecommendedAction.NOTHING_USEFUL_NOW -> "Nothing useful now"
        }
    }

    private fun bodyFor(action: RecommendedAction, plan: DailyStudyPlan, triggerAtMillis: Long): String {
        val reason = humanBodyReason(plan.reasons.firstOrNull()?.takeIf { it.isNotBlank() } ?: fallbackReason(action, plan))
        return when (action) {
            RecommendedAction.STUDY_NOW -> {
                val workLine = when {
                    plan.dueNow > 0 -> countPhrase(plan.dueNow, "due now")
                    plan.newProblemKanjiAvailable > 0 -> countPhrase(
                        plan.newProblemKanjiAvailable,
                        "new problem kanji available",
                    )
                    else -> reason
                }
                "$workLine. Open Kani to study now."
            }
            RecommendedAction.STUDY_ONCE_FOR_STREAK -> {
                "$reason. Open Kani now to keep your streak alive."
            }
            RecommendedAction.WAIT_UNTIL_LATER -> {
                val timeLabel = if (triggerAtMillis > 0L) {
                    formatTimeLabel(triggerAtMillis)
                } else {
                    "later"
                }
                "$reason. Next useful time: $timeLabel."
            }
            RecommendedAction.SYNC_FIRST -> {
                "$reason. Open Kani and sync now."
            }
            RecommendedAction.NOTHING_USEFUL_NOW -> "$reason."
        }
    }

    private fun humanBodyReason(reason: String): String {
        return reason.replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(Locale.ROOT)
            } else {
                char.toString()
            }
        }
    }

    private fun reasonIdsFor(action: RecommendedAction, plan: DailyStudyPlan): List<String> {
        return when (action) {
            RecommendedAction.STUDY_NOW -> {
                buildList {
                    if (plan.dueNow > 0) {
                        add("plan:due-now")
                    }
                    if (plan.newProblemKanjiAvailable > 0) {
                        add("plan:new-problem-kanji")
                    }
                }
            }
            RecommendedAction.STUDY_ONCE_FOR_STREAK -> listOf("plan:streak-needs-one-review")
            RecommendedAction.WAIT_UNTIL_LATER -> listOf("plan:due-later-cluster")
            RecommendedAction.SYNC_FIRST -> listOf("plan:sync-needed")
            RecommendedAction.NOTHING_USEFUL_NOW -> listOf("plan:nothing-useful-now")
        }
    }

    private fun capReached(family: ReminderFamily, request: DailyReminderDecisionRequest): Boolean {
        return when (family) {
            ReminderFamily.DUE -> request.dueReminderCapPerDay > 0 && request.dueRemindersShownToday >= request.dueReminderCapPerDay
            ReminderFamily.STREAK -> request.streakReminderCapPerDay > 0 && request.streakRemindersShownToday >= request.streakReminderCapPerDay
            ReminderFamily.SYNC -> request.syncReminderCapPerDay > 0 && request.syncRemindersShownToday >= request.syncReminderCapPerDay
        }
    }

    private fun capReasonId(family: ReminderFamily): String {
        return when (family) {
            ReminderFamily.DUE -> "reminder:due-cap-reached"
            ReminderFamily.STREAK -> "reminder:streak-cap-reached"
            ReminderFamily.SYNC -> "reminder:sync-cap-reached"
        }
    }

    private fun capHumanReason(family: ReminderFamily): String {
        return when (family) {
            ReminderFamily.DUE -> "due reminder cap reached"
            ReminderFamily.STREAK -> "streak reminder cap reached"
            ReminderFamily.SYNC -> "sync reminder cap reached"
        }
    }

    private fun fallbackReason(action: RecommendedAction, plan: DailyStudyPlan): String {
        return when (action) {
            RecommendedAction.STUDY_NOW -> {
                when {
                    plan.dueNow > 0 -> countPhrase(plan.dueNow, "due now")
                    plan.newProblemKanjiAvailable > 0 -> countPhrase(plan.newProblemKanjiAvailable, "new problem kanji available")
                    else -> "study now"
                }
            }
            RecommendedAction.STUDY_ONCE_FOR_STREAK -> "streak needs one review"
            RecommendedAction.WAIT_UNTIL_LATER -> countPhrase(plan.dueLater, "learning repeat later", "learning repeats later")
            RecommendedAction.SYNC_FIRST -> syncReason(plan.syncStatus)
            RecommendedAction.NOTHING_USEFUL_NOW -> "nothing useful now"
        }
    }

    private fun syncReason(syncStatus: SyncStatus): String {
        return when (syncStatus) {
            SyncStatus.CURRENT -> "sync is current"
            SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS -> "sync needed before Kani can judge progress"
            SyncStatus.NO_MANUAL_SYNC_YET -> "no manual sync yet"
            SyncStatus.UNKNOWN -> "sync freshness unknown"
        }
    }

    private fun countPhrase(count: Int, singular: String, plural: String = singular): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }

    private fun quietHoursSoon(
        nowMillis: Long,
        plannedTriggerAtMillis: Long,
        quietHoursStartMinuteOfDay: Int?,
        quietHoursLeadMinutes: Int,
    ): Boolean {
        val quietBoundary = quietHoursBoundaryMillis(nowMillis, quietHoursStartMinuteOfDay, quietHoursLeadMinutes)
        if (quietBoundary == null) {
            return false
        }
        return nowMillis >= quietBoundary || plannedTriggerAtMillis > quietBoundary
    }

    private fun adjustForQuietHours(
        nowMillis: Long,
        plannedTriggerAtMillis: Long,
        quietHoursStartMinuteOfDay: Int?,
        quietHoursLeadMinutes: Int,
    ): Long {
        val quietBoundary = quietHoursBoundaryMillis(nowMillis, quietHoursStartMinuteOfDay, quietHoursLeadMinutes)
            ?: return plannedTriggerAtMillis
        return maxOf(nowMillis, minOf(plannedTriggerAtMillis, quietBoundary))
    }

    private fun quietHoursBoundaryMillis(
        nowMillis: Long,
        quietHoursStartMinuteOfDay: Int?,
        quietHoursLeadMinutes: Int,
    ): Long? {
        val startMinuteOfDay = quietHoursStartMinuteOfDay ?: return null
        if (quietHoursLeadMinutes <= 0 || startMinuteOfDay !in 0 until MINUTES_PER_DAY) {
            return null
        }
        val startCalendar = Calendar.getInstance()
        startCalendar.timeInMillis = nowMillis
        startCalendar.set(Calendar.HOUR_OF_DAY, startMinuteOfDay / 60)
        startCalendar.set(Calendar.MINUTE, startMinuteOfDay % 60)
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)
        if (startCalendar.timeInMillis <= nowMillis) {
            startCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return startCalendar.timeInMillis - quietHoursLeadMinutes * MILLIS_PER_MINUTE
    }

    private fun formatTimeLabel(millis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        return String.format(
            Locale.ROOT,
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }
}
