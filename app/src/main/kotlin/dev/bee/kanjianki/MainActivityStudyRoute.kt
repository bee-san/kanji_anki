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

internal fun MainActivityStudy.renderLegacyStudyRoute() {
    base(MainActivityBase.NAV_STUDY)
}

internal fun MainActivityStudy.renderLegacyFlashcardRoute(session: RecordsSchedulerModels.StudySession) {
    renderLegacyStudyRoute()
    renderFlashcardSession(session)
}

internal fun MainActivityStudy.renderLegacyWritingRoute(session: RecordsSchedulerModels.StudySession) {
    renderLegacyStudyRoute()
    renderWritingSession(session)
}

internal fun MainActivityStudy.renderLegacySimilarWritingRepairRoute(
    repair: RecordsImportModels.SimilarKanjiWritingRepair,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    now: Long,
) {
    renderLegacyStudyRoute()
    renderSimilarWritingRepair(repair, plan, now)
}
