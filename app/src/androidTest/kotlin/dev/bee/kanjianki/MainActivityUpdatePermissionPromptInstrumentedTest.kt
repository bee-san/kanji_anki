package dev.bee.kanjianki

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.updatecore.UpdateTextPolicy
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUpdatePermissionPromptInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
        clearUpdatesCache()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.update_prompt_no_anki")
        )
        MainActivityRuntimeOverrides.setInstallPermission(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        KaniTestDatabase.delete(context)
        clearUpdatesCache()
    }

    @Test
    fun homePromptAsksOnceAndRecordsDecline() {
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(System.currentTimeMillis(), "Already on 0.0.1.", "", "", "")
        }
        MainActivityRuntimeOverrides.setInstallPermission(false)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(PROMPT_TITLE)
            composeRule.onAllNodes(hasText(UpdateTextPolicy.installPermissionDialogNotNowLabel()))
                .onFirst()
                .performClick()
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                composeRule.onAllNodes(hasText(PROMPT_TITLE)).fetchSemanticsNodes().isEmpty()
            }
            scenario.onActivity { activity ->
                assertTrue(activity.store.installPermissionPromptShown())
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            waitForText(HOME_TITLE)
            composeRule.onAllNodes(hasText(HOME_TITLE)).onFirst().assertIsDisplayed()
            composeRule.onAllNodes(hasText(PROMPT_TITLE)).assertCountEquals(0)
        }
    }

    @Test
    fun homePromptReturnsForNewPendingVersion() {
        LocalStore(context).use { store ->
            store.recordInstallPermissionPrompted("")
            store.recordAutoUpdateResult(
                System.currentTimeMillis(),
                "Android needs permission to finish installing.",
                "v9.9.9",
                "kani-test.apk",
                "waiting",
            )
        }
        MainActivityRuntimeOverrides.setInstallPermission(false)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(PROMPT_TITLE)
            composeRule.onAllNodes(hasText(UpdateTextPolicy.installPermissionDialogMessage("v9.9.9")))
                .onFirst()
                .assertIsDisplayed()
            composeRule.onAllNodes(hasText(UpdateTextPolicy.installPermissionDialogNotNowLabel()))
                .onFirst()
                .performClick()
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                composeRule.onAllNodes(hasText(PROMPT_TITLE)).fetchSemanticsNodes().isEmpty()
            }
            scenario.onActivity { activity ->
                assertEquals("v9.9.9", activity.store.installPermissionPromptLastVersion())
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            waitForText(HOME_TITLE)
            composeRule.onAllNodes(hasText(PROMPT_TITLE)).assertCountEquals(0)
        }
    }

    @Test
    fun resumeInstallConsumesVerifiedPendingUpdateAutomatically() {
        val updatesDir = File(context.cacheDir, "updates")
        assertTrue(updatesDir.mkdirs() || updatesDir.isDirectory)
        FileOutputStream(File(updatesDir, "kani-test.apk")).use { output ->
            output.write(byteArrayOf(1, 2, 3))
        }
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(
                System.currentTimeMillis(),
                "Android needs permission to finish installing.",
                "v9.9.9",
                "kani-test.apk",
                "waiting",
            )
        }
        MainActivityRuntimeOverrides.setInstallPermission(true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(HOME_TITLE)
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                var pendingCleared = false
                scenario.onActivity { activity ->
                    pendingCleared = !activity.store.autoUpdateStatus().hasPendingUpdate()
                }
                pendingCleared
            }
            scenario.onActivity { activity ->
                val status = activity.store.autoUpdateStatus()
                assertFalse(status.hasPendingUpdate())
                assertEquals("APK metadata could not be read. Install blocked.", status.lastResult)
            }
            composeRule.onAllNodes(hasText(PROMPT_TITLE)).assertCountEquals(0)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clearUpdatesCache() {
        File(context.cacheDir, "updates").listFiles()?.forEach { file -> file.delete() }
    }

    companion object {
        private const val HOME_TITLE = "Kani"
        private const val PROMPT_TITLE = "Keep Kani up to date"
        private const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
