package dev.bee.kanjianki

import java.util.concurrent.atomic.AtomicLong

internal enum class StudyRouteLoadKind(val wireName: String) {
    STANDARD("standard"),
    RECOVERY("recovery"),
    TARGETED("targeted"),
}

internal enum class StudyRouteComputationBranch(val wireName: String) {
    UNRESOLVED("unresolved"),
    PENDING_ANSWER("pending-answer"),
    CONTINUED_RECOVERY("continued-recovery"),
    STORED_RECOVERY("stored-recovery"),
    EMPTY_QUEUE("empty-queue"),
    NO_SESSION("no-session"),
    ACTIVE_SESSION("active-session"),
    SOURCE_SYNC_CHANGED("source-sync-changed"),
    TARGET_UNAVAILABLE("target-unavailable"),
    TARGETED_SESSION("targeted-session"),
    PENDING_REPAIR("pending-repair"),
    HARD_CAP("hard-cap"),
}

internal enum class StudyRouteLifecycleStage(val wireName: String) {
    CANDIDATE_CREATED("candidate-created"),
    COMPUTATION_PREPARED("computation-prepared"),
    PUBLICATION_DECIDED("publication-decided"),
}

internal enum class StudyRouteLifecycleOutcome(val wireName: String) {
    CREATED("created"),
    PREPARED("prepared"),
    ACCEPTED("accepted"),
    ACCEPTED_TERMINAL("accepted-terminal"),
    RETRY("retry"),
    DROPPED_STALE("dropped-stale"),
}

/**
 * Privacy-safe Study route trace. Route snapshots are reduced to lifecycle metadata at creation,
 * so neither the release log nor a test observer can retain card or answer content.
 */
internal class StudyRouteLifecycleEvent private constructor(
    val candidateId: Long,
    val stage: StudyRouteLifecycleStage,
    val routeKind: StudyRouteLoadKind,
    val branch: StudyRouteComputationBranch,
    val expectedPhase: StudySessionPhase,
    val expectedVersion: Long,
    val currentPhase: StudySessionPhase,
    val currentVersion: Long,
    val trackerStateEquivalent: Boolean,
    val terminalEligible: Boolean,
    val outcome: StudyRouteLifecycleOutcome,
) {
    fun format(): String = "study-route lifecycle candidate_id=$candidateId " +
        "event=${stage.wireName} route=${routeKind.wireName} branch=${branch.wireName} " +
        "expected_phase=${expectedPhase.name} expected_version=$expectedVersion " +
        "current_phase=${currentPhase.name} current_version=$currentVersion " +
        "tracker_state_equivalent=$trackerStateEquivalent terminal_eligible=$terminalEligible " +
        "outcome=${outcome.wireName}"

    companion object {
        fun candidateCreated(
            candidateId: Long,
            routeKind: StudyRouteLoadKind,
            expectedRoute: StudyRouteSnapshot,
            trackerStateEquivalent: Boolean,
            currentRoute: StudyRouteSnapshot = expectedRoute,
        ): StudyRouteLifecycleEvent = fromRoutes(
            candidateId = candidateId,
            stage = StudyRouteLifecycleStage.CANDIDATE_CREATED,
            routeKind = routeKind,
            branch = StudyRouteComputationBranch.UNRESOLVED,
            expectedRoute = expectedRoute,
            currentRoute = currentRoute,
            trackerStateEquivalent = trackerStateEquivalent,
            terminalEligible = false,
            outcome = StudyRouteLifecycleOutcome.CREATED,
        )

        fun computationPrepared(
            candidateId: Long,
            routeKind: StudyRouteLoadKind,
            branch: StudyRouteComputationBranch,
            expectedRoute: StudyRouteSnapshot,
            currentRoute: StudyRouteSnapshot,
            trackerStateEquivalent: Boolean,
            terminalEligible: Boolean,
        ): StudyRouteLifecycleEvent = fromRoutes(
            candidateId = candidateId,
            stage = StudyRouteLifecycleStage.COMPUTATION_PREPARED,
            routeKind = routeKind,
            branch = branch,
            expectedRoute = expectedRoute,
            currentRoute = currentRoute,
            trackerStateEquivalent = trackerStateEquivalent,
            terminalEligible = terminalEligible,
            outcome = StudyRouteLifecycleOutcome.PREPARED,
        )

        fun publicationDecided(
            candidateId: Long,
            routeKind: StudyRouteLoadKind,
            branch: StudyRouteComputationBranch,
            expectedRoute: StudyRouteSnapshot,
            currentRoute: StudyRouteSnapshot,
            trackerStateEquivalent: Boolean,
            terminalEligible: Boolean,
            outcome: StudyRouteLifecycleOutcome,
        ): StudyRouteLifecycleEvent = fromRoutes(
            candidateId = candidateId,
            stage = StudyRouteLifecycleStage.PUBLICATION_DECIDED,
            routeKind = routeKind,
            branch = branch,
            expectedRoute = expectedRoute,
            currentRoute = currentRoute,
            trackerStateEquivalent = trackerStateEquivalent,
            terminalEligible = terminalEligible,
            outcome = outcome,
        )

        private fun fromRoutes(
            candidateId: Long,
            stage: StudyRouteLifecycleStage,
            routeKind: StudyRouteLoadKind,
            branch: StudyRouteComputationBranch,
            expectedRoute: StudyRouteSnapshot,
            currentRoute: StudyRouteSnapshot,
            trackerStateEquivalent: Boolean,
            terminalEligible: Boolean,
            outcome: StudyRouteLifecycleOutcome,
        ): StudyRouteLifecycleEvent = StudyRouteLifecycleEvent(
            candidateId = candidateId,
            stage = stage,
            routeKind = routeKind,
            branch = branch,
            expectedPhase = expectedRoute.phase,
            expectedVersion = expectedRoute.version.value,
            currentPhase = currentRoute.phase,
            currentVersion = currentRoute.version.value,
            trackerStateEquivalent = trackerStateEquivalent,
            terminalEligible = terminalEligible,
            outcome = outcome,
        )
    }
}

internal fun interface StudyRouteLifecycleObserver {
    fun onEvent(event: StudyRouteLifecycleEvent)
}

internal object StudyRouteLifecycleDiagnostics {
    private val nextCandidateId = AtomicLong(0L)

    @Volatile
    private var testObserver: StudyRouteLifecycleObserver? = null

    fun newCandidateId(): Long = nextCandidateId.incrementAndGet()

    fun isRecording(): Boolean = testObserver != null || AppDebugLog.isCapturing()

    fun record(event: StudyRouteLifecycleEvent) {
        testObserver?.let { observer ->
            runCatching { observer.onEvent(event) }
        }
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log(event.format())
        }
    }

    fun setObserverForTests(observer: StudyRouteLifecycleObserver?) {
        testObserver = observer
    }

    fun resetForTests() {
        testObserver = null
        nextCandidateId.set(0L)
    }
}
