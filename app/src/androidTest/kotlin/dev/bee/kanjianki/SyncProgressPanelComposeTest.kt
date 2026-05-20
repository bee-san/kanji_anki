package dev.bee.kanjianki

import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SyncProgressPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSyncProgressTitle() {
        composeRule.setContent {
            SyncProgressTitle("Syncing cards")
        }

        composeRule.onNodeWithText("Syncing cards").assertIsDisplayed()
    }

    @Test
    fun rendersSyncProgressScreenTitle() {
        val panel = syncProgressPanel()

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNodeWithText("Syncing cards").assertIsDisplayed()
    }

    @Test
    fun syncProgressScreenUsesSingleComposeBridge() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val screen = syncProgressScreenView(context, "Syncing cards", SyncProgressPanel())

        assertTrue(screen is ComposeView)
    }

    @Test
    fun updatesProgressContentWhenPanelIdentityChanges() {
        val firstPanel = syncProgressPanel()
        val secondPanel = syncProgressPanel()
        val activePanel = mutableStateOf(firstPanel)

        firstPanel.render(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE))
        secondPanel.render(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = activePanel.value
            )
        }

        composeRule.onNodeWithText("Finding note type").assertIsDisplayed()

        composeRule.runOnIdle {
            activePanel.value = secondPanel
        }

        composeRule.onNodeWithText("Reading notes").assertIsDisplayed()
    }

    @Test
    fun rendersKnownTotalProgressWithRangeSemantics() {
        val panel = syncProgressPanel()
        panel.render(SyncProgress.cardsScanned(0, 0))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNodeWithText("0 / 0 cards scanned").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sync progress: 0 / 0 cards scanned")
            .assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
    }

    @Test
    fun rendersEtaAndFinishingCopy() {
        val panel = syncProgressPanel()
        panel.render(SyncProgress.cardsScanned(0, 50_000))
        SystemClock.sleep(1100L)
        panel.render(SyncProgress.cardsScanned(3, 50_000))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNode(hasText("cards/sec - about", substring = true)).assertIsDisplayed()
        composeRule.onNode(hasText("hr left", substring = true)).assertIsDisplayed()

        composeRule.runOnIdle {
            panel.render(SyncProgress.cardsScanned(50_000, 50_000))
        }

        composeRule.onNode(hasText("finishing up", substring = true)).assertIsDisplayed()
    }

    @Test
    fun rendersSecondAndMinuteEtaUnits() {
        val secondsPanel = syncProgressPanel()
        secondsPanel.render(SyncProgress.cardsScanned(0, 200))
        SystemClock.sleep(1100L)
        secondsPanel.render(SyncProgress.cardsScanned(199, 200))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = secondsPanel
            )
        }

        composeRule.onNode(hasText("sec left", substring = true)).assertIsDisplayed()

        val minutesPanel = syncProgressPanel()
        minutesPanel.render(SyncProgress.cardsScanned(0, 600))
        SystemClock.sleep(1100L)
        minutesPanel.render(SyncProgress.cardsScanned(3, 600))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = minutesPanel
            )
        }

        composeRule.onNode(hasText("min left", substring = true)).assertIsDisplayed()
    }

    @Test
    fun rendersEstimatingCopyBeforeEnoughProgressOrTime() {
        val panel = syncProgressPanel()
        panel.render(SyncProgress.cardsScanned(0, 10))
        panel.render(SyncProgress.cardsScanned(2, 10))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNode(hasText("cards/sec - estimating time left", substring = true)).assertIsDisplayed()
    }

    @Test
    fun keepsKnownCountAcrossLocalStages() {
        val panel = syncProgressPanel()
        panel.render(SyncProgress.cardsScanned(7, 9))
        panel.render(SyncProgress.atStage(SyncProgress.Stage.BUILDING_PRACTICE_QUEUE))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNodeWithText("Building practice queue").assertIsDisplayed()
        composeRule.onNodeWithText("7 / 9 cards scanned").assertIsDisplayed()
        composeRule.onNodeWithText("Saving the practice queue.").assertIsDisplayed()

        composeRule.runOnIdle {
            panel.render(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
        }

        composeRule.onNodeWithText("Archiving imported suspended cards").assertIsDisplayed()
        composeRule.onNodeWithText("7 / 9 cards scanned").assertIsDisplayed()
        composeRule.onNodeWithText("Updating archived suspended cards.").assertIsDisplayed()
    }

    @Test
    fun rendersDefensiveUnknownStageCopyBeforeTotalIsKnown() {
        val panel = syncProgressPanel()
        panel.render(SyncProgress.atStage(null))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNodeWithText("Syncing cards").assertIsDisplayed()
        composeRule.onNodeWithText("Preparing card scan.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sync progress: Syncing cards").assertIsDisplayed()
    }

    @Test
    fun rendersAllPreScanStageCopy() {
        val panel = syncProgressPanel()

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.runOnIdle {
            panel.render(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE))
        }
        composeRule.onNodeWithText("Finding note type").assertIsDisplayed()
        composeRule.onNodeWithText("Checking collection shape.").assertIsDisplayed()

        composeRule.runOnIdle {
            panel.render(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))
        }
        composeRule.onNodeWithText("Reading notes").assertIsDisplayed()
        composeRule.onNodeWithText("Reading notes before the card total is known.").assertIsDisplayed()

        composeRule.runOnIdle {
            panel.render(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS))
        }
        composeRule.onNodeWithText("Processing imported cards").assertIsDisplayed()
        composeRule.onNodeWithText("AnkiDroid read finished. Processing imported cards locally.").assertIsDisplayed()
    }

    private fun syncProgressPanel(): SyncProgressPanel {
        return SyncProgressPanel()
    }
}
