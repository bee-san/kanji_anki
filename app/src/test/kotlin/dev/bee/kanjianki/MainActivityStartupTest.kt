package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStartupTest {
    @Test
    fun startQueuesBackgroundStartupTasksInsteadOfRunningThemInline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, Intent(context, NoopStartupActivity::class.java))
            val activity = controller.get()
            val ioTasks = QueueingExecutorService()
            val maintenanceTasks = QueueingExecutorService()
            replaceField(activity, "io", ioTasks)
            replaceField(activity, "maintenance", maintenanceTasks)

            controller.create().start().resume()

            // io stays reserved for user-facing route loads. Only the theme-cache warm (which
            // also front-loads any pending DB migration) is queued there at startup; the home
            // route load is not, because NoopStartupActivity overrides renderHome to a no-op.
            assertEquals(1, ioTasks.pendingCount())
            // Background maintenance runs on the separate maintenance executor so it cannot block
            // route loads on cold boot: (1) the scheduler block (auto-sync/auto-update/backup,
            // incl. first-time WorkManager init), and (2) the resume-time update-install gating,
            // which reads auto-update status off the UI thread (ANR fix). The reminder re-arm is
            // deliberately still pending because this no-op activity never settles a real async
            // route. Heavy asset warmup runs on its own dedicated thread.
            assertEquals(2, maintenanceTasks.pendingCount())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    @Config(sdk = [32])
    fun normalLaunchDoesNotPromptForAnkiPermissionBeforeHomeRender() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        registerAnkiDroidProvider(context)

        val controller = Robolectric.buildActivity(
            PermissionTrackingStartupActivity::class.java,
            Intent(context, PermissionTrackingStartupActivity::class.java),
        )
        val activity = controller.get()

        controller.create().start().resume()

        assertEquals(1, activity.renderHomeCalls)
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun androidXStartupProviderIsRemovedFromMergedManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val startupAuthority = context.packageName + ".androidx-startup"

        assertNull(
            "Kani does not use AndroidX Startup initializers; keep the provider out of cold startup.",
            context.packageManager.resolveContentProvider(startupAuthority, PackageManager.GET_META_DATA),
        )
    }

    @Test
    fun coldLaunchHelpersStayLazyUntilRouteNeedsThem() {
        assertLazyDelegates(
            MainActivityBase::class.java,
            "permissionHandler",
            "writingRecognizerProvider",
            "studyPlanProvider",
            "shellHost",
            "startup",
            "activityLifecycle",
        )
        assertLazyDelegates(MainActivityHome::class.java, "focusQueue", "browseDetail")
        assertLazyDelegates(MainActivityGames::class.java, "gameEngine", "gameRandom")
        assertLazyDelegates(
            MainActivityStudy::class.java,
            "flashcardUi",
            "writingUi",
            "writingFlow",
            "writingCheck",
            "writingReview",
            "doneActions",
            "choiceSessions",
            "studyProgress",
            "moreNewCards",
            "studyState",
            "writingSession",
            "dictionaryLookupProvider",
            "studyQueueCoordinator",
        )
    }

    @Test
    fun screenshotLaunchAppliesRequestedThemeChoiceBeforeRendering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val intent = Intent(context, NoopStartupActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_THEME, "dark")
            }
            val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, intent)
            val activity = controller.get()

            controller.create().start().resume()

            assertEquals(KaniThemeChoice.DARK, activity.screenshotThemeChoiceOverride)
            assertEquals(KaniThemeChoice.DARK, activity.store.appThemeChoice())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun screenshotLaunchAppliesRequestedLocaleBeforeRendering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val previousLocale = Locale.getDefault()
        try {
            val intent = Intent(context, NoopStartupActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_LOCALE, "ja")
            }
            val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, intent)

            controller.create().start().resume()

            assertEquals("ja", Locale.getDefault().language)
        } finally {
            Locale.setDefault(previousLocale)
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun screenshotLaunchReadsRequestedScrollPositionAndOffset() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, NoopStartupActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_SCROLL_POSITION, "middle")
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_SCROLL_Y, 1080)
        }
        val controller = Robolectric.buildActivity(NoopStartupActivity::class.java, intent)
        val activity = controller.get()

        controller.create().start().resume()

        assertEquals("middle", activity.screenshotScrollPositionLabel())
        assertEquals(1080, activity.screenshotScrollY())
    }

    private class NoopStartupActivity : MainActivity() {
        override fun renderHome() {
            // Keep the test focused on startup scheduling, not home rendering.
        }
    }

    private class PermissionTrackingStartupActivity : MainActivity() {
        var renderHomeCalls = 0

        override fun renderHome() {
            renderHomeCalls += 1
        }
    }

    private fun registerAnkiDroidProvider(context: Context) {
        shadowOf(context.packageManager).addOrUpdateProvider(
            ProviderInfo().apply {
                authority = "com.ichi2.anki.api.provider"
                name = "FakeAnkiDroidProvider"
                packageName = "com.ichi2.anki"
            },
        )
    }

    private fun replaceField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        field.set(activity, value)
    }

    private fun assertLazyDelegates(owner: Class<*>, vararg propertyNames: String) {
        for (propertyName in propertyNames) {
            val delegateField = owner.declaredFields.firstOrNull { it.name == "$propertyName\$delegate" }
            assertNotNull(
                "${owner.simpleName}.$propertyName should stay lazy to keep cold route startup lean.",
                delegateField,
            )
            assertTrue(
                "${owner.simpleName}.$propertyName should be backed by kotlin.Lazy.",
                Lazy::class.java.isAssignableFrom(delegateField!!.type),
            )
        }
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private class QueueingExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            val remaining = tasks.toMutableList()
            tasks.clear()
            return remaining
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun pendingCount(): Int = tasks.size
    }
}
