package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.anki.AnkiDroidGateway
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressAnalyticsLiveRouteInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.progress_live_route")
        )
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(false)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
    }

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        MainActivityRuntimeOverrides.setCollectionGateway(null)
        MainActivityRuntimeOverrides.setWritingRecognizer(null)
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null)
        MainActivityRuntimeOverrides.setInstallPermission(null)
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
        MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        if (::context.isInitialized) {
            context.deleteDatabase("kanji_anki_simple.db")
        }
    }

    @Test
    fun liveStatsRouteUsesSingleStandardBottomNav() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.renderStats() }
            assertVisibleAny("stats overview", "Stats overview", "統計の概要")
            assertVisibleAny("standard stats nav tab", "Stats", "統計")
            assertVisibleAny("standard settings nav tab", "Settings", "設定")
            assertAbsentExactText("Progress")
            assertAbsentExactText("Profile")
            assertAbsentExactText("進捗")
            assertAbsentExactText("プロフィール")
        }
    }

    private fun assertVisibleAny(label: String, vararg texts: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val object2 = texts.firstNotNullOfOrNull { text ->
            device.wait(Until.findObject(By.pkg(pkg).text(text)), 5_000L)
        }
        assertNotNull("Missing visible $label; tried: ${texts.joinToString()}", object2)
    }

    private fun assertAbsentExactText(text: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val object2 = device.wait(Until.findObject(By.pkg(pkg).text(text)), 500L)
        assertNull("Unexpected legacy progress nav text: $text", object2)
    }
}
