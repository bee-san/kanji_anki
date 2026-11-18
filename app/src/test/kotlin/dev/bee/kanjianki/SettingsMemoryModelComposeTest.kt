package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.MemoryModelTextCopy
import dev.bee.kanjianki.memory.MemoryModelCopy
import dev.bee.kanjianki.memory.RwkvRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsMemoryModelComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun panelFollowsTheModelStatusAndOffersARebuildOnlyWhenIdle() {
        var rebuilds = 0
        val status = MutableStateFlow<RwkvRuntime.Status>(RwkvRuntime.Status.Rebuilding(250, 1_000))
        val panel = SettingsMemoryModelPanelModel(
            title = MemoryModelTextCopy.title(),
            body = MemoryModelTextCopy.body(),
            modelLine = MemoryModelTextCopy.modelLine("RWKV-Curve small (SM92)", 331_511),
            provenanceLine = MemoryModelTextCopy.provenanceLine(),
            status = status,
            rebuildLabel = MemoryModelTextCopy.rebuildLabel(),
            onRebuild = Runnable { rebuilds++ },
        )
        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsSubmenuScreen(
                    SettingsSubmenuScreenModel(
                        "Home", Runnable {}, "Back", Runnable {},
                        "Study settings", "Scheduling", listOf(panel),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("settings-panel-memory-model").assertIsDisplayed()
        composeRule.onNodeWithText(MemoryModelTextCopy.statusRebuilding(250, 1_000)).assertIsDisplayed()
        composeRule.onNodeWithText(MemoryModelTextCopy.modelLine("RWKV-Curve small (SM92)", 331_511)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsMemoryModelControlDescriptions.REBUILD).assertDoesNotExist()

        status.value = RwkvRuntime.Status.Ready
        composeRule.onNodeWithText(MemoryModelTextCopy.statusReady()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(SettingsMemoryModelControlDescriptions.REBUILD)
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, rebuilds) }
    }

    @Test
    fun statusCopyCoversEveryState() {
        assertEquals(MemoryModelTextCopy.statusChecking(), MemoryModelCopy.statusLine(RwkvRuntime.Status.Unchecked))
        assertEquals(MemoryModelTextCopy.statusChecking(), MemoryModelCopy.statusLine(RwkvRuntime.Status.Checking))
        assertTrue(MemoryModelCopy.statusLine(RwkvRuntime.Status.Failed("boom")).contains("boom"))
        assertEquals(MemoryModelTextCopy.statusRebuilding(0, 0), MemoryModelCopy.statusLine(RwkvRuntime.Status.Rebuilding(0, 0)))
        assertTrue(MemoryModelCopy.preparingMessage(RwkvRuntime.Status.Rebuilding(1, 4)).contains("25%"))
        assertEquals(MemoryModelTextCopy.preparingToast(null), MemoryModelCopy.preparingMessage(RwkvRuntime.Status.Checking))
        assertEquals(MemoryModelTextCopy.failedToast(), MemoryModelCopy.preparingMessage(RwkvRuntime.Status.Failed("x")))
    }
}
