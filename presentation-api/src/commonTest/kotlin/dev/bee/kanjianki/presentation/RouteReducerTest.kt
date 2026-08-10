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
    fun aRouteLevelCopyQueuesOnTheRouteSoLeavingDropsIt() {
        // On the route rather than the shell because the route is what the user is
        // looking at: `Lifecycle.Exited` clears this queue, so a copy confirmation for
        // a screen they navigated away from is dropped instead of surfacing over the
        // next one.
        val loaded = home.withContent("dashboard")
        val (state, intent) = RouteReducer.reduce(
            loaded,
            KaniAction.RequestCopy(
                text = "tag:kani_repaired is:suspended",
                confirmation = UiText.Key("clipboard.copied"),
            ),
        )

        assertNull(intent, "copying is not a reload")
        assertEquals(
            KaniEffect.CopyToClipboard(
                text = "tag:kani_repaired is:suspended",
                confirmation = UiText.Key("clipboard.copied"),
            ),
            requireNotNull(state.effects.head).effect,
        )
        assertEquals("dashboard", state.content.valueOrNull, "copying must not clear the screen")

        val (exited, _) = RouteReducer.reduce(state, KaniAction.Lifecycle.Exited)
        assertTrue(exited.effects.isEmpty)
    }

    @Test
    fun tickingAStudyCheckboxReloadsTheListWithoutBlankingIt() {
        // The row that was just unmarked has left the study queue, so the summary
        // above the list is now wrong and the route must reload. Keeping the old rows
        // visible while it does is the difference between a checkbox and a flicker.
        // Saving a mnemonic reloads the same way: the detail's saved note and its
        // "stuck" helper both come from the store, so the screen reflects the write
        // by re-reading it, not by trusting the field it was typed into.
        val loaded = home.withContent("results")

        for (
            action in listOf<KaniAction>(
                KaniAction.Browse.SetStudied(kanji = "脱", studied = false),
                KaniAction.Browse.SetAllStudied(studied = true),
                KaniAction.SaveMnemonic(kanji = "脱", note = "snake escaping"),
            )
        ) {
            val (state, intent) = RouteReducer.reduce(loaded, action)

            assertEquals(RouteIntent.Load, intent, action.toString())
            assertEquals(Loadable.Refreshing("results"), state.content, action.toString())
            assertFalse(state.isInitialLoad, action.toString())
        }
    }

    @Test
    fun gradingContinuingOrUndoingReloadsTheCardWithoutBlankingIt() {
        // Each has changed the session by the time the route hears it, and the next
        // card comes from re-reading the snapshot. Keeping the answered card visible
        // while the reload runs is the difference between grading and a flicker.
        val loaded = home.withContent("card")

        for (
            action in listOf<KaniAction>(
                KaniAction.Study.Grade(rating = "good"),
                KaniAction.Study.Continue,
                KaniAction.Study.Undo,
                KaniAction.Game.Start(modeId = "meaning_pop"),
                KaniAction.Game.Answer(answer = "take off"),
                KaniAction.Game.Continue,
                KaniAction.MissingKanji.ScanIntent,
                KaniAction.MissingKanji.CancelScan,
                KaniAction.MissingKanji.AddToKani(literals = setOf("脱")),
                KaniAction.MissingKanji.CreateAnkiNotes(literals = setOf("脱"), deckName = "Kani"),
                KaniAction.MissingKanji.ExportCsv(literals = setOf("脱")),
                KaniAction.MissingKanji.Remove(literal = "脱"),
                KaniAction.MissingKanji.DismissResult,
                KaniAction.Settings.SetToggle(key = "import_weak_cards", enabled = false),
                KaniAction.Settings.SetChoice(key = "new_card_sort", optionId = "frequency"),
                KaniAction.Settings.SetNumber(key = "promotion_interval_days", value = 28),
                KaniAction.Settings.Command(id = "reset_ladder"),
            )
        ) {
            val (state, intent) = RouteReducer.reduce(loaded, action)

            assertEquals(RouteIntent.Load, intent, action.toString())
            assertEquals(Loadable.Refreshing("card"), state.content, action.toString())
        }
    }

    @Test
    fun revealingACardsAnswerDoesNotReloadTheRoute() {
        // Reveal turns over an answer the surface already holds, so nothing the host
        // must reload has changed — reloading would fetch the same card and flicker.
        val loaded = home.withContent("card")

        val (state, intent) = RouteReducer.reduce(loaded, KaniAction.Study.Reveal)

        assertNull(intent)
        assertEquals(Loadable.Loaded("card"), state.content)
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
