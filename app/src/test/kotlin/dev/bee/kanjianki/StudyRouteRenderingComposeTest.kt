package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
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

    @Test
    fun explicitContinueRemainsDisabledUntilFeedbackIsApplied() {
        var continueEnabled by mutableStateOf(false)
        var clicks = 0

        composeRule.setContent {
            MeaningChoiceResultActionBar(
                status = "Correct",
                statusColor = MainActivityBase.TEAL,
                actionTone = StudyActionTone.PASS,
                continueEnabled = continueEnabled,
                onNext = { clicks++ },
            )
        }

        composeRule.onNode(SemanticsMatcher.expectValue(StudyExplicitContinueSemantics, true))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        assertEquals(0, clicks)

        composeRule.runOnIdle { continueEnabled = true }
        composeRule.onNode(SemanticsMatcher.expectValue(StudyExplicitContinueSemantics, true)).performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
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
