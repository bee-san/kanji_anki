package dev.bee.kanjianki

import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val REPAIR_OUTCOME_SKIP = "skip"
private const val REVIEW_LOG_TAG = "KaniReview"

/**
 * Activity-lifetime guard for review session tokens. Persistence remains the source of truth, but
 * guarding before [MainActivityBase.io] is touched prevents rapid buttons/swipes from queuing the
 * duplicate database work, toast, and route load in the first place.
 *
 * A successfully handled token deliberately remains claimed for the rest of the activity. A task
 * that is dropped before it writes, fails while processing, or cannot be enqueued releases the
 * claim so the still-visible card can be submitted again.
 */
internal class ReviewSubmissionGate {
    private val claimedTokens = ConcurrentHashMap.newKeySet<String>()

    fun tryClaim(token: String): Boolean = claimedTokens.add(token)

    fun release(token: String) {
        claimedTokens.remove(token)
    }
}

internal class MainActivityStudyReviewFlow(private val activity: MainActivityStudy) {
    private enum class ReviewWriteDisposition {
        HANDLED,
        RETRYABLE_DROP,
    }

    private data class ReviewDiagnostics(
        val source: String,
        val tokenId: String,
        val submittedAtNanos: Long,
    )

    private val submissionGate = ReviewSubmissionGate()

    /**
     * Runs a review write pipeline on the background io executor. Answering a card
     * used to run every store read/write (FSRS apply, review-log insert, streak read,
     * reminder rescheduling) synchronously inside the Pass/Fail click handler on the
     * main thread. Only the toast has to run on main; renderStudy() and
     * renderStudyForKanji() are safe to call from the background thread because they
     * only bump the async loader generation and queue the next route load.
     *
     * Ordering: the io executor is single-threaded, so queued writes run in tap order.
     * Session-token idempotency is enforced before this helper by [submissionGate].
     */
    private fun runReviewWrite(work: () -> Unit) {
        activity.io.execute {
            withStudyLoadProbe("reviewWrite.total") { work() }
        }
    }

