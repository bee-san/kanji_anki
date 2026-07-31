package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Goal 192's done-when: a fake host drives every route through immutable state and
 * actions, in a common test, with no platform underneath it.
 */
class FakeHostDrivesEveryRouteTest {
    private val everyDestination: List<KaniDestination> = listOf(
        KaniDestination.Home,
        KaniDestination.Study,
        KaniDestination.Stats,
        KaniDestination.FocusQueue,
        KaniDestination.RecentMistakes,
        KaniDestination.Games,
        KaniDestination.MissingKanji,
        KaniDestination.Browse(query = "bridge", allKanjiScope = true),
        KaniDestination.Detail(kanji = "橋", fromBrowse = true, query = "bridge"),
        KaniDestination.ReadOnlyDetail(kanji = "窓", query = "window"),
    ) + SettingsSection.entries.map(KaniDestination::Settings)

    @Test
    fun everyRouteCanBeOpenedLoadedAndRenderedWithNoPlatform() {
        val host = FakeHost()

        for (destination in everyDestination) {
            host.open(destination)

            assertSame(destination, host.shell.current, destination.route)
            assertEquals(destination.tab, host.shell.selectedTab, destination.route)
            assertEquals(
                Loadable.Loaded(destination.route),
                host.current.content,
                destination.route,
            )
            assertNull(host.current.failure, destination.route)
            assertFalse(host.current.isBusy, destination.route)
        }

        assertEquals(everyDestination.size, host.loads.size, "every route loaded exactly once")
        assertTrue(host.loads.values.all { it == 1 }, "no route loaded twice: ${host.loads}")
    }

    @Test
    fun everyRouteIsReachableFromItsTabAndBackReturnsWithoutStranding() {
        val host = FakeHost()

        for (destination in everyDestination) {
            host.dispatch(KaniAction.Navigation.SelectTab(destination.tab))
            host.open(destination)
            assertEquals(destination, host.shell.current, destination.route)

            host.dispatch(KaniAction.Navigation.Back)

            // Back from a route reached through its own tab lands somewhere real —
            // either the previous entry or the destination's declared parent. A
            // route where neither exists would be a dead end.
            assertTrue(host.shell.backStack.isNotEmpty(), destination.route)
            if (destination != destination.tab.root) {
                assertTrue(
                    host.shell.current != destination,
                    "back from ${destination.route} went nowhere",
                )
            }
        }
    }

    @Test
    fun aRouteThatFailsToLoadOffersARetryThatSucceeds() {
        // The whole failure lifecycle without a provider: fail, render, retry,
        // recover — driven by actions a test can list.
        val failure = PresentationFailure(
            kind = PresentationFailure.Kind.PROVIDER_UNAVAILABLE,
            message = UiText.Key("provider.unreachable"),
        )
        var attempts = 0
        val host = FakeHost()
        val flaky = object {
            fun result(): ContentResult<String> =
                if (attempts++ == 0) ContentResult.Failure(failure) else ContentResult.Success("ok")
        }

        var state = RouteState<String>(KaniDestination.Study)
        val (entered, firstIntent) = RouteReducer.reduce(state, KaniAction.Lifecycle.Entered)
        assertEquals(RouteIntent.Load, firstIntent)
        state = entered.applying(flaky.result())

        assertEquals(Loadable.Failed(failure), state.content)
        assertTrue(assertNotNull(state.failure).isRetryable)

        val (retrying, retryIntent) = RouteReducer.reduce(state, KaniAction.Retry)
        assertEquals(RouteIntent.Load, retryIntent)
        state = retrying.applying(flaky.result())

        assertEquals(Loadable.Loaded("ok"), state.content)
        assertNull(state.failure)
        assertEquals(2, attempts)
        assertTrue(host.delivered.isEmpty(), "a load failure is state, not a one-shot effect")
    }

    @Test
    fun aRouteWhoseContentIsPermanentlyUnavailableStaysHonestAboutIt() {
        val failure = PresentationFailure(
            kind = PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED,
            message = UiText.Key("provider.auth"),
            diagnostic = "401 from AnkiConnect",
        )
        val host = FakeHost(
            content = mapOf(KaniDestination.Study.route to ContentResult.Failure(failure)),
        )

        host.open(KaniDestination.Study)

        assertEquals(Loadable.Failed(failure), host.current.content)
        assertFalse(
            assertNotNull(host.current.failure).isRetryable,
            "an auth failure must not offer a blind retry",
        )
    }

