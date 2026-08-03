package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.Loadable
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopShellHostTest {
    @Test
    fun anOrdinaryLaunchStartsAtHomeAndCarriesTheHostCapabilities() {
        val host = hostWith(
            capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE),
        )

        assertEquals(KaniDestination.Home, host.shell.current)
        assertTrue(host.shell.capabilities.contains(PlatformCapability.BACKUP_RESTORE))
        // Absent, and visibly so: the shell gates on this rather than offering a
        // "remember my key" toggle the session-only store cannot honor.
        assertTrue(PlatformCapability.SECRET_PERSISTENCE in host.shell.capabilities.missing)
    }

    @Test
    fun aLaunchRequestReplacesTheStackRatherThanPushingOntoIt() {
        val host = hostWith(
            launch = KaniLaunchRequest(
                destination = KaniDestination.Stats,
                suppressesStudyResume = false,
            ),
        )

        assertEquals(KaniDestination.Stats, host.shell.current)
        // Back still works, through `current.parent`, because the user tapped a
        // widget and never walked Home -> Stats.
        assertNull(host.dispatch(KaniAction.Navigation.Back))
        assertEquals(KaniDestination.Home, host.shell.current)
    }

    @Test
    fun enteringAnIdleRouteAsksForALoadAndEnteringAgainDoesNot() {
        val host = hostWith()

        val first = host.dispatch(KaniAction.Lifecycle.Entered)
        assertNotNull(first)
        assertEquals(KaniDestination.Home, first?.destination)
        assertTrue(host.route(KaniDestination.Home).isInitialLoad)

        runBlocking { host.perform(first!!) }

        // Returning to a loaded screen must not reload: on a large collection that
        // is a visible stall for content that is already correct.
        assertNull(host.dispatch(KaniAction.Lifecycle.Entered))
    }

    @Test
    fun aRefreshReloadsEvenWhenTheRouteIsAlreadyLoaded() {
        val host = hostWith()
        runBlocking { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        val pending = host.dispatch(KaniAction.Lifecycle.Refresh)

        assertNotNull(pending)
        // Refreshing keeps the previous content on screen rather than blanking it.
        assertTrue(host.route(KaniDestination.Home).content is Loadable.Refreshing)
    }

    @Test
    fun eachRouteKeepsItsOwnLoadStateAndTwoBrowseQueriesShareOne() {
        val host = hostWith()
        runBlocking { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        host.dispatch(KaniAction.Navigation.Open(KaniDestination.Browse(query = "水")))
        val browse = host.dispatch(KaniAction.Lifecycle.Entered)
        assertNotNull(browse)
        runBlocking { host.perform(browse!!) }

        // A different query is the same Browse screen with different content, which
        // is what the user sees; keying by destination would reload from scratch.
        host.dispatch(KaniAction.Navigation.Open(KaniDestination.Browse(query = "火")))
        assertNull(host.dispatch(KaniAction.Lifecycle.Entered))

        // And Home is untouched by any of it.
        assertTrue(host.route(KaniDestination.Home).content is Loadable.Loaded)
    }

    @Test
    fun leavingARouteClearsItsEffectsRatherThanTheOneBeingEntered() {
        val host = hostWith()
        runBlocking { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        // Navigation is applied to the route being left. If it were applied to the
        // arriving route, its `Entered` load would be cleared before it ran.
        host.dispatch(KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.ROOT)))
        val arriving = host.dispatch(KaniAction.Lifecycle.Entered)

        assertNotNull(arriving)
        assertEquals(KaniDestination.Settings(SettingsSection.ROOT), arriving?.destination)
    }

    @Test
    fun aLoadThatThrowsBecomesARetryableScreenRatherThanTakingTheWindowDown() {
        val host = DesktopShellHost(
            loadRoute = { throw IllegalStateException("database is on fire") },
        )

        runBlocking { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        val failure = host.route(KaniDestination.Home).failure
        assertEquals(PresentationFailure.Kind.UNKNOWN, failure?.kind)
        assertTrue(failure?.isRetryable == true)
        // The throwable's own text is diagnostic-only; a SQL error is not copy.
        assertTrue(failure?.diagnostic.orEmpty().contains("database is on fire"))
        assertEquals(
            "Kani could not load this screen.",
            (failure?.message as? dev.bee.kanjianki.presentation.UiText.Literal)?.text,
        )
    }

    @Test
    fun aProviderFailureKeepsItsOwnKindRatherThanBecomingUnknown() {
        val host = DesktopShellHost(
            loadRoute = {
                throw CollectionFailure(
                    kind = CollectionFailureKind.INVALID_CONFIGURATION,
                    message = "no profile is open",
                )
            },
        )

        runBlocking { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        // The point of carrying the kind: `UNKNOWN` is retryable, and offering "try
        // again" for a profile that is not open sends the user in a circle.
        val failure = host.route(KaniDestination.Home).failure
        assertEquals(PresentationFailure.Kind.CONFIGURATION, failure?.kind)
        assertEquals(false, failure?.isRetryable)
        // Still diagnostic-only. The engine's own text never becomes copy.
        assertTrue(failure?.diagnostic.orEmpty().contains("no profile is open"))
    }

    @Test
    fun aCancelledLoadStaysRetryableAndAnOrdinaryThrowIsStillUnknown() {
        val cancelled = DesktopShellHost(loadRoute = { throw CollectionFailure.cancelled() })
        runBlocking { cancelled.perform(cancelled.dispatch(KaniAction.Lifecycle.Entered)!!) }
        assertEquals(
            PresentationFailure.Kind.CANCELLED,
            cancelled.route(KaniDestination.Home).failure?.kind,
        )

        // And a plain exception is not misclassified as a provider problem.
        val plain = DesktopShellHost(loadRoute = { throw IllegalStateException("boom") })
        runBlocking { plain.perform(plain.dispatch(KaniAction.Lifecycle.Entered)!!) }
        assertEquals(
            PresentationFailure.Kind.UNKNOWN,
            plain.route(KaniDestination.Home).failure?.kind,
        )
    }

    @Test
    fun retryAfterAFailureAsksForTheLoadAgain() {
        var attempts = 0
        val host = DesktopShellHost(
            loadRoute = {
                attempts++
                if (attempts == 1) throw IllegalStateException("transient") else success()
            },
        )
        runBlocking { host.perform(host.dispatch(KaniAction.Lifecycle.Entered)!!) }

        val retry = host.dispatch(KaniAction.Retry)
        assertNotNull(retry)
        runBlocking { host.perform(retry!!) }

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

    private fun hostWith(
        launch: KaniLaunchRequest? = null,
        capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    ) = DesktopShellHost(
        launch = launch,
        capabilities = capabilities,
        loadRoute = { success() },
    )

    private fun success() = ContentResult.Success(
        DesktopRouteContent(
            provider = DesktopProviderStatus(
                message = "ready",
                availability = CollectionAvailability.READY,
                capabilities = emptySet(),
            ),
            studyItemCount = 3,
            dueCount = 1,
            themeChoice = KaniThemeChoice.GIRLYPOP,
        ),
    )
}
