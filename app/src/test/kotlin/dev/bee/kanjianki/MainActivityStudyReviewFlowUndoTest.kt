package dev.bee.kanjianki

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyReviewFlowUndoTest {
    @Test
    fun undoLastRatingSchedulesStatsPrecomputeAsyncInsteadOfRecomputingInline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val startupIo = QueueingExecutorService()
        val startupMaintenance = QueueingExecutorService()
        val undoIo = QueueingExecutorService()
        val maintenanceIo = QueueingExecutorService()
        var refreshCalls = 0
        var widgetRefreshCalls = 0
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val intent = Intent(context, TestMainActivity::class.java)
            val controller = Robolectric.buildActivity(TestMainActivity::class.java, intent)
            val activity = controller.get()
            replaceField(activity, "io", startupIo)
            replaceField(activity, "maintenance", startupMaintenance)
            replaceLazyDelegate(
                activity,
                "statsPrecomputeScheduler",
                StatsPrecomputeScheduler(
                    background = Executor { command -> command.run() },
                    isFresh = { false },
                    refresh = { nowMillis ->
                        refreshCalls += 1
                        activity.store.recomputeStatsSnapshotSynchronously(nowMillis)
                    },
                ),
            )

            LocalStore(context).use { store ->
                clearStatsCache(store)
                val afterReview = studyItem("裂")
                val request = reviewRequest("裂", "undo-token")
                store.saveStudyItem(afterReview)
                store.saveReview(request, "good", 2_000L, afterReview, afterReview)
                activity.store = store
                activity.studyUndoState.capture(
                    StudyReviewActions.AppliedReviewSnapshot("undo-token", afterReview, afterReview),
                    "good",
                    2_000L,
                )

                controller.create().start().resume()
                activity.cancelPendingHomeRouteLoads()
                startupIo.shutdownNow()
                startupMaintenance.shutdownNow()
                replaceField(activity, "io", undoIo)
                replaceField(activity, "maintenance", maintenanceIo)
                installWidgetRefreshRecorder(activity) { widgetRefreshCalls += 1 }
                activity.clearRenderedKanji()
                clearStatsCache(store)

                assertNull(activity.store.cachedStatsSnapshotOrNull())

                activity.undoLastRating()

                // The undo store work itself now runs on the background io executor: the
                // tap only queues the write, nothing renders synchronously.
                assertNull(activity.renderedKanji())
                assertEquals(1, undoIo.pendingCount())

                undoIo.runNext()

                // The undo write ran and rendered the restored kanji. The stats precompute is now
                // queued on the maintenance executor (not io), so io is drained and maintenance
                // holds exactly the precompute work -- keeping the heavy recompute off the
                // route-load path.
                assertEquals("裂", activity.renderedKanji())
                assertEquals(1, widgetRefreshCalls)
                assertEquals(0, undoIo.pendingCount())
                assertEquals(1, maintenanceIo.pendingCount())
                assertNull(activity.store.cachedStatsSnapshotOrNull())

                maintenanceIo.runNext()

                assertEquals(1, refreshCalls)
                assertNotNull(activity.store.cachedStatsSnapshotOrNull())
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

    private fun clearStatsCache(store: LocalStore) {
        val db = store.writableDatabase
        db.delete(LocalStoreBase.TABLE_STATS_SCREEN_CACHE, null, null)
        db.delete(LocalStoreBase.TABLE_STATS_CACHE_STATE, null, null)
    }

    private fun reviewRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, true, false, 0)
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }

    private fun replaceLazyDelegate(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityHome::class.java.getDeclaredField("$propertyName\$delegate")
        field.isAccessible = true
        field.set(activity, lazyOf(value))
    }

    private fun installWidgetRefreshRecorder(activity: MainActivity, onRefresh: () -> Unit) {
        val field = MainActivityStudy::class.java.getDeclaredField("writingReview\$delegate")
        field.isAccessible = true
        val reviewFlow = (field.get(activity) as Lazy<*>).value as MainActivityStudyReviewFlow
        reviewFlow.widgetRefresher = Runnable { onRefresh() }
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

    private class TestMainActivity : MainActivity() {
        private var lastRenderedKanji: String? = null

        override fun renderStudyForKanji(kanji: String?) {
            lastRenderedKanji = kanji
        }

        fun clearRenderedKanji() {
            lastRenderedKanji = null
        }

        fun renderedKanji(): String? {
            return lastRenderedKanji
        }
    }
}