    @Test
    fun eachRouteKeepsItsOwnStateSoOneScreensFailureIsNotAnothersReducer() {
        val host = FakeHost(
            content = mapOf(
                KaniDestination.Stats.route to ContentResult.Failure(
                    PresentationFailure(PresentationFailure.Kind.TRANSIENT),
                ),
            ),
        )

        host.open(KaniDestination.Home)
        host.open(KaniDestination.Stats)

        assertNotNull(host.route(KaniDestination.Stats).failure)
        assertNull(host.route(KaniDestination.Home).failure)
        assertEquals("home", host.route(KaniDestination.Home).content.valueOrNull)
    }

    @Test
    fun returningToAnAlreadyLoadedRouteDoesNotReloadIt() {
        val host = FakeHost()

        host.open(KaniDestination.Home)
        host.open(KaniDestination.Games)
        host.dispatch(KaniAction.Navigation.Back)
        host.dispatch(KaniAction.Lifecycle.Entered)

        assertSame(KaniDestination.Home, host.shell.current)
        assertEquals(1, host.loads["home"])
    }

    @Test
    fun anExplicitRefreshOnAVisitedRouteReloadsWithoutBlankingIt() {
        val host = FakeHost()
        host.open(KaniDestination.Home)

        host.dispatch(KaniAction.Lifecycle.Refresh)

        assertEquals(2, host.loads["home"])
        assertEquals("home", host.current.content.valueOrNull)
    }

