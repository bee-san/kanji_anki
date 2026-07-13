package dev.bee.kanjianki

import android.content.Context
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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
    @Before
    fun clearPendingAnswerBeforeTest() {
        clearPendingAnswerPreferences()
    }

    @After
    fun clearPendingAnswerAfterTest() {
        clearPendingAnswerPreferences()
    }

    @Test
    fun correctReviewAppliesOnceButDoesNotAdvanceUntilContinue() {
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

            val feedback = activity.studyAnswerFeedbackState!!
            assertEquals(StudyAnswerOutcome.CORRECT, feedback.outcome)
            assertTrue(feedback.feedbackVisible)
            assertFalse(feedback.continueEnabled)
            assertFalse(store.hasConsumedToken(session.token))
            assertEquals(1, reviewIo.pendingCount())
            assertEquals(0, activity.renderCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(1, store.consumedTokens().size)
            assertEquals(0, activity.renderCount())
            assertEquals(session.token, activity.activeSession!!.token)
            assertTrue(feedback.continueEnabled)
            assertEquals(1, ShadowToast.shownToastCount())

            // Advancing time cannot replace the answered card. Only Continue may
            // request the next route, and repeated taps remain one-shot.
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
            assertEquals(0, activity.renderCount())
            assertTrue(activity.continueAfterStudyAnswer())
            assertFalse(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())

            assertFalse(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            assertEquals(0, reviewIo.pendingCount())
            assertEquals(1, store.consumedTokens().size)
            assertEquals(1, activity.renderCount())
            assertEquals(1, ShadowToast.shownToastCount())
        }
    }

    @Test
    fun wrongReviewAlsoPersistsFeedbackUntilContinue() {
        withReviewActivity("誤") { activity, store, reviewIo, session ->
            assertTrue(activity.submitReview(MainActivityBase.RATING_AGAIN, false))
            val feedback = activity.studyAnswerFeedbackState!!
            assertEquals(StudyAnswerOutcome.INCORRECT, feedback.outcome)

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)

            assertTrue(store.hasConsumedToken(session.token))
            assertTrue(feedback.continueEnabled)
            assertEquals(session.token, activity.activeSession!!.token)
            assertEquals(0, activity.renderCount())

            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
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
            assertEquals(1, activity.retryReloadCount())
            assertFalse(swipeFeedback.committed)
            assertEquals(StudySwipeReleaseKind.SETTLE_BACK, swipeFeedback.releaseRequest.kind)

            activity.store = store
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            assertEquals(1, reviewIo.pendingCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(0, activity.renderCount())
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals(session.token, activity.pendingStudyAnswerSnapshot()?.feedback?.sessionToken)
            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(null, activity.pendingStudyAnswerSnapshot())
            assertEquals(1, activity.renderCount())
        }
    }

    @Test
    fun staleCommitReloadsPersistedRevisionAndNextRatingAppliesOnce() {
        withReviewActivity("更") { activity, store, reviewIo, session ->
            ShadowToast.reset()
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            store.saveStudyItem(session.item!!.copyBuilder().schedulerRevision(1L).build())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(store.hasConsumedToken(session.token))
            assertEquals(0, activity.renderCount())
            assertEquals(1, activity.retryReloadCount())
            assertEquals(1L, activity.activeSession!!.item!!.schedulerRevision)
            assertEquals(0, ShadowToast.shownToastCount())

            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(0, activity.renderCount())
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
            assertEquals(1, ShadowToast.shownToastCount())
        }
    }

    @Test
    fun preConsumedReviewReconcilesToPersistedStateAndLeavesNextCardAnswerable() {
        withReviewActivity("済") { activity, store, reviewIo, session ->
            ShadowToast.reset()
            val nextToken = "token-after-persistence-duplicate"
            activity.reloadPersistedSessionOnRender()
            startTrackedTask(activity, session)
            persistCompetingReview(store, session, nextToken)

            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(activity.studySessionTracker.hasActiveTask())
            assertEquals(0, activity.renderCount())
            assertEquals(0, activity.retryReloadCount())
            assertEquals(session.token, activity.activeSession!!.token)
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals(1, store.consumedTokens().size)
            assertEquals(0, ShadowToast.shownToastCount())

            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
            assertEquals(nextToken, activity.activeSession!!.token)

            // The consumed token remains claimed, but the reloaded persisted
            // session has its own token and can be answered normally.
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            assertEquals(1, reviewIo.pendingCount())
            reviewIo.shutdownNow()
        }
    }

    @Test
    fun commitDuplicateDiscardsPreparedTaskAndReconcilesForward() {
        withReviewActivity("競") { activity, store, reviewIo, session ->
            ShadowToast.reset()
            val nextToken = "token-after-commit-duplicate"
            activity.reloadPersistedSessionOnRender()
            startTrackedTask(activity, session)
            installCommitDuplicateTrigger(store, nextToken)

            try {
                assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
                reviewIo.runNext()
                shadowOf(Looper.getMainLooper()).idle()
            } finally {
                store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS force_review_commit_duplicate")
            }

            assertFalse(activity.studySessionTracker.hasActiveTask())
            assertEquals(0, activity.renderCount())
            assertEquals(0, activity.retryReloadCount())
            assertEquals(session.token, activity.activeSession!!.token)
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(0, ShadowToast.shownToastCount())

            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
            assertEquals(nextToken, activity.activeSession!!.token)
            assertEquals(1L, activity.activeSession!!.item!!.schedulerRevision)

            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            assertEquals(1, reviewIo.pendingCount())
            reviewIo.shutdownNow()
        }
    }

    @Test
    fun enqueueRejectionReturnsFalseSoOptimisticSwipeCanReset() {
        withReviewActivity("拒") { activity, _, reviewIo, _ ->
            reviewIo.shutdown()
            val swipeFeedback = StudySwipeFeedbackState().apply { update(-104f) }

            val accepted = submitReviewWithSwipeFeedback(swipeFeedback) {
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
            assertEquals(0, activity.renderCount())
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(activity.continueAfterStudyAnswer())
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
                activity.retryStore = store
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

    private fun startTrackedTask(
        activity: TestMainActivity,
        session: RecordsSchedulerModels.StudySession,
    ) {
        val item = session.item!!
        activity.startActiveStudyTask(
            activity.sessionTaskKey(session),
            item.kanji,
            session.taskType,
            1_000L,
        )
        assertTrue(activity.studySessionTracker.hasActiveTask())
    }

    private fun persistCompetingReview(
        store: LocalStore,
        session: RecordsSchedulerModels.StudySession,
        nextToken: String,
    ) {
        val before = session.item!!
        val after = before.copyBuilder()
            .totalReviews(before.totalReviews + 1)
            .activeToken(nextToken)
            .build()
        val request = RecordsSchedulerModels.ReviewRequest(
            before.kanji,
            session.token,
            MainActivityBase.RATING_GOOD,
            false,
            true,
            false,
            0,
        )
        val commit = store.saveReviewOutcome(
            after,
            request,
            MainActivityBase.RATING_GOOD,
            2_000L,
            before,
        )
        assertTrue(commit.applied())
    }

    /**
     * Inserts the competing review inside the production insert statement, after
     * hasConsumedToken() has returned false but before commitReview() evaluates
     * the UNIQUE conflict. The trigger also advances the persisted revision and
     * token, modelling the already-committed state the duplicate path must load.
     */
    private fun installCommitDuplicateTrigger(store: LocalStore, nextToken: String) {
        store.writableDatabase.execSQL(
            """
            CREATE TRIGGER force_review_commit_duplicate
            BEFORE INSERT ON review_log
            WHEN NEW.token = 'token-裂' AND NEW.rating <> '__external_commit__'
            BEGIN
                INSERT INTO review_log (
                    kanji,
                    token,
                    rating,
                    writing_required,
                    writing_passed,
                    manual_override,
                    reviewed_at
                ) VALUES (
                    NEW.kanji,
                    NEW.token,
                    '__external_commit__',
                    NEW.writing_required,
                    NEW.writing_passed,
                    NEW.manual_override,
                    NEW.reviewed_at
                );
                UPDATE study_items
                SET scheduler_revision = scheduler_revision + 1,
                    active_token = '$nextToken',
                    total_reviews = total_reviews + 1
                WHERE kanji = NEW.kanji;
            END
            """.trimIndent(),
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

    private fun clearPendingAnswerPreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
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
        private var retryReloadCount = 0
        private var reloadPersistedSessionOnRender = false
        var retryStore: LocalStore? = null

        override fun renderStudy() {
            studyRenderCount += 1
            if (reloadPersistedSessionOnRender) {
                refreshActiveSession(activeSession?.item?.kanji)
            }
        }

        override fun renderStudyForKanji(kanji: String?) {
            retryReloadCount += 1
            refreshActiveSession(kanji)
        }

        private fun refreshActiveSession(kanji: String?) {
            val previous = activeSession ?: return
            val refreshed = retryStore?.studyItems()?.firstOrNull { it.kanji == kanji } ?: return
            activeSession = RecordsSchedulerModels.StudySession(
                refreshed,
                previous.row,
                refreshed.activeToken,
                previous.taskType,
                previous.writingRequired,
                previous.prompt,
            )
            flashcardAnswerRevealed = false
        }

        fun reloadPersistedSessionOnRender() {
            reloadPersistedSessionOnRender = true
        }

        fun clearRenderCount() {
            studyRenderCount = 0
        }

        fun renderCount(): Int {
            return studyRenderCount
        }

        fun retryReloadCount(): Int = retryReloadCount
    }
}
