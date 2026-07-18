package dev.bee.kanjianki

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.bee.kanjianki.core.RecordsSchedulerModels
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StudySessionViewModelTest {
    @Test
    fun initialStateIsIdleAndEmpty() {
        val state = StudySessionViewModel().uiState.value

        assertEquals(StudySessionPhase.IDLE, state.phase)
        assertFalse(state.sessionActive)
        assertEquals(StudySessionProgressUiState(), state.progress)
        assertEquals(StudySessionGeneration(0L), state.sessionGeneration)
        assertEquals(StudyRouteVersion(0L), state.routeVersion)
    }

    @Test
    fun mountedFeedbackTransitionsArePublishedAsImmutableSnapshots() {
        val viewModel = StudySessionViewModel()
        val session = session("token-1")
        viewModel.mountSession(session)

        val feedback = viewModel.feedbackFor(session.token)
        assertEquals(StudySessionPhase.ACTIVE, viewModel.uiState.value.phase)
        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, "good"))
        assertEquals(StudySessionPhase.SUBMITTING, viewModel.uiState.value.phase)
        assertEquals("good", viewModel.uiState.value.feedback?.selectedAnswer)

        assertTrue(feedback.markApplied(session.token))
        assertEquals(StudySessionPhase.FEEDBACK, viewModel.uiState.value.phase)
        assertTrue(feedback.tryContinue())
        assertEquals(StudySessionPhase.ADVANCING, viewModel.uiState.value.phase)
    }

    @Test
    fun trackerMutationsPublishOneCanonicalCountPair() {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("route-token"))

        val initial = viewModel.acceptedRouteSnapshot()
        viewModel.tracker.setTargetCount(7)
        viewModel.tracker.registerTaskShown("session:kanji_meaning:裂:route-token")
        viewModel.tracker.markTaskCompleted("session:kanji_meaning:裂:route-token")

        val snapshot = viewModel.acceptedRouteSnapshot()

        assertTrue(snapshot.version.value > initial.version.value)
        assertEquals("route-token", snapshot.sessionToken)
        assertEquals(7, snapshot.progress.targetCount)
        assertEquals(1, snapshot.progress.completedCount)
        assertEquals(snapshot.progress.targetCount, snapshot.displayedTargetCount)
        assertEquals(snapshot.progress.completedCount, snapshot.displayedCompletedCount)
        assertEquals(6, snapshot.remainingCount)
        assertSame(viewModel.uiState.value.progress, snapshot.progress)
    }

    @Test
    fun pendingTaskAtBoundaryPublishesOnlyCanonicalProgress() {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("route-token"))
        viewModel.tracker.setTargetCount(Int.MAX_VALUE - 1)
        val beforeInclusion = viewModel.acceptedRouteSnapshot()

        assertTrue(viewModel.tracker.includePendingTask("last-pending"))

        val atLimit = viewModel.acceptedRouteSnapshot()
        assertTrue(atLimit.version.value > beforeInclusion.version.value)
        assertEquals(Int.MAX_VALUE, atLimit.progress.targetCount)
        assertEquals(0, atLimit.progress.completedCount)
        assertEquals(Int.MAX_VALUE, atLimit.remainingCount)
        assertSame(viewModel.uiState.value.progress, atLimit.progress)

        val publishedState = viewModel.uiState.value
        assertFalse(viewModel.tracker.includePendingTask("overflow"))
        assertSame(publishedState, viewModel.uiState.value)
        assertEquals(atLimit, viewModel.acceptedRouteSnapshot())
    }

    @Test
    fun studyLoadTrackerCommitRejectsAStaleRouteBeforePublishingStagedProgress() {
        val viewModel = viewModelAt(completed = 5, target = 7)
        val staleRoute = viewModel.acceptedRouteSnapshot()
        val staged = viewModel.tracker.copyForStaging()
        staged.startActiveTask("stale-task", "裂", "kanji_meaning", 0L, false)

        viewModel.mountSession(session("replacement-token"))
        val replacementRoute = viewModel.acceptedRouteSnapshot()

        assertNull(viewModel.acceptStudyLoadTracker(staleRoute, staged))
        assertFalse(viewModel.tracker.hasActiveTask())
        assertEquals(replacementRoute, viewModel.acceptedRouteSnapshot())

        val acceptedStaging = viewModel.tracker.copyForStaging()
        acceptedStaging.startActiveTask("current-task", "謎", "kanji_meaning", 0L, false)
        val accepted = viewModel.acceptStudyLoadTracker(replacementRoute, acceptedStaging)

        assertNotNull(accepted)
        assertTrue(viewModel.tracker.hasActiveTask())
        assertTrue(viewModel.acceptedRouteSnapshot().progress.activeTask)
    }

    @Test
    fun studyLoadTrackerCommitAcceptsOnlyTheSingleLoadingPresentationTransition() {
        val viewModel = viewModelAt(completed = 5, target = 7)
        val expected = viewModel.acceptedRouteSnapshot()
        val staged = viewModel.tracker.copyForStaging()
        staged.startActiveTask("stale-task", "裂", "kanji_meaning", 0L, false)

        viewModel.showLoading()
        val loading = viewModel.acceptedRouteSnapshot()
        assertNotNull(
            viewModel.claimCurrentRouteAction(
                requireNotNull(loading.sessionToken),
                loading.sessionGeneration,
                loading.version,
            ),
        )
        val supersededLoading = viewModel.acceptedRouteSnapshot()

        assertNull(viewModel.acceptStudyLoadTracker(expected, staged))
        assertFalse(viewModel.tracker.hasActiveTask())
        assertEquals(supersededLoading, viewModel.acceptedRouteSnapshot())
    }

    @Test
    fun directFiveOfSevenCompletionIsRejectedWithoutAnyMutation() {
        val viewModel = viewModelAt(completed = 5, target = 7)
        val beforeState = viewModel.uiState.value
        val beforeSnapshot = viewModel.acceptedRouteSnapshot()

        assertNull(
            viewModel.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                beforeSnapshot.sessionGeneration,
                beforeSnapshot.version,
                beforeSnapshot.sessionToken,
            ),
        )
        assertFalse(
            viewModel.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                beforeSnapshot.sessionGeneration,
                beforeSnapshot.version,
                beforeSnapshot.sessionToken,
            ),
        )

        assertSame(beforeState, viewModel.uiState.value)
        assertEquals(beforeSnapshot, viewModel.acceptedRouteSnapshot())
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
        val viewModel = viewModelAt(completed = 5, target = 7)
        val live = viewModel.acceptedRouteSnapshot()

        assertTrue(
            viewModel.reconcileRouteTarget(
                reconciledTarget = 5,
                expectedGeneration = live.sessionGeneration,
                expectedVersion = live.version,
            ),
        )
        val terminal = viewModel.acceptedRouteSnapshot()

        assertTrue(terminal.version.value > live.version.value)
        assertEquals(5, terminal.progress.completedCount)
        assertEquals(5, terminal.progress.targetCount)
        assertEquals(StudySessionPhase.ACTIVE, terminal.phase)
        assertEquals(StudyRouteCompletionReason.TARGET_RECONCILIATION, terminal.completionReason)
        assertFalse(terminal.isComplete)

        assertTrue(
            viewModel.completeRoute(
                StudyRouteCompletionReason.TARGET_RECONCILIATION,
                terminal.sessionGeneration,
                terminal.version,
                terminal.sessionToken,
            ),
        )
        val done = viewModel.acceptedRouteSnapshot()
        assertTrue(done.version.value > terminal.version.value)
        assertTrue(done.isComplete)
        assertEquals(StudyRouteCompletionReason.TARGET_RECONCILIATION, done.completionReason)

        val lateFeedback = viewModel.feedbackFor(requireNotNull(done.sessionToken))
        lateFeedback.begin(StudyAnswerOutcome.CORRECT)
        assertEquals(done, viewModel.acceptedRouteSnapshot())
        assertFalse(
            viewModel.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                done.sessionGeneration,
                done.version,
                done.sessionToken,
            ),
        )
    }

    @Test
    fun directTrackerTargetDropIsRejectedWithoutMutatingTheRoute() {
        val viewModel = viewModelAt(completed = 5, target = 7)
        val live = viewModel.acceptedRouteSnapshot()

        assertThrows(IllegalArgumentException::class.java) {
            viewModel.tracker.setTargetCount(5)
        }
        assertEquals(live, viewModel.acceptedRouteSnapshot())
    }

    @Test
    fun hardCapCannotCompleteAnEmptyIdleRouteButNoSessionCan() {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("empty"))
        val activeEmpty = viewModel.acceptedRouteSnapshot()

        assertNull(
            viewModel.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                activeEmpty.sessionGeneration,
                activeEmpty.version,
                activeEmpty.sessionToken,
            ),
        )
        assertFalse(
            viewModel.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                activeEmpty.sessionGeneration,
                activeEmpty.version,
                activeEmpty.sessionToken,
            ),
        )
        assertEquals(activeEmpty, viewModel.acceptedRouteSnapshot())
        viewModel.reset()
        val idle = viewModel.acceptedRouteSnapshot()
        val noSessionEvidence = requireNotNull(
            viewModel.acceptCompletionEvidence(
                StudyRouteCompletionReason.NO_SESSION,
                idle.sessionGeneration,
                idle.version,
                idle.sessionToken,
            ),
        )
        assertTrue(
            viewModel.completeRoute(
                StudyRouteCompletionReason.NO_SESSION,
                noSessionEvidence.sessionGeneration,
                noSessionEvidence.version,
                noSessionEvidence.sessionToken,
            ),
        )
        assertTrue(viewModel.acceptedRouteSnapshot().isComplete)
    }

    @Test
    fun staleFeedbackTokenGenerationAndVersionAreExactNoOps() {
        val viewModel = StudySessionViewModel()
        val mounted = session("current-token")
        viewModel.mountSession(mounted)
        val currentFeedback = viewModel.feedbackFor(mounted.token)
        assertTrue(currentFeedback.begin(StudyAnswerOutcome.CORRECT, "good"))
        viewModel.tracker.setTargetCount(2)

        val beforeState = viewModel.uiState.value
        val beforeRoute = viewModel.acceptedRouteSnapshot()
        val beforeTracker = viewModel.tracker.snapshot()
        val staleToken = StudyAnswerFeedbackSnapshot(
            sessionToken = "stale-token",
            phase = StudyAnswerFeedbackPhase.APPLIED,
            outcome = StudyAnswerOutcome.CORRECT,
            selectedAnswer = "stale",
        )

        assertFalse(viewModel.acceptFeedback(staleToken, beforeRoute.sessionGeneration, beforeRoute.version))
        assertExactNoOp(viewModel, beforeState, beforeRoute, beforeTracker, currentFeedback)

        val currentTokenFeedback = staleToken.copy(sessionToken = mounted.token)
        assertFalse(
            viewModel.acceptFeedback(
                currentTokenFeedback,
                beforeRoute.sessionGeneration.next(),
                beforeRoute.version,
            ),
        )
        assertExactNoOp(viewModel, beforeState, beforeRoute, beforeTracker, currentFeedback)

        assertFalse(
            viewModel.acceptFeedback(
                currentTokenFeedback,
                beforeRoute.sessionGeneration,
                beforeRoute.version.next(),
            ),
        )
        assertExactNoOp(viewModel, beforeState, beforeRoute, beforeTracker, currentFeedback)
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
        val viewModel = StudySessionViewModel()
        val mounted = session("stable-token")
        viewModel.mountSession(mounted)
        val mountedRoute = viewModel.acceptedRouteSnapshot()

        viewModel.tracker.setTargetCount(1)
        viewModel.tracker.markTaskCompleted("session:kanji_meaning:裂:stable-token")
        val afterProgress = viewModel.acceptedRouteSnapshot()
        assertEquals(mountedRoute.sessionGeneration, afterProgress.sessionGeneration)
        assertTrue(afterProgress.version.value > mountedRoute.version.value)

        val feedback = viewModel.feedbackFor(mounted.token)
        assertTrue(feedback.begin(StudyAnswerOutcome.INCORRECT, "again"))
        assertTrue(feedback.markApplied(mounted.token))
        val applied = viewModel.acceptedRouteSnapshot()
        assertEquals(mountedRoute.sessionGeneration, applied.sessionGeneration)
        assertEquals(StudySessionPhase.FEEDBACK, applied.phase)

        assertTrue(feedback.tryContinue())
        val continued = viewModel.acceptedRouteSnapshot()
        assertEquals(mountedRoute.sessionGeneration, continued.sessionGeneration)
        assertEquals(StudySessionPhase.ADVANCING, continued.phase)
        assertTrue(continued.version.value > applied.version.value)
    }

    @Test
    fun repeatedWrongLearnAheadAndRepairWorkEachBlockCompletion() {
        val workCases = listOf(
            StudyRouteCompletionReason.LEARN_AHEAD_REPEAT to StudyRoutePendingWork.of(
                requeuedTaskKeys = listOf("wrong", "wrong"),
            ),
            StudyRouteCompletionReason.LEARN_AHEAD_REPEAT to StudyRoutePendingWork.of(
                learnAheadRepeatTaskKeys = listOf("repeat"),
            ),
            StudyRouteCompletionReason.REPAIR to StudyRoutePendingWork.of(
                repairTaskKeys = listOf("repair"),
            ),
        )

        for ((reason, work) in workCases) {
            val viewModel = viewModelAt(completed = 1, target = 1)
            val terminal = viewModel.acceptedRouteSnapshot()
            assertTrue(
                viewModel.acceptPendingWork(
                    work,
                    reason,
                    terminal.sessionGeneration,
                    terminal.version,
                ),
            )
            val blocked = viewModel.acceptedRouteSnapshot()
            val beforeCompletionAttempt = viewModel.uiState.value

            assertTrue(blocked.pendingWork.hasBlockers)
            assertFalse(
                viewModel.completeRoute(
                    StudyRouteCompletionReason.HARD_CAP,
                    blocked.sessionGeneration,
                    blocked.version,
                    blocked.sessionToken,
                ),
            )
            assertSame(beforeCompletionAttempt, viewModel.uiState.value)
        }
    }

    @Test
    fun pendingWorkCannotForgeTargetReconciliationProvenance() {
        val viewModel = viewModelAt(completed = 1, target = 1)
        val route = viewModel.acceptedRouteSnapshot()

        assertFalse(
            viewModel.acceptPendingWork(
                StudyRoutePendingWork.of(pendingTaskKeys = listOf("forged")),
                StudyRouteCompletionReason.TARGET_RECONCILIATION,
                route.sessionGeneration,
                route.version,
            ),
        )
        assertEquals(route, viewModel.acceptedRouteSnapshot())
    }

    @Test
    fun pendingWorkMergesAndCannotRewriteCompletedRouteProvenance() {
        val viewModel = viewModelAt(completed = 1, target = 1)
        var route = viewModel.acceptedRouteSnapshot()
        assertTrue(
            viewModel.acceptPendingWork(
                StudyRoutePendingWork.of(requeuedTaskKeys = listOf("wrong")),
                StudyRouteCompletionReason.LEARN_AHEAD_REPEAT,
                route.sessionGeneration,
                route.version,
            ),
        )
        route = viewModel.acceptedRouteSnapshot()
        assertTrue(
            viewModel.acceptPendingWork(
                StudyRoutePendingWork.of(repairTaskKeys = listOf("repair")),
                StudyRouteCompletionReason.REPAIR,
                route.sessionGeneration,
                route.version,
            ),
        )
        route = viewModel.acceptedRouteSnapshot()
        assertEquals(setOf("wrong", "repair"), route.pendingWork.taskKeys)
        assertTrue(
            viewModel.resolvePendingWork(
                route.pendingWork.taskKeys,
                route.sessionGeneration,
                route.version,
            ),
        )
        route = viewModel.acceptedRouteSnapshot()
        val hardCapEvidence = requireNotNull(
            viewModel.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                route.sessionGeneration,
                route.version,
                route.sessionToken,
            ),
        )
        assertTrue(
            viewModel.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                hardCapEvidence.sessionGeneration,
                hardCapEvidence.version,
                hardCapEvidence.sessionToken,
            ),
        )
        val done = viewModel.acceptedRouteSnapshot()
        assertFalse(
            viewModel.acceptPendingWork(
                StudyRoutePendingWork.NONE,
                StudyRouteCompletionReason.RESTORE,
                done.sessionGeneration,
                done.version,
            ),
        )
        assertEquals(done, viewModel.acceptedRouteSnapshot())
    }

    @Test
    fun restoreKeepsGenerationStableAndResetAdvancesItOnce() {
        val viewModel = StudySessionViewModel()
        val mounted = session("restore-token")
        viewModel.mountSession(mounted)
        viewModel.tracker.setTargetCount(1)
        val generation = viewModel.acceptedRouteSnapshot().sessionGeneration
        val restored = StudyAnswerFeedbackState.restore(
            StudyAnswerFeedbackSnapshot(
                sessionToken = mounted.token,
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = "good",
            ),
        )

        assertSame(restored, viewModel.feedbackFor(mounted.token, restored))
        assertEquals(generation, viewModel.acceptedRouteSnapshot().sessionGeneration)
        assertEquals(StudySessionPhase.FEEDBACK, viewModel.acceptedRouteSnapshot().phase)

        viewModel.reset()
        val reset = viewModel.acceptedRouteSnapshot()
        assertEquals(generation.next(), reset.sessionGeneration)
        assertEquals(StudySessionPhase.IDLE, reset.phase)
        assertEquals(StudySessionProgressUiState(), reset.progress)
        assertEquals(viewModel.tracker.snapshot().targetCount, reset.progress.targetCount)
        assertEquals(StudyRoutePendingWork.NONE, reset.pendingWork)
        assertNull(viewModel.feedbackState())
    }

    @Test
    fun replacingTheMountedSessionAdvancesGenerationButRemountingItDoesNot() {
        val viewModel = StudySessionViewModel()
        val first = session("first")
        viewModel.mountSession(first)
        val firstRoute = viewModel.acceptedRouteSnapshot()

        viewModel.mountSession(first)
        val remounted = viewModel.acceptedRouteSnapshot()
        assertEquals(firstRoute.sessionGeneration, remounted.sessionGeneration)
        assertTrue(remounted.version.value > firstRoute.version.value)

        viewModel.mountSession(session("second"))
        val secondRoute = viewModel.acceptedRouteSnapshot()
        assertEquals(firstRoute.sessionGeneration.next(), secondRoute.sessionGeneration)
        assertTrue(secondRoute.version.value > firstRoute.version.value)
    }

    @Test
    fun replacingSessionDataWithTheSameTokenStillAdvancesGeneration() {
        val viewModel = StudySessionViewModel()
        val first = session("shared-token")
        viewModel.mountSession(first)
        val firstRoute = viewModel.acceptedRouteSnapshot()
        val replacement = RecordsSchedulerModels.StudySession(
            item = first.item,
            row = first.row,
            token = first.token,
            taskType = first.taskType,
            writingRequired = first.writingRequired,
            prompt = "replacement prompt",
        )

        viewModel.mountSession(replacement)

        val replacedData = viewModel.acceptedRouteSnapshot()
        assertEquals(firstRoute.sessionGeneration, replacedData.sessionGeneration)
        assertTrue(replacedData.version.value > firstRoute.version.value)
    }

    @Test
    fun staleAutoContinueGuardPublishesNoEffect() = runTest {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("current"))
        val route = viewModel.acceptedRouteSnapshot()

        assertFalse(
            viewModel.requestAutoContinue(
                "stale",
                route.sessionGeneration,
                route.version,
            ),
        )
        assertNull(withTimeoutOrNull(50L) { viewModel.effects.first() })
    }

    @Test
    fun autoContinueIsDeliveredAsOneShotEffect() = runTest {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("token"))
        val route = viewModel.acceptedRouteSnapshot()
        val effect = async { viewModel.effects.first() }

        assertTrue(viewModel.requestAutoContinue("token", route.sessionGeneration, route.version))

        assertEquals(
            StudySessionEffect.AutoContinue("token", route.sessionGeneration, route.version),
            effect.await(),
        )
    }

    @Test
    fun queuedAutoContinueIsRejectedAfterTheRouteAdvances() = runTest {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("token"))
        val route = viewModel.acceptedRouteSnapshot()
        assertTrue(viewModel.requestAutoContinue("token", route.sessionGeneration, route.version))
        val effect = viewModel.effects.first() as StudySessionEffect.AutoContinue

        viewModel.tracker.setTargetCount(1)

        assertFalse(
            viewModel.isCurrentRoute(
                effect.sessionToken,
                effect.sessionGeneration,
                effect.routeVersion,
            ),
        )
    }

    @Test
    fun routeActionClaimIsAtomicAndCannotBeReused() {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("token"))
        val route = viewModel.acceptedRouteSnapshot()

        val claim = viewModel.claimCurrentRouteAction(
            "token",
            route.sessionGeneration,
            route.version,
        )
        assertNotNull(claim)
        val claimed = viewModel.acceptedRouteSnapshot()
        assertEquals(route.sessionGeneration, claimed.sessionGeneration)
        assertTrue(claimed.version.value > route.version.value)
        assertTrue(viewModel.consumeRouteAction(requireNotNull(claim)))
        assertFalse(viewModel.consumeRouteAction(claim))
        assertNull(
            viewModel.claimCurrentRouteAction(
                "token",
                route.sessionGeneration,
                route.version,
            ),
        )
    }

    @Test
    fun terminalRouteRejectsEveryLateActionWithoutMutation() = runTest {
        val viewModel = viewModelAt(completed = 1, target = 1)
        val route = viewModel.acceptedRouteSnapshot()
        val evidence = requireNotNull(
            viewModel.acceptCompletionEvidence(
                StudyRouteCompletionReason.HARD_CAP,
                route.sessionGeneration,
                route.version,
                route.sessionToken,
            ),
        )
        assertTrue(
            viewModel.completeRoute(
                StudyRouteCompletionReason.HARD_CAP,
                evidence.sessionGeneration,
                evidence.version,
                evidence.sessionToken,
            ),
        )
        val completeState = viewModel.uiState.value
        val completeRoute = viewModel.acceptedRouteSnapshot()
        val token = requireNotNull(completeRoute.sessionToken)

        assertSame(
            completeState,
            StudySessionReducer.reduce(completeState, StudySessionEvent.RouteActionClaimed),
        )
        assertFalse(viewModel.requestAutoContinue(token))
        assertFalse(
            viewModel.requestAutoContinue(
                token,
                completeRoute.sessionGeneration,
                completeRoute.version,
            ),
        )
        assertNull(
            viewModel.claimCurrentRouteAction(
                token,
                completeRoute.sessionGeneration,
                completeRoute.version,
            ),
        )
        assertFalse(
            viewModel.consumeRouteAction(
                StudyRouteActionClaim(
                    token,
                    completeRoute.sessionGeneration,
                    completeRoute.version,
                ),
            ),
        )

        assertSame(completeState, viewModel.uiState.value)
        assertEquals(completeRoute, viewModel.acceptedRouteSnapshot())
        assertEquals(completeRoute.completionReason, completeRoute.completionEvidenceReason)
        assertNull(withTimeoutOrNull(50L) { viewModel.effects.first() })
    }

    @Test
    fun seededTransitionTracesPreserveRouteInvariants() {
        val seeds = listOf(7, 19, 41, 73)
        for (seed in seeds) {
            val random = Random(seed)
            val viewModel = StudySessionViewModel()
            viewModel.mountSession(session("seed-$seed"))
            viewModel.tracker.setTargetCount(7)
            var nextTask = 0

            repeat(80) { step ->
                val route = viewModel.acceptedRouteSnapshot()
                if (route.isComplete) return@repeat
                when (random.nextInt(6)) {
                    0 -> if (route.progress.completedCount < route.progress.targetCount) {
                        viewModel.tracker.markTaskCompleted("seed-$seed-task-${nextTask++}")
                    }
                    1 -> viewModel.acceptPendingWork(
                        StudyRoutePendingWork.of(requeuedTaskKeys = listOf("wrong-${random.nextInt(3)}")),
                        StudyRouteCompletionReason.LEARN_AHEAD_REPEAT,
                        route.sessionGeneration,
                        route.version,
                    )
                    2 -> viewModel.resolvePendingWork(
                        route.pendingWork.taskKeys,
                        route.sessionGeneration,
                        route.version,
                    )
                    3 -> if (route.progress.completedCount > 0) {
                        viewModel.reconcileRouteTarget(
                            route.progress.completedCount,
                            route.sessionGeneration,
                            route.version,
                        )
                    }
                    4 -> viewModel.acceptCompletionEvidence(
                        StudyRouteCompletionReason.HARD_CAP,
                        route.sessionGeneration,
                        route.version,
                        route.sessionToken,
                    )?.let { evidence ->
                        viewModel.completeRoute(
                            StudyRouteCompletionReason.HARD_CAP,
                            evidence.sessionGeneration,
                            evidence.version,
                            evidence.sessionToken,
                        )
                    }
                    else -> viewModel.acceptFeedback(
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

                assertRouteInvariants(seed, step, viewModel.acceptedRouteSnapshot())
            }
        }
    }

    @Test
    fun viewModelStoreRetainsTheRealSessionAcrossConfigurationOwnerReplacement() {
        val store = ViewModelStore()
        val firstOwner = TestOwner(store)
        val first = ViewModelProvider(firstOwner)[StudySessionViewModel::class.java]
        val mounted = session("retained-token")
        first.mountSession(mounted)
        first.tracker.setTargetCount(8)

        val replacementOwner = TestOwner(store)
        val replacement = ViewModelProvider(replacementOwner)[StudySessionViewModel::class.java]

        assertSame(first, replacement)
        assertSame(mounted, replacement.uiState.value.currentSession)
        assertEquals(8, replacement.uiState.value.progress.targetCount)
        store.clear()
    }

    private fun assertExactNoOp(
        viewModel: StudySessionViewModel,
        beforeState: StudySessionUiState,
        beforeRoute: StudyRouteSnapshot,
        beforeTracker: StudySessionTracker.Snapshot,
        beforeFeedback: StudyAnswerFeedbackState,
    ) {
        assertSame(beforeState, viewModel.uiState.value)
        assertEquals(beforeRoute, viewModel.acceptedRouteSnapshot())
        assertEquals(beforeTracker, viewModel.tracker.snapshot())
        assertSame(beforeFeedback, viewModel.feedbackState())
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

    private fun viewModelAt(completed: Int, target: Int): StudySessionViewModel {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("route-token"))
        viewModel.tracker.setTargetCount(target)
        repeat(completed) { index ->
            viewModel.tracker.markTaskCompleted("session:kanji_meaning:字$index:token-$index")
        }
        return viewModel
    }

    private class TestOwner(
        override val viewModelStore: ViewModelStore,
    ) : ViewModelStoreOwner

    private fun session(token: String): RecordsSchedulerModels.StudySession =
        RecordsSchedulerModels.StudySession(
            item = null,
            row = null,
            token = token,
            taskType = "kanji_meaning",
            writingRequired = false,
            prompt = "meaning",
        )
}