    @Test
    fun aConfirmationFlowRunsEndToEndWithNoDialogFramework() {
        // Restore is confirm-gated. The fake host can show the dialog, answer it,
        // and observe the confirmed action take effect — all as values.
        val host = FakeHost(
            capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE),
        )
        host.open(KaniDestination.Settings(SettingsSection.AUTOMATION))
        val confirmed = KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.UPDATE))
        host.enqueue(
            KaniEffect.Confirm(
                title = UiText.Key("restore.title"),
                body = UiText.Key("restore.body"),
                confirmLabel = UiText.Key("restore.confirm"),
                dismissLabel = UiText.Key("cancel"),
                confirm = confirmed,
                isDestructive = true,
            ),
        )

        val shown = host.deliverOneEffect()
        host.dispatch(assertNotNull(shown as? KaniEffect.Confirm).confirm)

        assertEquals(
            KaniDestination.Settings(SettingsSection.UPDATE),
            host.shell.current,
        )
        assertTrue(host.current.effects.isEmpty, "a confirmed dialog must not re-show")
    }

    @Test
    fun aCapabilityTheHostLacksIsExplainedAndTheActionNeverRuns() {
        // The dead-button failure, end to end: an Android host has no tray, so the
        // tray action must not reach the reducer at all.
        val host = FakeHost(capabilities = PlatformCapabilities.of(PlatformCapability.NOTIFICATIONS))
        host.open(KaniDestination.Settings(SettingsSection.AUTOMATION))

        host.gate(
            PlatformCapability.TRAY_PRESENCE,
            KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.APPEARANCE)),
            UiText.Key("tray.unsupported"),
        )
        host.deliverAllEffects()

        assertEquals(
            KaniDestination.Settings(SettingsSection.AUTOMATION),
            host.shell.current,
            "a gated navigation must not happen",
        )
        assertEquals(
            listOf<KaniEffect>(KaniEffect.ShowMessage(message = UiText.Key("tray.unsupported"))),
            host.delivered,
        )
    }

    @Test
    fun aCapabilityTheHostHasLetsTheActionThrough() {
        val host = FakeHost(
            capabilities = PlatformCapabilities.of(PlatformCapability.PROVIDER_BROWSER_HANDOFF),
        )
        host.open(KaniDestination.Home)

        host.gate(
            PlatformCapability.PROVIDER_BROWSER_HANDOFF,
            KaniAction.Navigation.Open(KaniDestination.Browse(query = "tag:kani_repaired")),
            UiText.Key("browser.unsupported"),
        )
        host.deliverAllEffects()

        assertEquals(
            KaniDestination.Browse(query = "tag:kani_repaired"),
            host.shell.current,
        )
        assertTrue(host.delivered.isEmpty(), "a supported action needs no explanation")
    }

    @Test
    fun everyEffectKindReachesTheHostExactlyOnce() {
        // If an effect kind could not be driven from common code, a host would need
        // a platform-specific side channel for it — which is the thing this module
        // exists to avoid.
        val host = FakeHost()
        host.open(KaniDestination.Settings())
        val effects = listOf(
            KaniEffect.ShowMessage(
                message = UiText.Key("sync.done"),
                actionLabel = UiText.Key("view"),
                action = KaniAction.Navigation.Open(KaniDestination.Stats),
            ),
            KaniEffect.Confirm(
                title = UiText.Key("t"),
                body = UiText.Key("b"),
                confirmLabel = UiText.Key("ok"),
                dismissLabel = UiText.Key("no"),
                confirm = KaniAction.Retry,
            ),
            KaniEffect.OpenUrl("https://example.invalid/licenses"),
            KaniEffect.CopyToClipboard(
                text = "tag:kani_repaired is:suspended",
                confirmation = UiText.Key("copied"),
            ),
            KaniEffect.PickFile(
                purpose = KaniEffect.FilePurpose.BACKUP_EXPORT,
                suggestedName = "kani-backup.gz",
            ),
            KaniEffect.RequestFocus("study-answer"),
        )
        effects.forEach(host::enqueue)

        host.deliverAllEffects()

        assertEquals(effects, host.delivered)
        assertTrue(host.current.effects.isEmpty)
    }

    @Test
    fun everyFilePurposeCanBeRequestedFromCommonCode() {
        val host = FakeHost()
        host.open(KaniDestination.Settings(SettingsSection.AUTOMATION))
        KaniEffect.FilePurpose.entries.forEach { host.enqueue(KaniEffect.PickFile(it)) }

        host.deliverAllEffects()

        assertEquals(
            KaniEffect.FilePurpose.entries.map<KaniEffect.FilePurpose, KaniEffect> {
                KaniEffect.PickFile(it)
            },
            host.delivered,
        )
    }

    @Test
    fun aRouteLeftBehindDropsItsUndeliveredEffectsButKeepsItsContent() {
        val host = FakeHost()
        host.open(KaniDestination.Home)
        host.enqueue(KaniEffect.ShowMessage(UiText.Key("stale")))

        host.dispatch(KaniAction.Lifecycle.Exited)

        assertTrue(host.route(KaniDestination.Home).effects.isEmpty)
        assertEquals("home", host.route(KaniDestination.Home).content.valueOrNull)
        assertTrue(host.delivered.isEmpty())
    }

    @Test
    fun aRestoredSessionResumesOnItsRouteWithoutAStackBehindIt() {
        // The desktop host restores a session from disk and Android restores from
        // saved state; both arrive as a single destination with no history.
        val destination = assertNotNull(
            KaniDestinationCodec.decode(
                KaniDestinationCodec.encode(
                    KaniDestination.Detail(kanji = "脱", fromBrowse = true, query = "escape"),
                ),
            ),
        )
        val host = FakeHost(restoredAt = destination)

        host.dispatch(KaniAction.Lifecycle.Entered)

        assertEquals(destination, host.shell.current)
        assertEquals(Loadable.Loaded("detail"), host.current.content)
        assertEquals(listOf(destination), host.shell.backStack, "restore invents no history")

        host.dispatch(KaniAction.Navigation.Back)

        // Back has no stack entry to pop, so it falls to the destination's declared
        // parent — the search the user was in. Without that fallback, back would be
        // dead on every restored deep screen.
        assertEquals(KaniDestination.Browse(query = "escape"), host.shell.current)
    }

    @Test
    fun aStudyBadgeIsCarriedByTheShellWithoutTheShellDerivingIt() {
        // The count comes from :application. The shell holds it so the nav bar can
        // render it; computing it here would be a second source of truth.
        val host = FakeHost()
        host.open(KaniDestination.Home)

        assertEquals(0, host.shell.studyBadgeCount)
        assertEquals(7, ShellState(studyBadgeCount = 7).studyBadgeCount)
    }

    @Test
    fun aScreenCanRenderEveryTextKindThroughAHostResolver() {
        val resolver = FakeUiTextResolver(
            mapOf(
                "study.due" to "{0} due",
                "queue.size.one" to "%d card",
                "queue.size.other" to "%d cards",
            ),
        )

        assertEquals("橋", resolver.resolve(UiText.Literal("橋")))
        assertEquals("", resolver.resolve(UiText.EMPTY))
        assertEquals(
            "12 due",
            resolver.resolve(UiText.Key("study.due", listOf(UiText.Literal("12")))),
        )
        assertEquals("1 card", resolver.resolve(UiText.Quantity("queue.size", count = 1)))
        assertEquals("9 cards", resolver.resolve(UiText.Quantity("queue.size", count = 9)))
        assertEquals("unknown.key", resolver.resolve(UiText.Key("unknown.key")))
    }
}
