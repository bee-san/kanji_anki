package dev.bee.kanjianki

import android.content.Intent
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsChromeLocaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeActionModelsUseJapaneseSettingsLabel() {
        withLocale(Locale.JAPAN) {
            withMainActivity { activity ->
                val labels = homeActionModels(activity).map { action -> action.label }

                assertTrue(labels.contains("設定"))
            }
        }
    }

    @Test
    fun settingsHeroFactoryUsesJapaneseTitle() {
        withLocale(Locale.JAPAN) {
            val model = settingsAutomationHeroModel(
                current = RecordsSyncModels.Settings.kikuDefaults(),
                reminder = LocalStoreBase.ReminderSettings(false, 8, 5),
                autoSync = LocalStoreBase.AutoSyncSettings(false, false, 7, 30, 0L, 0L, 0L),
                autoUpdate = LocalStoreBase.AutoUpdateStatus(false, 0L, "", "", "", ""),
                notificationsAllowed = true,
            )

            assertEquals("概要", model.cockpitLabel)
            assertEquals("設定", model.title)
        }
    }

    @Test
    fun settingsLoadingRouteUsesJapaneseTitle() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
        withLocale(Locale.JAPAN) {
            withMainActivity { activity ->
                replaceLazyDelegate(
                    activity,
                    "statsPrecomputeScheduler",
                    StatsPrecomputeScheduler(
                        background = Executor { },
                        isFresh = { true },
                        refresh = { },
                    ),
                )
                replaceLazyDelegate(
                    activity,
                    "asyncHomeRouteLoader",
                    AsyncHomeRouteLoader(
                        background = Executor { task -> backgroundTasks.addLast(task) },
                        postToMain = { task -> mainTasks.addLast(task) },
                        loadingTaskScheduler = LoadingTaskScheduler { _, task ->
                            task.run()
                            LoadingTaskHandle { }
                        },
                    ),
                )

                activity.renderSettings()
                assertEquals(1, backgroundTasks.size)

                backgroundTasks.removeFirst().run()
                assertTrue(mainTasks.isNotEmpty())
                mainTasks.removeFirst().run()
                shadowOf(Looper.getMainLooper()).idle()

                val content = latestHomeRouteContent(activity) ?: error("home route content missing")
                composeRule.setContent { content() }
                composeRule.onAllNodesWithText("設定").assertCountEquals(1)
            }
        }
    }

    @Test
    fun studyTopBarUsesJapaneseSettingsDescription() {
        withLocale(Locale.JAPAN) {
            composeRule.setContent {
                StudyTopBar(
                    completed = 1,
                    target = 4,
                    fraction = 0.25f,
                    onClose = {},
                    onSettings = {},
                )
            }

            composeRule.onNodeWithContentDescription("設定").assertIsDisplayed()
        }
    }

    private fun withMainActivity(block: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            }
            val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .get()
            activity.cancelPendingHomeRouteLoads()
            activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            block(activity)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(
            android.content.Context::class.java,
            List::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun replaceLazyDelegate(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityHome::class.java.getDeclaredField("$propertyName\$delegate")
        field.isAccessible = true
        field.set(activity, lazyOf(value))
    }

    @Suppress("UNCHECKED_CAST")
    private fun latestHomeRouteContent(activity: MainActivity): (@Composable () -> Unit)? {
        val field = MainActivityHome::class.java.getDeclaredField("latestHomeRouteContent")
        field.isAccessible = true
        return field.get(activity) as? (@Composable () -> Unit)
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
