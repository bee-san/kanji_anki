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
    val details = buildList {
        addAll(plan.reasons)
        if (plan.recommendedAction == RecommendedAction.WAIT_UNTIL_LATER && plan.nextUsefulReminderAtMillis > 0L) {
            add(HomeTextCopy.nextUsefulTimeLabel(plan.nextUsefulReminderAtMillis))
        }
    }
    return HomeTodayPlanModel(
        title = HomeTextCopy.todayPlanTitle(),
        summary = HomeTextCopy.todayPlanSummary(plan),
        details = details,
        actionLabel = actionLabel,
        onClick = onClick,
    )
}
