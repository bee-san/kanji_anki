package dev.bee.kanjianki

/**
 * Pure decision core for Study route resolution.
 *
 * The Study route is computed on a background executor and published on the main thread
 * through a compare-and-set against [StudyRouteSnapshot] and the staged
 * [StudySessionTracker]. Every place where that pipeline used to decide "render this",
 * "retry", or "reload in recovery-only mode" is expressed here as a function of an
 * immutable input, so the decisions can be tested exhaustively without an Activity.
 *
 * The coordinator ([MainActivityStudyQueueCoordinator]) gathers inputs, asks this object
 * what to do, and performs the effects. Effects that re-enter the route (tracker-race
 * retries, recovery-only reloads, source-sync restarts) must be admitted by
 * [admitRerender], which enforces the anti-livelock invariants:
 *
 * 1. Terminal decisions are absorbing: a terminal branch whose canonical route already
 *    proves completion is accepted instead of retried ([resolvePublication]).
 * 2. A recovery-only reload is attempted at most once per distinct route input (route
 *    snapshot modulo version, plus the durable recovery payload). Asking again for the
 *    same input would recompute the same decision, so it is refused.
 * 3. Tracker-race retries against one route input are bounded, and the whole chain of
 *    internal re-renders started by one external entry is bounded. Exceeding either
 *    budget halts the chain instead of scheduling another render.
 *
 * External entries (user navigation, lifecycle restoration, explicit Retry) reset the
 * loop state; only re-renders the pipeline schedules for itself are counted.
 */
internal object StudyRouteResolver {
    /** Maximum consecutive tracker-CAS retries against the same route input. */
    const val MAX_TRACKER_RETRIES_PER_INPUT: Int = 3

    /** Maximum internal re-renders one external entry may trigger before the chain halts. */
    const val MAX_INTERNAL_RERENDER_CHAIN: Int = 8

    // ---------------------------------------------------------------------------------
    // Publication: what to do with a computed candidate on the main thread.
    // ---------------------------------------------------------------------------------

    fun resolvePublication(input: StudyRoutePublicationInput): StudyRoutePublicationDecision = when {
        input.published -> StudyRoutePublicationDecision.ACCEPT
        !input.routeStillCurrent -> StudyRoutePublicationDecision.DROP_STALE
        // A terminal loader does not need to publish staged tracker internals when the
        // still-current canonical route already proves completion. Retrying that CAS
        // race can livelock because each retry can lose to another tracker revision.
        input.terminalBranch && input.currentCanComplete && input.trackerStateEquivalent ->
            StudyRoutePublicationDecision.ACCEPT_TERMINAL_RACE
        else -> StudyRoutePublicationDecision.RETRY
    }

    // ---------------------------------------------------------------------------------
    // Terminal branches: how a done/empty/no-session render consumes a continued marker.
    // ---------------------------------------------------------------------------------

    /** Branches whose terminal evidence is "there is no next session". */
    fun isSessionAbsenceBranch(branch: StudyRouteComputationBranch): Boolean = when (branch) {
        StudyRouteComputationBranch.EMPTY_QUEUE,
        StudyRouteComputationBranch.NO_SESSION,
        StudyRouteComputationBranch.TARGET_UNAVAILABLE,
        -> true
        else -> false
    }

    fun resolveTerminal(input: StudyTerminalInput): StudyTerminalDecision {
        val marker = input.marker ?: return StudyTerminalDecision.RENDER_TERMINAL
        if (isSessionAbsenceBranch(input.branch) && !input.evidence.canComplete) {
            return if (canClearMarkerForSessionAbsence(marker, input.evidence, input.mountedSessionToken)) {
                StudyTerminalDecision.CLEAR_MARKER_FOR_SESSION_ABSENCE
            } else {
                StudyTerminalDecision.RECOVER
            }
        }
        return if (canClearMarkerForTerminal(marker, input.evidence, input.mountedSessionToken)) {
            StudyTerminalDecision.CLEAR_MARKER_KEEP_SESSION
        } else {
            StudyTerminalDecision.RECOVER
        }
    }

