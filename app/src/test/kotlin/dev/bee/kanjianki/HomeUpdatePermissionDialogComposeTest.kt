package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeUpdatePermissionDialogComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nullModelRendersNothing() {
        composeRule.setContent {
            HomeUpdatePermissionDialog(null)
        }

        composeRule.onNodeWithText("Keep Kani up to date").assertDoesNotExist()
    }

    @Test
    fun allowButtonRunsAllowCallback() {
        val allowed = AtomicInteger()
        val declined = AtomicInteger()

        composeRule.setContent {
            HomeUpdatePermissionDialog(
                HomeUpdatePermissionDialogModels.create(
                    pendingVersion = "v0.5.0",
                    onAllow = allowed::incrementAndGet,
                    onNotNow = declined::incrementAndGet,
                )
            )
        }

        composeRule.onNodeWithText("Keep Kani up to date").assertIsDisplayed()
        composeRule.onNodeWithText("Allow").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, allowed.get())
            assertEquals(0, declined.get())
        }
    }

    @Test
    fun notNowButtonRunsDeclineCallback() {
        val allowed = AtomicInteger()
        val declined = AtomicInteger()

        composeRule.setContent {
            HomeUpdatePermissionDialog(
                HomeUpdatePermissionDialogModels.create(
                    pendingVersion = null,
                    onAllow = allowed::incrementAndGet,
                    onNotNow = declined::incrementAndGet,
                )
            )
        }

        composeRule.onNodeWithText("Not now").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(0, allowed.get())
            assertEquals(1, declined.get())
        }
    }
}
