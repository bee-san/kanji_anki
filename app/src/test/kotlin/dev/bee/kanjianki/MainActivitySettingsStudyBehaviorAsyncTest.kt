package dev.bee.kanjianki

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreSchema
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The settings routes render asynchronously: the screen model (which reads many settings from the
 * SQLite-backed store) is built on the background [io] executor and composed on the main thread
 * when ready, so tapping into settings responds well under the 1s latency budget. These tests
 * inject a controllable [io] executor and route loader so the off-main build, the deferred
 * new-card-sort preview refresh, and the resume-after-pause re-render can be driven step by step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySettingsStudyBehaviorAsyncTest {
    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.v2.createComposeRule()

    @Test
    fun resumeRerendersStudyBehaviorWhenPreviewRefreshCompletesWhilePaused() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        val ioTasks = QueueingExecutorService()
        val maintenanceTasks = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            }
            val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
                .create()
                .start()
                .resume()
            val activity = controller.get()
            activity.cancelPendingHomeRouteLoads()
            activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            replaceBaseField(activity, "io", ioTasks)
            // The resume below (after the screenshot extra is cleared) queues the resume-time
            // update-install check on the maintenance executor. Stub it so that background work
            // is captured deterministically instead of running on a real thread against the
            // test's open LocalStore; the test only drains io.
            replaceBaseField(activity, "maintenance", maintenanceTasks)
            installRouteLoader(activity, ioTasks)

            LocalStore(context).use { store ->
                store.saveSuccessfulSync(
                    RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                    emptyList(),
                    dashboardRows(3),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    1_000L,
                    2_000L,
                    null,
                )
                activity.store = store
                activity.cachedNewCardSortPreviewRows = null

                activity.renderSettingsStudyBehavior()
                shadowOf(Looper.getMainLooper()).idle()

                // The screen model is built off-main, not on the click path.
                assertEquals(1, ioTasks.pendingCount())
                assertNull(activity.cachedNewCardSortPreviewRows)

                // Build the model on the background thread; it schedules the preview refresh and
                // posts the rendered content back to the main thread.
                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(1, ioTasks.pendingCount())
                assertNull(activity.cachedNewCardSortPreviewRows)

                controller.pause()
                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()

                // Preview computed while paused: cache is populated but the re-render is deferred.
                assertNotNull(activity.cachedNewCardSortPreviewRows)
                assertEquals(0, ioTasks.pendingCount())
                activity.contentScrollY = 123

                controller.resume()
                drainAsync(ioTasks)

                assertEquals(0, activity.contentScrollY)
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            context.deleteDatabase(LocalStoreSchema.DB_NAME)
        }
    }

    @Test
    fun renderStudyBehaviorSchedulesPreviewRefreshInsteadOfBuildingPreviewOnMain() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ioTasks = QueueingExecutorService()
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
            replaceBaseField(activity, "io", ioTasks)
            installRouteLoader(activity, ioTasks)

            LocalStore(context).use { store ->
                prepareEmptyStore(store)
                activity.store = store
                activity.cachedNewCardSortPreviewRows = null

                activity.renderSettingsStudyBehavior()
                shadowOf(Looper.getMainLooper()).idle()

                // Model build is queued off-main.
                assertEquals(1, ioTasks.pendingCount())
                assertNull(activity.cachedNewCardSortPreviewRows)

                // Building the model schedules the preview refresh as a separate background task
                // rather than computing it on the main thread.
                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(1, ioTasks.pendingCount())
                assertNull(activity.cachedNewCardSortPreviewRows)

                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()
                assertNotNull(activity.cachedNewCardSortPreviewRows)

                drainAsync(ioTasks)
                assertEquals(0, ioTasks.pendingCount())
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderStudyBehaviorUsesFreshPreviewCacheWithoutSchedulingRefresh() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ioTasks = QueueingExecutorService()
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
            replaceBaseField(activity, "io", ioTasks)
            installRouteLoader(activity, ioTasks)

            LocalStore(context).use { store ->
                prepareEmptyStore(store)
                activity.store = store
                val cached = SettingsNewCardSortPreviewRowsSnapshot(
                    sourceRows = emptyList(),
                    sourceVersion = store.newCardSortPreviewCacheVersion(),
                    previewRowsByMode = emptyMap(),
                    previewWarningsByMode = emptyMap(),
                )
                activity.cachedNewCardSortPreviewRows = cached

                activity.renderSettingsStudyBehavior()
                shadowOf(Looper.getMainLooper()).idle()

                // Only the model build is queued; no preview refresh yet.
                assertEquals(1, ioTasks.pendingCount())

                // Building the model with a fresh preview cache does not schedule another refresh.
                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(0, ioTasks.pendingCount())
                assertSame(cached, activity.cachedNewCardSortPreviewRows)
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

    private fun dashboardRows(count: Int): List<RecordsImportModels.DashboardRow> {
        return List(count) { index ->
            RecordsImportModels.DashboardRow(
                "字$index",
                index + 1,
                "meaning $index",
                "reading $index",
                "browser $index",
                index % 13,
                "reason-${index % 4}",
                "reason text $index",
                1,
                0,
                0,
                emptyList<RecordsImportModels.Example>(),
            )
        }
    }

    private fun replaceBaseField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        field.set(activity, value)
    }

    /**
     * Replaces the lazily-created [AsyncHomeRouteLoader] with one whose background executor is the
     * controllable [ioTasks] queue and whose main dispatch posts to the real (Robolectric-driven)
     * main looper, so the whole async settings render serializes on a single inspectable queue.
     */
    private fun installRouteLoader(activity: MainActivity, ioTasks: QueueingExecutorService) {
        val field = MainActivityHome::class.java.getDeclaredField("asyncHomeRouteLoader\$delegate")
        field.isAccessible = true
        field.set(
            activity,
            lazyOf(
                AsyncHomeRouteLoader(
                    background = ioTasks,
                    postToMain = { runnable -> activity.main.post(runnable) },
                ),
            ),
        )
    }

    private fun drainAsync(ioTasks: QueueingExecutorService) {
        var guard = 0
        while (ioTasks.pendingCount() > 0 && guard < MAX_DRAIN_STEPS) {
            ioTasks.runNext()
            shadowOf(Looper.getMainLooper()).idle()
            guard += 1
        }
        shadowOf(Looper.getMainLooper()).idle()
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

    private companion object {
        const val MAX_DRAIN_STEPS = 50
    }
}
