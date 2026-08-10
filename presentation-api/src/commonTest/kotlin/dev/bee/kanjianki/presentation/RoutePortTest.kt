package dev.bee.kanjianki.presentation

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Runs a port call to completion without a coroutines runtime.
 *
 * The ports suspend because a real adapter does I/O; the state transitions under
 * test do not depend on that, and pulling in a test dispatcher here would make
 * `:presentation-api` depend on more than the standard library for no gain.
 */
private fun <T> runSync(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return requireNotNull(outcome) { "port did not complete synchronously" }.getOrThrow()
}

private fun <T> blocking(port: RouteContentPort<T>): ContentResult<T> = runSync(port::load)

private fun <C> blockingSubmit(port: RouteCommandPort<C>, command: C): ActionOutcome =
    runSync { port.submit(command) }

class RoutePortTest {
    @Test
    fun aContentPortIsTheOnlyWayContentEntersPortableState() {
        // The port is a fun interface so a test can be the :application layer. If
        // this needed a real use case, the module boundary would already be broken.
        val port = RouteContentPort { ContentResult.Success("dashboard") }

        val state = RouteState<String>(KaniDestination.Home).applying(blocking(port))

        assertEquals(Loadable.Loaded("dashboard"), state.content)
    }

    @Test
    fun aPortFailureBecomesRenderableStateRatherThanAThrownException() {
        // Common code cannot catch a platform exception type, so the port's contract
        // is a value. A port that threw would crash the shared reducer on one host
        // and not the other.
        val failure = PresentationFailure(
            kind = PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
            message = UiText.Key("provider.unreachable"),
            diagnostic = "ECONNREFUSED 127.0.0.1:8765",
        )
        val port = RouteContentPort<String> { ContentResult.Failure(failure) }

        val state = RouteState<String>(KaniDestination.Home).applying(blocking(port))

        assertEquals(Loadable.Failed(failure), state.content)
        assertEquals("ECONNREFUSED 127.0.0.1:8765", failure.diagnostic)
    }

    @Test
    fun anAppliedCommandIsDistinctFromARejectedOneWhichIsDistinctFromAFailure() {
        // A lost revision-CAS commit is the system working correctly. Rendering it
        // as an error would produce error copy for a non-error, and rendering it as
        // success would advance UI state off a commit that did not land.
        val outcomes = listOf(
            ActionOutcome.Applied,
            ActionOutcome.Rejected(UiText.Key("review.stale")),
            ActionOutcome.Failed(PresentationFailure(PresentationFailure.Kind.CONFLICT)),
        )

        assertEquals(3, outcomes.distinct().size)
        assertSame(ActionOutcome.Applied, ActionOutcome.Applied)
    }

    @Test
    fun aCommandPortReturnsWhatHappenedWithoutTheUiReinterpretingIt() {
        val submitted = mutableListOf<String>()
        val port = RouteCommandPort<String> { command ->
            submitted += command
            when (command) {
                "commit" -> ActionOutcome.Applied
                "commit-stale" -> ActionOutcome.Rejected(UiText.Key("review.stale"))
                else -> ActionOutcome.Failed(
                    PresentationFailure(PresentationFailure.Kind.UNKNOWN),
                )
            }
        }

        assertEquals(ActionOutcome.Applied, blockingSubmit(port, "commit"))
        assertEquals(
            ActionOutcome.Rejected(UiText.Key("review.stale")),
            blockingSubmit(port, "commit-stale"),
        )
        assertTrue(blockingSubmit(port, "?") is ActionOutcome.Failed)
        assertEquals(listOf("commit", "commit-stale", "?"), submitted)
    }

    @Test
    fun aRejectionCarriesCopyTheUserCanActOnRatherThanASilentNoOp() {
        val rejected = ActionOutcome.Rejected(UiText.Key("review.stale"))
        val state = RouteState<String>(KaniDestination.Study)
            .withContent("queue")
            .enqueue(KaniEffect.ShowMessage(message = rejected.reason, isError = false))

        assertEquals(
            KaniEffect.ShowMessage(message = UiText.Key("review.stale")),
            requireNotNull(state.effects.head).effect,
        )
        assertEquals("queue", state.content.valueOrNull, "a rejection must not clear content")
    }

    @Test
    fun aDispatcherIsAPlainFunctionSoATestCanBeTheHost() {
        val seen = mutableListOf<KaniAction>()
        val dispatcher = ActionDispatcher { seen += it }

        dispatcher.dispatch(KaniAction.Retry)
        dispatcher.dispatch(KaniAction.Navigation.SelectTab(KaniTab.STUDY))

        assertEquals(
            listOf(KaniAction.Retry, KaniAction.Navigation.SelectTab(KaniTab.STUDY)),
            seen,
        )
    }

    @Test
    fun anIdleRouteHasNoValueAndIsNotBusy() {
        assertNull(Loadable.Idle.valueOrNull)
        assertNull(Loadable.Loading.valueOrNull)
        assertNull(
            Loadable.Failed(PresentationFailure(PresentationFailure.Kind.UNKNOWN)).valueOrNull,
        )
        assertEquals(false, Loadable.Idle.isBusy)
        assertEquals(true, Loadable.Loading.isBusy)
        assertEquals(true, Loadable.Refreshing("x").isBusy)
        assertEquals(false, Loadable.Loaded("x").isBusy)
    }

    @Test
    fun everyFailureKindDeclaresWhetherARetryIsHonest() {
        // The user-visible consequence: a retry button on a failure a retry cannot
        // fix strands them in a loop.
        val retryable = PresentationFailure.Kind.entries.filter(PresentationFailure.Kind::retryable)
        val notRetryable = PresentationFailure.Kind.entries - retryable.toSet()

        assertEquals(
            listOf(
                PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
                PresentationFailure.Kind.TRANSIENT,
                PresentationFailure.Kind.CANCELLED,
                PresentationFailure.Kind.CONFLICT,
                PresentationFailure.Kind.UNKNOWN,
            ),
            retryable,
        )
        assertEquals(
            listOf(
                PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED,
                PresentationFailure.Kind.CONFIGURATION,
                PresentationFailure.Kind.CAPABILITY_MISSING,
            ),
            notRetryable,
        )
        for (kind in PresentationFailure.Kind.entries) {
            assertEquals(kind.retryable, PresentationFailure(kind).isRetryable, kind.name)
        }
    }
}
