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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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

                assertEquals(1, ioTasks.pendingCount())
                assertNull(activity.cachedNewCardSortPreviewRows)

                controller.pause()
                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()

                assertNotNull(activity.cachedNewCardSortPreviewRows)
                activity.contentScrollY = 123

                controller.resume()
                shadowOf(Looper.getMainLooper()).idle()

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

            LocalStore(context).use { store ->
                prepareEmptyStore(store)
                activity.store = store
                activity.cachedNewCardSortPreviewRows = null

                activity.renderSettingsStudyBehavior()
                shadowOf(Looper.getMainLooper()).idle()

                assertEquals(1, ioTasks.pendingCount())
                assertNull(activity.cachedNewCardSortPreviewRows)

                ioTasks.runNext()
                shadowOf(Looper.getMainLooper()).idle()

                assertNotNull(activity.cachedNewCardSortPreviewRows)
                assertEquals(0, ioTasks.pendingCount())
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
}
