package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyRouteRenderingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        StudyContinueUiDiagnostics.resetForTests()
    }

    @Test
    fun activeHeaderAndDonePredicateReadOneAcceptedSnapshot() {
        val snapshot = routeSnapshot(
            version = 41L,
            completed = 5,
            target = 7,
            phase = StudySessionPhase.ACTIVE,
        )

        composeRule.setContent {
            Column {
                StudyTopBar(
                    routeSnapshot = snapshot,
                    onClose = {},
                    onSettings = {},
                )
                if (snapshot.isComplete) {
                    StudyDoneScreen(doneModel(), routeSnapshot = snapshot)
                }
            }
        }

        composeRule.onNodeWithTag(StudyUiTestTags.PROGRESS)
            .assertTextEquals("5 / 7")
            .assert(SemanticsMatcher.expectValue(StudyRouteVersionSemantics, 41L))
        composeRule.onNodeWithTag(StudyUiTestTags.DONE).assertDoesNotExist()
    }

    @Test
    fun terminalHeaderAndDoneScreenExposeTheSameSnapshotVersion() {
        val snapshot = routeSnapshot(
            version = 42L,
            completed = 7,
            target = 7,
            phase = StudySessionPhase.COMPLETE,
        )

        composeRule.setContent {
            Column {
                StudyTopBar(
                    routeSnapshot = snapshot,
                    onClose = {},
                    onSettings = {},
                )
                StudyDoneScreen(doneModel(), routeSnapshot = snapshot)
            }
        }

        composeRule.onNodeWithTag(StudyUiTestTags.PROGRESS)
            .assertTextEquals("7 / 7")
            .assert(SemanticsMatcher.expectValue(StudyRouteVersionSemantics, 42L))
        composeRule.onNodeWithTag(StudyUiTestTags.DONE)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(StudyRouteVersionSemantics, 42L))
    }

    /**
     * Continue enablement follows the observable parameter, not the feedback object.
     *
     * `StudyAnswerFeedbackState` lives in `:application`, a plain JVM module that has no
     * Compose dependency, so its phase is an ordinary field. A composable that reads
     * `continueAction.feedbackState.continueEnabled` therefore subscribes to nothing and
     * freezes at whatever enablement existed when it first composed. That is exactly what
     * happened: the action bar preferred the field over the parameter, and the Continue
     * button stayed disabled after the answer applied.
     *
     * The failure mode is nasty because production masked it -- the state machine
     * republishes a route snapshot on every feedback change, which recomposes the caller
     * for unrelated reasons and re-reads the field on the way through. So the bug only
     * showed where a test drove the feedback state directly. This test pins the direction
     * of trust by handing the composable a *stale* action object whose field says
     * "disabled" while the parameter says "enabled".
     */
    @Test
    fun continueEnablementFollowsTheObservableParameterNotTheFeedbackField() {
        StudyContinueUiDiagnostics.resetForTests()
        // Left in SUBMITTING: `continueEnabled` on this object is false, permanently.
        val stale = StudyAnswerFeedbackState("session-token").apply {
            begin(StudyAnswerOutcome.CORRECT)
        }
        val route = routeSnapshot(
            version = 7L,
            completed = 5,
            target = 7,
            phase = StudySessionPhase.FEEDBACK,
        )
        val action = StudyContinueAction(stale, { route }) { true }

        composeRule.setContent {
            MeaningChoiceResultActionBar(
                status = "Correct",
                statusColor = MainActivityBase.TEAL,
                actionTone = StudyActionTone.PASS,
                continueEnabled = true,
                continueAction = action,
                onNext = {},
            )
        }

        composeRule.onNode(SemanticsMatcher.expectValue(StudyExplicitContinueSemantics, true))
            .assertIsEnabled()
    }

    @Test
    fun explicitContinueLogsSubmittingAppliedAndAcceptedClickStates() {
        val events = CopyOnWriteArrayList<StudyContinueUiEvent>()
        StudyContinueUiDiagnostics.resetForTests()
        StudyContinueUiDiagnostics.setObserverForTests(events::add)
        val feedback = StudyAnswerFeedbackState("session-token").apply {
            begin(StudyAnswerOutcome.CORRECT)
        }
        var route by mutableStateOf(
            routeSnapshot(
                version = 7L,
                completed = 5,
                target = 7,
                phase = StudySessionPhase.SUBMITTING,
            ).copy(feedback = feedback.snapshot()),
        )
        var clicks = 0
        val continueAction = StudyContinueAction(feedback, { route }) {
            clicks++
            val accepted = feedback.tryContinue()
            route = route.copy(
                version = StudyRouteVersion(9L),
                phase = StudySessionPhase.ADVANCING,
                feedback = feedback.snapshot(),
            )
            accepted
        }

        composeRule.setContent {
            MeaningChoiceResultActionBar(
                status = "Correct",
                statusColor = MainActivityBase.TEAL,
                actionTone = StudyActionTone.PASS,
                // Derived from the observable route, exactly as every production caller
                // does it. The feedback state itself lives in `:application` and holds its
                // phase in a plain field, so reading that field here would subscribe to
                // nothing and the button would never enable.
                continueEnabled = route.feedback?.phase == StudyAnswerFeedbackPhase.APPLIED,
                continueAction = continueAction,
                onNext = {},
            )
        }

        composeRule.onNode(SemanticsMatcher.expectValue(StudyExplicitContinueSemantics, true))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        assertEquals(0, clicks)

        composeRule.runOnIdle {
            assertTrue(feedback.markApplied(feedback.sessionToken))
            route = route.copy(
                version = StudyRouteVersion(8L),
                phase = StudySessionPhase.FEEDBACK,
                feedback = feedback.snapshot(),
            )
        }
        composeRule.onNode(SemanticsMatcher.expectValue(StudyExplicitContinueSemantics, true))
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, clicks)
            val applied = events.last {
                it.stage == StudyContinueUiStage.STATE_CHANGED &&
                    it.feedbackPhase == StudyAnswerFeedbackPhase.APPLIED
            }
            assertTrue(applied.enabled)
            assertEquals(StudySessionPhase.FEEDBACK, applied.routePhase)
            assertEquals(8L, applied.routeVersion)
            assertNotNull(applied.bounds)

            val clickEntry = events.single { it.stage == StudyContinueUiStage.CLICK_ENTRY }
            assertEquals(StudyAnswerFeedbackPhase.APPLIED, clickEntry.feedbackPhase)
            assertEquals(StudySessionPhase.FEEDBACK, clickEntry.routePhase)
            val completed = events.single { it.stage == StudyContinueUiStage.CLICK_COMPLETED }
            assertEquals(StudyContinueUiOutcome.ACCEPTED, completed.outcome)
            assertEquals(StudyAnswerFeedbackPhase.CONTINUED, completed.feedbackPhase)
            assertEquals(StudySessionPhase.ADVANCING, completed.routePhase)
        }
    }

    private fun routeSnapshot(
        version: Long,
        completed: Int,
        target: Int,
        phase: StudySessionPhase,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        version = StudyRouteVersion(version),
        sessionGeneration = StudySessionGeneration(3L),
        sessionToken = "session-token",
        phase = phase,
        progress = StudySessionProgressUiState(
            completedCount = completed,
            targetCount = target,
        ),
        completionEvidenceReason = if (phase == StudySessionPhase.COMPLETE) {
            StudyRouteCompletionReason.HARD_CAP
        } else {
            null
        },
        completionReason = if (phase == StudySessionPhase.COMPLETE) {
            StudyRouteCompletionReason.HARD_CAP
        } else {
            null
        },
    )

    private fun doneModel(): StudyDoneScreenModel = StudyDoneScreenModel(
        modeLabel = "Practice",
        title = "Done!",
        headline = null,
        body = "Session complete",
        summaryLines = emptyList(),
        showDoneActions = false,
        availableStudyMoreNewCards = 0,
        showBackHome = true,
        backHomePrimary = true,
        onStudyMore = Runnable {},
        onContinueAll = Runnable {},
        onBackHome = Runnable {},
    )
}
