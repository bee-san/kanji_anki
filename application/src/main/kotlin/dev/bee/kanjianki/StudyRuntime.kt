package dev.bee.kanjianki

import dev.bee.kanjianki.application.StudyUseCases
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudySessionSelector
import dev.bee.kanjianki.data.ReviewTokenQuery
import dev.bee.kanjianki.data.StudyQueueSnapshot

/**
 * One study session, driven portably.
 *
 * The scheduler-facing half of Goal 195, shared by both hosts: it selects the next
 * card with `StudySessionSelector`, applies a grade with `BridgeScheduler`, commits it
 * through [StudyReviewCommit] and [StudyUseCases], and holds the one-card feedback
 * gate — the same pipeline `MainActivityStudyQueueCoordinator` and
 * `MainActivityStudyReviewFlow` run on Android, minus the Android UI and its
 * view-model's generation/route-version bookkeeping (which `StudySessionStateMachine`
 * owns and which a reload-per-action host does not need). A host maps the
 * [StudyRouteRender] this produces to its portable
 * [dev.bee.kanjianki.presentation.StudySession] and renders it.
 *
 * Deliberately host-agnostic: no dispatcher, no clock beyond the `nowMillis` the caller
 * passes, no Compose — which is what lets one runtime serve the desktop window and,
 * from Goal 200, the Android host, and makes the persist-restart-prove-state test a
 * plain function call.
 *
 * The persistence invariants are CLAUDE.md's and are enforced here:
 * - **Token-idempotent.** A grade whose token is already in the review log advances
 *   nothing; the session moves on.
 * - **Revision-CAS.** The commit carries the item's expected revision (from
 *   `beforeReview.schedulerRevision`), so a stale grade against a since-advanced item
 *   is rejected by the repository.
 * - **Only APPLIED advances state.** Feedback reaches APPLIED only when the commit
 *   landed; a duplicate or stale commit leaves the card retryable.
 */
class StudyRuntime(private val useCases: StudyUseCases) {
    private val selector = StudySessionSelector()
    private var phase: StudySessionPhase = StudySessionPhase.IDLE
    private var session: RecordsSchedulerModels.StudySession? = null
    private var feedback: StudyAnswerFeedbackState? = null
    private var lastApplied: AppliedReviewSnapshot? = null
    private var completedCount: Int = 0
    private var targetCount: Int = 0

    /** The current render: the active session plus the route snapshot the host maps. */
    fun render(): StudyRouteRender = StudyRouteRender(
        session = session,
        routeSnapshot = snapshot(),
        undoable = lastApplied != null,
    )

    /**
     * Loads the first (or next) card from the committed scheduler state.
     *
     * Selection is against the persisted queue, so a card a competing commit already
     * advanced is not re-served. When the queue is drained the session completes; the
     * caller reads a null session plus the snapshot phase to tell done from empty.
     */
    suspend fun load(nowMillis: Long): StudyRouteRender {
        phase = StudySessionPhase.LOADING
        val queue = useCases.loadQueue(nowMillis)
        targetCount = countDue(queue, nowMillis)
        completedCount = 0
        mount(selectNext(queue, nowMillis))
        return render()
    }

