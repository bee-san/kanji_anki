package dev.bee.kanjianki.core

/**
 * Routes a study session around a platform that cannot recognize handwriting.
 *
 * ADR 0005: `writing-core` is portable, but only Android's ML Kit recognizer has
 * passed the offline quality/licensing gate, so a host without it declares no writing
 * recognition. Showing an ungradeable `write_kanji` task, inventing a result, or
 * treating a recognizer error as a pass would each corrupt the shared scheduler
 * contract — so this pure policy filters writing *before* the task is presented and
 * re-routes to the item's core recognition revalidation instead.
 *
 * What it must never do, and does not: mark writing passed, mutate stored repair
 * order/enablement, mint a review or timeline token, change scheduler state, or
 * discard the failure cause. It only changes which task the *already-selected* item
 * presents — the item stays selected and studyable, so a due writing-only card is not
 * repeatedly selected-then-skipped. Restoring the same state on a capable host makes
 * the writing task available again, because nothing here was persisted.
 *
 * Deterministic: the same item and the same capability re-route to the same task, so a
 * reload cannot flip between offering and skipping writing.
 */
object StudyCapabilityPolicy {
    /** The trace reason recorded when a writing task is re-routed off an incapable host. */
    const val WRITE_UNAVAILABLE_TRACE: String = "write_kanji_unavailable_on_platform"

    /** The writing wire names the recognition capability gates. */
    private val WRITING_TASKS: Set<String> = setOf(
        StudyTaskTypes.WRITE_KANJI,
        StudyTaskTypes.WRITING_REMEDIATION,
        StudyTaskTypes.TARGETED_WRITING,
        StudyTaskTypes.REPAIR_WRITING,
        StudyTaskTypes.CONTEXT_WRITING,
        StudyTaskTypes.GUIDED_WRITING,
        StudyTaskTypes.BLIND_WRITING,
        StudyTaskTypes.SAMPLED_HANDWRITING,
    )

    @JvmStatic
    fun isWritingTask(taskType: String?): Boolean = taskType in WRITING_TASKS

    /**
     * The session to present, given whether writing recognition is available.
     *
     * When it is, or the task is not writing, the session passes through unchanged and
     * [Rerouted.traceReason] is null. Otherwise the session is rebuilt on the same
     * item, row, and token with the core recognition revalidation task
     * ([StudyTaskTypes.KANJI_MEANING]) and `writingRequired = false`, and the trace
     * reason is [WRITE_UNAVAILABLE_TRACE]. The token is deliberately preserved: a
     * re-route is not a new card, and minting a token here would be the review-token
     * creation the ADR forbids for unavailability.
     */
    @JvmStatic
    fun reroute(
        session: RecordsSchedulerModels.StudySession?,
        writingRecognitionAvailable: Boolean,
    ): Rerouted {
        if (session == null || writingRecognitionAvailable || !isWritingTask(session.taskType)) {
            return Rerouted(session, null)
        }
        val rerouted = RecordsSchedulerModels.StudySession(
            session.item,
            session.row,
            session.token,
            StudyTaskTypes.KANJI_MEANING,
            false,
            session.prompt,
        )
        return Rerouted(rerouted, WRITE_UNAVAILABLE_TRACE)
    }

    /** The re-routed (or unchanged) session and the trace reason, if any. */
    data class Rerouted(
        val session: RecordsSchedulerModels.StudySession?,
        val traceReason: String?,
    )
}
