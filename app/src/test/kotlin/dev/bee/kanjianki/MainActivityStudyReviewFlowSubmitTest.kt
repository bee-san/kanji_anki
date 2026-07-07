package dev.bee.kanjianki

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.LocalStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * Verifies that answering a card performs its store writes on the background io
 * executor instead of the main-thread click handler, while keeping the persisted
 * review-token idempotency (double submits of the same session stay duplicates).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyReviewFlowSubmitTest {
    @Test
    fun submitReviewQueuesStoreWritesOnBackgroundExecutorAndStaysIdempotent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val startupIo = QueueingExecutorService()
        val startupMaintenance = QueueingExecutorService()
        val reviewIo = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val intent = Intent(context, TestMainActivity::class.java)
            val controller = Robolectric.buildActivity(TestMainActivity::class.java, intent)
            val activity = controller.get()
            replaceField(activity, "io", startupIo)
            replaceField(activity, "maintenance", startupMaintenance)

            LocalStore(context).use { store ->
                activity.store = store
                controller.create().start().resume()
                activity.cancelPendingHomeRouteLoads()
                startupIo.shutdownNow()
                startupMaintenance.shutdownNow()
                replaceField(activity, "io", reviewIo)

                val item = studyItem("裂")
                store.saveStudyItem(item)
                val session = RecordsSchedulerModels.StudySession(
                    item,
                    null,
                    item.activeToken,
                    BridgeScheduler.TASK_KANJI_MEANING,
                    false,
                    "split",
                )
                activity.activeSession = session
                activity.clearRenderedStudy()

                activity.submitReview(MainActivityBase.RATING_GOOD, false)

                // The click handler only queued the write: nothing persisted yet and
                // no re-render happened synchronously.
                assertFalse(store.hasConsumedToken(session.token))
                assertEquals(1, reviewIo.pendingCount())
                assertFalse(activity.renderedStudy())

                reviewIo.runNext()
                shadowOf(Looper.getMainLooper()).idle()

                assertTrue(store.hasConsumedToken(session.token))
                assertTrue(activity.renderedStudy())
                assertEquals(1, store.consumedTokens().size)

                // A queued double-tap of the same session stays a no-op duplicate.
                activity.submitReview(MainActivityBase.RATING_GOOD, false)
                reviewIo.runNext()
                shadowOf(Looper.getMainLooper()).idle()

                assertEquals(1, store.consumedTokens().size)
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

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
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
        private var studyRendered = false

        override fun renderStudy() {
            studyRendered = true
        }

        fun clearRenderedStudy() {
            studyRendered = false
        }

        fun renderedStudy(): Boolean {
            return studyRendered
        }
    }
}