    /**
     * Applies a grade to the visible card and advances the feedback gate.
     *
     * [rating] is a scheduler wire name. A grade is accepted only when a card is
     * mounted and unanswered — the double-commit gate the UI also enforces. On an
     * APPLIED commit the feedback moves to APPLIED and the card stays mounted until
     * [continueCard]; on a duplicate or stale commit the card is left retryable.
     */
    suspend fun grade(rating: String, nowMillis: Long): StudyRouteRender {
        val current = session ?: return render()
        val gate = feedback ?: return render()
        if (gate.snapshot().phase != StudyAnswerFeedbackPhase.UNANSWERED) return render()
        val item = current.item ?: return render()

        val outcome = if (rating == StudyRatings.AGAIN) StudyAnswerOutcome.INCORRECT else StudyAnswerOutcome.CORRECT
        gate.begin(outcome, rating)
        phase = StudySessionPhase.SUBMITTING

        val consumed = useCases.reviewTokenStatus(
            ReviewTokenQuery(current.token, item.kanji, current.taskType, item.answerSignature),
        ).consumed
        if (consumed) {
            // Already committed by a competing path: the card is done. Advance the gate
            // rather than double-applying.
            gate.markApplied(current.token)
            phase = StudySessionPhase.FEEDBACK
            return render()
        }

        val queue = useCases.loadQueue(nowMillis)
        val scheduler = BridgeScheduler.withWeights(queue.schedulerFsrsWeights?.toDoubleArray())
        val parameters = queue.schedulerParameters.withTargetRetention(
            queue.schedulerParameters.targetRetentionForRank(current.row?.jitenRank),
        )
        val request = StudyReviewRequestPolicy.from(
            current,
            null,
            0,
            rating,
            false,
        ).request()
        val result = scheduler.applyReview(
            item,
            request,
            HashSet(),
            nowMillis,
            parameters,
            queue.syncSettings,
            queue.learningSteps,
            queue.studyLadder,
        )
        if (result.duplicate) {
            gate.markApplied(current.token)
            phase = StudySessionPhase.FEEDBACK
            return render()
        }

        val commit = StudyReviewCommit.saveAppliedReview(
            request = request,
            result = result,
            beforeReview = item,
            reviewedAt = nowMillis,
            writer = { command -> useCases.commitReview(command) },
            recorder = { _, _, _, _ -> },
            marker = { },
        )
        val committed = commit.item
        if (!commit.applied() || committed == null) {
            // A stale or duplicate commit changed nothing: reset so the card is
            // retryable rather than stranded mid-submit.
            gate.resetForRetry(current.token)
            phase = StudySessionPhase.ACTIVE
            return render()
        }
        gate.markApplied(current.token)
        phase = StudySessionPhase.FEEDBACK
        lastApplied = AppliedReviewSnapshot(current.token, item, committed)
        completedCount += 1
        return render()
    }

    /** Advances past the one-card feedback gate to the next card. */
    suspend fun continueCard(nowMillis: Long): StudyRouteRender {
        val gate = feedback ?: return render()
        if (!gate.tryContinue()) return render()
        val queue = useCases.loadQueue(nowMillis)
        mount(selectNext(queue, nowMillis))
        return render()
    }

    /**
     * Reverses the last applied review and re-selects.
     *
     * Only when [render]'s `undoable` was true — the runtime holds exactly one
     * reversible snapshot, cleared once used, so undo cannot walk past the card it
     * remembers. The repository's own boundary check rejects an undo whose item has
     * since advanced, in which case nothing changes.
     */
    suspend fun undo(nowMillis: Long): StudyRouteRender {
        val snapshot = lastApplied ?: return render()
        lastApplied = null
        val reversed = useCases.undoLastReview(snapshot)
        if (!reversed) return render()
        completedCount = (completedCount - 1).coerceAtLeast(0)
        val queue = useCases.loadQueue(nowMillis)
        mount(selectNext(queue, nowMillis))
        return render()
    }

    private fun mount(next: RecordsSchedulerModels.StudySession?) {
        session = next
        feedback = next?.let { StudyAnswerFeedbackState(it.token) }
        phase = if (next != null) StudySessionPhase.ACTIVE else StudySessionPhase.COMPLETE
    }

    private fun snapshot(): StudyRouteSnapshot = StudyRouteSnapshot(
        sessionToken = session?.token,
        phase = phase,
        feedback = feedback?.snapshot(),
        progress = StudySessionProgressUiState(
            targetCount = targetCount.coerceAtLeast(completedCount),
            completedCount = completedCount,
            activeTask = session != null,
        ),
    )

    private fun selectNext(queue: StudyQueueSnapshot, nowMillis: Long): RecordsSchedulerModels.StudySession? =
        selector.nextSession(
            queue.studyItems,
            queue.activeRows,
            nowMillis,
            queue.studyAheadMinutes * MINUTE_MILLIS,
            null,
            queue.syncSettings,
            queue.studyLadder,
        )

    private fun countDue(queue: StudyQueueSnapshot, nowMillis: Long): Int =
        selector.dueCount(
            queue.studyItems,
            queue.activeRows,
            nowMillis,
            queue.studyAheadMinutes * MINUTE_MILLIS,
            queue.studyLadder,
        )

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

/** What a host renders: the active session and the route snapshot the model maps. */
data class StudyRouteRender(
    val session: RecordsSchedulerModels.StudySession?,
    val routeSnapshot: StudyRouteSnapshot,
    val undoable: Boolean,
)
