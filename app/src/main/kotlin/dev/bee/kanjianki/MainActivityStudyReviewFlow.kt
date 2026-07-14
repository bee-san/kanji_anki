package dev.bee.kanjianki

import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.EvidenceSource
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiReadingAligner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudyExampleSelector
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.PresentationVariant
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.data.ReviewChoiceLog
import dev.bee.kanjianki.data.ReviewCommitDisposition
import dev.bee.kanjianki.data.ReviewCommitResult
import dev.bee.kanjianki.data.SimilarChoiceCommit
import dev.bee.kanjianki.widget.KaniWidgetUpdater
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

    /** Refreshes installed widgets only after a review-side mutation is durably persisted. */
    @JvmField
    internal var widgetRefresher: Runnable = Runnable {
        KaniWidgetUpdater.requestUpdate(activity)
    }

    /**
     * Runs a review write pipeline on the background io executor. Answering a card
     * used to run every store read/write (FSRS apply, review-log insert, streak read,
     * reminder rescheduling) synchronously inside the Pass/Fail click handler on the
     * main thread. Compose-observable feedback is marshalled back to main; route
     * reloads from retry/undo paths only bump the async loader generation and queue
     * a replacement route load.
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
        frameRating: String? = null,
        work: (ReviewDiagnostics) -> ReviewWriteDisposition,
    ): Boolean {
        val token = session.token
        val tokenId = reviewTokenId(token)
        if (!submissionGate.tryClaim(token)) {
            logReviewEvent("review event=duplicate-suppressed source=$source token_id=$tokenId phase=enqueue")
            return false
        }

        val diagnostics = ReviewDiagnostics(source, tokenId, reviewNowNanos())
        return try {
            activity.io.execute {
                // This task can only run after execute() accepted it. Starting
                // correlation here also guarantees a retry/error cannot clear a
                // marker and then race with the caller publishing it afterward.
                if (frameRating != null) {
                    StudyCardFrameDiagnostics.onReviewEnqueued(
                        token,
                        source,
                        frameRating,
                        diagnostics.submittedAtNanos,
                    )
                }
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
                        releaseRetryableSubmission(token, source, tokenId, "retryable-drop")
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
                    releaseRetryableSubmission(token, source, tokenId, "processing-error")
                    logReviewError(source, tokenId, "processing", error)
                    if (error is Error) {
                        throw error
                    }
                }
            }
            // Do not attribute frames to a tap that the gate suppressed or the
            // executor rejected. This runs only after execute() accepted the task.
            logReviewEvent("review event=queued source=$source token_id=$tokenId")
            true
        } catch (error: RuntimeException) {
            submissionGate.release(token)
            logReviewError(source, tokenId, "enqueue", error)
            false
        }
    }

    private fun releaseRetryableSubmission(
        token: String,
        source: String,
        tokenId: String,
        reason: String,
    ) {
        submissionGate.release(token)
        StudyCardFrameDiagnostics.onReviewFailed(token, reason)
        logReviewEvent(
            "review event=ui-reset-requested source=$source token_id=$tokenId reason=$reason",
        )
        activity.postToMainIfActive {
            if (activity.activeSession?.token == token) {
                activity.flashcardSwipeFeedback?.cancelCommit()
                activity.resetStudyAnswerForRetry(token)
                // The persisted row may have advanced independently (STALE),
                // and typing submission marks the visible card revealed before
                // its async write starts. Reloading the same kanji resets all
                // interaction state and picks up the current persisted revision;
                // no review progression, toast, or undo snapshot is produced by
                // this retry path.
                val kanji = activity.activeSession?.item?.kanji.orEmpty()
                if (kanji.isNotBlank()) {
                    activity.renderStudyForKanji(kanji)
                }
            }
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
        interactionSource: String = "review-action",
        answerEvidence: AnswerEvidence? = null,
    ): Boolean {
        val session = activity.activeSession ?: return false
        if (activity.activeSimilarWritingRepair != null) {
            return submitSimilarWritingRepair(rating)
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
        val effectiveEvidence = answerEvidence ?: inferredAnswerEvidence(session, mappedReview.ratingCode())
        val request = if (effectiveEvidence == null) {
            mappedReview.request()
        } else {
            mappedReview.request().withAnswerEvidence(effectiveEvidence)
        }
        return runTokenReviewWrite(session, interactionSource, frameRating = rating) { diagnostics ->
            performNormalReview(session, request, ladder, diagnostics)
        }
    }

    private fun inferredAnswerEvidence(
        session: RecordsSchedulerModels.StudySession,
        rating: String,
    ): AnswerEvidence? {
        val failed = StudyRatings.AGAIN == StudyRatings.normalize(rating)
        val example = StudyExampleSelector.exampleForSession(session)
        val alignedReading = example?.let {
            KanjiReadingAligner.alignPlain(
                it.expression,
                it.reading,
                activity.currentDictionaryLookup(),
            )?.firstOrNull { pair -> pair.kanji == session.item?.kanji }?.canonicalReading
        }.orEmpty()
        val common = when (session.taskType) {
            StudyTaskTypes.KANJI_MEANING -> AnswerEvidence(
                coreSkill = CoreSkill.RECOGNITION,
                failureKind = if (failed) FailureKind.UNKNOWN else null,
                evidenceSource = EvidenceSource.INFERRED,
                presentationVariant = PresentationVariant.STANDARD_GLYPH,
                renderedExpression = session.item?.kanji.orEmpty(),
            )
            StudyTaskTypes.FONT_MEANING -> AnswerEvidence(
                coreSkill = CoreSkill.RECOGNITION,
                failureKind = if (failed) FailureKind.UNKNOWN else null,
                evidenceSource = EvidenceSource.INFERRED,
                presentationVariant = PresentationVariant.FONT_GLYPH,
                renderedExpression = session.item?.kanji.orEmpty(),
            )
            StudyTaskTypes.WORD_READING -> AnswerEvidence(
                coreSkill = CoreSkill.CONTEXTUAL_READING,
                failureKind = if (failed) FailureKind.WRONG_READING else null,
                evidenceSource = EvidenceSource.INFERRED,
                presentationVariant = PresentationVariant.PLAIN_WORD,
                renderedExpression = example?.expression.orEmpty(),
                renderedReading = example?.reading.orEmpty(),
                correctAnswer = alignedReading,
            )
            StudyTaskTypes.SENTENCE_READING -> AnswerEvidence(
                coreSkill = CoreSkill.CONTEXTUAL_READING,
                failureKind = if (failed) FailureKind.WRONG_READING else null,
                evidenceSource = EvidenceSource.INFERRED,
                presentationVariant = PresentationVariant.SENTENCE_CONTEXT,
                renderedExpression = example?.expression.orEmpty(),
                renderedReading = example?.reading.orEmpty(),
                correctAnswer = alignedReading,
            )
            StudyTaskTypes.TYPE_MEANING, StudyTaskTypes.TYPING_MEANING -> AnswerEvidence(
                coreSkill = CoreSkill.RECOGNITION,
                failureKind = if (failed) FailureKind.MEANING_UNKNOWN else null,
                evidenceSource = EvidenceSource.OBJECTIVE_CHOICE,
                selectedAnswer = activity.typingAnswerState?.text?.toString().orEmpty(),
                correctAnswer = StudyTextCopy.collectionMeaningForSession(session),
                renderedExpression = session.item?.kanji.orEmpty(),
            )
            else -> null
        }
        if (common != null) return common
        if (!session.writingRequired) return null
        return AnswerEvidence(
            coreSkill = CoreSkill.RECOGNITION,
            failureKind = if (failed) FailureKind.WRITING_SHAPE else null,
            evidenceSource = EvidenceSource.WRITING_EVALUATOR,
            selectedAnswer = activity.activeAnalysis?.status?.name.orEmpty(),
            correctAnswer = session.item?.kanji.orEmpty(),
            renderedExpression = session.item?.kanji.orEmpty(),
        )
    }

    fun submitSimilarWritingRepair(rating: String): Boolean {
        val repair = activity.activeSimilarWritingRepair ?: return false
        val session = activity.activeSession ?: return false
        if (session.taskType != MainActivityBase.TASK_REPAIR_WRITING ||
            session.token != repair.activeToken
        ) {
            return false
        }
        val now = System.currentTimeMillis()
        return runTokenReviewWrite(session, "similar-writing-repair") {
            if (activity.activeSimilarWritingRepair !== repair) {
                return@runTokenReviewWrite ReviewWriteDisposition.RETRYABLE_DROP
            }
            val completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                rating,
                now,
                activity.store::finishSimilarWritingRepair,
                activity.studySessionTracker::recordRepairOutcome,
                activity::markStudyTaskCompleted,
            )
            if (!completion.saved) {
                return@runTokenReviewWrite ReviewWriteDisposition.RETRYABLE_DROP
            }
            runCatching {
                activity.completeActiveRepairStudyTask(activity.similarRepairStudyTaskKey(repair), rating, now)
            }
            refreshWidgetAfterPersistedReviewMutation()
            showToast(StudyTextCopy.similarWritingRepairSavedToast(completion.passed))
            activity.markStudyAnswerApplied(session.token)
            activity.requestReminderRearm("review")
            ReviewWriteDisposition.HANDLED
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
            val completion = StudyRepairActions.skipSimilarWritingRepair(
                repair,
                now,
                activity.store::skipSimilarWritingRepair,
                activity.studySessionTracker::recordRepairOutcome,
                activity::markStudyTaskCompleted,
            )
            if (completion.saved) {
                refreshWidgetAfterPersistedReviewMutation()
            }
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
        correctAnswer: String = targetKanji,
    ): Boolean {
        val session = activity.activeSession ?: return false
        val writingOutcome = StudyReviewWritingOutcome.from(activity.activeAnalysis)
        val hintsUsed = activity.hintsUsed
        val now = System.currentTimeMillis()
        val rating = if (correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN
        val coreSkill = when (rung) {
            RecordsBase.LadderRung.KANJI_READING,
            RecordsBase.LadderRung.READING_KANJI,
            RecordsBase.LadderRung.WORD_READING,
            RecordsBase.LadderRung.SENTENCE_READING -> CoreSkill.CONTEXTUAL_READING
            else -> CoreSkill.RECOGNITION
        }
        val failureKind = if (correct) {
            null
        } else {
            when (rung) {
                RecordsBase.LadderRung.KANJI_READING -> FailureKind.WRONG_READING
                RecordsBase.LadderRung.READING_KANJI -> FailureKind.HOMOPHONE_CONFUSION
                else -> FailureKind.MEANING_UNKNOWN
            }
        }
        val presentation = if (coreSkill == CoreSkill.CONTEXTUAL_READING) {
            PresentationVariant.PLAIN_WORD
        } else {
            PresentationVariant.STANDARD_GLYPH
        }
        val request = StudyReviewRequestPolicy.from(
            session,
            writingOutcome,
            hintsUsed,
            rating,
            false,
        ).request().withAnswerEvidence(
            AnswerEvidence(
                coreSkill = coreSkill,
                failureKind = failureKind,
                evidenceSource = EvidenceSource.OBJECTIVE_CHOICE,
                presentationVariant = presentation,
                selectedAnswer = selectedChoice,
                correctAnswer = correctAnswer,
            )
        )
        return runTokenReviewWrite(session, "choice-answer") { diagnostics ->
            if (!activity.isActiveToken(session.token)) {
                return@runTokenReviewWrite ReviewWriteDisposition.RETRYABLE_DROP
            }
            performNormalReview(
                session,
                request,
                null,
                diagnostics,
                if (rung == RecordsBase.LadderRung.MEANING_KANJI) {
                    ReviewChoiceLog(
                        targetKanji,
                        choiceSignature,
                        selectedChoice,
                        correct,
                        rung.wireName(),
                        now,
                    )
                } else {
                    null
                },
            )
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
            val result = activity.store.evaluateSimilarChoice(card, selectedKanji)
            val rating = if (result.correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN
            val mappedReview = StudyReviewRequestPolicy.from(
                session,
                writingOutcome,
                hintsUsed,
                rating,
                false
            )
            val request = mappedReview.request().withAnswerEvidence(
                AnswerEvidence(
                    coreSkill = CoreSkill.RECOGNITION,
                    failureKind = if (result.correct) null else FailureKind.VISUAL_CONFUSION,
                    evidenceSource = EvidenceSource.OBJECTIVE_CHOICE,
                    presentationVariant = PresentationVariant.STANDARD_GLYPH,
                    selectedAnswer = selectedKanji,
                    correctAnswer = card.targetKanji,
                    confusedWith = if (result.correct) "" else selectedKanji,
                )
            )
            performNormalReview(
                session,
                request,
                ladder,
                diagnostics,
                similarChoice = SimilarChoiceCommit(card, selectedKanji, now),
            )
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
        choiceLog: ReviewChoiceLog? = null,
        similarChoice: SimilarChoiceCommit? = null,
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
            return reconcilePersistedDuplicate(diagnostics, "persistence")
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

        val preparedTask = activity.studySessionTracker.prepareActiveTask(
            activity.sessionTaskKey(session),
            result.appliedRating,
            now,
            true,
        )
        val commit = try {
            saveAppliedReview(
                session,
                request,
                result,
                now,
                diagnostics,
                preparedTask?.timing,
                choiceLog,
                similarChoice,
            )
        } catch (error: Throwable) {
            activity.studySessionTracker.rollbackPreparedTask(preparedTask)
            throw error
        }
        if (commit.disposition != ReviewCommitDisposition.APPLIED) {
            if (commit.disposition == ReviewCommitDisposition.DUPLICATE) {
                return reconcilePersistedDuplicate(diagnostics, "commit")
            }
            activity.studySessionTracker.rollbackPreparedTask(preparedTask)
            logReviewEvent(
                "review event=stale-suppressed source=${diagnostics.source} " +
                    "token_id=${diagnostics.tokenId} phase=commit",
            )
            return ReviewWriteDisposition.RETRYABLE_DROP
        }
        refreshWidgetAfterPersistedReviewMutation()
        activity.studySessionTracker.commitPreparedTask(preparedTask)
        val streak: StudyStatsStore.StudyStreak = activity.store.studyStreak(now)
        showToast(HomeTextCopy.reviewToast(false, result.appliedRating, streak.currentDays))
        activity.markStudyAnswerApplied(session.token)
        logReviewEvent(
            "review event=feedback-ready source=${diagnostics.source} token_id=${diagnostics.tokenId} " +
                "tap_to_feedback_ms=${formatReviewMillis(reviewElapsedMillis(diagnostics.submittedAtNanos, reviewNowNanos()))}",
        )
        activity.requestReminderRearm("review")
        return ReviewWriteDisposition.HANDLED
    }

    /**
     * A persisted duplicate is already complete, even when this activity still
     * shows the session that submitted it. Drop that stale task instead of
     * resuming its timer, invalidate any study-item snapshot read before the
     * competing commit, and let the normal Study route select from the
     * committed scheduler state.
     */
    private fun reconcilePersistedDuplicate(
        diagnostics: ReviewDiagnostics,
        phase: String,
    ): ReviewWriteDisposition {
        activity.studySessionTracker.abandonActiveTask()
        activity.store.clearStudyItemsCache()
        logReviewEvent(
            "review event=duplicate-reconciled source=${diagnostics.source} " +
                "token_id=${diagnostics.tokenId} phase=$phase",
        )
        activity.markStudyAnswerApplied(activity.activeSession?.token.orEmpty())
        return ReviewWriteDisposition.HANDLED
    }

    fun undoLastRating() {
        val pending = activity.studyUndoState.pending ?: return
        val answeredRecovery = activity.pendingStudyRecovery()
            ?.takeIf { it.snapshot.feedback.sessionToken == pending.snapshot.token }
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
            refreshWidgetAfterPersistedReviewMutation()
            // Undo deletes the persisted review token and restores the pre-review
            // item, including that token. Make the restored card genuinely
            // answerable again instead of letting the activity-lifetime gate treat
            // its next rating as a late duplicate.
            submissionGate.release(pending.snapshot.token)
            logReviewEvent(
                "review event=token-released token_id=${reviewTokenId(pending.snapshot.token)} reason=undo",
            )
            activity.clearStudyAnswerAfterUndo(pending.snapshot.token, answeredRecovery)
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
        taskTiming: dev.bee.kanjianki.data.ReviewTaskTiming?,
        choiceLog: ReviewChoiceLog?,
        similarChoice: SimilarChoiceCommit?,
    ): ReviewCommitResult {
        val item = session.item ?: return ReviewCommitResult.stale()
        val commit = StudyReviewActions.saveAppliedReview(
            request,
            result,
            item,
            now,
            StudyReviewActions.ReviewWriter { command ->
                val startedAtNanos = reviewNowNanos()
                try {
                    activity.store.commitReview(command)
                } finally {
                    logReviewEvent(
                        "review event=persist-finished source=${diagnostics.source} token_id=${diagnostics.tokenId} " +
                            "duration_ms=${formatReviewMillis(reviewElapsedMillis(startedAtNanos, reviewNowNanos()))}",
                    )
                }
            },
            activity.studySessionTracker::recordReviewOutcome,
            activity::markStudyRunPassed,
            taskTiming,
            choiceLog,
            similarChoice,
        )
        if (commit.disposition == ReviewCommitDisposition.APPLIED && commit.item != null) {
            activity.studyUndoState.capture(
                StudyReviewActions.AppliedReviewSnapshot(request.token, item, commit.item),
                result.appliedRating,
                now,
            )
        }
        return commit
    }

    private fun refreshWidgetAfterPersistedReviewMutation() {
        try {
            widgetRefresher.run()
        } catch (error: Exception) {
            AppDebugLog.logError("widget refresh request failed after persisted review mutation", error)
        }
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
