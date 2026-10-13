package dev.bee.kanjianki

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class StudySessionStateMachineTest {
    @Test
    fun initialStateIsIdleAndEmpty() {
        val state = StudySessionStateMachine().snapshot()

        assertEquals(StudySessionPhase.IDLE, state.phase)
        assertFalse(state.sessionActive)
        assertEquals(StudySessionProgressUiState(), state.progress)
        assertEquals(StudySessionGeneration(0L), state.sessionGeneration)
        assertEquals(StudyRouteVersion(0L), state.routeVersion)
    }

    @Test
    fun mountedFeedbackTransitionsArePublishedAsImmutableSnapshots() {
        val stateMachine = StudySessionStateMachine()
        val session = session("token-1")
        stateMachine.mountSession(session)

        val feedback = stateMachine.feedbackFor(session.token)
        assertEquals(StudySessionPhase.ACTIVE, stateMachine.snapshot().phase)
        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, "good"))
        assertEquals(StudySessionPhase.SUBMITTING, stateMachine.snapshot().phase)
        assertEquals("good", stateMachine.snapshot().feedback?.selectedAnswer)

        assertTrue(feedback.markApplied(session.token))
        assertEquals(StudySessionPhase.FEEDBACK, stateMachine.snapshot().phase)
        assertTrue(feedback.tryContinue())
        assertEquals(StudySessionPhase.ADVANCING, stateMachine.snapshot().phase)
    }

    @Test
    fun trackerMutationsPublishOneCanonicalCountPair() {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("route-token"))

        val initial = stateMachine.acceptedRouteSnapshot()
        stateMachine.tracker.setTargetCount(7)
        stateMachine.tracker.registerTaskShown("session:kanji_meaning:裂:route-token")
        stateMachine.tracker.markTaskCompleted("session:kanji_meaning:裂:route-token")

        val snapshot = stateMachine.acceptedRouteSnapshot()

        assertTrue(snapshot.version.value > initial.version.value)
        assertEquals("route-token", snapshot.sessionToken)
        assertEquals(7, snapshot.progress.targetCount)
        assertEquals(1, snapshot.progress.completedCount)
        assertEquals(snapshot.progress.targetCount, snapshot.displayedTargetCount)
        assertEquals(snapshot.progress.completedCount, snapshot.displayedCompletedCount)
        assertEquals(6, snapshot.remainingCount)
        assertSame(stateMachine.snapshot().progress, snapshot.progress)
    }

    @Test
    fun pendingTaskAtBoundaryPublishesOnlyCanonicalProgress() {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("route-token"))
        stateMachine.tracker.setTargetCount(Int.MAX_VALUE - 1)
        val beforeInclusion = stateMachine.acceptedRouteSnapshot()

        assertTrue(stateMachine.tracker.includePendingTask("last-pending"))

        val atLimit = stateMachine.acceptedRouteSnapshot()
        assertTrue(atLimit.version.value > beforeInclusion.version.value)
        assertEquals(Int.MAX_VALUE, atLimit.progress.targetCount)
        assertEquals(0, atLimit.progress.completedCount)
        assertEquals(Int.MAX_VALUE, atLimit.remainingCount)
        assertSame(stateMachine.snapshot().progress, atLimit.progress)

        val publishedState = stateMachine.snapshot()
        assertFalse(stateMachine.tracker.includePendingTask("overflow"))
        assertSame(publishedState, stateMachine.snapshot())
        assertEquals(atLimit, stateMachine.acceptedRouteSnapshot())
    }

    @Test
    fun studyLoadTrackerCommitRejectsAStaleRouteBeforePublishingStagedProgress() {
        val stateMachine = stateMachineAt(completed = 5, target = 7)
        val staleRoute = stateMachine.acceptedRouteSnapshot()
        val staged = stateMachine.tracker.copyForStaging()
        staged.startActiveTask("stale-task", "裂", "kanji_meaning", 0L, false)

        stateMachine.mountSession(session("replacement-token"))
        val replacementRoute = stateMachine.acceptedRouteSnapshot()

        assertNull(stateMachine.acceptStudyLoadTracker(staleRoute, staged))
        assertFalse(stateMachine.tracker.hasActiveTask())
        assertEquals(replacementRoute, stateMachine.acceptedRouteSnapshot())

        val acceptedStaging = stateMachine.tracker.copyForStaging()
        acceptedStaging.startActiveTask("current-task", "謎", "kanji_meaning", 0L, false)
        val accepted = stateMachine.acceptStudyLoadTracker(replacementRoute, acceptedStaging)

        assertNotNull(accepted)
        assertTrue(stateMachine.tracker.hasActiveTask())
        assertTrue(stateMachine.acceptedRouteSnapshot().progress.activeTask)
    }

    @Test
    fun studyLoadTrackerCommitAcceptsOnlyTheSingleLoadingPresentationTransition() {
        val stateMachine = stateMachineAt(completed = 5, target = 7)
        val expected = stateMachine.acceptedRouteSnapshot()
        val staged = stateMachine.tracker.copyForStaging()
        staged.startActiveTask("stale-task", "裂", "kanji_meaning", 0L, false)

        stateMachine.showLoading()
        val loading = stateMachine.acceptedRouteSnapshot()
        assertNotNull(
            stateMachine.claimCurrentRouteAction(
                requireNotNull(loading.sessionToken),
                loading.sessionGeneration,
                loading.version,
            ),
        )
        val supersededLoading = stateMachine.acceptedRouteSnapshot()

        assertNull(stateMachine.acceptStudyLoadTracker(expected, staged))
        assertFalse(stateMachine.tracker.hasActiveTask())
        assertEquals(supersededLoading, stateMachine.acceptedRouteSnapshot())
    }

    @Test
    fun stagedStudyLoadTargetShrinkPublishesExplicitReconciliation() {
        val stateMachine = stateMachineAt(completed = 5, target = 7)
        val expected = stateMachine.acceptedRouteSnapshot()
        val staged = stateMachine.tracker.copyForStaging()
        staged.initializeSessionPlan(emptyList())

        val accepted = requireNotNull(stateMachine.acceptStudyLoadTracker(expected, staged))

        assertEquals(5, accepted.progress.completedCount)
        assertEquals(5, accepted.progress.targetCount)
        assertEquals(StudyRouteCompletionReason.TARGET_RECONCILIATION, accepted.completionEvidenceReason)
        assertFalse(accepted.isComplete)
    }

    @Test
    fun directFiveOfSevenCompletionIsRejectedWithoutAnyMutation() {
        val stateMachine = stateMachineAt(completed = 5, target = 7)
        val beforeState = stateMachine.snapshot()
        val beforeSnapshot = stateMachine.acceptedRouteSnapshot()

        assertNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                beforeSnapshot.sessionGeneration,
                beforeSnapshot.version,
                beforeSnapshot.sessionToken,
            ),
        )
        assertFalse(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                beforeSnapshot.sessionGeneration,
                beforeSnapshot.version,
                beforeSnapshot.sessionToken,
            ),
        )

        assertSame(beforeState, stateMachine.snapshot())
        assertEquals(beforeSnapshot, stateMachine.acceptedRouteSnapshot())
    }

    @Test
    fun directCompletePresentationEventCannotBypassValidation() {
        val state = StudySessionUiState(
            phase = StudySessionPhase.ACTIVE,
            currentSession = session("token"),
            progress = StudySessionProgressUiState(targetCount = 7, completedCount = 5),
            routeVersion = StudyRouteVersion(8L),
            sessionGeneration = StudySessionGeneration(2L),
        )

        val reduced = StudySessionReducer.reduce(
            state,
            StudySessionEvent.PresentationChanged(StudySessionPhase.COMPLETE),
        )

        assertSame(state, reduced)
    }

    @Test
    fun persistedTerminalPresentationRestoresOnlyWithoutAnActiveSession() {
        val restored = StudySessionStateMachine()

        assertTrue(restored.restoreTerminalPresentation(StudyRouteCompletionReason.HARD_CAP))
        assertTrue(restored.acceptedRouteSnapshot().isComplete)
        assertEquals(
            StudyRouteCompletionReason.HARD_CAP,
            restored.acceptedRouteSnapshot().completionReason,
        )

        val active = StudySessionStateMachine()
        active.mountSession(session("active-token"))
        val activeRoute = active.acceptedRouteSnapshot()

        assertFalse(active.restoreTerminalPresentation(StudyRouteCompletionReason.HARD_CAP))
        assertEquals(activeRoute, active.acceptedRouteSnapshot())
    }

    @Test
    fun presentationTransitionMatrixAllowsOnlyLoading() {
        val active = StudySessionUiState(
            phase = StudySessionPhase.ACTIVE,
            currentSession = session("token"),
        )

        for (requestedPhase in StudySessionPhase.entries) {
            val reduced = StudySessionReducer.reduce(
                active,
                StudySessionEvent.PresentationChanged(requestedPhase),
            )

            if (requestedPhase == StudySessionPhase.LOADING) {
                assertEquals(StudySessionPhase.LOADING, reduced.phase)
            } else {
                assertSame(requestedPhase.name, active, reduced)
            }
        }
    }

    @Test
    fun nonCanonicalProgressRefreshDoesNotEraseAnAcceptedCompletionReason() {
        val state = StudySessionUiState(
            phase = StudySessionPhase.COMPLETE,
            progress = StudySessionProgressUiState(targetCount = 1, completedCount = 1),
            completionReason = StudyRouteCompletionReason.HARD_CAP,
        )

        val reduced = StudySessionReducer.reduce(
            state,
            StudySessionEvent.ProgressChanged(state.progress.copy(movedForwardCount = 1)),
        )

        assertEquals(StudySessionPhase.COMPLETE, reduced.phase)
        assertEquals(StudyRouteCompletionReason.HARD_CAP, reduced.completionReason)
    }

    @Test
    fun targetReconciliationPublishesANonDoneNOfNFrameBeforeCompletion() {
        val stateMachine = stateMachineAt(completed = 5, target = 7)
        val live = stateMachine.acceptedRouteSnapshot()

        assertTrue(
            stateMachine.reconcileRouteTarget(
                reconciledTarget = 5,
                expectedGeneration = live.sessionGeneration,
                expectedVersion = live.version,
            ),
        )
        val terminal = stateMachine.acceptedRouteSnapshot()

        assertTrue(terminal.version.value > live.version.value)
        assertEquals(5, terminal.progress.completedCount)
        assertEquals(5, terminal.progress.targetCount)
        assertEquals(StudySessionPhase.ACTIVE, terminal.phase)
        assertEquals(StudyRouteCompletionReason.TARGET_RECONCILIATION, terminal.completionReason)
        assertFalse(terminal.isComplete)

        assertTrue(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.TARGET_RECONCILIATION,
                terminal.sessionGeneration,
                terminal.version,
                terminal.sessionToken,
            ),
        )
        val done = stateMachine.acceptedRouteSnapshot()
        assertTrue(done.version.value > terminal.version.value)
        assertTrue(done.isComplete)
        assertEquals(StudyRouteCompletionReason.TARGET_RECONCILIATION, done.completionReason)

        val lateFeedback = stateMachine.feedbackFor(requireNotNull(done.sessionToken))
        lateFeedback.begin(StudyAnswerOutcome.CORRECT)
        assertEquals(done, stateMachine.acceptedRouteSnapshot())
        assertFalse(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                done.sessionGeneration,
                done.version,
                done.sessionToken,
            ),
        )
    }

    @Test
    fun directTrackerTargetDropIsRejectedWithoutMutatingTheRoute() {
        val stateMachine = stateMachineAt(completed = 5, target = 7)
        val live = stateMachine.acceptedRouteSnapshot()

        assertThrows(IllegalArgumentException::class.java) {
            stateMachine.tracker.setTargetCount(5)
        }
        assertEquals(live, stateMachine.acceptedRouteSnapshot())
    }

    @Test
    fun hardCapCannotCompleteAnEmptyIdleRouteButNoSessionCan() {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("empty"))
        val activeEmpty = stateMachine.acceptedRouteSnapshot()

        assertNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                activeEmpty.sessionGeneration,
                activeEmpty.version,
                activeEmpty.sessionToken,
            ),
        )
        assertFalse(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                activeEmpty.sessionGeneration,
                activeEmpty.version,
                activeEmpty.sessionToken,
            ),
        )
        assertEquals(activeEmpty, stateMachine.acceptedRouteSnapshot())
        stateMachine.reset()
        val idle = stateMachine.acceptedRouteSnapshot()
        val noSessionEvidence = requireNotNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.NO_SESSION,
                idle.sessionGeneration,
                idle.version,
                idle.sessionToken,
            ),
        )
        assertTrue(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.NO_SESSION,
                noSessionEvidence.sessionGeneration,
                noSessionEvidence.version,
                noSessionEvidence.sessionToken,
            ),
        )
        assertTrue(stateMachine.acceptedRouteSnapshot().isComplete)
    }

    @Test
    fun repeatedNoSessionTerminalAcceptanceReusesTheAcceptedDoneRoute() {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("terminal"))
        val active = stateMachine.acceptedRouteSnapshot()
        val absent = requireNotNull(stateMachine.acceptTerminalSessionAbsence(active))
        val evidence = requireNotNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.NO_SESSION,
                absent.sessionGeneration,
                absent.version,
                absent.sessionToken,
            ),
        )
        assertTrue(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.NO_SESSION,
                evidence.sessionGeneration,
                evidence.version,
                evidence.sessionToken,
            ),
        )
        val done = stateMachine.acceptedRouteSnapshot()

        val repeated = stateMachine.acceptTerminalSessionAbsence(done)

        assertEquals(done, repeated)
        assertEquals(done, stateMachine.acceptedRouteSnapshot())
    }

    @Test
    fun hardCapCompletionAcceptsTheLoadingSnapshotPublishedByTheRouteLoader() {
        val stateMachine = stateMachineAt(completed = 1, target = 1)
        stateMachine.showLoading()
        val loading = stateMachine.acceptedRouteSnapshot()

        assertEquals(StudySessionPhase.LOADING, loading.phase)
        val evidence = requireNotNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                loading.sessionGeneration,
                loading.version,
                loading.sessionToken,
            ),
        )
        assertTrue(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                evidence.sessionGeneration,
                evidence.version,
                evidence.sessionToken,
            ),
        )
        assertTrue(stateMachine.acceptedRouteSnapshot().isComplete)
    }

    @Test
    fun loadingFramePreservesTerminalEvidenceForAcceptedReload() {
        val stateMachine = stateMachineAt(completed = 1, target = 1)
        val active = stateMachine.acceptedRouteSnapshot()
        val evidence = requireNotNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                active.sessionGeneration,
                active.version,
                active.sessionToken,
            ),
        )
        assertTrue(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                evidence.sessionGeneration,
                evidence.version,
                evidence.sessionToken,
            ),
        )
        val expected = stateMachine.acceptedRouteSnapshot()
        val staged = stateMachine.tracker.copyForStaging()

        stateMachine.showLoading()
        val loading = stateMachine.acceptedRouteSnapshot()

        assertEquals(StudySessionPhase.LOADING, loading.phase)
        assertEquals(StudyRouteCompletionReason.HARD_CAP, loading.completionEvidenceReason)
        assertNotNull(stateMachine.acceptStudyLoadTracker(expected, staged))
    }

    @Test
    fun staleFeedbackTokenGenerationAndVersionAreExactNoOps() {
        val stateMachine = StudySessionStateMachine()
        val mounted = session("current-token")
        stateMachine.mountSession(mounted)
        val currentFeedback = stateMachine.feedbackFor(mounted.token)
        assertTrue(currentFeedback.begin(StudyAnswerOutcome.CORRECT, "good"))
        stateMachine.tracker.setTargetCount(2)

        val beforeState = stateMachine.snapshot()
        val beforeRoute = stateMachine.acceptedRouteSnapshot()
        val beforeTracker = stateMachine.tracker.snapshot()
        val staleToken = StudyAnswerFeedbackSnapshot(
            sessionToken = "stale-token",
            phase = StudyAnswerFeedbackPhase.APPLIED,
            outcome = StudyAnswerOutcome.CORRECT,
            selectedAnswer = "stale",
        )

        assertFalse(stateMachine.acceptFeedback(staleToken, beforeRoute.sessionGeneration, beforeRoute.version))
        assertExactNoOp(stateMachine, beforeState, beforeRoute, beforeTracker, currentFeedback)

        val currentTokenFeedback = staleToken.copy(sessionToken = mounted.token)
        assertFalse(
            stateMachine.acceptFeedback(
                currentTokenFeedback,
                beforeRoute.sessionGeneration.next(),
                beforeRoute.version,
            ),
        )
        assertExactNoOp(stateMachine, beforeState, beforeRoute, beforeTracker, currentFeedback)

        assertFalse(
            stateMachine.acceptFeedback(
                currentTokenFeedback,
                beforeRoute.sessionGeneration,
                beforeRoute.version.next(),
            ),
        )
        assertExactNoOp(stateMachine, beforeState, beforeRoute, beforeTracker, currentFeedback)
    }

    @Test
    fun staleReducerFeedbackIsTheExactSameState() {
        val state = StudySessionUiState(
            currentSession = session("new-token"),
            phase = StudySessionPhase.ACTIVE,
            routeVersion = StudyRouteVersion(9L),
            sessionGeneration = StudySessionGeneration(3L),
        )
        val stale = StudyAnswerFeedbackSnapshot(
            sessionToken = "old-token",
            phase = StudyAnswerFeedbackPhase.APPLIED,
            outcome = StudyAnswerOutcome.CORRECT,
            selectedAnswer = "good",
        )

        val reduced = StudySessionReducer.reduce(state, StudySessionEvent.FeedbackChanged(stale))

        assertSame(state, reduced)
    }

    @Test
    fun generationIsStableAcrossProgressAndExplicitFeedbackTransitions() {
        val stateMachine = StudySessionStateMachine()
        val mounted = session("stable-token")
        stateMachine.mountSession(mounted)
        val mountedRoute = stateMachine.acceptedRouteSnapshot()

        stateMachine.tracker.setTargetCount(1)
        stateMachine.tracker.markTaskCompleted("session:kanji_meaning:裂:stable-token")
        val afterProgress = stateMachine.acceptedRouteSnapshot()
        assertEquals(mountedRoute.sessionGeneration, afterProgress.sessionGeneration)
        assertTrue(afterProgress.version.value > mountedRoute.version.value)

        val feedback = stateMachine.feedbackFor(mounted.token)
        assertTrue(feedback.begin(StudyAnswerOutcome.INCORRECT, "again"))
        assertTrue(feedback.markApplied(mounted.token))
        val applied = stateMachine.acceptedRouteSnapshot()
        assertEquals(mountedRoute.sessionGeneration, applied.sessionGeneration)
        assertEquals(StudySessionPhase.FEEDBACK, applied.phase)

        assertTrue(feedback.tryContinue())
        val continued = stateMachine.acceptedRouteSnapshot()
        assertEquals(mountedRoute.sessionGeneration, continued.sessionGeneration)
        assertEquals(StudySessionPhase.ADVANCING, continued.phase)
        assertTrue(continued.version.value > applied.version.value)
    }

    @Test
    fun postFeedbackRoutePreparationCapturesTheAcceptedFeedbackSnapshot() {
        val stateMachine = StudySessionStateMachine()
        val mounted = session("feedback-render")
        stateMachine.mountSession(mounted)
        val before = stateMachine.acceptedRouteSnapshot()

        val prepared = prepareAcceptedStudyRoute(
            routeProvider = {
                val feedback = stateMachine.feedbackFor(mounted.token)
                assertTrue(feedback.begin(StudyAnswerOutcome.INCORRECT, "again"))
                assertTrue(feedback.markApplied(mounted.token))
                "render model"
            },
            routeSnapshotProvider = stateMachine::acceptedRouteSnapshot,
        )

        assertEquals("render model", prepared.model)
        assertEquals(StudySessionPhase.FEEDBACK, prepared.routeSnapshot.phase)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, prepared.routeSnapshot.feedback?.phase)
        assertTrue(prepared.routeSnapshot.version.value > before.version.value)
    }

    @Test
    fun canonicalTrackerProgressBlocksEveryOutstandingWorkCategory() {
        val workCases = listOf<Pair<String, (StudySessionStateMachine) -> Unit>>(
            "pending plan" to { it.tracker.initializeSessionPlan(listOf("pending")) },
            "requeued wrong answer" to {
                it.tracker.admitPendingTask("wrong")
                it.tracker.admitPendingTask("wrong")
            },
            "learn-ahead repeat" to { it.tracker.initializeSessionPlan(emptyList(), listOf("repeat")) },
            "repair" to { it.tracker.admitPendingTask("repair") },
        )

        for ((category, publishWork) in workCases) {
            val stateMachine = stateMachineAt(completed = 1, target = 1)
            publishWork(stateMachine)
            val blocked = stateMachine.acceptedRouteSnapshot()

            assertEquals(category, 1, blocked.progress.completedCount)
            assertEquals(category, 2, blocked.progress.targetCount)
            assertFalse(category, blocked.canComplete)
            assertFalse(
                category,
                stateMachine.completeRoute(
                    StudyRouteCompletionReason.HARD_CAP,
                    blocked.sessionGeneration,
                    blocked.version,
                    blocked.sessionToken,
                ),
            )
        }
    }

    @Test
    fun restoreKeepsGenerationStableAndResetAdvancesItOnce() {
        val stateMachine = StudySessionStateMachine()
        val mounted = session("restore-token")
        stateMachine.mountSession(mounted)
        stateMachine.tracker.setTargetCount(1)
        val generation = stateMachine.acceptedRouteSnapshot().sessionGeneration
        val restored = StudyAnswerFeedbackState.restore(
            StudyAnswerFeedbackSnapshot(
                sessionToken = mounted.token,
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = "good",
            ),
        )

        assertSame(restored, stateMachine.feedbackFor(mounted.token, restored))
        assertEquals(generation, stateMachine.acceptedRouteSnapshot().sessionGeneration)
        assertEquals(StudySessionPhase.FEEDBACK, stateMachine.acceptedRouteSnapshot().phase)

        stateMachine.reset()
        val reset = stateMachine.acceptedRouteSnapshot()
        assertEquals(generation.next(), reset.sessionGeneration)
        assertEquals(StudySessionPhase.IDLE, reset.phase)
        assertEquals(StudySessionProgressUiState(), reset.progress)
        assertEquals(stateMachine.tracker.snapshot().targetCount, reset.progress.targetCount)
        assertNull(stateMachine.feedbackState())
    }

    @Test
    fun replacingTheMountedSessionAdvancesGenerationButRemountingItDoesNot() {
        val stateMachine = StudySessionStateMachine()
        val first = session("first")
        stateMachine.mountSession(first)
        val firstRoute = stateMachine.acceptedRouteSnapshot()

        stateMachine.mountSession(first)
        val remounted = stateMachine.acceptedRouteSnapshot()
        assertEquals(firstRoute.sessionGeneration, remounted.sessionGeneration)
        assertTrue(remounted.version.value > firstRoute.version.value)

        stateMachine.mountSession(session("second"))
        val secondRoute = stateMachine.acceptedRouteSnapshot()
        assertEquals(firstRoute.sessionGeneration.next(), secondRoute.sessionGeneration)
        assertTrue(secondRoute.version.value > firstRoute.version.value)
    }

    @Test
    fun replacingSessionDataWithTheSameTokenStillAdvancesGeneration() {
        val stateMachine = StudySessionStateMachine()
        val first = session("shared-token")
        stateMachine.mountSession(first)
        val firstRoute = stateMachine.acceptedRouteSnapshot()
        val replacement = RecordsSchedulerModels.StudySession(
            item = first.item,
            row = first.row,
            token = first.token,
            taskType = first.taskType,
            writingRequired = first.writingRequired,
            prompt = "replacement prompt",
        )

        stateMachine.mountSession(replacement)

        val replacedData = stateMachine.acceptedRouteSnapshot()
        assertEquals(firstRoute.sessionGeneration, replacedData.sessionGeneration)
        assertTrue(replacedData.version.value > firstRoute.version.value)
    }


    @Test
    fun routeActionClaimIsAtomicAndCannotBeReused() {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("token"))
        val route = stateMachine.acceptedRouteSnapshot()

        val claim = stateMachine.claimCurrentRouteAction(
            "token",
            route.sessionGeneration,
            route.version,
        )
        assertNotNull(claim)
        val claimed = stateMachine.acceptedRouteSnapshot()
        assertEquals(route.sessionGeneration, claimed.sessionGeneration)
        assertTrue(claimed.version.value > route.version.value)
        assertTrue(stateMachine.consumeRouteAction(requireNotNull(claim)))
        assertFalse(stateMachine.consumeRouteAction(claim))
        assertNull(
            stateMachine.claimCurrentRouteAction(
                "token",
                route.sessionGeneration,
                route.version,
            ),
        )
    }

    @Test
    fun terminalRouteRejectsEveryLateActionWithoutMutation() {
        val stateMachine = stateMachineAt(completed = 1, target = 1)
        val route = stateMachine.acceptedRouteSnapshot()
        val evidence = requireNotNull(
            stateMachine.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                route.sessionGeneration,
                route.version,
                route.sessionToken,
            ),
        )
        assertTrue(
            stateMachine.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                evidence.sessionGeneration,
                evidence.version,
                evidence.sessionToken,
            ),
        )
        val completeState = stateMachine.snapshot()
        val completeRoute = stateMachine.acceptedRouteSnapshot()
        val token = requireNotNull(completeRoute.sessionToken)

        assertSame(
            completeState,
            StudySessionReducer.reduce(completeState, StudySessionEvent.RouteActionClaimed),
        )
        assertNull(
            stateMachine.claimCurrentRouteAction(
                token,
                completeRoute.sessionGeneration,
                completeRoute.version,
            ),
        )
        assertFalse(
            stateMachine.consumeRouteAction(
                StudyRouteActionClaim(
                    token,
                    completeRoute.sessionGeneration,
                    completeRoute.version,
                ),
            ),
        )

        assertSame(completeState, stateMachine.snapshot())
        assertEquals(completeRoute, stateMachine.acceptedRouteSnapshot())
        assertEquals(completeRoute.completionReason, completeRoute.completionEvidenceReason)
    }

    @Test
    fun processDeathRestoresAppliedFeedbackWithoutReacceptingTheCommittedRevision() {
        val before = item("裂", "裂|分裂|ぶんれつ|split", revision = 7L)
        val after = before.copyBuilder()
            .schedulerRevision(8L)
            .activeToken(null)
            .build()
        val original = StudySessionStateMachine()
        original.mountSession(session("process-death-token", before))
        val feedback = original.feedbackFor("process-death-token")
        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, "good"))
        assertTrue(feedback.markApplied("process-death-token"))
        val durable = StudyPendingAnswerSnapshot(
            feedback = feedback.snapshot(),
            kanji = before.kanji,
            taskType = "kanji_meaning",
            writingRequired = false,
            prompt = "meaning",
            answerSignature = before.answerSignature,
            schedulerRevision = before.schedulerRevision,
        )
        original.close()

        val restored = StudySessionStateMachine()
        restored.mountSession(durable.restoreSession(after, row = null))
        val restoredFeedback = restored.feedbackFor(
            durable.feedback.sessionToken,
            StudyAnswerFeedbackState.restore(durable.feedback),
        )

        assertEquals(StudySessionPhase.FEEDBACK, restored.snapshot().phase)
        assertFalse(restoredFeedback.begin(StudyAnswerOutcome.INCORRECT, "again"))
        assertFalse(restoredFeedback.markApplied(durable.feedback.sessionToken))
        assertFalse(
            restored.acceptAppliedReview(
                AppliedReviewSnapshot(durable.feedback.sessionToken, before, after),
                "good",
                1_000L,
            ),
        )
        assertTrue(restoredFeedback.tryContinue())
        assertFalse(restoredFeedback.tryContinue())
    }

    @Test
    fun duplicateAppliedCallbackCannotReplaceUndoAuthorityOrAdvanceRuntimeTwice() {
        val before = item("裂", "裂|分裂|ぶんれつ|split", revision = 7L)
        val after = before.copyBuilder().schedulerRevision(8L).build()
        val snapshot = AppliedReviewSnapshot("duplicate-token", before, after)
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session(snapshot.token, before))

        assertTrue(stateMachine.acceptAppliedReview(snapshot, "good", 1_000L))
        val acceptedState = stateMachine.snapshot()
        val acceptedUndo = stateMachine.undoState.pending

        assertFalse(stateMachine.acceptAppliedReview(snapshot, "again", 2_000L))
        assertSame(acceptedState, stateMachine.snapshot())
        assertSame(acceptedUndo, stateMachine.undoState.pending)
        assertEquals("good", stateMachine.undoState.pending?.label)
        assertEquals(1_000L, stateMachine.undoState.pending?.createdAtMillis)
    }

    @Test
    fun staleSchedulerRevisionCannotPublishReviewOrUndoState() {
        val current = item("裂", "裂|分裂|ぶんれつ|split", revision = 8L)
        val staleBefore = current.copyBuilder().schedulerRevision(7L).build()
        val staleAfter = current.copyBuilder().schedulerRevision(8L).build()
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("stale-revision-token", current))
        val beforeState = stateMachine.snapshot()

        assertFalse(
            stateMachine.acceptAppliedReview(
                AppliedReviewSnapshot("stale-revision-token", staleBefore, staleAfter),
                "good",
                1_000L,
            ),
        )
        assertSame(beforeState, stateMachine.snapshot())
        assertNull(stateMachine.undoState.pending)
    }

    @Test
    fun undoAuthorityTracksOnlyTheCurrentAcceptedReviewAndCanBeCleared() {
        val before = item("裂", "裂|分裂|ぶんれつ|split", revision = 7L)
        val after = before.copyBuilder().schedulerRevision(8L).build()
        val accepted = AppliedReviewSnapshot("undo-token", before, after)
        val stale = AppliedReviewSnapshot(
            "stale-undo-token",
            before.copyBuilder().schedulerRevision(6L).build(),
            before,
        )
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session(accepted.token, before))

        assertTrue(stateMachine.acceptAppliedReview(accepted, "good", 1_000L))
        assertFalse(stateMachine.acceptAppliedReview(stale, "again", 2_000L))
        assertSame(accepted, stateMachine.undoState.pending?.snapshot)

        stateMachine.undoState.clear()

        assertNull(stateMachine.undoState.pending)
        assertTrue(stateMachine.acceptAppliedReview(accepted, "good", 3_000L))
    }

    @Test
    fun rapidRepeatedInputClaimsOneAnswerAndOneReviewSubmission() {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("rapid-input-token"))
        val feedback = stateMachine.feedbackFor("rapid-input-token")

        val acceptedAnswers = (0 until 100).count {
            feedback.begin(StudyAnswerOutcome.CORRECT, "good")
        }
        assertEquals(1, acceptedAnswers)

        val workerCount = 32
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workerCount)
        val acceptedClaims = AtomicInteger()
        repeat(workerCount) {
            Thread {
                ready.countDown()
                start.await()
                if (stateMachine.tryClaimReviewToken("rapid-input-token")) {
                    acceptedClaims.incrementAndGet()
                }
                done.countDown()
            }.start()
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, acceptedClaims.get())
    }

    @Test
    fun adversarialReviewSeedsPreserveRouteInvariants() {
        val seeds = listOf(0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144)
        for (seed in seeds) {
            val random = Random(seed)
            val stateMachine = StudySessionStateMachine()
            stateMachine.mountSession(session("seed-$seed"))
            stateMachine.tracker.setTargetCount(7)
            var nextTask = 0

            repeat(80) { step ->
                val route = stateMachine.acceptedRouteSnapshot()
                if (route.isComplete) return@repeat
                when (random.nextInt(6)) {
                    0 -> if (route.progress.completedCount < route.progress.targetCount) {
                        stateMachine.tracker.markTaskCompleted("seed-$seed-task-${nextTask++}")
                    }
                    1 -> stateMachine.tracker.includePendingTask("pending-$seed-$step")
                    2 -> stateMachine.tracker.includePendingTask("requeued-${random.nextInt(3)}")
                    3 -> if (route.progress.completedCount > 0) {
                        stateMachine.reconcileRouteTarget(
                            route.progress.completedCount,
                            route.sessionGeneration,
                            route.version,
                        )
                    }
                    4 -> stateMachine.acceptCompletionEvidence(
                        StudyRouteCompletionReason.HARD_CAP,
                        route.sessionGeneration,
                        route.version,
                        route.sessionToken,
                    )?.let { evidence ->
                        stateMachine.completeRoute(
                            StudyRouteCompletionReason.HARD_CAP,
                            evidence.sessionGeneration,
                            evidence.version,
                            evidence.sessionToken,
                        )
                    }
                    else -> stateMachine.acceptFeedback(
                        StudyAnswerFeedbackSnapshot(
                            sessionToken = "stale-$seed-$step",
                            phase = StudyAnswerFeedbackPhase.APPLIED,
                            outcome = StudyAnswerOutcome.INCORRECT,
                            selectedAnswer = "stale",
                        ),
                        route.sessionGeneration,
                        route.version,
                    )
                }

                assertRouteInvariants(seed, step, stateMachine.acceptedRouteSnapshot())
            }
        }
    }

    private fun assertExactNoOp(
        stateMachine: StudySessionStateMachine,
        beforeState: StudySessionUiState,
        beforeRoute: StudyRouteSnapshot,
        beforeTracker: StudySessionTracker.Snapshot,
        beforeFeedback: StudyAnswerFeedbackState,
    ) {
        assertSame(beforeState, stateMachine.snapshot())
        assertEquals(beforeRoute, stateMachine.acceptedRouteSnapshot())
        assertEquals(beforeTracker, stateMachine.tracker.snapshot())
        assertSame(beforeFeedback, stateMachine.feedbackState())
    }

    private fun assertRouteInvariants(seed: Int, step: Int, route: StudyRouteSnapshot) {
        val message = "seed=$seed step=$step route=$route"
        assertTrue(message, route.progress.completedCount >= 0)
        assertTrue(message, route.progress.targetCount >= route.progress.completedCount)
        assertEquals(message, route.progress.completedCount, route.displayedCompletedCount)
        assertEquals(message, route.progress.targetCount, route.displayedTargetCount)
        assertEquals(
            message,
            route.progress.targetCount - route.progress.completedCount,
            route.remainingCount,
        )
        if (route.phase == StudySessionPhase.COMPLETE) {
            assertTrue(message, route.canComplete)
            assertTrue(message, route.isComplete)
        }
    }

    private fun stateMachineAt(completed: Int, target: Int): StudySessionStateMachine {
        val stateMachine = StudySessionStateMachine()
        stateMachine.mountSession(session("route-token"))
        stateMachine.tracker.setTargetCount(target)
        repeat(completed) { index ->
            stateMachine.tracker.markTaskCompleted("session:kanji_meaning:字$index:token-$index")
        }
        return stateMachine
    }

    private fun session(
        token: String,
        item: RecordsStudyModels.StudyItem? = null,
    ): RecordsSchedulerModels.StudySession =
        RecordsSchedulerModels.StudySession(
            item = item,
            row = null,
            token = token,
            taskType = "kanji_meaning",
            writingRequired = false,
            prompt = "meaning",
        )

    private fun item(
        kanji: String,
        answerSignature: String,
        revision: Long,
    ): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            answerSignature,
            1_000L,
        ).copyBuilder()
            .answerSignature(answerSignature)
            .activeToken("token")
            .schedulerRevision(revision)
            .build()
}
