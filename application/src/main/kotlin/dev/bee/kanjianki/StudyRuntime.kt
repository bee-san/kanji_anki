package dev.bee.kanjianki

import dev.bee.kanjianki.application.StudyUseCases
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.KanjiReadingChoicePlanner
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner
import dev.bee.kanjianki.core.ReadingKanjiChoicePlanner
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyCapabilityPolicy
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudySessionSelector
import dev.bee.kanjianki.core.StudyTaskTypes
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
class StudyRuntime(
    private val useCases: StudyUseCases,
    /**
     * Whether this host can recognize handwriting (ML Kit on Android; absent on
     * desktop GA per ADR 0005). When false, a selected writing task is re-routed to
     * core recognition by [StudyCapabilityPolicy] before it is ever presented — the
     * card stays studyable, so a writing-only due card is not selected-then-skipped.
     */
    private val writingRecognitionAvailable: Boolean = true,
    /**
     * The active-task timer, whose frozen reading rides in the review transaction.
     *
     * Owned here rather than by a host because the runtime is what knows when a task
     * becomes visible ([mount]) and when it is answered ([grade]), and because
     * `ReviewCommitCommand.taskTiming` has to be part of the *same* transaction as the
     * review — CLAUDE.md's persistence contract. A host that timed tasks itself would
     * either commit timing in a second transaction or lose it whenever the runtime
     * advanced a card on its own.
     *
     * Injectable so a test can supply a deterministic elapsed clock; the default is the
     * monotonic one, because a wall clock moving backwards would produce negative
     * durations in stats.
     */
    private val tracker: StudySessionTracker = StudySessionTracker(),
) {
    private val selector = StudySessionSelector()
    private val meaningKanjiPlanner = MeaningKanjiChoicePlanner()
    private var phase: StudySessionPhase = StudySessionPhase.IDLE
    private var session: RecordsSchedulerModels.StudySession? = null
    private var choicePrompt: StudyChoicePrompt? = null
    private var selectionTrace: String? = null
    private var feedback: StudyAnswerFeedbackState? = null
    private var lastApplied: AppliedReviewSnapshot? = null
    private var completedCount: Int = 0
    private var targetCount: Int = 0

    /** The current render: the active session plus the route snapshot the host maps. */
    fun render(): StudyRouteRender = StudyRouteRender(
        session = session,
        routeSnapshot = snapshot(),
        undoable = lastApplied != null,
        choicePrompt = choicePrompt,
        selectionTrace = selectionTrace,
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
        mount(selectNext(queue, nowMillis), nowMillis)
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

        // Freeze the timer before the transaction, commit it inside, and settle the
        // tracker on the disposition: `prepareActiveTask` mutates no progress, so a
        // stale commit rolls back to a still-running timer and the card stays retryable
        // with its elapsed time intact.
        val prepared = tracker.prepareActiveTask(
            key = current.token,
            outcome = result.appliedRating,
            answeredAt = nowMillis,
            countProgress = true,
        )
        val commit = StudyReviewCommit.saveAppliedReview(
            request = request,
            result = result,
            beforeReview = item,
            reviewedAt = nowMillis,
            taskTiming = prepared?.timing,
            writer = { command -> useCases.commitReview(command) },
            recorder = { _, _, _, _ -> },
            marker = { },
        )
        val committed = commit.item
        if (!commit.applied() || committed == null) {
            // A stale or duplicate commit changed nothing: reset so the card is
            // retryable rather than stranded mid-submit.
            tracker.rollbackPreparedTask(prepared)
            gate.resetForRetry(current.token)
            phase = StudySessionPhase.ACTIVE
            return render()
        }
        tracker.commitPreparedTask(prepared)
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
        mount(selectNext(queue, nowMillis), nowMillis)
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
        mount(selectNext(queue, nowMillis), nowMillis)
        return render()
    }

    private suspend fun mount(selected: RecordsSchedulerModels.StudySession?, nowMillis: Long) {
        // Re-route a writing task off an incapable host before anything observes the
        // session — the token, feedback, and choice prompt must all belong to the task
        // actually presented, not the writing task that was filtered out.
        val routed = StudyCapabilityPolicy.reroute(selected, writingRecognitionAvailable)
        val next = routed.session
        selectionTrace = routed.traceReason
        session = next
        choicePrompt = next?.let { buildChoicePrompt(it, nowMillis) }
        feedback = next?.let { StudyAnswerFeedbackState(it.token) }
        phase = if (next != null) StudySessionPhase.ACTIVE else StudySessionPhase.COMPLETE
        // The timer starts when the card is mounted, not when the host draws it: the
        // runtime is the only thing that knows a card was replaced, and a task whose
        // timer started at first paint would miss every card the host re-rendered.
        // `resumeImmediately` because a mounted card is visible — the runtime has no
        // window concept, and a host that is backgrounded pauses through [pauseTask].
        if (next != null) {
            tracker.startActiveTask(
                key = next.token,
                kanji = next.item?.kanji,
                taskType = next.taskType,
                startedAt = nowMillis,
                resumeImmediately = true,
            )
        }
    }

    /**
     * Freezes the visible task's timer, for a host going to the background.
     *
     * Idempotent and safe with no active task, so a host may call it from `onPause`
     * without asking whether a card is up. Without it, time spent with the app closed is
     * counted as time spent studying the card, which is exactly what
     * `StudyTaskTimingPolicy`'s active-elapsed measure exists to exclude.
     */
    fun pauseTask() {
        tracker.pauseActiveTask()
    }

    /** Resumes the visible task's timer, for a host returning to the foreground. */
    fun resumeTask() {
        tracker.resumeActiveTask()
    }

    /**
     * The multiple-choice options for a choice task, or null for a non-choice card.
     *
     * Built from the same `:core` planners Android uses, fed the same
     * `StudyChoiceDataSnapshot`. `meaning_kanji`, `kanji_reading`, and `reading_kanji`
     * are covered; `similar_kanji` carries its own persisted choice state and
     * explanation sub-system and is left to the flashcard fallback until that is
     * shared too. A planner that cannot build a valid card (too few choices, missing
     * data) returns null, and the host renders the flashcard fallback — the same
     * degradation Android's choice sessions take.
     */
    private suspend fun buildChoicePrompt(
        current: RecordsSchedulerModels.StudySession,
        nowMillis: Long,
    ): StudyChoicePrompt? {
        val kanji = current.item?.kanji ?: return null
        return when (current.taskType) {
            StudyTaskTypes.MEANING_KANJI -> {
                val row = current.row ?: return null
                val data = useCases.loadChoiceData(row.kanji, nowMillis)
                val card = meaningKanjiPlanner.buildChoiceCard(
                    row,
                    data.activeRows,
                    data.inventory,
                    null,
                    data.wrongPickCounts,
                    null,
                ) ?: return null
                StudyChoicePrompt(question = row.primaryMeaning, choices = card.choices, correct = card.targetKanji)
            }
            StudyTaskTypes.KANJI_READING -> {
                val data = useCases.loadChoiceData(kanji, nowMillis)
                val card = KanjiReadingChoicePlanner.buildChoiceCard(
                    kanji,
                    data.kanjiReadingUsages,
                    data.kanjiReadingPool,
                    null,
                ) ?: return null
                StudyChoicePrompt(question = card.word, choices = card.choices, correct = card.correctReading)
            }
            StudyTaskTypes.READING_KANJI -> {
                val data = useCases.loadChoiceData(kanji, nowMillis)
                val card = ReadingKanjiChoicePlanner.buildChoiceCard(
                    kanji,
                    data.readingKanjiUsages,
                    data.readingKanjiCandidates,
                    null,
                ) ?: return null
                StudyChoicePrompt(question = card.blankedWord, choices = card.choices, correct = card.targetKanji)
            }
            else -> null
        }
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

/** What a host renders: the active session, the route snapshot, and any choice card. */
data class StudyRouteRender(
    val session: RecordsSchedulerModels.StudySession?,
    val routeSnapshot: StudyRouteSnapshot,
    val undoable: Boolean,
    val choicePrompt: StudyChoicePrompt? = null,
    /**
     * The non-review selection trace, when the runtime re-routed the card.
     *
     * [StudyCapabilityPolicy.WRITE_UNAVAILABLE_TRACE] when a writing task was filtered
     * off an incapable host; null otherwise. Carried so a host or a test can prove the
     * routing happened without the writing task ever being presented.
     */
    val selectionTrace: String? = null,
)

/**
 * A multiple-choice card's options, built by the runtime from a `:core` planner.
 *
 * Present only for a choice task the runtime could build a valid card for; the host
 * maps it to `StudyCard.Choice`, and its absence on a choice task is the flashcard
 * fallback. [correct] is the option that would have been right — the kanji for a
 * meaning-kanji or reading-kanji card, the reading for a kanji-reading card — shown as
 * green feedback after a pick.
 */
data class StudyChoicePrompt(
    val question: String,
    val choices: List<String>,
    val correct: String,
)
