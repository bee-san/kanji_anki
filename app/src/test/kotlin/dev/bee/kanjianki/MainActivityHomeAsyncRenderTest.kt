package dev.bee.kanjianki

import android.content.Intent
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.data.LocalStore
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
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityHomeAsyncRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renderHomeShowsLoadingScreenBeforeBackgroundLoadCompletes() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
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
                    background = Executor { backgroundTasks.addLast(it) },
                    postToMain = { mainTasks.addLast(it) },
                ),
            )

            activity.renderHome()
            shadowOf(Looper.getMainLooper()).idle()

            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
            assertEquals(1, backgroundTasks.size)
            assertTrue(mainTasks.isEmpty())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderHomeQueuesHomeLoadAheadOfStatsPrecompute() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val precomputeTasks = ArrayDeque<Runnable>()
        val scheduledOrder = mutableListOf<String>()
        val mainTasks = ArrayDeque<Runnable>()
        val ioTasks = QueueingExecutorService()
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
            replaceField(activity, "io", ioTasks)
            replaceLazyDelegate(
                activity,
                "statsPrecomputeScheduler",
                StatsPrecomputeScheduler(
                    background = Executor {
                        scheduledOrder.add("stats-precompute")
                        precomputeTasks.addLast(it)
                    },
                    isFresh = { false },
                    refresh = { },
                ),
            )
            replaceLazyDelegate(
                activity,
                "asyncHomeRouteLoader",
                AsyncHomeRouteLoader(
                    background = Executor {
                        scheduledOrder.add("home-load")
                        backgroundTasks.addLast(it)
                    },
                    postToMain = { mainTasks.addLast(it) },
                ),
            )

            activity.renderHome()
            assertEquals(1, backgroundTasks.size)
            assertTrue(precomputeTasks.isEmpty())
            assertTrue(mainTasks.isEmpty())
            assertEquals(0, ioTasks.pendingCount())

            backgroundTasks.removeFirst().run()
            assertEquals(1, mainTasks.size)
            assertTrue(precomputeTasks.isEmpty())
            assertEquals(0, ioTasks.pendingCount())

            mainTasks.removeFirst().run()
            assertEquals(1, ioTasks.pendingCount())
            assertTrue(precomputeTasks.isEmpty())

            ioTasks.runNext()

            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
            assertEquals(listOf("home-load", "stats-precompute"), scheduledOrder)
            assertEquals(1, precomputeTasks.size)
            assertTrue(mainTasks.isEmpty())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderFocusQueueShowsLoadingScreenBeforeBackgroundLoadCompletes() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
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
                    background = Executor { backgroundTasks.addLast(it) },
                    postToMain = { mainTasks.addLast(it) },
                ),
            )

            activity.renderFocusQueue()
            shadowOf(Looper.getMainLooper()).idle()

            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
            assertEquals(1, backgroundTasks.size)
            assertTrue(mainTasks.isEmpty())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderDetailShowsLoadingScreenBeforeBackgroundLoadCompletes() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
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
                    background = Executor { backgroundTasks.addLast(it) },
                    postToMain = { mainTasks.addLast(it) },
                ),
            )

            activity.renderDetail("裂", true, "裂")
            shadowOf(Looper.getMainLooper()).idle()

            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
            assertEquals(1, backgroundTasks.size)
            assertTrue(mainTasks.isEmpty())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderFocusQueueShowsEmptyStateAfterBackgroundLoadCompletes() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
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

            LocalStore(context).use { store ->
                prepareEmptyStore(store)
                activity.store = store

                replaceLazyDelegate(
                    activity,
                    "asyncHomeRouteLoader",
                    AsyncHomeRouteLoader(
                        background = Executor { backgroundTasks.addLast(it) },
                        postToMain = { mainTasks.addLast(it) },
                    ),
                )

                activity.renderFocusQueue()
                shadowOf(Looper.getMainLooper()).idle()

                assertEquals(1, backgroundTasks.size)
                backgroundTasks.removeFirst().run()
                assertEquals(1, mainTasks.size)
                mainTasks.removeFirst().run()
                shadowOf(Looper.getMainLooper()).idle()

                val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                assertTrue(contentRoot.childCount > 0)
                val content = latestHomeRouteContent(activity)
                composeRule.setContent {
                    content?.invoke() ?: error("home route content missing")
                }
                composeRule.onNodeWithText(HomeTextCopy.noKanjiQueuedTitle()).assertIsDisplayed()
                composeRule.onNodeWithText(HomeTextCopy.focusQueueNoKanjiQueuedBody()).assertIsDisplayed()
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderRecentMistakesShowsEmptyStateAfterBackgroundLoadCompletes() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
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

            LocalStore(context).use { store ->
                prepareEmptyStore(store)
                activity.store = store

                replaceLazyDelegate(
                    activity,
                    "asyncHomeRouteLoader",
                    AsyncHomeRouteLoader(
                        background = Executor { backgroundTasks.addLast(it) },
                        postToMain = { mainTasks.addLast(it) },
                    ),
                )

                activity.renderRecentMistakes()
                shadowOf(Looper.getMainLooper()).idle()

                assertEquals(1, backgroundTasks.size)
                backgroundTasks.removeFirst().run()
                assertEquals(1, mainTasks.size)
                mainTasks.removeFirst().run()
                shadowOf(Looper.getMainLooper()).idle()

                val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                assertTrue(contentRoot.childCount > 0)
                val content = latestHomeRouteContent(activity)
                composeRule.setContent {
                    content?.invoke() ?: error("home route content missing")
                }
                composeRule.onNodeWithText(HomeTextCopy.noRecentMistakesTitle()).assertIsDisplayed()
                composeRule.onNodeWithText(HomeTextCopy.noRecentMistakesBody()).assertIsDisplayed()
            }
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

    private fun prepareEmptyStore(store: LocalStore) {
        val db = store.writableDatabase
        store.clearSyncMirrorTables(db)
        db.delete(LocalStoreBase.TABLE_STATS_SCREEN_CACHE, null, null)
        db.delete(LocalStoreBase.TABLE_STATS_CACHE_STATE, null, null)
    }

    private fun replaceLazyDelegate(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityHome::class.java.getDeclaredField("$propertyName\$delegate")
        field.isAccessible = true
        field.set(activity, lazyOf(value))
    }

    private fun replaceField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        field.set(activity, value)
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

        override fun isShutdown(): Boolean {
            return shutdown
        }

        override fun isTerminated(): Boolean {
            return shutdown && tasks.isEmpty()
        }

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
            return isTerminated()
        }

        override fun execute(command: Runnable) {
            if (shutdown) {
                throw IllegalStateException("executor is shut down")
            }
            tasks.addLast(command)
        }

        fun pendingCount(): Int {
            return tasks.size
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun latestHomeRouteContent(activity: MainActivity): (@Composable () -> Unit)? {
        val field = MainActivityHome::class.java.getDeclaredField("latestHomeRouteContent")
        field.isAccessible = true
        return field.get(activity) as? (@Composable () -> Unit)
    }

}
