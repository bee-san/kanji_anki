package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StudyRouteResolverTest {
    // ---------------------------------------------------------------------------------
    // Publication
    // ---------------------------------------------------------------------------------

    @Test
    fun publicationTableMatchesTheLegacyCoordinatorExpressionForEveryInput() {
        for (input in allPublicationInputs()) {
            assertEquals(input.toString(), legacyPublication(input), StudyRouteResolver.resolvePublication(input))
        }
    }

    @Test
    fun publicationDecisionsRespectTheirPreconditions() {
        for (input in allPublicationInputs()) {
            when (StudyRouteResolver.resolvePublication(input)) {
                StudyRoutePublicationDecision.ACCEPT -> assertTrue(input.published)
                StudyRoutePublicationDecision.ACCEPT_TERMINAL_RACE -> {
                    assertFalse(input.published)
                    assertTrue(input.routeStillCurrent)
                    assertTrue(input.terminalBranch)
                    assertTrue(input.currentCanComplete)
                    assertTrue(input.trackerStateEquivalent)
                }
                StudyRoutePublicationDecision.RETRY -> {
                    assertFalse(input.published)
                    assertTrue("a retry needs a still-current route", input.routeStillCurrent)
                    assertFalse(
                        "a completed terminal route with equivalent tracker state is absorbing",
                        input.terminalBranch && input.currentCanComplete && input.trackerStateEquivalent,
                    )
                }
                StudyRoutePublicationDecision.DROP_STALE -> {
                    assertFalse(input.published)
                    assertFalse(input.routeStillCurrent)
                }
            }
        }
    }

    @Test
    fun publicationOutcomesMapToLifecycleWireOutcomes() {
        assertEquals(StudyRouteLifecycleOutcome.ACCEPTED, StudyRoutePublicationDecision.ACCEPT.outcome)
        assertEquals(
            StudyRouteLifecycleOutcome.ACCEPTED_TERMINAL,
            StudyRoutePublicationDecision.ACCEPT_TERMINAL_RACE.outcome,
        )
        assertEquals(StudyRouteLifecycleOutcome.RETRY, StudyRoutePublicationDecision.RETRY.outcome)
        assertEquals(StudyRouteLifecycleOutcome.DROPPED_STALE, StudyRoutePublicationDecision.DROP_STALE.outcome)
    }

    /** #601: a no-visible-change tracker revision must not retry a completed terminal route. */
    @Test
    fun regression601TerminalTrackerRaceIsAcceptedInsteadOfRetried() {
        val decision = StudyRouteResolver.resolvePublication(
            StudyRoutePublicationInput(
                published = false,
                routeStillCurrent = true,
                terminalBranch = true,
                currentCanComplete = true,
                trackerStateEquivalent = true,
            ),
        )
        assertEquals(StudyRoutePublicationDecision.ACCEPT_TERMINAL_RACE, decision)

        val nonTerminal = StudyRouteResolver.resolvePublication(
            StudyRoutePublicationInput(
                published = false,
                routeStillCurrent = true,
                terminalBranch = false,
                currentCanComplete = true,
                trackerStateEquivalent = true,
            ),
        )
        assertEquals(StudyRoutePublicationDecision.RETRY, nonTerminal)
    }

    // ---------------------------------------------------------------------------------
    // Terminal marker consumption
    // ---------------------------------------------------------------------------------

    @Test
    fun terminalTable() {
        data class Row(
            val name: String,
            val branch: StudyRouteComputationBranch,
            val marker: StudyContinuedMarkerFacts?,
            val evidence: StudyRouteSnapshot,
            val mounted: String?,
            val expected: StudyTerminalDecision,
        )
        val continuedA = StudyContinuedMarkerFacts(StudyAnswerFeedbackPhase.CONTINUED, "a")
        val rows = listOf(
            Row(
                "no marker renders directly",
                StudyRouteComputationBranch.HARD_CAP,
                null,
                route(StudySessionPhase.ACTIVE, "a", target = 2, completed = 1),
                "a",
                StudyTerminalDecision.RENDER_TERMINAL,
            ),
            Row(
                "hard cap with completion evidence keeps the session for done",
                StudyRouteComputationBranch.HARD_CAP,
                continuedA,
                route(StudySessionPhase.LOADING, "a", target = 1, completed = 1),
                "a",
                StudyTerminalDecision.CLEAR_MARKER_KEEP_SESSION,
            ),
            Row(
                "hard cap without completion evidence recovers",
                StudyRouteComputationBranch.HARD_CAP,
                continuedA,
                route(StudySessionPhase.ADVANCING, "a", target = 2, completed = 1),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "hard cap with a zero target cannot be consumed",
                StudyRouteComputationBranch.HARD_CAP,
                continuedA,
                route(StudySessionPhase.LOADING, "a", target = 0, completed = 0),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "hard cap marker for another session recovers",
                StudyRouteComputationBranch.HARD_CAP,
                StudyContinuedMarkerFacts(StudyAnswerFeedbackPhase.CONTINUED, "b"),
                route(StudySessionPhase.LOADING, "a", target = 1, completed = 1),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "session-less completion with nothing mounted clears",
                StudyRouteComputationBranch.NO_SESSION,
                continuedA,
                route(StudySessionPhase.IDLE, null, target = 1, completed = 1),
                null,
                StudyTerminalDecision.CLEAR_MARKER_KEEP_SESSION,
            ),
            Row(
                "session-less completion with a stale mounted session recovers",
                StudyRouteComputationBranch.EMPTY_QUEUE,
                continuedA,
                route(StudySessionPhase.IDLE, null, target = 1, completed = 1),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "no-session after continue with an active timer clears for absence",
                StudyRouteComputationBranch.NO_SESSION,
                continuedA,
                route(StudySessionPhase.ADVANCING, "a", target = 2, completed = 1, activeTask = true),
                "a",
                StudyTerminalDecision.CLEAR_MARKER_FOR_SESSION_ABSENCE,
            ),
            Row(
                "absence requires a continued marker",
                StudyRouteComputationBranch.NO_SESSION,
                StudyContinuedMarkerFacts(StudyAnswerFeedbackPhase.APPLIED, "a"),
                route(StudySessionPhase.ADVANCING, "a", target = 2, completed = 1),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "absence requires a mounted session",
                StudyRouteComputationBranch.EMPTY_QUEUE,
                continuedA,
                route(StudySessionPhase.ADVANCING, "a", target = 2, completed = 1),
                null,
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "absence rejects an active (unanswered) phase",
                StudyRouteComputationBranch.TARGET_UNAVAILABLE,
                continuedA,
                route(StudySessionPhase.ACTIVE, "a", target = 2, completed = 1),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
            Row(
                "absence rejects evidence for a different session",
                StudyRouteComputationBranch.NO_SESSION,
                continuedA,
                route(StudySessionPhase.LOADING, "b", target = 2, completed = 1),
                "a",
                StudyTerminalDecision.RECOVER,
            ),
        )
        for (row in rows) {
            assertEquals(
                row.name,
                row.expected,
                StudyRouteResolver.resolveTerminal(
                    StudyTerminalInput(row.branch, row.marker, row.evidence, row.mounted),
                ),
            )
        }
    }

    /**
     * #607: when the loader proves there is no next session, the continued marker must be
     * consumable while the route is still ADVANCING and cannot complete yet. Requiring
     * completion first made recovery and completion wait on each other forever.
     */
    @Test
    fun regression607NoSessionAbsenceDoesNotWaitForCompletionEvidence() {
        val evidence = route(StudySessionPhase.ADVANCING, "t", target = 2, completed = 1, activeTask = true)
        assertFalse(evidence.canComplete)
        val decision = StudyRouteResolver.resolveTerminal(
            StudyTerminalInput(
                StudyRouteComputationBranch.NO_SESSION,
                StudyContinuedMarkerFacts(StudyAnswerFeedbackPhase.CONTINUED, "t"),
                evidence,
                mountedSessionToken = "t",
            ),
        )
        assertEquals(StudyTerminalDecision.CLEAR_MARKER_FOR_SESSION_ABSENCE, decision)
    }

    /** #608: the same absence clear must be reachable from the LOADING presentation phase. */
    @Test
    fun regression608AbsenceClearIsReachableWhileLoading() {
        val evidence = route(StudySessionPhase.LOADING, "t", target = 2, completed = 1, activeTask = true)
        for (branch in listOf(
            StudyRouteComputationBranch.EMPTY_QUEUE,
            StudyRouteComputationBranch.NO_SESSION,
            StudyRouteComputationBranch.TARGET_UNAVAILABLE,
        )) {
            assertEquals(
                branch.name,
                StudyTerminalDecision.CLEAR_MARKER_FOR_SESSION_ABSENCE,
                StudyRouteResolver.resolveTerminal(
                    StudyTerminalInput(
                        branch,
                        StudyContinuedMarkerFacts(StudyAnswerFeedbackPhase.CONTINUED, "t"),
                        evidence,
                        mountedSessionToken = "t",
                    ),
                ),
            )
        }
    }

    @Test
    fun terminalDecisionMatchesTheLegacyPredicatesExhaustively() {
        var checked = 0
        for (input in allTerminalInputs()) {
            val decision = StudyRouteResolver.resolveTerminal(input)
            assertEquals(input.toString(), legacyTerminal(input), decision)
            when (decision) {
                StudyTerminalDecision.RENDER_TERMINAL -> assertEquals(null, input.marker)
                StudyTerminalDecision.CLEAR_MARKER_KEEP_SESSION -> {
                    assertTrue(input.evidence.canComplete)
                    assertEquals(input.evidence.sessionToken, input.mountedSessionToken)
                }
                StudyTerminalDecision.CLEAR_MARKER_FOR_SESSION_ABSENCE -> {
                    assertTrue(StudyRouteResolver.isSessionAbsenceBranch(input.branch))
                    assertEquals(input.mountedSessionToken, input.marker?.feedbackSessionToken)
                    assertEquals(StudyAnswerFeedbackPhase.CONTINUED, input.marker?.feedbackPhase)
                }
                StudyTerminalDecision.RECOVER -> assertTrue(input.marker != null)
            }
            checked++
        }
        assertTrue(checked > 1_000)
    }

    // ---------------------------------------------------------------------------------
    // Re-render admission (anti-livelock)
    // ---------------------------------------------------------------------------------

    @Test
    fun recoveryIsAttemptedAtMostOncePerInput() {
        val key = loopKey(StudySessionPhase.ADVANCING, "payload")
        val first = StudyRouteResolver.admitRerender(StudyRouteLoopState(), StudyRouteRerenderKind.RECOVERY, key)
        assertEquals(StudyRouteRerenderAdmission.ALLOW, first.admission)
        val second = StudyRouteResolver.admitRerender(first.state, StudyRouteRerenderKind.RECOVERY, key)
        assertEquals(StudyRouteRerenderAdmission.SUPPRESS_REPEATED_RECOVERY, second.admission)
        assertEquals("a refused re-render leaves the state unchanged", first.state, second.state)

        val changedPayload = StudyRouteResolver.admitRerender(
            first.state,
            StudyRouteRerenderKind.RECOVERY,
            loopKey(StudySessionPhase.ADVANCING, "other"),
        )
        assertEquals(StudyRouteRerenderAdmission.ALLOW, changedPayload.admission)
    }

    @Test
    fun loopKeyIgnoresPresentationVersionButNotRouteContent() {
        val base = route(StudySessionPhase.LOADING, "a", target = 2, completed = 1)
        assertEquals(
            StudyRouteLoopKey.of(base, "p"),
            StudyRouteLoopKey.of(base.copy(version = StudyRouteVersion(99L)), "p"),
        )
        assertFalse(
            StudyRouteLoopKey.of(base, "p") ==
                StudyRouteLoopKey.of(base.copy(progress = base.progress.copy(completedCount = 2)), "p"),
        )
        assertFalse(StudyRouteLoopKey.of(base, "p") == StudyRouteLoopKey.of(base, null))
    }

    @Test
    fun trackerRetriesAreBoundedPerInput() {
        val key = loopKey(StudySessionPhase.LOADING, null)
        var state = StudyRouteLoopState()
        repeat(StudyRouteResolver.MAX_TRACKER_RETRIES_PER_INPUT) {
            val resolution = StudyRouteResolver.admitRerender(state, StudyRouteRerenderKind.TRACKER_RETRY, key)
            assertEquals(StudyRouteRerenderAdmission.ALLOW, resolution.admission)
            state = resolution.state
        }
        assertEquals(
            StudyRouteRerenderAdmission.SUPPRESS_RETRY_BUDGET,
            StudyRouteResolver.admitRerender(state, StudyRouteRerenderKind.TRACKER_RETRY, key).admission,
        )
    }

    @Test
    fun internalChainIsBoundedEvenWhenEveryInputDiffers() {
        var state = StudyRouteLoopState()
        var allowed = 0
        for (i in 0 until 100) {
            val kind = StudyRouteRerenderKind.entries[i % StudyRouteRerenderKind.entries.size]
            val resolution = StudyRouteResolver.admitRerender(state, kind, loopKey(StudySessionPhase.LOADING, "p$i"))
            state = resolution.state
            if (resolution.admission.allowed) allowed++
        }
        assertEquals(StudyRouteResolver.MAX_INTERNAL_RERENDER_CHAIN, allowed)
    }

    @Test
    fun freshExternalEntryStartsANewChain() {
        val key = loopKey(StudySessionPhase.ADVANCING, "payload")
        val exhausted = StudyRouteResolver.admitRerender(
            StudyRouteLoopState(),
            StudyRouteRerenderKind.RECOVERY,
            key,
        ).state
        assertFalse(
            StudyRouteResolver.admitRerender(exhausted, StudyRouteRerenderKind.RECOVERY, key).admission.allowed,
        )
        assertTrue(
            StudyRouteResolver.admitRerender(StudyRouteLoopState(), StudyRouteRerenderKind.RECOVERY, key)
                .admission.allowed,
        )
    }

    /** Every request sequence over a small input space is bounded and never repeats a recovery. */
    @Test
    fun exhaustiveRequestSequencesAreBounded() {
        val keys = listOf(
            loopKey(StudySessionPhase.ADVANCING, "p"),
            loopKey(StudySessionPhase.LOADING, "p"),
        )
        val requests = StudyRouteRerenderKind.entries.flatMap { kind -> keys.map { kind to it } }
        val length = 7
        var sequences = 0
        fun explore(state: StudyRouteLoopState, depth: Int, allowed: Int, recovered: Set<StudyRouteLoopKey>) {
            assertTrue(allowed <= StudyRouteResolver.MAX_INTERNAL_RERENDER_CHAIN)
            if (depth == length) {
                sequences++
                return
            }
            for ((kind, key) in requests) {
                val resolution = StudyRouteResolver.admitRerender(state, kind, key)
                val admitted = resolution.admission.allowed
                if (admitted && kind == StudyRouteRerenderKind.RECOVERY) {
                    assertFalse("recovery repeated for $key", key in recovered)
                }
                if (!admitted) assertEquals(state, resolution.state)
                explore(
                    resolution.state,
                    depth + 1,
                    allowed + if (admitted) 1 else 0,
                    if (admitted && kind == StudyRouteRerenderKind.RECOVERY) recovered + key else recovered,
                )
            }
        }
        explore(StudyRouteLoopState(), 0, 0, emptySet())
        assertEquals(Math.pow(requests.size.toDouble(), length.toDouble()).toInt(), sequences)
    }

    /**
     * Closed-loop simulation of the coordinator: every published render may (adversarially)
     * demand another render, the world may or may not change between renders, and the
     * only way to re-enter is through [StudyRouteResolver.admitRerender]. No seed may
     * produce more internal renders than the chain budget.
     */
    @Test
    fun randomizedEventSequencesNeverProduceAnUnboundedRenderLoop() {
        val random = Random(0x5EED)
        val phases = StudySessionPhase.entries
        val branches = StudyRouteComputationBranch.entries
        repeat(20_000) { iteration ->
            var state = StudyRouteLoopState()
            var route = route(StudySessionPhase.ADVANCING, "t", target = 2, completed = 1, activeTask = true)
            var payload: String? = "marker"
            var renders = 0
            var halted = false
            while (!halted) {
                renders++
                assertTrue(
                    "seed iteration $iteration rendered $renders times",
                    renders <= StudyRouteResolver.MAX_INTERNAL_RERENDER_CHAIN + 1,
                )
                // Adversarial world: maybe mutate the input the next render observes.
                when (random.nextInt(4)) {
                    0 -> route = route.copy(version = route.version.next())
                    1 -> route = route.copy(phase = phases[random.nextInt(phases.size)])
                    2 -> payload = if (random.nextBoolean()) null else "marker-${random.nextInt(3)}"
                    else -> Unit
                }
                val kind = if (random.nextBoolean()) {
                    val publication = StudyRouteResolver.resolvePublication(
                        StudyRoutePublicationInput(
                            published = random.nextInt(5) == 0,
                            routeStillCurrent = random.nextBoolean(),
                            terminalBranch = random.nextBoolean(),
                            currentCanComplete = random.nextBoolean(),
                            trackerStateEquivalent = random.nextBoolean(),
                        ),
                    )
                    when (publication) {
                        StudyRoutePublicationDecision.RETRY -> StudyRouteRerenderKind.TRACKER_RETRY
                        StudyRoutePublicationDecision.DROP_STALE -> null
                        // An accepted render may still fail its effect CAS and recover.
                        else -> if (random.nextInt(3) == 0) null else StudyRouteRerenderKind.RECOVERY
                    }
                } else {
                    val marker = StudyContinuedMarkerFacts(StudyAnswerFeedbackPhase.CONTINUED, "t")
                    val decision = StudyRouteResolver.resolveTerminal(
                        StudyTerminalInput(branches[random.nextInt(branches.size)], marker, route, "t"),
                    )
                    // Worst case: every clear attempt loses its CAS and recovers.
                    if (decision == StudyTerminalDecision.RENDER_TERMINAL) {
                        null
                    } else {
                        StudyRouteRerenderKind.RECOVERY
                    }
                } ?: StudyRouteRerenderKind.entries[random.nextInt(StudyRouteRerenderKind.entries.size)]
                val resolution = StudyRouteResolver.admitRerender(state, kind, StudyRouteLoopKey.of(route, payload))
                state = resolution.state
                halted = !resolution.admission.allowed
            }
        }
    }

    @Test
    fun loopGuardLogCarriesNoCardContent() {
        val key = StudyRouteLoopKey.of(
            route(StudySessionPhase.LOADING, "secret-token", target = 1, completed = 0),
            "secret payload 漢",
        )
        val log = studyRouteLoopGuardLog(
            StudyRouteRerenderKind.RECOVERY,
            StudyRouteRerenderAdmission.SUPPRESS_REPEATED_RECOVERY,
            StudyRouteLoopState(chainLength = 2),
            key,
        )
        assertEquals(
            "study-route loop-guard kind=recovery admission=suppress-repeated-recovery chain=2 " +
                "consecutive_retries=0 phase=LOADING generation=0 can_complete=false recovery_payload=true",
            log,
        )
    }

    // ---------------------------------------------------------------------------------
    // Fixtures and legacy oracles (verbatim logic from before the extraction)
    // ---------------------------------------------------------------------------------

    private fun route(
        phase: StudySessionPhase,
        token: String?,
        target: Int,
        completed: Int,
        activeTask: Boolean = false,
        feedbackPhase: StudyAnswerFeedbackPhase? = null,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        version = StudyRouteVersion(7L),
        sessionToken = token,
        phase = phase,
        feedback = feedbackPhase?.let { StudyAnswerFeedbackSnapshot(token ?: "x", it, null, "") },
        progress = StudySessionProgressUiState(
            targetCount = target,
            completedCount = completed,
            activeTask = activeTask,
        ),
    )

    private fun loopKey(phase: StudySessionPhase, payload: String?): StudyRouteLoopKey =
        StudyRouteLoopKey.of(route(phase, "a", target = 2, completed = 1), payload)

    private fun allPublicationInputs(): List<StudyRoutePublicationInput> {
        val bools = listOf(false, true)
        return bools.flatMap { published ->
            bools.flatMap { current ->
                bools.flatMap { terminal ->
                    bools.flatMap { canComplete ->
                        bools.map { equivalent ->
                            StudyRoutePublicationInput(published, current, terminal, canComplete, equivalent)
                        }
                    }
                }
            }
        }
    }

    private fun legacyPublication(input: StudyRoutePublicationInput): StudyRoutePublicationDecision {
        val terminalRaceAccepted = input.terminalBranch &&
            input.routeStillCurrent && input.currentCanComplete && input.trackerStateEquivalent
        return when {
            input.published -> StudyRoutePublicationDecision.ACCEPT
            terminalRaceAccepted -> StudyRoutePublicationDecision.ACCEPT_TERMINAL_RACE
            input.routeStillCurrent -> StudyRoutePublicationDecision.RETRY
            else -> StudyRoutePublicationDecision.DROP_STALE
        }
    }

    private fun allTerminalInputs(): List<StudyTerminalInput> {
        val tokens = listOf(null, "a", "b")
        val markers = listOf<StudyContinuedMarkerFacts?>(null) +
            StudyAnswerFeedbackPhase.entries.flatMap { phase ->
                listOf("a", "b").map { StudyContinuedMarkerFacts(phase, it) }
            }
        val progresses = listOf(
            Triple(0, 0, false),
            Triple(1, 1, false),
            Triple(2, 1, false),
            Triple(1, 1, true),
        )
        val feedbackPhases = listOf(null, StudyAnswerFeedbackPhase.CONTINUED, StudyAnswerFeedbackPhase.APPLIED)
        val inputs = ArrayList<StudyTerminalInput>()
        for (branch in StudyRouteComputationBranch.entries) {
            for (marker in markers) {
                for (phase in StudySessionPhase.entries) {
                    for (evidenceToken in tokens) {
                        for (mounted in tokens) {
                            for ((target, completed, active) in progresses) {
                                for (feedbackPhase in feedbackPhases) {
                                    inputs += StudyTerminalInput(
                                        branch,
                                        marker,
                                        route(phase, evidenceToken, target, completed, active, feedbackPhase),
                                        mounted,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return inputs
    }

    private fun legacyTerminal(input: StudyTerminalInput): StudyTerminalDecision {
        val marker = input.marker ?: return StudyTerminalDecision.RENDER_TERMINAL
        val evidence = input.evidence
        val activeToken = input.mountedSessionToken
        fun legacyTerminalClear(): Boolean {
            if (!evidence.canComplete) return false
            if (evidence.sessionToken == null) {
                if (activeToken != null) return false
            } else if (
                evidence.progress.targetCount <= 0 ||
                activeToken.isNullOrEmpty() ||
                evidence.sessionToken != activeToken ||
                marker.feedbackSessionToken != activeToken
            ) {
                return false
            }
            return true
        }
        fun legacyAbsenceClear(): Boolean {
            activeToken ?: return false
            return !(
                evidence.isComplete ||
                    (evidence.phase != StudySessionPhase.ADVANCING && evidence.phase != StudySessionPhase.LOADING) ||
                    evidence.sessionToken != activeToken ||
                    marker.feedbackPhase != StudyAnswerFeedbackPhase.CONTINUED ||
                    marker.feedbackSessionToken != activeToken
                )
        }
        val clear = when (input.branch) {
            StudyRouteComputationBranch.EMPTY_QUEUE,
            StudyRouteComputationBranch.NO_SESSION,
            StudyRouteComputationBranch.TARGET_UNAVAILABLE,
            -> if (evidence.canComplete) {
                if (legacyTerminalClear()) StudyTerminalDecision.CLEAR_MARKER_KEEP_SESSION else null
            } else {
                if (legacyAbsenceClear()) StudyTerminalDecision.CLEAR_MARKER_FOR_SESSION_ABSENCE else null
            }
            else -> if (legacyTerminalClear()) StudyTerminalDecision.CLEAR_MARKER_KEEP_SESSION else null
        }
        return clear ?: StudyTerminalDecision.RECOVER
    }
}
