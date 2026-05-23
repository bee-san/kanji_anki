package dev.bee.kanjianki
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

        panel.render(SyncProgress.cardsScanned(1, 2))

        composeRule.onNodeWithText("1 / 2 cards scanned").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sync progress: 1 / 2 cards scanned")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
    }

    @Test
    fun rendersEtaAndFinishingCopy() {
        val clock = FakeClock()
        val panel = syncProgressPanel(clock)
        panel.render(SyncProgress.cardsScanned(0, 50_000))
        clock.now += 1100L
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
        val secondsClock = FakeClock()
        val secondsPanel = syncProgressPanel(secondsClock)
        secondsPanel.render(SyncProgress.cardsScanned(0, 200))
        secondsClock.now += 1100L
        secondsPanel.render(SyncProgress.cardsScanned(199, 200))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = secondsPanel
            )
        }

        composeRule.onNode(hasText("sec left", substring = true)).assertIsDisplayed()

        val minutesClock = FakeClock()
        val minutesPanel = syncProgressPanel(minutesClock)
        minutesPanel.render(SyncProgress.cardsScanned(0, 600))
        minutesClock.now += 1100L
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
        val clock = FakeClock()
        val panel = syncProgressPanel(clock)
        panel.render(SyncProgress.cardsScanned(0, 10))
        panel.render(SyncProgress.cardsScanned(2, 10))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNode(hasText("cards/sec - estimating time left", substring = true)).assertIsDisplayed()

        val slowClock = FakeClock()
        val slowPanel = syncProgressPanel(slowClock)
        slowPanel.render(SyncProgress.cardsScanned(0, 10))
        slowClock.now += 500L
        slowPanel.render(SyncProgress.cardsScanned(3, 10))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = slowPanel
            )
        }

        composeRule.onNode(hasText("cards/sec - estimating time left", substring = true)).assertIsDisplayed()
    }

    @Test
    fun keepsKnownCountAcrossLocalStages() {
        val panel = syncProgressPanel()
        panel.render(SyncProgress.cardsScanned(7, 9))
        panel.render(SyncProgress.atStage(SyncProgress.Stage.SAVING_LOCAL_DATA))

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNodeWithText("Saving local data").assertIsDisplayed()
        composeRule.onNodeWithText("7 / 9 cards scanned").assertIsDisplayed()
        composeRule.onNodeWithText("Saving the Anki snapshot and import evidence.").assertIsDisplayed()

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

    private fun syncProgressPanel(clock: FakeClock = FakeClock()): SyncProgressPanel {
        return SyncProgressPanel(clock::elapsedRealtime)
    }

    private class FakeClock(var now: Long = 1L) {
        fun elapsedRealtime(): Long = now
    }
}
