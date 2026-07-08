package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

private const val REPAIR_OUTCOME_SKIP = "skip"

internal class MainActivityStudyReviewFlow(private val activity: MainActivityStudy) {
    /**
     * Runs a review write pipeline on the background io executor. Answering a card
     * used to run every store read/write (FSRS apply, review-log insert, streak read,
     * reminder rescheduling) synchronously inside the Pass/Fail click handler on the
     * main thread. Only the toast has to run on main; renderStudy() and
     * renderStudyForKanji() are safe to call from the background thread because they
     * only bump the async loader generation and queue the next route load.
     *
     * Ordering and idempotency: the io executor is single-threaded, so queued review
     * writes run in tap order, and duplicate submissions of the same session token are
     * dropped by the persisted review_log idempotency check inside
     * [performNormalReview].
     */
    private fun runReviewWrite(work: () -> Unit) {
        activity.io.execute {
            withStudyLoadProbe("reviewWrite.total") { work() }
        }
    }

    private fun showToast(text: String) {
        activity.postToMainIfActive {
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun submitReview(
        rating: String,
        override: Boolean,
        ladder: RecordsBase.StudyLadderSettings? = null,
    ) {
        val session = activity.activeSession ?: return
        if (activity.activeSimilarWritingRepair != null) {
            submitSimilarWritingRepair(rating)
            return
        }
        // Build the request on the main thread: it reads tap-time UI state (writing
        // analysis, hints used) that belongs to the card the user just answered.
        val mappedReview = StudyReviewRequestPolicy.from(
            session,
            StudyReviewWritingOutcome.from(activity.activeAnalysis),
            activity.hintsUsed,
            rating,
            override
        )
        val request = mappedReview.request()
        runReviewWrite {
            performNormalReview(session, request, ladder)
        }
    }

    fun submitSimilarWritingRepair(rating: String) {
        val repair = activity.activeSimilarWritingRepair ?: return
        val now = System.currentTimeMillis()
        runReviewWrite {
            if (activity.activeSimilarWritingRepair !== repair) {
                return@runReviewWrite
            }
            activity.completeActiveRepairStudyTask(activity.similarRepairStudyTaskKey(repair), rating, now)
            val completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                rating,
                now,
                activity.store::finishSimilarWritingRepair,
                activity.studySessionTracker::recordRepairOutcome,
                activity::markStudyTaskCompleted
            )
            showToast(StudyTextCopy.similarWritingRepairSavedToast(completion.passed))
            activity.activeSimilarWritingRepair = null
            activity.renderStudy()
            ReminderScheduler.schedule(activity)
        }
    }

    fun skipSimilarWritingRepair() {
        val repair = activity.activeSimilarWritingRepair ?: return
        val now = System.currentTimeMillis()
        runReviewWrite {
            if (activity.activeSimilarWritingRepair !== repair) {
                return@runReviewWrite
            }
            activity.completeActiveRepairStudyTask(activity.similarRepairStudyTaskKey(repair), REPAIR_OUTCOME_SKIP, now)
            StudyRepairActions.skipSimilarWritingRepair(
                repair,
                now,
                activity.store::skipSimilarWritingRepair,
                activity.studySessionTracker::recordRepairOutcome,
                activity::markStudyTaskCompleted,
            )
            showToast(StudyTextCopy.similarWritingRepairSkippedToast())
            activity.activeSimilarWritingRepair = null
            activity.renderStudy()
            ReminderScheduler.schedule(activity)
        }
    }

    fun submitSimilarKanjiChoice(card: RecordsImportModels.SimilarKanjiChoiceCard, selectedKanji: String) {
        val session = activity.activeSession ?: return
        // Capture tap-time UI state on the main thread; the store write and review
        // apply run on the background executor.
        val writingOutcome = StudyReviewWritingOutcome.from(activity.activeAnalysis)
        val hintsUsed = activity.hintsUsed
        val now = System.currentTimeMillis()
        runReviewWrite {
            val ladder = activity.studyLadderSettings()
            val result = activity.store.submitSimilarChoice(
                card,
                selectedKanji,
                now,
                ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)
            )
            val rating = if (result.correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN
            val mappedReview = StudyReviewRequestPolicy.from(
                session,
                writingOutcome,
                hintsUsed,
                rating,
                false
            )
            performNormalReview(session, mappedReview.request(), ladder)
        }
    }

    /**
     * Applies a review on the background executor. [session] is the session that was
     * active when the user answered; if the active session moved on before this task
     * ran (e.g. a queued double-tap completion racing the next card's activation), the
     * write is dropped.
     */
    private fun performNormalReview(
        session: RecordsSchedulerModels.StudySession,
        request: RecordsSchedulerModels.ReviewRequest,
        ladder: RecordsBase.StudyLadderSettings?,
    ) {
        val current = activity.activeSession
        if (current == null || current.token != session.token) {
            return
        }
        val item = session.item ?: return
        val scheduler = BridgeScheduler()
        // Idempotency is anchored in persistence: hasConsumedToken() checks the
        // review_log, and a token only lands there after a review is successfully
        // saved (see ReviewTransitionEngine, which consumes the in-memory token only
        // on success). The item advance and the review_log row are written in one
        // transaction (LocalStore.saveReviewOutcome), so a token is present in the
        // log if and only if the item was actually advanced: a review that failed
        // mid-apply rolled both back, so its token is retryable, and the engine only
        // ever tests membership of request.token.
        val consumed = HashSet<String>()
        if (activity.store.hasConsumedToken(request.token)) {
            consumed.add(request.token)
        }
        val now = System.currentTimeMillis()
        val parameters = activity.store.schedulerParameters()
        val sessionRank = session.row?.jitenRank
        val effectiveParameters = parameters.withTargetRetention(
            parameters.targetRetentionForRank(sessionRank)
        )
        val result = scheduler.applyReview(
            item,
            request,
            consumed,
            now,
            effectiveParameters,
            activity.settings(),
            activity.store.learningStepSettings(),
            ladder ?: activity.studyLadderSettings()
        )
        activity.completeActiveStudyTask(activity.sessionTaskKey(session), result.appliedRating, now)
        var streak: StudyStatsStore.StudyStreak? = null
        if (!result.duplicate) {
            saveAppliedReview(session, request, result, now)
            streak = activity.store.studyStreak(now)
            ReminderScheduler.schedule(activity)
        }
        val currentStreakDays = streak?.currentDays ?: 0
        showToast(HomeTextCopy.reviewToast(result.duplicate, result.appliedRating, currentStreakDays))
        activity.renderStudy()
    }

    fun undoLastRating() {
        val pending = activity.studyUndoState.pending ?: return
        runReviewWrite {
            if (activity.studyUndoState.pending !== pending) {
                return@runReviewWrite
            }
            val currentItem = activity.findStudyItem(activity.store.studyItems(), pending.snapshot.afterReview.kanji)
            if (!StudyReviewActions.matchesUndoBoundary(currentItem, pending.snapshot.afterReview)) {
                activity.studyUndoState.clear()
                activity.renderStudy()
                return@runReviewWrite
            }
            val restoredKanji = pending.snapshot.beforeReview.kanji
            activity.studyUndoState.clear()
            val restored = runCatching { activity.store.undoLastAppliedReview(pending.snapshot) }.getOrDefault(false)
            if (!restored) {
                activity.renderStudy()
                return@runReviewWrite
            }
            activity.renderStudyForKanji(restoredKanji)
            activity.scheduleStatsPrecomputeIfStaleAsync()
            ReminderScheduler.schedule(activity)
        }
    }

    private fun saveAppliedReview(
        session: RecordsSchedulerModels.StudySession,
        request: RecordsSchedulerModels.ReviewRequest,
        result: RecordsSchedulerModels.ReviewResult,
        now: Long,
    ) {
        val item = session.item ?: return
        StudyReviewActions.saveAppliedReview(
            request,
            result,
            item,
            now,
            StudyReviewActions.ReviewWriter { savedItem, savedRequest, appliedRating, reviewedAt, before ->
                activity.store.saveReviewOutcome(savedItem, savedRequest, appliedRating, reviewedAt, before)
            },
            activity.studySessionTracker::recordReviewOutcome,
            activity::markStudyRunPassed
        )
        activity.studyUndoState.capture(
            StudyReviewActions.AppliedReviewSnapshot(request.token, item, result.item),
            result.appliedRating,
            now,
        )
        ReminderScheduler.schedule(activity)
    }
}
