package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShellReducerTest {
    @Test
    fun aShellStartsAtHomeWithNoCapabilitiesAssumed() {
        val state = ShellState()

        assertSame(KaniDestination.Home, state.current)
        assertEquals(KaniTab.HOME, state.selectedTab)
        assertEquals(PlatformCapabilities.NONE, state.capabilities)
        assertFalse(state.canGoBack)
        assertTrue(state.effects.isEmpty)
    }

    @Test
    fun aShellStateWithoutACurrentDestinationCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> { ShellState(backStack = emptyList()) }
        assertFailsWith<IllegalArgumentException> { ShellState(studyBadgeCount = -1) }
    }

    @Test
    fun openingAScreenPushesItAndBackPopsIt() {
        val opened = ShellReducer.reduce(
            ShellState(),
            KaniAction.Navigation.Open(KaniDestination.Games),
        )

        assertEquals(listOf(KaniDestination.Home, KaniDestination.Games), opened.backStack)
        assertTrue(opened.canGoBack)
        assertEquals(
            ShellState(),
            ShellReducer.reduce(opened, KaniAction.Navigation.Back),
        )
    }

    @Test
    fun revisitingAScreenAlreadyOnTheStackUnwindsInsteadOfPushingADuplicate() {
        // Home -> Detail -> Browse -> Detail would otherwise grow without bound and
        // make back walk a loop the user never took.
        val detail = KaniDestination.Detail(kanji = "橋", fromBrowse = true, query = "bridge")
        val browse = KaniDestination.Browse(query = "bridge")
        var state = ShellState()
        for (destination in listOf(detail, browse, detail)) {
            state = ShellReducer.reduce(state, KaniAction.Navigation.Open(destination))
        }

        assertEquals(listOf(KaniDestination.Home, detail), state.backStack)
    }

    @Test
    fun openingTheScreenAlreadyShowingChangesNothing() {
        val state = ShellReducer.reduce(
            ShellState(),
            KaniAction.Navigation.Open(KaniDestination.Home),
        )

        assertEquals(ShellState(), state)
    }

    @Test
    fun selectingATabResetsToThatTabsRootAndDropsTheRestOfTheStack() {
        var state = ShellState()
        state = ShellReducer.reduce(
            state,
            KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "端")),
        )

        state = ShellReducer.reduce(state, KaniAction.Navigation.SelectTab(KaniTab.STUDY))

        assertEquals(listOf(KaniDestination.Study), state.backStack)
        assertEquals(KaniTab.STUDY, state.selectedTab)
        assertFalse(
            state.backStack.size > 1,
            "a tab switch must not leave the previous tab's stack behind",
        )
    }

    @Test
    fun reselectingTheCurrentTabFromANestedScreenReturnsToItsRoot() {
        var state = ShellState()
        state = ShellReducer.reduce(
            state,
            KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.LICENSES)),
        )

        state = ShellReducer.reduce(state, KaniAction.Navigation.SelectTab(KaniTab.SETTINGS))

        assertEquals(listOf(KaniDestination.Settings()), state.backStack)
    }

    @Test
    fun reselectingATabAlreadyAtItsRootIsANoOp() {
        val state = ShellReducer.reduce(
            ShellState(),
            KaniAction.Navigation.SelectTab(KaniTab.HOME),
        )

        assertEquals(ShellState(), state)
    }

    @Test
    fun theHighlightedTabAlwaysMatchesTheVisibleScreen() {
        // Storing the selected tab separately is what lets a nav bar highlight
        // Settings while a Home screen is showing; deriving it cannot drift.
        for (section in SettingsSection.entries) {
            val state = ShellReducer.reduce(
                ShellState(),
                KaniAction.Navigation.Open(KaniDestination.Settings(section)),
            )
            assertEquals(KaniTab.SETTINGS, state.selectedTab, section.name)
        }
        val browse = ShellReducer.reduce(
            ShellState(),
            KaniAction.Navigation.Open(KaniDestination.Browse(query = "x")),
        )
        assertEquals(KaniTab.HOME, browse.selectedTab)
    }

    @Test
    fun backFromARestoredNestedScreenFallsBackToItsParent() {
        // A deep link or a restored session arrives with no stack behind it. Without
        // the parent fallback, back would be dead.
        val restored = ShellState(
            backStack = listOf(KaniDestination.Settings(SettingsSection.UPDATE)),
        )

        val state = ShellReducer.reduce(restored, KaniAction.Navigation.Back)

        assertTrue(restored.canGoBack)
        assertEquals(
            listOf(KaniDestination.Settings(SettingsSection.AUTOMATION)),
            state.backStack,
        )
    }

    @Test
    fun backAtHomeIsUnchangedSoTheHostDecidesWhatLeavingMeans() {
        // Closing a window and finishing an Activity are different things, and the
        // reducer must not invent a screen to show instead.
        val state = ShellReducer.reduce(ShellState(), KaniAction.Navigation.Back)

        assertEquals(ShellState(), state)
    }

    @Test
    fun backWalksAWholeRestoredSettingsChainToHomeWithoutLooping() {
        var state = ShellState(
            backStack = listOf(KaniDestination.Settings(SettingsSection.HOW_IT_WORKS)),
        )
        val visited = mutableListOf(state.current)
        repeat(SettingsSection.entries.size + 1) {
            state = ShellReducer.reduce(state, KaniAction.Navigation.Back)
            visited += state.current
        }

        assertSame(KaniDestination.Home, state.current)
        assertEquals(
            listOf(
                KaniDestination.Settings(SettingsSection.HOW_IT_WORKS),
                KaniDestination.Settings(SettingsSection.DISPLAY_DATA),
                KaniDestination.Settings(SettingsSection.ROOT),
                KaniDestination.Home,
            ),
            visited.distinct(),
        )
    }

    @Test
    fun nonNavigationActionsLeaveTheShellAlone() {
        // The shell owns navigation and effects. Rating a card, retrying a load, and
        // entering a route are the route's business, and a shell that reacted to
        // them would be a second place deriving product state.
        val state = ShellState(studyBadgeCount = 4)
        val untouched = listOf(
            KaniAction.Retry,
            KaniAction.Consume.Failure,
            KaniAction.Lifecycle.Entered,
            KaniAction.Lifecycle.Exited,
            KaniAction.Lifecycle.Refresh,
        )

        for (action in untouched) {
            assertEquals(state, ShellReducer.reduce(state, action), action.toString())
        }
    }

    @Test
    fun changingWhichKanjiAreStudiedIsRouteContentAndNotShellState() {
        // The study badge is recomputed from `:application`'s data on the next load. A
        // shell that adjusted the count itself would be guessing at a number it is
        // about to be told. A mnemonic save is the same shape of persisted-content
        // change one route down, so it too leaves the shell untouched.
        val state = ShellState(studyBadgeCount = 4)
        val selections = listOf<KaniAction>(
            KaniAction.Browse.SetStudied(kanji = "脱", studied = false),
            KaniAction.Browse.SetAllStudied(studied = true),
            KaniAction.SaveMnemonic(kanji = "脱", note = "snake escaping"),
        )

        for (action in selections) {
            assertEquals(state, ShellReducer.reduce(state, action), action.toString())
        }
    }

    @Test
    fun gradingACardIsRouteContentAndLeavesTheShellAndItsBadgeAlone() {
        // Which card is next and how many remain both come from `:application`'s
        // snapshot on the reload the route asks for; a shell that moved the badge
        // itself would guess at a number it is about to be told.
        val state = ShellState(studyBadgeCount = 4)
        val studyActions = listOf<KaniAction>(
            KaniAction.Study.Grade(rating = "good"),
            KaniAction.Study.Reveal,
            KaniAction.Study.Continue,
            KaniAction.Study.Undo,
            KaniAction.Game.Start(modeId = "meaning_pop"),
            KaniAction.Game.Answer(answer = "take off"),
            KaniAction.Game.Continue,
            KaniAction.MissingKanji.ScanIntent,
            KaniAction.MissingKanji.AddToKani(literals = setOf("脱")),
            KaniAction.MissingKanji.ExportCsv(literals = setOf("脱")),
            KaniAction.MissingKanji.DismissResult,
        )

        for (action in studyActions) {
            assertEquals(state, ShellReducer.reduce(state, action), action.toString())
        }
    }

    @Test
    fun consumingAnEffectRemovesItFromTheShellQueue() {
        val state = ShellState(
            effects = EffectQueue().enqueue(KaniEffect.OpenUrl("https://example.invalid")),
        )
        val head = requireNotNull(state.effects.head)

        val consumed = ShellReducer.reduce(state, KaniAction.Consume.Effect(head.id))

        assertTrue(consumed.effects.isEmpty)
    }

    @Test
    fun anOrdinaryLaunchStartsAtHomeAndARestoredOneWhereItLeftOff() {
        assertEquals(
            listOf(KaniDestination.Home),
            ShellReducer.launch(request = null).backStack,
        )
        assertEquals(
            listOf<KaniDestination>(KaniDestination.Games),
            ShellReducer.launch(request = null, restored = KaniDestination.Games).backStack,
        )
    }

    @Test
    fun aDeepLinkReplacesTheLaunchStackRatherThanPushingOntoIt() {
        // The user tapped a widget; they did not walk Home -> Stats. Synthesizing
        // that history would make back retrace a path they never took.
        val request = requireNotNull(
            KaniLaunchCodec.request(KaniLaunchCodec.Target.STATS),
        )

        val state = ShellReducer.launch(request)

        assertEquals(listOf<KaniDestination>(KaniDestination.Stats), state.backStack)
        assertEquals(KaniTab.STATS, state.selectedTab)
    }

    @Test
    fun backFromADeepLinkedScreenFallsThroughToItsParent() {
        // The reason a one-entry launch stack is safe: a widget-launched Detail
        // still has somewhere to go, via the destination's own parent rather than a
        // faked history.
        val request = requireNotNull(
            KaniLaunchCodec.request(KaniLaunchCodec.Target.KANJI_DETAIL, kanji = "脱"),
        )
        val launched = ShellReducer.launch(request)

        assertTrue(launched.canGoBack)
        assertEquals(
            listOf<KaniDestination>(KaniDestination.Home),
            ShellReducer.reduce(launched, KaniAction.Navigation.Back).backStack,
        )
    }

    @Test
    fun anExplicitRequestOutranksARestoredSession() {
        // Only reachable on a host whose session outlives the process — a desktop
        // tray asking for Study over yesterday's saved Games screen. An explicit
        // ask is newer information than a saved session.
        val request = requireNotNull(
            KaniLaunchCodec.request(KaniLaunchCodec.Target.STUDY),
        )

        val state = ShellReducer.launch(request, restored = KaniDestination.Games)

        assertEquals(listOf<KaniDestination>(KaniDestination.Study), state.backStack)
    }

    @Test
    fun everyLaunchTargetProducesAUsableShell() {
        // Exhaustive, because a launch state that fails ShellState's own invariants
        // is a crash on startup — the worst place to find one.
        for (target in KaniLaunchCodec.Target.entries) {
            val request = requireNotNull(KaniLaunchCodec.request(target, kanji = "脱"))
            val state = ShellReducer.launch(request)

            assertEquals(request.destination, state.current, target.wireName)
            assertEquals(request.destination.tab, state.selectedTab, target.wireName)
            assertTrue(state.effects.isEmpty, target.wireName)
        }
    }

    @Test
    fun aCopyRequestBecomesAQueuedClipboardEffectWithItsConfirmation() {
        // Queued rather than written directly, which is what pairs the write with the
        // confirmation: a screen reaching for the clipboard itself would have to
        // remember its own toast, and half of them would not. The queue also means
        // the write survives a recomposition between the tap and the host handling it.
        val state = ShellReducer.reduce(
            ShellState(),
            KaniAction.RequestCopy(
                text = "tag:kani_repaired is:suspended",
                confirmation = UiText.Key("clipboard.copied"),
            ),
        )

        assertEquals(
            KaniEffect.CopyToClipboard(
                text = "tag:kani_repaired is:suspended",
                confirmation = UiText.Key("clipboard.copied"),
            ),
            requireNotNull(state.effects.head).effect,
        )
        // Copying is not navigation: the stack and the tab must be untouched.
        assertEquals(ShellState().backStack, state.backStack)
    }

    @Test
    fun aCopyRequestWithNothingToCopyIsRejectedAtConstruction() {
        // An empty clipboard write is not a user intent, and silently queueing one
        // would show a "copied" confirmation for a clipboard that did not change.
        assertFailsWith<IllegalArgumentException> { KaniAction.RequestCopy(text = "") }
    }

    @Test
    fun aCopyRequestNeedsNoConfirmationToBeValid() {
        // Not every copy is worth a toast — a diagnostic dump the user asked for by
        // name says enough by itself.
        val request = KaniAction.RequestCopy(text = "kani-diagnostics")
        assertEquals(UiText.EMPTY, request.confirmation)
    }

    @Test
    fun gatingAnActionOnAPresentCapabilityPassesItThrough() {
        val state = ShellState(
            capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE),
        )
        val action = KaniAction.Navigation.Open(KaniDestination.Settings())

        val (gated, allowed) = ShellReducer.gate(
            state,
            PlatformCapability.BACKUP_RESTORE,
            action,
            UiText.Key("backup.unavailable"),
        )

        assertSame(action, allowed)
        assertTrue(gated.effects.isEmpty)
    }

    @Test
    fun gatingOnAMissingCapabilityExplainsInsteadOfSilentlyDoingNothing() {
        // The failure this prevents is a screen offering a button that does nothing
        // — a dead tray toggle on Android, a dead reminder on desktop.
        val (gated, allowed) = ShellReducer.gate(
            ShellState(),
            PlatformCapability.TRAY_PRESENCE,
            KaniAction.Retry,
            UiText.Key("tray.unsupported"),
        )

        assertNull(allowed)
        assertEquals(
            KaniEffect.ShowMessage(message = UiText.Key("tray.unsupported")),
            requireNotNull(gated.effects.head).effect,
        )
    }
}
