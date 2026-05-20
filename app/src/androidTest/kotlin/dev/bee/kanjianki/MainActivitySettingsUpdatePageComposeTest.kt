package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class MainActivitySettingsUpdatePageComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersUpdatePageCopyAndWiresActions() {
        var homeClicked = false
        var backClicked = false
        var checkClicked = false
        var installClicked = false
        var toggleClicked = false

        composeRule.setContent {
            SettingsUpdatePage(
                model = SettingsUpdatePageModel(
                    title = SettingsTextCopy.updatePageTitle(),
                    body = SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME),
                    onHome = { homeClicked = true },
                    onBack = { backClicked = true },
                    onCheckForUpdate = { checkClicked = true },
                    panel = SettingsUpdatePanelModel(
                        title = SettingsTextCopy.automaticUpdatesTitle(),
                        statusLine = SettingsTextCopy.autoUpdatePanelStatus(true),
                        statusColor = ComposeColor(0xFF00AEB5),
                        lastCheckLine = SettingsTextCopy.autoUpdateLastCheckLine("Today at 09:15"),
                        lastResultLine = SettingsTextCopy.autoUpdateLastResultLine("APK verified. Android installer started."),
                        installPermissionLine = SettingsTextCopy.installPermissionLine(true),
                        installPermissionColor = ComposeColor(0xFF00AEB5),
                        hasPendingUpdate = true,
                        pendingVersionLine = SettingsTextCopy.verifiedApkReadyLine("v0.4.34"),
                        pendingMessageLine = SettingsTextCopy.pendingUpdateFallback(),
                        canInstallUpdates = true,
                        onInstallVerifiedUpdate = { installClicked = true },
                        onOpenInstallSettings = {},
                        onToggleAutomaticUpdates = { toggleClicked = true },
                        automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(true)
                    )
                )
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.updatePageTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.automaticUpdatesTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdatePanelStatus(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateLastCheckLine("Today at 09:15")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateLastResultLine("APK verified. Android installer started.")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.installPermissionLine(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.verifiedApkReadyLine("v0.4.34")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.pendingUpdateFallback()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.installVerifiedUpdateLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.automaticUpdatesToggleLabel(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.checkForUpdateLabel()).assertIsDisplayed()

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.installVerifiedUpdateLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.automaticUpdatesToggleLabel(true)).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.checkForUpdateLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
            assertTrue(backClicked)
            assertTrue(installClicked)
            assertTrue(toggleClicked)
            assertTrue(checkClicked)
        }
    }

    @Test
    fun rendersUpdateRunProgressAndWiresNavigation() {
        var homeClicked = false
        var backClicked = false

        composeRule.setContent {
            SettingsUpdateRunScreen(
                model = SettingsUpdateRunModel(
                    title = "Checking release",
                    body = "Downloading metadata and verifying assets.",
                    progressLabel = "Checking GitHub Releases",
                    onHome = { homeClicked = true },
                    onBack = { backClicked = true }
                )
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("Checking release").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading metadata and verifying assets.").assertIsDisplayed()
        composeRule.onNodeWithText("Checking GitHub Releases").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Checking GitHub Releases").assertIsDisplayed()

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
            assertTrue(backClicked)
        }
    }
}
