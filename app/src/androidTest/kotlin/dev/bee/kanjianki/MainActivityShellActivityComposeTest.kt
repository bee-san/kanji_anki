package dev.bee.kanjianki

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import dev.bee.kanjianki.core.RecordsSchedulerModels
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class MainActivityShellActivityComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityBootsThroughComposeShell() {
        composeRule.onNodeWithTag("main-activity-shell")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun browseDraftSurvivesShellRepublicationAndActivityRecreation() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.renderBrowseKanji("initial query")
        }
        waitForText("initial query")

        composeRule.onNodeWithText("initial query").performTextReplacement("draft query")
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.rerenderLatestHomeRoute()
        }
        waitForText("draft query")
        composeRule.onNodeWithText("draft query").assertTextContains("draft query")

        composeRule.activityRule.scenario.recreate()

        waitForText("draft query")
        composeRule.onNodeWithText("draft query").assertTextContains("draft query")
    }

    @Test
    fun pendingStatsLoadRestoresStatsInsteadOfPreviousHomeSubroute() {
        val blockerStarted = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.io.execute {
                blockerStarted.countDown()
                try {
                    releaseBlocker.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        assertTrue(blockerStarted.await(5, TimeUnit.SECONDS))

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.renderFocusDone(
                RecordsSchedulerModels.AdaptiveLoadPlan(
                    20,
                    2,
                    0,
                    listOf("裂", "列"),
                    0,
                    false,
                    "Done",
                ),
            )
            assertTrue(activity.doneActions.hasRetainedStudyDone())
            activity.currentHomeRouteRestoration = HomeRouteRestoration.browse(
                query = "old query",
                onlySimilarKanji = false,
                allKanjiScope = false,
            )
            activity.renderStats()
            assertEquals(MainActivityBase.NAV_STATS_ROUTE, activity.currentRoute)
            assertNull(activity.currentHomeRouteRestoration)
        }

        composeRule.activityRule.scenario.recreate()
        releaseBlocker.countDown()

        composeRule.onNodeWithTag("main-route-${MainActivityBase.NAV_STATS_ROUTE}")
            .assertIsDisplayed()
    }

    @Test
    fun studyMoreDialogAndDraftRestoreThroughProductionActivity() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.cancelPendingHomeRouteLoads()
            activity.renderFocusDone(
                RecordsSchedulerModels.AdaptiveLoadPlan(
                    20,
                    2,
                    0,
                    listOf("裂", "列"),
                    0,
                    false,
                    "Done",
                ),
            )
            activity.showStudyMoreNewCardsDialog(availableAtOpen = 7)
        }
        waitForText("How many extra new cards?")
        composeRule.onNodeWithText("5").performTextReplacement("3")

        composeRule.activityRule.scenario.recreate()

        waitForText("How many extra new cards?")
        composeRule.onNodeWithText("3").assertTextContains("3")
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.doneActions.hasRetainedStudyDone())
            assertEquals(5, activity.studyDoneViewModel.dialogInitialCount)
            assertEquals("3", activity.studyDoneViewModel.dialogRequestText)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
