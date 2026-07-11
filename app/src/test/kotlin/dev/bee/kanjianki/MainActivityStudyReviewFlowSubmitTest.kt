package dev.bee.kanjianki

import android.content.Intent
import android.database.DatabaseUtils
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
import org.robolectric.shadows.ShadowToast
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
    fun submitReviewSuppressesDuplicateBeforeAndAfterWorkerExecution() {
        withReviewActivity("裂") { activity, store, reviewIo, session ->
            ShadowToast.reset()
            assertTrue(
                activity.submitReview(
                    MainActivityBase.RATING_GOOD,
                    false,
                    interactionSource = "card",
                )
            )
            assertFalse(
                activity.submitReview(
                    MainActivityBase.RATING_GOOD,
                    false,
                    interactionSource = "action-bar",
                )
            )

            // The duplicate is rejected at tap time, before it can queue any store
            // work, toast, or replacement Study route.
            assertFalse(store.hasConsumedToken(session.token))
            assertEquals(1, reviewIo.pendingCount())
            assertEquals(0, activity.renderCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(1, store.consumedTokens().size)
            assertEquals(1, activity.renderCount())
            assertEquals(1, ShadowToast.shownToastCount())

            // The token stays claimed after success. A late duplicate is also a
            // complete no-op rather than a persisted duplicate that still toasts and
            // reloads Study.
            assertFalse(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(0, reviewIo.pendingCount())
            assertEquals(1, store.consumedTokens().size)
            assertEquals(1, activity.renderCount())
            assertEquals(1, ShadowToast.shownToastCount())
        }
    }

    @Test
    fun failedReviewProcessingReleasesTokenForRetry() {
        withReviewActivity("衡") { activity, store, reviewIo, session ->
            // Simulate a processing-time dependency failure after the task has been
            // accepted and dequeued. The review wrapper must release the in-memory
            // claim because persistence never consumed the token.
            val swipeFeedback = StudySwipeFeedbackState().apply {
                update(96f)
                commit(MainActivityBase.RATING_GOOD)
            }
            activity.flashcardSwipeFeedback = swipeFeedback
            clearStore(activity)
            assertTrue(
                activity.submitReview(
                    MainActivityBase.RATING_GOOD,
                    false,
                    interactionSource = "card",
                )
            )
            assertEquals(1, reviewIo.pendingCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(0, reviewIo.pendingCount())
            assertEquals(0, activity.renderCount())
            assertFalse(swipeFeedback.committed)
            assertEquals(StudySwipeReleaseKind.SETTLE_BACK, swipeFeedback.releaseRequest.kind)

            activity.store = store
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            assertEquals(1, reviewIo.pendingCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(1, activity.renderCount())
        }
    }

    @Test
    fun enqueueRejectionReturnsFalseSoOptimisticSwipeCanReset() {
        withReviewActivity("拒") { activity, _, reviewIo, _ ->
            reviewIo.shutdown()
            val swipeFeedback = StudySwipeFeedbackState().apply { update(-104f) }

            val accepted = submitReviewWithSwipeFeedback(swipeFeedback, MainActivityBase.RATING_AGAIN) {
                activity.submitReview(
                    MainActivityBase.RATING_AGAIN,
                    false,
                    interactionSource = "card",
                )
            }

            assertFalse(accepted)
            assertFalse(swipeFeedback.committed)
            assertEquals(StudySwipeReleaseKind.SETTLE_BACK, swipeFeedback.releaseRequest.kind)
        }
    }

    @Test
    fun loggedChoiceUsesSameGateSoDuplicateWritesOneChoiceRowAndOneReview() {
        withReviewActivity("拉") { activity, store, reviewIo, session ->
            activity.submitLoggedChoiceReview(
                "拉",
                "choice-signature",
                "提",
                false,
                RecordsBase.LadderRung.MEANING_KANJI,
            )
            activity.submitLoggedChoiceReview(
                "拉",
                "choice-signature",
                "提",
                false,
                RecordsBase.LadderRung.MEANING_KANJI,
            )

            assertEquals(1, reviewIo.pendingCount())
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(1L, choiceLogCount(store, "拉"))
            assertEquals(1, store.consumedTokens().size)
            assertEquals(1, activity.renderCount())
        }
    }

    private fun withReviewActivity(
        kanji: String,
        test: (TestMainActivity, LocalStore, QueueingExecutorService, RecordsSchedulerModels.StudySession) -> Unit,
    ) {
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
                activity.clearRenderCount()
                test(activity, store, reviewIo, session)
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun choiceLogCount(store: LocalStore, targetKanji: String): Long {
        return DatabaseUtils.longForQuery(
            store.readableDatabase,
            "SELECT COUNT(*) FROM similar_kanji_review_log WHERE target_kanji = ?",
            arrayOf(targetKanji),
        )
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

    private fun clearStore(activity: MainActivity) {
        val field = MainActivityBase::class.java.getDeclaredField("store")
        field.isAccessible = true
        field.set(activity, null)
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
        private var studyRenderCount = 0

        override fun renderStudy() {
            studyRenderCount += 1
        }

        fun clearRenderCount() {
            studyRenderCount = 0
        }

        fun renderCount(): Int {
            return studyRenderCount
        }
    }
}