    /**
     * Claims [session]'s token before enqueueing its write. Returns false when an equivalent
     * submission is already queued or completed, allowing choice handlers to avoid queueing their
     * auxiliary choice-log write as well.
     */
    private fun runTokenReviewWrite(
        session: RecordsSchedulerModels.StudySession,
        source: String,
        work: (ReviewDiagnostics) -> ReviewWriteDisposition,
    ): Boolean {
        val token = session.token
        val tokenId = reviewTokenId(token)
        if (!submissionGate.tryClaim(token)) {
            logReviewEvent("review event=duplicate-suppressed source=$source token_id=$tokenId phase=enqueue")
            return false
        }

        val diagnostics = ReviewDiagnostics(source, tokenId, reviewNowNanos())
        logReviewEvent("review event=queued source=$source token_id=$tokenId")
        return try {
            activity.io.execute {
                val startedAtNanos = reviewNowNanos()
                val queueWaitMs = reviewElapsedMillis(diagnostics.submittedAtNanos, startedAtNanos)
                logReviewEvent(
                    "review event=write-start source=$source token_id=$tokenId " +
                        "queue_wait_ms=${formatReviewMillis(queueWaitMs)}",
                )
                try {
                    val disposition = withUiTrace("kani.review.write") {
                        withStudyLoadProbe("reviewWrite.total") { work(diagnostics) }
                    }
                    if (disposition == ReviewWriteDisposition.RETRYABLE_DROP) {
                        submissionGate.release(token)
                    }
                    val finishedAtNanos = reviewNowNanos()
                    logReviewEvent(
                        "review event=write-finished source=$source token_id=$tokenId " +
                            "queue_wait_ms=${formatReviewMillis(queueWaitMs)} " +
                            "write_ms=${formatReviewMillis(reviewElapsedMillis(startedAtNanos, finishedAtNanos))} " +
                            "tap_to_finish_ms=${formatReviewMillis(reviewElapsedMillis(diagnostics.submittedAtNanos, finishedAtNanos))} " +
                            "outcome=${disposition.name.lowercase(Locale.ROOT)}",
                    )
                } catch (error: Throwable) {
                    submissionGate.release(token)
                    logReviewError(source, tokenId, "processing", error)
                    if (error is Error) {
                        throw error
                    }
                }
            }
            true
        } catch (error: RuntimeException) {
            submissionGate.release(token)
            logReviewError(source, tokenId, "enqueue", error)
            false
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
    ): Boolean {
        val session = activity.activeSession ?: return false
        if (activity.activeSimilarWritingRepair != null) {
            submitSimilarWritingRepair(rating)
            return true
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
        return runTokenReviewWrite(session, "submit-review") { diagnostics ->
            performNormalReview(session, request, ladder, diagnostics)
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
            activity.requestReminderRearm("review")
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
            activity.requestReminderRearm("review")
        }
    }

    /**
     * Records an ordinary meaning/reading choice and applies its review inside one guarded IO
     * task. Previously the choice row was queued separately before [submitReview], so a duplicate
     * answer could still write an extra choice row even when review persistence rejected its
     * session token.
     */
    fun submitLoggedChoiceReview(
        targetKanji: String,
        choiceSignature: String,
        selectedChoice: String,
        correct: Boolean,
        rung: RecordsBase.LadderRung,
    ): Boolean {
        val session = activity.activeSession ?: return false
        val writingOutcome = StudyReviewWritingOutcome.from(activity.activeAnalysis)
        val hintsUsed = activity.hintsUsed
        val now = System.currentTimeMillis()
        val rating = if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN
        val request = StudyReviewRequestPolicy.from(
            session,
            writingOutcome,
            hintsUsed,
            rating,
            false,
        ).request()
        return runTokenReviewWrite(session, "choice-answer") { diagnostics ->
            if (!activity.isActiveToken(session.token)) {
                return@runTokenReviewWrite ReviewWriteDisposition.RETRYABLE_DROP
            }
            activity.store.recordChoiceReviewLog(
                targetKanji,
                choiceSignature,
                selectedChoice,
                correct,
                rung.wireName(),
                now,
            )
            performNormalReview(session, request, null, diagnostics)
        }
    }

    fun submitSimilarKanjiChoice(card: RecordsImportModels.SimilarKanjiChoiceCard, selectedKanji: String): Boolean {
        val session = activity.activeSession ?: return false
        // Capture tap-time UI state on the main thread; the store write and review
        // apply run on the background executor.
        val writingOutcome = StudyReviewWritingOutcome.from(activity.activeAnalysis)
        val hintsUsed = activity.hintsUsed
        val now = System.currentTimeMillis()
        return runTokenReviewWrite(session, "similar-choice") { diagnostics ->
            if (!activity.isActiveToken(session.token)) {
                return@runTokenReviewWrite ReviewWriteDisposition.RETRYABLE_DROP
            }
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
            performNormalReview(session, mappedReview.request(), ladder, diagnostics)
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
        diagnostics: ReviewDiagnostics,
    ): ReviewWriteDisposition {
        val current = activity.activeSession
        if (current == null || current.token != session.token) {
            logReviewEvent(
                "review event=dropped source=${diagnostics.source} token_id=${diagnostics.tokenId} reason=stale-session",
            )
            return ReviewWriteDisposition.RETRYABLE_DROP
        }
        val item = session.item ?: return ReviewWriteDisposition.RETRYABLE_DROP
        // Idempotency is anchored in persistence: hasConsumedToken() checks the
        // review_log, and a token only lands there after a review is successfully
        // saved (see ReviewTransitionEngine, which consumes the in-memory token only
        // on success). The item advance and the review_log row are written in one
        // transaction (LocalStore.saveReviewOutcome), so a token is present in the
        // log if and only if the item was actually advanced: a review that failed
        // mid-apply rolled both back, so its token is retryable, and the engine only
        // ever tests membership of request.token.
        if (activity.store.hasConsumedToken(request.token)) {
            logReviewEvent(
                "review event=duplicate-suppressed source=${diagnostics.source} " +
                    "token_id=${diagnostics.tokenId} phase=persistence",
            )
            return ReviewWriteDisposition.HANDLED
        }
        val consumed = HashSet<String>()
        val now = System.currentTimeMillis()
        val parameters = activity.store.schedulerParameters()
        val scheduler = BridgeScheduler.withWeights(activity.store.schedulerFsrsWeights())
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
        if (result.duplicate) {
            logReviewEvent(
                "review event=duplicate-suppressed source=${diagnostics.source} " +
                    "token_id=${diagnostics.tokenId} phase=scheduler",
            )
            return ReviewWriteDisposition.HANDLED
        }

        activity.completeActiveStudyTask(activity.sessionTaskKey(session), result.appliedRating, now)
        saveAppliedReview(session, request, result, now, diagnostics)
        val streak: StudyStatsStore.StudyStreak = activity.store.studyStreak(now)
        showToast(HomeTextCopy.reviewToast(false, result.appliedRating, streak.currentDays))
        activity.renderStudy()
        logReviewEvent(
            "review event=next-route-requested source=${diagnostics.source} token_id=${diagnostics.tokenId} " +
                "tap_to_request_ms=${formatReviewMillis(reviewElapsedMillis(diagnostics.submittedAtNanos, reviewNowNanos()))}",
        )
        activity.requestReminderRearm("review")
        return ReviewWriteDisposition.HANDLED
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
            // Undo deletes the persisted review token and restores the pre-review
            // item, including that token. Make the restored card genuinely
            // answerable again instead of letting the activity-lifetime gate treat
            // its next rating as a late duplicate.
            submissionGate.release(pending.snapshot.token)
            logReviewEvent(
                "review event=token-released token_id=${reviewTokenId(pending.snapshot.token)} reason=undo",
            )
            activity.renderStudyForKanji(restoredKanji)
            activity.scheduleStatsPrecomputeIfStaleAsync()
            activity.requestReminderRearm("review")
        }
    }

    private fun saveAppliedReview(
        session: RecordsSchedulerModels.StudySession,
        request: RecordsSchedulerModels.ReviewRequest,
        result: RecordsSchedulerModels.ReviewResult,
        now: Long,
        diagnostics: ReviewDiagnostics,
    ) {
        val item = session.item ?: return
        StudyReviewActions.saveAppliedReview(
            request,
            result,
            item,
            now,
            StudyReviewActions.ReviewWriter { savedItem, savedRequest, appliedRating, reviewedAt, before ->
                val startedAtNanos = reviewNowNanos()
                try {
                    activity.store.saveReviewOutcome(savedItem, savedRequest, appliedRating, reviewedAt, before)
                } finally {
                    logReviewEvent(
                        "review event=persist-finished source=${diagnostics.source} token_id=${diagnostics.tokenId} " +
                            "duration_ms=${formatReviewMillis(reviewElapsedMillis(startedAtNanos, reviewNowNanos()))}",
                    )
                }
            },
            activity.studySessionTracker::recordReviewOutcome,
            activity::markStudyRunPassed
        )
        activity.studyUndoState.capture(
            StudyReviewActions.AppliedReviewSnapshot(request.token, item, result.item),
            result.appliedRating,
            now,
        )
    }

    private fun logReviewError(source: String, tokenId: String, phase: String, error: Throwable) {
        runCatching { Log.e(REVIEW_LOG_TAG, "Review $phase failed; token is retryable.", error) }
        AppDebugLog.logError(
            "review failed source=$source token_id=$tokenId phase=$phase retryable=true",
            error,
        )
    }

    private fun logReviewEvent(message: String) {
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log(message)
        }
    }
}

private fun reviewNowNanos(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}

private fun reviewElapsedMillis(startNanos: Long, endNanos: Long): Double {
    return (endNanos - startNanos).coerceAtLeast(0L) / 1_000_000.0
}

private fun formatReviewMillis(durationMs: Double): String {
    return String.format(Locale.US, "%.2f", durationMs)
}

/** Stable, content-free identifier that lets one debug-log session correlate a review pipeline. */
private fun reviewTokenId(token: String): String {
    return Integer.toUnsignedString(token.hashCode(), 16)
}
