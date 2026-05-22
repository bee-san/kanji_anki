package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

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

internal fun MainActivityStudy.renderLegacyStudyRoute() {
    base(MainActivityBase.NAV_STUDY)
}
