package dev.bee.kanjianki

@JvmInline
value class StudyRouteVersion(val value: Long) {
    fun next(): StudyRouteVersion = StudyRouteVersion(Math.addExact(value, 1L))
}

@JvmInline
value class StudySessionGeneration(val value: Long) {
    fun next(): StudySessionGeneration = StudySessionGeneration(Math.addExact(value, 1L))
}

enum class StudyRouteCompletionReason {
    HARD_CAP,
    FOCUS_COMPLETE,
    LEARN_AHEAD_REPEAT,
    REPAIR,
    NO_SESSION,
    TARGET_RECONCILIATION,
    EXPLICIT_CONTINUE,
    UNDO,
    RESTORE,
    STALE_CALLBACK_DROPPED,
}

data class PreparedStudyRoute<out Model>(
    val model: Model,
    val routeSnapshot: StudyRouteSnapshot,
)

fun <Model> prepareAcceptedStudyRoute(
    routeProvider: () -> Model,
    routeSnapshotProvider: () -> StudyRouteSnapshot,
): PreparedStudyRoute<Model> {
    val model = routeProvider()
    return PreparedStudyRoute(model, routeSnapshotProvider())
}

data class StudyRouteActionClaim(
    val sessionToken: String,
    val sessionGeneration: StudySessionGeneration,
    val routeVersion: StudyRouteVersion,
)

data class StudyRouteSnapshot(
    val version: StudyRouteVersion = StudyRouteVersion(0L),
    val sessionGeneration: StudySessionGeneration = StudySessionGeneration(0L),
    val sessionToken: String? = null,
    val phase: StudySessionPhase = StudySessionPhase.IDLE,
    val feedback: StudyAnswerFeedbackSnapshot? = null,
    val progress: StudySessionProgressUiState = StudySessionProgressUiState(),
    val completionEvidenceReason: StudyRouteCompletionReason? = null,
    val completionReason: StudyRouteCompletionReason? = null,
) {
    val displayedCompletedCount: Int
        get() = progress.completedCount

    val displayedTargetCount: Int
        get() = if (
            progress.activeTask &&
            progress.completedCount == progress.targetCount &&
            progress.targetCount < Int.MAX_VALUE
        ) {
            progress.targetCount + 1
        } else {
            progress.targetCount
        }

    val remainingCount: Int
        get() = progress.targetCount - progress.completedCount

    val canComplete: Boolean
        get() = progress.completedCount == progress.targetCount &&
            !progress.activeTask &&
            feedback?.phase != StudyAnswerFeedbackPhase.SUBMITTING &&
            feedback?.phase != StudyAnswerFeedbackPhase.APPLIED

    val isComplete: Boolean
        get() = phase == StudySessionPhase.COMPLETE && canComplete
}
