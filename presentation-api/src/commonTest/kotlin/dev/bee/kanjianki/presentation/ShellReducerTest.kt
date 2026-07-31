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
    fun consumingAnEffectRemovesItFromTheShellQueue() {
        val state = ShellState(
            effects = EffectQueue().enqueue(KaniEffect.OpenUrl("https://example.invalid")),
        )
        val head = requireNotNull(state.effects.head)

        val consumed = ShellReducer.reduce(state, KaniAction.Consume.Effect(head.id))

        assertTrue(consumed.effects.isEmpty)
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