    /**
     * A continued marker may be consumed by completion evidence only when the canonical
     * route can complete and the evidence belongs to the exact mounted session the marker
     * was written for (or, for a session-less terminal, no session is mounted).
     */
    fun canClearMarkerForTerminal(
        marker: StudyContinuedMarkerFacts,
        evidence: StudyRouteSnapshot,
        mountedSessionToken: String?,
    ): Boolean {
        if (!evidence.canComplete) return false
        if (evidence.sessionToken == null) return mountedSessionToken == null
        return evidence.progress.targetCount > 0 &&
            !mountedSessionToken.isNullOrEmpty() &&
            evidence.sessionToken == mountedSessionToken &&
            marker.feedbackSessionToken == mountedSessionToken
    }

    /**
     * Consumes an exact continued-answer handoff when the accepted loader proved that there
     * is no next session. Session absence must unmount the old session and reconcile its
     * stale timer/target before [StudyRouteSnapshot.canComplete] can become true, so this
     * deliberately does not require completion evidence (#607): requiring it would create
     * a circular dependency where recovery cannot clear before completion, while terminal
     * reconciliation cannot run before the exact recovery record clears (#608).
     */
    fun canClearMarkerForSessionAbsence(
        marker: StudyContinuedMarkerFacts,
        evidence: StudyRouteSnapshot,
        mountedSessionToken: String?,
    ): Boolean {
        val mounted = mountedSessionToken ?: return false
        return !evidence.isComplete &&
            (evidence.phase == StudySessionPhase.ADVANCING || evidence.phase == StudySessionPhase.LOADING) &&
            evidence.sessionToken == mounted &&
            marker.feedbackPhase == StudyAnswerFeedbackPhase.CONTINUED &&
            marker.feedbackSessionToken == mounted
    }

    // ---------------------------------------------------------------------------------
    // Re-render admission: the structural anti-livelock guard.
    // ---------------------------------------------------------------------------------

    fun admitRerender(
        state: StudyRouteLoopState,
        kind: StudyRouteRerenderKind,
        key: StudyRouteLoopKey,
    ): StudyRouteRerenderResolution {
        if (state.chainLength >= MAX_INTERNAL_RERENDER_CHAIN) {
            return StudyRouteRerenderResolution(state, StudyRouteRerenderAdmission.SUPPRESS_CHAIN_BUDGET)
        }
        return when (kind) {
            StudyRouteRerenderKind.RECOVERY -> if (key in state.recoveryKeys) {
                StudyRouteRerenderResolution(state, StudyRouteRerenderAdmission.SUPPRESS_REPEATED_RECOVERY)
            } else {
                StudyRouteRerenderResolution(
                    state.copy(
                        chainLength = state.chainLength + 1,
                        recoveryKeys = state.recoveryKeys + key,
                    ),
                    StudyRouteRerenderAdmission.ALLOW,
                )
            }

            StudyRouteRerenderKind.TRACKER_RETRY -> {
                val consecutive = if (state.lastRetryKey == key) state.consecutiveRetries else 0
                if (consecutive >= MAX_TRACKER_RETRIES_PER_INPUT) {
                    StudyRouteRerenderResolution(state, StudyRouteRerenderAdmission.SUPPRESS_RETRY_BUDGET)
                } else {
                    StudyRouteRerenderResolution(
                        state.copy(
                            chainLength = state.chainLength + 1,
                            lastRetryKey = key,
                            consecutiveRetries = consecutive + 1,
                        ),
                        StudyRouteRerenderAdmission.ALLOW,
                    )
                }
            }

            StudyRouteRerenderKind.SOURCE_SYNC_RETRY -> StudyRouteRerenderResolution(
                state.copy(chainLength = state.chainLength + 1),
                StudyRouteRerenderAdmission.ALLOW,
            )
        }
    }
}

/** Main-thread facts about a computed candidate at publication time. */
internal data class StudyRoutePublicationInput(
    /** The staged tracker (or, for route-only candidates, the route) was accepted. */
    val published: Boolean,
    /**
     * The canonical route is still compatible with the candidate's expected route even
     * though the tracker CAS lost. Always false for route-only candidates.
     */
    val routeStillCurrent: Boolean,
    /** The candidate renders a terminal (done/empty/no-session) branch. */
    val terminalBranch: Boolean,
    val currentCanComplete: Boolean,
    val trackerStateEquivalent: Boolean,
)

