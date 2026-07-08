package dev.bee.kanjianki

import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecommendedAction

internal fun homeTodayPlanModel(
    plan: DailyStudyPlan,
    onStudy: () -> Unit,
    onSync: () -> Unit,
): HomeTodayPlanModel {
    val actionLabel = when (plan.recommendedAction) {
        RecommendedAction.STUDY_NOW,
        RecommendedAction.STUDY_ONCE_FOR_STREAK -> HomeTextCopy.studyNowLabel()
        RecommendedAction.SYNC_FIRST -> HomeTextCopy.syncAnkiDroidLabel()
        RecommendedAction.WAIT_UNTIL_LATER,
        RecommendedAction.NOTHING_USEFUL_NOW -> null
    }
    val onClick = when (plan.recommendedAction) {
        RecommendedAction.STUDY_NOW,
        RecommendedAction.STUDY_ONCE_FOR_STREAK -> onStudy
        RecommendedAction.SYNC_FIRST -> onSync
        RecommendedAction.WAIT_UNTIL_LATER,
        RecommendedAction.NOTHING_USEFUL_NOW -> null
    }
    val summary = HomeTextCopy.todayPlanSummary(plan)
    val details = buildList {
        // Reason lines that just echo the summary (e.g. "sync needed before Kani can
        // judge progress" under the identical headline) add nothing but visual noise,
        // so only keep reasons the summary does not already state.
        for (reason in plan.reasons) {
            if (!summary.contains(reason, ignoreCase = true)) {
                add(reason)
            }
        }
        if (plan.recommendedAction == RecommendedAction.WAIT_UNTIL_LATER && plan.nextUsefulReminderAtMillis > 0L) {
            add(HomeTextCopy.nextUsefulTimeLabel(plan.nextUsefulReminderAtMillis))
        }
    }
    return HomeTodayPlanModel(
        title = HomeTextCopy.todayPlanTitle(),
        summary = summary,
        details = details,
        actionLabel = actionLabel,
        onClick = onClick,
    )
}
