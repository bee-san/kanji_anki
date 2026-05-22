package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels

internal fun MainActivityStudy.renderComposeStudyRoute(content: @Composable () -> Unit) {
    initializeSessionProgressTarget(activeStudyPlan)
    val progress = studySessionTracker.topBarProgress(activeSession != null, continueAllKanjiSession)
    composeRoute(MainActivityBase.NAV_STUDY) {
        Column {
            StudyTopBar(
                completed = progress.completed,
                target = progress.target,
                fraction = progress.fraction,
                onClose = ::renderHome,
                onSettings = ::renderSettings,
            )
            content()
        }
    }
}

internal fun MainActivityStudy.renderComposeStudyRouteWithActionBar(
    beforeContent: () -> Unit = {},
    content: @Composable () -> Unit,
    actionBar: @Composable () -> Unit,
) {
    initializeSessionProgressTarget(activeStudyPlan)
    val progress = studySessionTracker.topBarProgress(activeSession != null, continueAllKanjiSession)
    composeRouteWithActionBar(
        selected = MainActivityBase.NAV_STUDY,
        beforeContent = beforeContent,
        content = {
            Column {
                StudyTopBar(
                    completed = progress.completed,
                    target = progress.target,
                    fraction = progress.fraction,
                    onClose = ::renderHome,
                    onSettings = ::renderSettings,
                )
                content()
            }
        },
        actionBar = actionBar,
    )
}

internal fun MainActivityStudy.renderLegacyStudyRoute() {
    base(MainActivityBase.NAV_STUDY)
}

internal fun MainActivityStudy.renderFlashcardStudyRoute(session: RecordsSchedulerModels.StudySession) {
    renderComposeFlashcardSession(session)
}

internal fun MainActivityStudy.renderSimilarWritingRepairRoute(
    repair: RecordsImportModels.SimilarKanjiWritingRepair,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    now: Long,
) {
    renderSimilarWritingRepair(repair, plan, now)
}
