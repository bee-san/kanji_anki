package dev.bee.kanjianki

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.DatabaseUtils
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.data.LocalStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            var widgetRefreshes = 0
            installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }
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
            assertEquals(1, widgetRefreshes)

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
            assertEquals(1, widgetRefreshes)
        }
    }

    @Test
    fun selfGradedPassKeepsAnsweredCardMountedUntilExplicitContinue() {
        assertSelfGradedActionWaitsForExplicitContinue(pass = true)
    }

    @Test
    fun selfGradedFailKeepsAnsweredCardMountedUntilExplicitContinue() {
        assertSelfGradedActionWaitsForExplicitContinue(pass = false)
    }

    @Test
    fun retryableSelfGradedDropRestoresUnansweredWithoutAdvancing() {
        withReviewActivity("裂") { activity, store, reviewIo, session ->
            clearStore(activity)
            activity.prepareStudyAnswerFeedback(session.token)
            activity.buildFlashcardActionBar(revealed = true)
            requireNotNull(activity.flashcardActionBarState).onPass.run()
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, activity.studyAnswerFeedbackState?.snapshot()?.phase)

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            val feedback = activity.studyAnswerFeedbackState!!
            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, feedback.snapshot().phase)
            assertEquals(0, activity.renderCount())

            // A retried submit still waits for explicit Continue after it applies.
            activity.store = store
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(0, activity.renderCount())
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
        }
    }

    @Test
    fun widgetRefreshFailureDoesNotRollBackCommittedReview() {
        withReviewActivity("守") { activity, store, reviewIo, session ->
            installWidgetRefreshRecorder(activity) {
                throw IllegalStateException("widget host unavailable")
            }

            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals(0, activity.retryReloadCount())
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
    fun writingPassActionKeepsAnsweredCardMountedUntilExplicitContinue() {
        assertWritingActionWaitsForExplicitContinue(
            WritingAnalysis.Status.PASS,
            writingPassed = true,
            expectedOutcome = StudyAnswerOutcome.CORRECT,
        )
    }

    @Test
    fun writingFailActionKeepsAnsweredCardMountedUntilExplicitContinue() {
        assertWritingActionWaitsForExplicitContinue(
            WritingAnalysis.Status.WRONG,
            writingPassed = false,
            expectedOutcome = StudyAnswerOutcome.INCORRECT,
        )
    }

    @Test
    fun continueRepairsAStillSubmittingDurableEnvelopeAfterReviewCommit() {
        withReviewActivity("継") { activity, _, reviewIo, _ ->
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
            val submittingRaw = requireNotNull(preferences.getString("snapshot", null))
            assertEquals(
                StudyAnswerFeedbackPhase.SUBMITTING,
                activity.pendingStudyAnswerSnapshot()?.feedback?.phase,
            )

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(
                StudyAnswerFeedbackPhase.APPLIED,
                activity.pendingStudyAnswerSnapshot()?.feedback?.phase,
            )

            preferences.edit().putString("snapshot", submittingRaw).commit()

            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(
                StudyAnswerFeedbackPhase.CONTINUED,
                activity.pendingStudyAnswerSnapshot()?.feedback?.phase,
            )
        }
    }

    @Test
    fun failedReviewProcessingReleasesTokenForRetry() {
        withReviewActivity("衡") { activity, store, reviewIo, session ->
            var widgetRefreshes = 0
            installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }
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
            assertEquals(0, widgetRefreshes)

            activity.store = store
            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            assertEquals(1, reviewIo.pendingCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(0, activity.renderCount())
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals(session.token, activity.pendingStudyAnswerSnapshot()?.feedback?.sessionToken)
            assertEquals(1, widgetRefreshes)
            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(
                StudyAnswerFeedbackPhase.CONTINUED,
                activity.pendingStudyAnswerSnapshot()?.feedback?.phase,
            )
            assertEquals(1, activity.renderCount())
        }
    }

    @Test
    fun staleCommitReloadsPersistedRevisionAndNextRatingAppliesOnce() {
        withReviewActivity("更") { activity, store, reviewIo, session ->
            var widgetRefreshes = 0
            installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }
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
            assertEquals(0, widgetRefreshes)

            assertTrue(activity.submitReview(MainActivityBase.RATING_GOOD, false))
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(0, activity.renderCount())
            assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
            assertEquals(1, widgetRefreshes)
            assertTrue(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
            assertEquals(1, ShadowToast.shownToastCount())
        }
    }

    @Test
    fun preConsumedReviewReconcilesToPersistedStateAndLeavesNextCardAnswerable() {
        withReviewActivity("済") { activity, store, reviewIo, session ->
            var widgetRefreshes = 0
            installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }
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
            assertEquals(0, widgetRefreshes)

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
            var widgetRefreshes = 0
            installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }
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
            assertEquals(0, widgetRefreshes)

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
    fun persistedRepairCompletionAndSkipEachRefreshWidget() {
        withReviewActivity("修") { activity, store, reviewIo, _ ->
            var widgetRefreshes = 0
            val reviewFlow = installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }

            val completeRepair = persistRepair(store, "complete-refresh-token")
            activity.activeSimilarWritingRepair = completeRepair
            activity.activeSession = repairSession(requireNotNull(activity.activeSession), completeRepair.activeToken)
            reviewFlow.submitSimilarWritingRepair(MainActivityBase.RATING_GOOD)
            reviewIo.runNext()

            assertEquals(1, widgetRefreshes)

            val skipRepair = persistRepair(store, "skip-refresh-token")
            activity.activeSimilarWritingRepair = skipRepair
            activity.activeSession = repairSession(requireNotNull(activity.activeSession), skipRepair.activeToken)
            reviewFlow.skipSimilarWritingRepair()
            reviewIo.runNext()

            assertEquals(2, widgetRefreshes)
        }
    }

    @Test
    fun rejectedRepairCompletionAndSkipDoNotRefreshWidget() {
        withReviewActivity("拒") { activity, _, reviewIo, _ ->
            var widgetRefreshes = 0
            val reviewFlow = installWidgetRefreshRecorder(activity) { widgetRefreshes += 1 }

            val missingCompletion = repair(Long.MAX_VALUE - 1L, "missing-complete-token")
            activity.activeSimilarWritingRepair = missingCompletion
            activity.activeSession = repairSession(requireNotNull(activity.activeSession), missingCompletion.activeToken)
            reviewFlow.submitSimilarWritingRepair(MainActivityBase.RATING_GOOD)
            reviewIo.runNext()

            val missingSkip = repair(Long.MAX_VALUE, "missing-skip-token")
            activity.activeSimilarWritingRepair = missingSkip
            activity.activeSession = repairSession(requireNotNull(activity.activeSession), missingSkip.activeToken)
            reviewFlow.skipSimilarWritingRepair()
            reviewIo.runNext()

            assertEquals(0, widgetRefreshes)
        }
    }

    @Test
    fun rejectedRepairCompletionRestoresAnAnswerableCardWithoutAdvancingProgress() {
        withReviewActivity("拒") { activity, _, reviewIo, session ->
            val missingRepair = repair(Long.MAX_VALUE, session.token)
            activity.activeSimilarWritingRepair = missingRepair
            activity.activeSession = repairSession(session)
            startTrackedRepairTask(activity, missingRepair)

            assertTrue(activity.submitSimilarWritingRepair(MainActivityBase.RATING_GOOD))
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertEquals(1, reviewIo.pendingCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertNull(activity.pendingStudyAnswerSnapshot())
            assertTrue(activity.studySessionTracker.hasActiveTask())
            assertEquals(1, activity.retryReloadCount())
            assertEquals(0, activity.renderCount())
        }
    }

    @Test
    fun rejectedRepairEnqueueReturnsFalseAndLeavesTheRepairAnswerable() {
        withReviewActivity("拒") { activity, _, reviewIo, session ->
            val activeRepair = repair(Long.MAX_VALUE, session.token)
            activity.activeSimilarWritingRepair = activeRepair
            activity.activeSession = repairSession(session)
            startTrackedRepairTask(activity, activeRepair)
            reviewIo.shutdown()

            assertFalse(
                activity.submitReview(
                    MainActivityBase.RATING_GOOD,
                    false,
                    interactionSource = "repair-action-bar",
                ),
            )

            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertNull(activity.pendingStudyAnswerSnapshot())
            assertTrue(activity.studySessionTracker.hasActiveTask())
            assertEquals(0, activity.renderCount())
        }
    }

    @Test
    fun repairProcessingExceptionReleasesTheSubmissionForRetry() {
        withReviewActivity("拒") { activity, _, reviewIo, session ->
            val activeRepair = repair(Long.MAX_VALUE, session.token)
            activity.activeSimilarWritingRepair = activeRepair
            activity.activeSession = repairSession(session)
            startTrackedRepairTask(activity, activeRepair)

            assertTrue(activity.submitSimilarWritingRepair(MainActivityBase.RATING_GOOD))
            clearStore(activity)

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertNull(activity.pendingStudyAnswerSnapshot())
            assertTrue(activity.studySessionTracker.hasActiveTask())
            assertEquals(1, activity.retryReloadCount())
        }
    }

    @Test
    fun successfulRepairCompletionAppliesOnceAndWaitsForExplicitContinue() {
        withReviewActivity("修") { activity, store, reviewIo, session ->
            val activeRepair = persistRepair(store, session.token)
            activity.activeSimilarWritingRepair = activeRepair
            activity.activeSession = repairSession(session)
            startTrackedRepairTask(activity, activeRepair)

            assertTrue(activity.submitSimilarWritingRepair(MainActivityBase.RATING_GOOD))
            assertFalse(activity.submitSimilarWritingRepair(MainActivityBase.RATING_GOOD))
            assertEquals(1, reviewIo.pendingCount())
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, activity.studyAnswerFeedbackState?.snapshot()?.phase)

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)

            assertEquals("complete", repairStatus(store, activeRepair.id))
            assertFalse(activity.studySessionTracker.hasActiveTask())
            assertEquals(StudyAnswerFeedbackPhase.APPLIED, activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertEquals(session.token, activity.activeSession?.token)
            assertEquals(0, activity.renderCount())
            assertTrue(activity.continueAfterStudyAnswer())
            assertFalse(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
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
    fun undurablePendingAnswerIsRejectedBeforeReviewEnqueue() {
        withReviewActivity("長") { activity, store, reviewIo, session ->
            activity.activeSession = RecordsSchedulerModels.StudySession(
                session.item,
                session.row,
                session.token,
                session.taskType,
                session.writingRequired,
                "x".repeat(20_000),
            )

            assertFalse(activity.submitReview(MainActivityBase.RATING_GOOD, false))

            assertEquals(0, reviewIo.pendingCount())
            assertFalse(store.hasConsumedToken(session.token))
            assertEquals(StudyAnswerFeedbackPhase.UNANSWERED, activity.studyAnswerFeedbackState?.snapshot()?.phase)
            assertEquals(null, activity.pendingStudyAnswerSnapshot())
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

    /**
     * RED regression for the reported Study feedback freeze (card t_89db57a1).
     *
     * Reproduces the exact user symptom: a self-graded recognition card is graded
     * Fail, the review commits ("Fail saved" / "Incorrect."), the answered card
     * stays mounted, and Continue looks tappable — but it never advances.
     *
     * Root cause under test: [MainActivityStudy.markStudyAnswerApplied] hops to the
     * main thread through [MainActivityBase.postToMainIfActive], which silently drops
     * the runnable when the Activity is finishing/destroyed (the window between a
     * review commit and the applied callback during a config change / teardown). The
     * scheduler token is consumed and the row advances, but the in-memory feedback
     * gate is left in SUBMITTING. On the retained-holder re-render path
     * ([MainActivityStudyQueueCoordinator.renderStudy] lines that re-render an active
     * SUBMITTING/APPLIED card in place) nothing reconciles SUBMITTING against the
     * consumed token, so [StudyAnswerFeedbackState.continueEnabled] stays false and
     * [continueAfterStudyAnswer] returns false forever.
     *
     * This test FAILS today because the feedback stays stuck at SUBMITTING with the
     * token already consumed. It should PASS once the applied transition is made
     * durable across the dropped callback (e.g. reconciling a consumed-token
     * SUBMITTING card to APPLIED on the retained re-render path).
     */
    @Test
    fun appliedCallbackDroppedByFinishingActivityLeavesConsumedCardStuckAtSubmitting() {
        withReviewActivity("脱") { activity, store, reviewIo, session ->
            ShadowToast.reset()

            // 1) Grade Fail. The answer gate enters SUBMITTING and the review is
            //    queued to the (controllable) background executor.
            assertTrue(activity.submitReview(MainActivityBase.RATING_AGAIN, false, interactionSource = "card"))
            val feedback = requireNotNull(activity.studyAnswerFeedbackState)
            assertEquals(StudyAnswerOutcome.INCORRECT, feedback.outcome)
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, feedback.snapshot().phase)
            assertEquals(1, reviewIo.pendingCount())

            // 2) The Activity begins finishing (config change / teardown) BEFORE the
            //    review commit posts its applied callback. postToMainIfActive will
            //    drop that runnable.
            activity.finish()

            // 3) The background review commit runs to completion: the token is
            //    consumed and "Fail saved" is durable. Then drain the main looper so
            //    the dropped markStudyAnswerApplied runnable would have run if it were
            //    ever delivered.
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)

            // The review really committed — this is the "Fail saved" the user saw.
            assertTrue(store.hasConsumedToken(session.token))

            // 4) The answered card is still mounted with Incorrect feedback, but the
            //    gate never reached APPLIED, so Continue cannot advance. This is the
            //    freeze. The regression contract: a consumed-token answered card must
            //    be continuable.
            assertTrue(feedback.feedbackVisible)
            assertEquals(
                "Consumed-token answered card must reach APPLIED, not stay stuck at SUBMITTING",
                StudyAnswerFeedbackPhase.APPLIED,
                feedback.snapshot().phase,
            )
            assertTrue(
                "Continue must be enabled once the review is committed",
                feedback.continueEnabled,
            )
            assertTrue(
                "Continue must actually advance the answered card",
                activity.continueAfterStudyAnswer(),
            )
        }
    }

    /**
     * RED regression, retained-holder path (config change with a surviving
     * session/ViewModel, no process death). The in-memory SUBMITTING feedback whose
     * review token was consumed must be reconcilable to a continuable state, so the
     * mounted Continue button the user is staring at is not permanently dead.
     *
     * FAILS today: there is no path that promotes an in-memory consumed-token
     * SUBMITTING gate to APPLIED, so [continueAfterStudyAnswer] keeps returning false
     * and [StudyAnswerFeedbackState.continueEnabled] stays false. (Only the durable
     * process-death recovery path in MainActivityStudyQueueCoordinator reconciles
     * SUBMITTING against a consumed token; the retained-holder re-render does not.)
     */
    @Test
    fun retainedConsumedButUnappliedCardMustRecoverContinue() {
        withReviewActivity("脱") { activity, store, reviewIo, session ->
            assertTrue(activity.submitReview(MainActivityBase.RATING_AGAIN, false, interactionSource = "card"))
            val feedback = requireNotNull(activity.studyAnswerFeedbackState)

            // Commit the review while the Activity is finishing so the applied
            // callback is dropped, leaving the retained in-memory gate at SUBMITTING
            // with the token already consumed.
            activity.finish()
            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, feedback.snapshot().phase)

            // The user's mounted Continue button must not be frozen: the committed
            // review means Continue has to work.
            assertTrue(
                "Consumed answered card must expose a working Continue",
                requireNotNull(activity.studyAnswerFeedbackState).continueEnabled,
            )
            assertTrue(activity.continueAfterStudyAnswer())
        }
    }

    private fun assertSelfGradedActionWaitsForExplicitContinue(pass: Boolean) {
        withReviewActivity(if (pass) "正" else "誤") { activity, store, reviewIo, session ->
            activity.prepareStudyAnswerFeedback(session.token)
            activity.buildFlashcardActionBar(revealed = true)
            val actions = requireNotNull(activity.flashcardActionBarState)

            if (pass) actions.onPass.run() else actions.onFail.run()

            val feedback = requireNotNull(activity.studyAnswerFeedbackState)
            assertEquals(
                if (pass) StudyAnswerOutcome.CORRECT else StudyAnswerOutcome.INCORRECT,
                feedback.outcome,
            )
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, feedback.snapshot().phase)
            assertEquals(1, reviewIo.pendingCount())
            assertEquals(0, activity.renderCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(StudyAnswerFeedbackPhase.APPLIED, feedback.snapshot().phase)
            assertTrue(feedback.continueEnabled)
            assertEquals(session.token, activity.activeSession?.token)
            assertEquals(0, activity.renderCount())

            assertTrue(activity.continueAfterStudyAnswer())
            assertFalse(activity.continueAfterStudyAnswer())
            assertEquals(1, activity.renderCount())
        }
    }

    private fun assertWritingActionWaitsForExplicitContinue(
        status: WritingAnalysis.Status,
        writingPassed: Boolean,
        expectedOutcome: StudyAnswerOutcome,
    ) {
        withReviewActivity("書") { activity, store, reviewIo, session ->
            activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity)
            activity.writingFallbackActionsView = WritingFallbackActionsView(activity)
            activity.activeAnalysis = WritingAnalysis(
                status,
                if (writingPassed) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN,
                writingPassed,
                "result",
                emptyList(),
                null,
            )
            activity.prepareStudyAnswerFeedback(session.token)
            activity.updateResultActions()
            val action = requireNotNull(activity.writingPrimaryActionsView).currentModel()
            assertTrue(action.nextVisible)

            action.onNext.run()

            val feedback = requireNotNull(activity.studyAnswerFeedbackState)
            assertEquals(expectedOutcome, feedback.outcome)
            assertEquals(StudyAnswerFeedbackPhase.SUBMITTING, feedback.snapshot().phase)
            assertEquals(1, reviewIo.pendingCount())

            reviewIo.runNext()
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)

            assertTrue(store.hasConsumedToken(session.token))
            assertEquals(StudyAnswerFeedbackPhase.APPLIED, feedback.snapshot().phase)
            assertTrue(feedback.continueEnabled)
            assertEquals(session.token, activity.activeSession?.token)
            assertEquals(0, activity.renderCount())

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

    private fun installWidgetRefreshRecorder(
        activity: TestMainActivity,
        onRefresh: () -> Unit,
    ): MainActivityStudyReviewFlow {
        val field = MainActivityStudy::class.java.getDeclaredField("writingReview\$delegate")
        field.isAccessible = true
        val reviewFlow = (field.get(activity) as Lazy<*>).value as MainActivityStudyReviewFlow
        reviewFlow.widgetRefresher = Runnable { onRefresh() }
        return reviewFlow
    }

    private fun persistRepair(store: LocalStore, token: String): RecordsImportModels.SimilarKanjiWritingRepair {
        val draft = repair(0L, token)
        val values = ContentValues().apply {
            put("target_kanji", draft.targetKanji)
            put("repair_kanji", draft.repairKanji)
            put("choice_signature", draft.choiceSignature)
            put("wrong_selection", draft.wrongSelection)
            put("prompt_meaning", draft.promptMeaning)
            put("status", draft.status)
            put("due_at", draft.dueAtMillis)
            put("active_token", draft.activeToken)
            put("attempts", draft.attempts)
            put("created_at", draft.createdAtMillis)
            put("updated_at", draft.updatedAtMillis)
            put("completed_at", draft.completedAtMillis)
        }
        val id = store.writableDatabase.insertOrThrow("similar_kanji_repair_queue", null, values)
        return repair(id, token)
    }

    private fun repairStatus(store: LocalStore, id: Long): String? {
        return store.readableDatabase.query(
            "similar_kanji_repair_queue",
            arrayOf("status"),
            "id=?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    private fun repair(id: Long, token: String): RecordsImportModels.SimilarKanjiWritingRepair {
        return RecordsImportModels.SimilarKanjiWritingRepair(
            id,
            "末",
            "未",
            "widget-refresh-$token",
            "末",
            "not yet",
            "pending",
            0L,
            token,
            0,
            1_000L,
            1_000L,
            0L,
        )
    }

    private fun repairSession(
        session: RecordsSchedulerModels.StudySession,
        token: String = session.token,
    ): RecordsSchedulerModels.StudySession = RecordsSchedulerModels.StudySession(
        session.item?.copyBuilder()?.activeToken(token)?.build(),
        session.row,
        token,
        MainActivityBase.TASK_REPAIR_WRITING,
        true,
        "Write the similar kanji",
    )

    private fun startTrackedRepairTask(
        activity: TestMainActivity,
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
    ) {
        activity.startActiveStudyTask(
            activity.similarRepairStudyTaskKey(repair),
            repair.repairKanji,
            MainActivityBase.TASK_REPAIR_WRITING,
            1_000L,
        )
        assertTrue(activity.studySessionTracker.hasActiveTask())
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
            val session = RecordsSchedulerModels.StudySession(
                refreshed,
                previous.row,
                refreshed.activeToken,
                previous.taskType,
                previous.writingRequired,
                previous.prompt,
            )
            val advancing = pendingStudyRecovery()
                ?.takeIf { it.snapshot.feedback.phase == StudyAnswerFeedbackPhase.CONTINUED }
            if (advancing == null) {
                activeSession = session
            } else {
                assertTrue(
                    acceptNewActiveStudySession(
                        session,
                        StudyPromptSource.REASON_TEXT,
                        latestSuccessfulSyncAtMillis = 0L,
                        advancingRecovery = advancing,
                    ),
                )
            }
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
