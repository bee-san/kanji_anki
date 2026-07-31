package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StudyRouteRaceDiagnosticsTest {
    @Test
    fun terminalTrackerRaceLogExplainsWhyRetryWasSuppressed() {
        val route = StudyRouteSnapshot(
            phase = StudySessionPhase.COMPLETE,
            sessionGeneration = StudySessionGeneration(4L),
            version = StudyRouteVersion(27L),
            progress = StudySessionProgressUiState(targetCount = 1, completedCount = 1),
            completionEvidenceReason = StudyRouteCompletionReason.HARD_CAP,
            completionReason = StudyRouteCompletionReason.HARD_CAP,
        )

        val line = studyTrackerRaceLog(
            route = route,
            trackerStateEquivalent = true,
            terminalRaceAccepted = true,
        )

        assertEquals(
            "study-route tracker-publication event=accepted-terminal-race phase=COMPLETE " +
                "route_version=27 can_complete=true tracker_state_equivalent=true",
            line,
        )
        assertFalse(line.contains("kanji="))
        assertFalse(line.contains("session_token="))
    }
}
