package dev.bee.kanjianki.presentation

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs a suspend block to completion without a coroutines runtime.
 *
 * [KaniShellHost.perform] suspends because a real loader does I/O; the state
 * transitions under test do not depend on that, and a test dispatcher would make
 * `:presentation-api` depend on more than the standard library for no gain. Same
 * approach as `RoutePortTest`.
 */
private fun runSync(block: suspend () -> Unit) {
    var outcome: Result<Unit>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    requireNotNull(outcome) { "load did not complete synchronously" }.getOrThrow()
}

class KaniShellHostTest {
    @Test
    fun anOrdinaryLaunchStartsAtHomeAndCarriesTheCapabilities() {
        val host = hostWith(capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE))

        assertEquals(KaniDestination.Home, host.shell.current)
        assertTrue(host.shell.capabilities.contains(PlatformCapability.BACKUP_RESTORE))
    }

    @Test
    fun aLaunchRequestReplacesTheStackAndBackFallsToTheParent() {
        val host = hostWith(
            launch = KaniLaunchRequest(destination = KaniDestination.Stats, suppressesStudyResume = false),
        )

        assertEquals(KaniDestination.Stats, host.shell.current)
        assertNull(host.dispatch(KaniAction.Navigation.Back))
        assertEquals(KaniDestination.Home, host.shell.current)
    }

    @Test
    fun enteringAnIdleRouteLoadsAndEnteringAgainDoesNot() {
        val host = hostWith()

        val first = host.dispatch(KaniAction.Lifecycle.Entered)
        assertEquals(KaniDestination.Home, first?.destination)
        assertTrue(host.route(KaniDestination.Home).isInitialLoad)

        runSync { host.perform(first!!) }
        assertNull(host.dispatch(KaniAction.Lifecycle.Entered))
        assertTrue(host.route(KaniDestination.Home).content is Loadable.Loaded)
    }

    @Test
    fun aRefreshReloadsAndKeepsTheContentOnScreen() {
        val host = hostWith()
        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        val pending = host.dispatch(KaniAction.Lifecycle.Refresh)

        assertEquals(KaniDestination.Home, pending?.destination)
        assertTrue(host.route(KaniDestination.Home).content is Loadable.Refreshing)
    }

    @Test
    fun navigationIsAppliedToTheRouteBeingLeftNotTheOneArriving() {
        val host = hostWith()
        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        host.dispatch(KaniAction.Navigation.Open(KaniDestination.Stats))
        val arriving = host.dispatch(KaniAction.Lifecycle.Entered)

        assertEquals(KaniDestination.Stats, arriving?.destination)
    }

    @Test
    fun aLoadThatThrowsBecomesTheDefaultUnknownRetryableFailure() {
        val host = KaniShellHost<String>(loadRoute = { throw IllegalStateException("boom") })

        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        val failure = assertNotNull(host.route(KaniDestination.Home).failure)
        // The default classifier is UNKNOWN, which is retryable.
        assertEquals(PresentationFailure.Kind.UNKNOWN, failure.kind)
        assertTrue(failure.isRetryable)
        assertTrue(failure.diagnostic.orEmpty().contains("boom"))
        assertEquals("Kani could not load this screen.", (failure.message as? UiText.Literal)?.text)
    }

    @Test
    fun anInjectedClassifierKeepsADomainFailuresOwnKind() {
        val host = KaniShellHost<String>(
            classifyFailure = { PresentationFailure.Kind.CONFIGURATION },
            loadRoute = { throw IllegalStateException("no profile is open") },
        )

        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        val failure = host.route(KaniDestination.Home).failure
        assertEquals(PresentationFailure.Kind.CONFIGURATION, failure?.kind)
        assertEquals(false, failure?.isRetryable)
    }

    @Test
    fun retryAfterAFailureAsksForTheLoadAgain() {
        var attempts = 0
        val host = KaniShellHost<String>(
            loadRoute = {
                attempts++
                if (attempts == 1) throw IllegalStateException("transient") else ContentResult.Success("ok")
            },
        )
        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        runSync { host.perform(host.dispatch(KaniAction.Retry)!!) }

        assertEquals(2, attempts)
        assertTrue(host.route(KaniDestination.Home).content is Loadable.Loaded)
        assertNull(host.route(KaniDestination.Home).failure)
    }

    @Test
    fun aFailureRecordedDirectlyIsTheSameStateAsOneFromALoad() {
        val host = hostWith()
        val failure = PresentationFailure(PresentationFailure.Kind.PROVIDER_UNAVAILABLE)

        host.apply(KaniDestination.Home, ContentResult.Failure(failure))

        assertEquals(failure, host.route(KaniDestination.Home).failure)
    }

    @Test
    fun twoQueriesOfOneRouteShareLoadStateAndDistinctRoutesDoNot() {
        val host = hostWith()
        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        host.dispatch(KaniAction.Navigation.Open(KaniDestination.Browse(query = "水")))
        runSync { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }
        // A different query is the same Browse route; no reload.
        host.dispatch(KaniAction.Navigation.Open(KaniDestination.Browse(query = "火")))
        assertNull(host.dispatch(KaniAction.Lifecycle.Entered))

        assertTrue(host.route(KaniDestination.Home).content is Loadable.Loaded)
    }

    private fun hostWith(
        launch: KaniLaunchRequest? = null,
        capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    ) = KaniShellHost<String>(
        launch = launch,
        capabilities = capabilities,
        loadRoute = { ContentResult.Success("content") },
    )
}
