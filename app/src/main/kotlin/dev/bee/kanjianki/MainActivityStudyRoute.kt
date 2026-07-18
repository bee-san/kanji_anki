package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun MainActivityStudy.renderComposeStudyRoute(
    routeSnapshot: StudyRouteSnapshot,
    studySessionActive: Boolean = false,
    content: @Composable () -> Unit,
) {
    composeRoute(MainActivityBase.NAV_STUDY, studySessionActive = studySessionActive) {
        Column {
            StudyTopBar(
                routeSnapshot = routeSnapshot,
                onClose = ::renderHome,
                onSettings = ::renderSettings,
            )
            studyUndoState.undoMessageOrNull()?.let { undoMessage ->
                StudyUndoBanner(
                    undoMessage = undoMessage,
                    onUndo = ::undoLastRating,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            content()
        }
    }
}
