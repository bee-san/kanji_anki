package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRouteRaceDiagnosticsTest {
    @Test
    fun lifecycleLogsCorrelateCandidateBranchAndTerminalPublicationExactly() {
        val expected = privateRoute(
            phase = StudySessionPhase.ACTIVE,
            version = 26L,
            completedCount = 0,
        )
        val current = privateRoute(
            phase = StudySessionPhase.COMPLETE,
            version = 27L,
            completedCount = 1,
        )

        val created = StudyRouteLifecycleEvent.candidateCreated(
            candidateId = 41L,
            routeKind = StudyRouteLoadKind.STANDARD,
            expectedRoute = expected,
            trackerStateEquivalent = true,
        )
        val computed = StudyRouteLifecycleEvent.computationPrepared(
            candidateId = 41L,
            routeKind = StudyRouteLoadKind.STANDARD,
            branch = StudyRouteComputationBranch.HARD_CAP,
            expectedRoute = expected,
            currentRoute = current,
            trackerStateEquivalent = true,
            terminalEligible = true,
        )
        val published = StudyRouteLifecycleEvent.publicationDecided(
            candidateId = 41L,
            routeKind = StudyRouteLoadKind.STANDARD,
            branch = StudyRouteComputationBranch.HARD_CAP,
            expectedRoute = expected,
            currentRoute = current,
            trackerStateEquivalent = true,
            terminalEligible = true,
            outcome = StudyRouteLifecycleOutcome.ACCEPTED_TERMINAL,
        )

        assertEquals(
            "study-route lifecycle candidate_id=41 event=candidate-created route=standard " +
                "branch=unresolved expected_phase=ACTIVE expected_version=26 " +
                "current_phase=ACTIVE current_version=26 tracker_state_equivalent=true " +
                "terminal_eligible=false outcome=created",
            created.format(),
        )
        assertEquals(
            "study-route lifecycle candidate_id=41 event=computation-prepared route=standard " +
                "branch=hard-cap expected_phase=ACTIVE expected_version=26 " +
                "current_phase=COMPLETE current_version=27 tracker_state_equivalent=true " +
                "terminal_eligible=true outcome=prepared",
            computed.format(),
        )
        assertEquals(
            "study-route lifecycle candidate_id=41 event=publication-decided route=standard " +
                "branch=hard-cap expected_phase=ACTIVE expected_version=26 " +
                "current_phase=COMPLETE current_version=27 tracker_state_equivalent=true " +
                "terminal_eligible=true outcome=accepted-terminal",
            published.format(),
        )
        assertEquals(41L, published.candidateId)
        assertEquals(StudyRouteLifecycleOutcome.ACCEPTED_TERMINAL, published.outcome)
    }

    @Test
    fun retryPublicationExplainsFailedTrackerEquivalenceExactly() {
        val expected = privateRoute(StudySessionPhase.LOADING, 73L, completedCount = 1)
        val current = privateRoute(StudySessionPhase.COMPLETE, 74L, completedCount = 1)

        val event = StudyRouteLifecycleEvent.publicationDecided(
            candidateId = 99L,
            routeKind = StudyRouteLoadKind.RECOVERY,
            branch = StudyRouteComputationBranch.HARD_CAP,
            expectedRoute = expected,
            currentRoute = current,
            trackerStateEquivalent = false,
            terminalEligible = true,
            outcome = StudyRouteLifecycleOutcome.RETRY,
        )

        assertEquals(
            "study-route lifecycle candidate_id=99 event=publication-decided route=recovery " +
                "branch=hard-cap expected_phase=LOADING expected_version=73 " +
                "current_phase=COMPLETE current_version=74 tracker_state_equivalent=false " +
                "terminal_eligible=true outcome=retry",
            event.format(),
        )
    }

    @Test
    fun lifecycleEventsNeverRetainOrFormatStudyContent() {
        val privateRoute = privateRoute(StudySessionPhase.ACTIVE, 11L, completedCount = 0)
        val event = StudyRouteLifecycleEvent.publicationDecided(
            candidateId = 12L,
            routeKind = StudyRouteLoadKind.TARGETED,
            branch = StudyRouteComputationBranch.TARGETED_SESSION,
            expectedRoute = privateRoute,
            currentRoute = privateRoute,
            trackerStateEquivalent = true,
            terminalEligible = false,
            outcome = StudyRouteLifecycleOutcome.ACCEPTED,
        )
        val line = event.format()

        listOf(
            "private-session-token",
            "private user answer",
            "kanji=",
            "prompt=",
            "answer=",
            "token=",
            "session_token",
        ).forEach { forbidden ->
            assertFalse("must not contain $forbidden", line.contains(forbidden))
        }
        assertTrue(event::class.java.declaredFields.none { it.type == StudyRouteSnapshot::class.java })
    }

    private fun privateRoute(
        phase: StudySessionPhase,
        version: Long,
        completedCount: Int,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        phase = phase,
        sessionGeneration = StudySessionGeneration(4L),
        version = StudyRouteVersion(version),
        sessionToken = "private-session-token",
        feedback = StudyAnswerFeedbackSnapshot(
            sessionToken = "private-session-token",
            phase = StudyAnswerFeedbackPhase.CONTINUED,
            outcome = StudyAnswerOutcome.CORRECT,
            selectedAnswer = "private user answer",
        ),
        progress = StudySessionProgressUiState(targetCount = 1, completedCount = completedCount),
        completionEvidenceReason = StudyRouteCompletionReason.HARD_CAP.takeIf {
            phase == StudySessionPhase.COMPLETE
        },
        completionReason = StudyRouteCompletionReason.HARD_CAP.takeIf {
            phase == StudySessionPhase.COMPLETE
        },
    )
}
