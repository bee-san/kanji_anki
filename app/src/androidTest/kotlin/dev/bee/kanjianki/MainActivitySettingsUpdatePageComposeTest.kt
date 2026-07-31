package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        var betaClicked = false

        composeRule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsUpdatePage(
                    model = SettingsUpdatePageModel(
                    title = SettingsTextCopy.updatePageTitle(),
                    onHome = { homeClicked = true },
                    onBack = { backClicked = true },
                    onCheckForUpdate = { checkClicked = true },
                    panel = SettingsUpdatePanelModel(
                        title = SettingsTextCopy.automaticUpdatesTitle(),
                        statusLine = SettingsTextCopy.autoUpdatePanelStatus(true),
                        statusColor = MainActivityUiSupport.TEAL,
                        installedVersionLine = SettingsTextCopy.installedVersionLine("v0.4.33"),
                        latestVersionLine = SettingsTextCopy.latestVersionLine("v0.4.34"),
                        lastCheckLine = SettingsTextCopy.autoUpdateLastCheckLine("Today at 09:15"),
                        lastResultLine = SettingsTextCopy.autoUpdateLastResultLine("APK verified. Android installer started."),
                        installPermissionLine = SettingsTextCopy.installPermissionLine(true),
                        installPermissionColor = MainActivityUiSupport.TEAL,
                        hasPendingUpdate = true,
                        pendingVersionLine = SettingsTextCopy.verifiedApkReadyLine("v0.4.34"),
                        pendingMessageLine = SettingsTextCopy.pendingUpdateFallback(true),
                        canInstallUpdates = true,
                        onInstallVerifiedUpdate = { installClicked = true },
                        onOpenInstallSettings = {},
                        onToggleAutomaticUpdates = { toggleClicked = true },
                        automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(true),
                        showAutoUpdateInBackground = false,
                        autoUpdateInBackgroundLabel = SettingsTextCopy.autoUpdateInBackgroundLabel(),
                        onAutoUpdateInBackground = {},
                        betaUpdatesEnabled = false,
                        betaUpdatesToggleLabel = SettingsTextCopy.betaUpdatesToggleLabel(false),
                        betaUpdatesDescription = SettingsTextCopy.betaUpdatesDescription(),
                        onToggleBetaUpdates = { betaClicked = true },
                    )
                )
                )
            }
        }

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.updatePageTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.automaticUpdatesTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdatePanelStatus(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.installedVersionLine("v0.4.33")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.latestVersionLine("v0.4.34")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateLastCheckLine("Today at 09:15")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateLastResultLine("APK verified. Android installer started.")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.installPermissionLine(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.verifiedApkReadyLine("v0.4.34")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.pendingUpdateFallback(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.installVerifiedUpdateLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.automaticUpdatesToggleLabel(true)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.betaUpdatesDescription()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.betaUpdatesToggleLabel(false)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.checkForUpdateLabel()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateInBackgroundLabel()).assertDoesNotExist()

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).performScrollTo().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).performScrollTo().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.installVerifiedUpdateLabel()).performScrollTo().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.automaticUpdatesToggleLabel(true)).performScrollTo().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.betaUpdatesToggleLabel(false)).performScrollTo().performClick()
        composeRule.onNodeWithText(SettingsTextCopy.checkForUpdateLabel()).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
            assertTrue(backClicked)
            assertTrue(installClicked)
            assertTrue(toggleClicked)
            assertTrue(betaClicked)
            assertTrue(checkClicked)
        }
    }

    @Test
    fun rendersUpdateOverviewPanelAndWiresOpenUpdater() {
        var openClicked = false
        var backgroundClicked = false
        composeRule.setContent {
            SettingsUpdateOverviewPanel(
                model = SettingsUpdateOverviewPanelModel(
                    panel = SettingsUpdatePanelModel(
                        title = SettingsTextCopy.appUpdatesTitle(),
                        statusLine = SettingsTextCopy.autoUpdatePanelStatus(false),
                        statusColor = MainActivityUiSupport.MUTED,
                        installedVersionLine = SettingsTextCopy.installedVersionLine("v0.4.33"),
                        latestVersionLine = SettingsTextCopy.latestVersionLine(null),
                        lastCheckLine = SettingsTextCopy.autoUpdateLastCheckLine("not yet"),
                        lastResultLine = SettingsTextCopy.autoUpdateLastResultLine("No result yet."),
                        installPermissionLine = SettingsTextCopy.installPermissionLine(false),
                        installPermissionColor = MainActivityUiSupport.CORAL,
                        hasPendingUpdate = false,
                        pendingVersionLine = null,
                        pendingMessageLine = null,
                        canInstallUpdates = false,
                        onInstallVerifiedUpdate = {},
                        onOpenInstallSettings = {},
                        onToggleAutomaticUpdates = {},
                        automaticUpdatesToggleLabel = SettingsTextCopy.automaticUpdatesToggleLabel(false),
                        showAutoUpdateInBackground = true,
                        autoUpdateInBackgroundLabel = SettingsTextCopy.autoUpdateInBackgroundLabel(),
                        onAutoUpdateInBackground = { backgroundClicked = true },
                        betaUpdatesEnabled = false,
                        betaUpdatesToggleLabel = SettingsTextCopy.betaUpdatesToggleLabel(false),
                        betaUpdatesDescription = SettingsTextCopy.betaUpdatesDescription(),
                        onToggleBetaUpdates = {},
                    ),
                    openUpdaterLabel = SettingsTextCopy.openUpdaterLabel(),
                    onOpenUpdater = { openClicked = true }
                )
            )
        }

        composeRule.onNodeWithText(SettingsTextCopy.appUpdatesTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.installedVersionLine("v0.4.33")).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.latestVersionLine(null)).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.setupAppInstallsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateInBackgroundLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.autoUpdateInBackgroundLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.openUpdaterLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.openUpdaterLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(openClicked)
            assertTrue(backgroundClicked)
        }
    }

    @Test
    fun rendersUpdateRunProgressAndWiresNavigation() {
        var homeClicked = false
        var backClicked = false

        composeRule.setContent {
            SettingsUpdateRunScreen(
                model = SettingsUpdateRunModel(
                    title = "Checking for updates",
                    progressLabel = "Checking releases",
                    onHome = { homeClicked = true },
                    onBack = { backClicked = true }
                )
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).assertIsDisplayed()
        composeRule.onNodeWithText("Checking for updates").assertIsDisplayed()
        composeRule.onNodeWithText("Checking releases").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Checking releases").assertIsDisplayed()

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).performClick()
        composeRule.onNodeWithText(SettingsTextCopy.backToSettingsLabel()).performClick()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
            assertTrue(backClicked)
        }
    }
}