internal enum class StudyRoutePublicationDecision(val outcome: StudyRouteLifecycleOutcome) {
    ACCEPT(StudyRouteLifecycleOutcome.ACCEPTED),
    ACCEPT_TERMINAL_RACE(StudyRouteLifecycleOutcome.ACCEPTED_TERMINAL),
    RETRY(StudyRouteLifecycleOutcome.RETRY),
    DROP_STALE(StudyRouteLifecycleOutcome.DROPPED_STALE),
}

/** The parts of a durable continued-answer marker the terminal decision reads. */
internal data class StudyContinuedMarkerFacts(
    val feedbackPhase: StudyAnswerFeedbackPhase,
    val feedbackSessionToken: String,
) {
    companion object {
        fun of(recovery: StoredPendingStudyRecovery): StudyContinuedMarkerFacts = StudyContinuedMarkerFacts(
            feedbackPhase = recovery.snapshot.feedback.phase,
            feedbackSessionToken = recovery.snapshot.feedback.sessionToken,
        )
    }
}

internal data class StudyTerminalInput(
    val branch: StudyRouteComputationBranch,
    val marker: StudyContinuedMarkerFacts?,
    val evidence: StudyRouteSnapshot,
    val mountedSessionToken: String?,
)

internal enum class StudyTerminalDecision {
    /** No continued marker is outstanding: render the terminal screen directly. */
    RENDER_TERMINAL,

    /** Completion evidence proves the run is done: consume the marker, keep the session. */
    CLEAR_MARKER_KEEP_SESSION,

    /** No next session exists: consume the marker, unmount, and reconcile the tracker. */
    CLEAR_MARKER_FOR_SESSION_ABSENCE,

    /** The evidence cannot consume the marker: reload in recovery-only mode (guarded). */
    RECOVER,
}

internal enum class StudyRouteRerenderKind(val wireName: String) {
    /** The canonical route is current but the staged tracker lost its revision CAS. */
    TRACKER_RETRY("tracker-retry"),

    /** A durable recovery record could not be claimed/cleared; reload recovery-only. */
    RECOVERY("recovery"),

    /** A sync finished while the route was computing; recompute from fresh data. */
    SOURCE_SYNC_RETRY("source-sync-retry"),
}

internal enum class StudyRouteRerenderAdmission(val wireName: String) {
    ALLOW("allow"),
    SUPPRESS_REPEATED_RECOVERY("suppress-repeated-recovery"),
    SUPPRESS_RETRY_BUDGET("suppress-retry-budget"),
    SUPPRESS_CHAIN_BUDGET("suppress-chain-budget"),
    ;

    val allowed: Boolean
        get() = this == ALLOW
}

/**
 * The input a re-render would observe. The route version is excluded because it advances
 * on presentation-only events (loading, claimed actions) that do not change what the
 * loader would decide.
 */
internal data class StudyRouteLoopKey private constructor(
    val route: StudyRouteSnapshot,
    val recoveryFingerprint: String?,
) {
    companion object {
        fun of(route: StudyRouteSnapshot, recoveryFingerprint: String?): StudyRouteLoopKey =
            StudyRouteLoopKey(route.copy(version = StudyRouteVersion(0L)), recoveryFingerprint)
    }
}

internal data class StudyRouteLoopState(
    val chainLength: Int = 0,
    val recoveryKeys: Set<StudyRouteLoopKey> = emptySet(),
    val lastRetryKey: StudyRouteLoopKey? = null,
    val consecutiveRetries: Int = 0,
)

internal data class StudyRouteRerenderResolution(
    val state: StudyRouteLoopState,
    val admission: StudyRouteRerenderAdmission,
)

internal fun studyRouteLoopGuardLog(
    kind: StudyRouteRerenderKind,
    admission: StudyRouteRerenderAdmission,
    state: StudyRouteLoopState,
    key: StudyRouteLoopKey,
): String = "study-route loop-guard kind=${kind.wireName} admission=${admission.wireName} " +
    "chain=${state.chainLength} consecutive_retries=${state.consecutiveRetries} " +
    "phase=${key.route.phase.name} generation=${key.route.sessionGeneration.value} " +
    "can_complete=${key.route.canComplete} recovery_payload=${key.recoveryFingerprint != null}"
