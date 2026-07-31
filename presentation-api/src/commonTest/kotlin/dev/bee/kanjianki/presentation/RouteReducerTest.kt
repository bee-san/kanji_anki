package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RouteReducerTest {
    private val home = RouteState<String>(KaniDestination.Home)

    @Test
    fun anUnvisitedRouteStartsIdleWithNothingOnScreen() {
        assertSame(Loadable.Idle, home.content)
        assertNull(home.failure)
        assertFalse(home.isInitialLoad)
        assertFalse(home.isBusy)
    }

    @Test
    fun enteringAnIdleRouteRequestsALoadAndShowsTheFirstLoadState() {
        val (state, intent) = RouteReducer.reduce(home, KaniAction.Lifecycle.Entered)

        assertEquals(RouteIntent.Load, intent)
        assertSame(Loadable.Loading, state.content)
        assertTrue(state.isInitialLoad)
    }

    @Test
    fun enteringAnAlreadyLoadedRouteDoesNotReloadIt() {
        // Coming back from a subscreen would otherwise reload on every back press,
        // which on a large collection is a visible stall for content already correct.
        val loaded = home.withContent("dashboard")

        val (state, intent) = RouteReducer.reduce(loaded, KaniAction.Lifecycle.Entered)

        assertNull(intent)
        assertEquals(loaded, state)
    }

    @Test
    fun anExplicitRefreshKeepsTheOldContentVisibleWhileReloading() {
        // A refresh that blanks the screen loses information the user already had.
        val loaded = home.withContent("dashboard")

        val (state, intent) = RouteReducer.reduce(loaded, KaniAction.Lifecycle.Refresh)

        assertEquals(RouteIntent.Load, intent)
        assertEquals(Loadable.Refreshing("dashboard"), state.content)
        assertEquals("dashboard", state.content.valueOrNull)
        assertTrue(state.isBusy)
        assertFalse(state.isInitialLoad, "a refresh must not show the first-load skeleton")
    }

    @Test
    fun refreshingAnEmptyRouteFallsBackToTheFirstLoadState() {
        val (state, intent) = RouteReducer.reduce(home, KaniAction.Lifecycle.Refresh)

        assertEquals(RouteIntent.Load, intent)
        assertSame(Loadable.Loading, state.content)
    }

    @Test
    fun aFailedFirstLoadShowsTheFailureFullScreen() {
        val failure = PresentationFailure(
            PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
            UiText.Key("provider.unreachable"),
        )

        val state = home.loading().withFailure(failure)

        assertEquals(Loadable.Failed(failure), state.content)
        assertEquals(failure, state.failure)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun aFailedRefreshKeepsTheLastGoodContentAlongsideTheError() {
        val failure = PresentationFailure(PresentationFailure.Kind.TRANSIENT)

        val state = home.withContent("dashboard").loading().withFailure(failure)

        assertEquals(Loadable.Loaded("dashboard"), state.content)
        assertEquals(failure, state.failure)
    }

    @Test
    fun retryingReloadsAndClearsTheVisibleFailure() {
        val failed = home.withFailure(PresentationFailure(PresentationFailure.Kind.TRANSIENT))

        val (state, intent) = RouteReducer.reduce(failed, KaniAction.Retry)

        assertEquals(RouteIntent.Load, intent)
        assertNull(state.failure)
    }

    @Test
    fun dismissingAFailureDoesNotRetryIt() {
        val failure = PresentationFailure(PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED)
        val failed = home.withContent("dashboard").withFailure(failure)

        val (state, intent) = RouteReducer.reduce(failed, KaniAction.Consume.Failure)

        assertNull(intent)
        assertNull(state.failure)
        assertEquals("dashboard", state.content.valueOrNull)
        assertFalse(failure.isRetryable, "an auth failure must not offer a blind retry")
    }

    @Test
    fun recordingContentClearsAnEarlierFailure() {
        val state = home
            .withFailure(PresentationFailure(PresentationFailure.Kind.TRANSIENT))
            .withContent("dashboard")

        assertNull(state.failure)
        assertEquals(Loadable.Loaded("dashboard"), state.content)
    }

    @Test
    fun leavingARouteDropsUndeliveredEffectsRatherThanShowingThemLater() {
        // An undelivered effect describes a screen the user is no longer looking at.
        val state = home.enqueue(KaniEffect.ShowMessage(UiText.Key("sync.done")))

        val (exited, intent) = RouteReducer.reduce(state, KaniAction.Lifecycle.Exited)

        assertNull(intent)
        assertTrue(exited.effects.isEmpty)
    }

    @Test
    fun leavingDoesNotDiscardLoadedContent() {
        // Effects are transient; content is not. Clearing both would make every
        // back press a reload.
        val state = home.withContent("dashboard").enqueue(KaniEffect.OpenUrl("https://x.invalid"))

        val (exited, _) = RouteReducer.reduce(state, KaniAction.Lifecycle.Exited)

        assertEquals("dashboard", exited.content.valueOrNull)
    }

    @Test
    fun consumingAnEffectRemovesOnlyThatEffect() {
        val state = home
            .enqueue(KaniEffect.ShowMessage(UiText.Key("first")))
            .enqueue(KaniEffect.ShowMessage(UiText.Key("second")))
        val head = requireNotNull(state.effects.head)

        val (consumed, intent) = RouteReducer.reduce(state, KaniAction.Consume.Effect(head.id))

        assertNull(intent)
        assertEquals(1, consumed.effects.pending.size)
        assertEquals(
            KaniEffect.ShowMessage(UiText.Key("second")),
            requireNotNull(consumed.effects.head).effect,
        )
    }

    @Test
    fun navigationIsTheShellsBusinessAndLeavesRouteStateAlone() {
        val loaded = home.withContent("dashboard")
        val navigations = listOf(
            KaniAction.Navigation.Back,
            KaniAction.Navigation.SelectTab(KaniTab.STATS),
            KaniAction.Navigation.Open(KaniDestination.Games),
        )

        for (action in navigations) {
            val (state, intent) = RouteReducer.reduce(loaded, action)
            assertNull(intent, action.toString())
            assertEquals(loaded, state, action.toString())
        }
    }

    @Test
    fun aPortSuccessOrFailureFoldsIntoStateTheSameWayEveryTime() {
        val failure = PresentationFailure(PresentationFailure.Kind.CONFLICT)

        assertEquals(
            home.withContent("dashboard"),
            home.applying(ContentResult.Success("dashboard")),
        )
        assertEquals(
            home.withFailure(failure),
            home.applying(ContentResult.Failure(failure)),
        )
    }
}
