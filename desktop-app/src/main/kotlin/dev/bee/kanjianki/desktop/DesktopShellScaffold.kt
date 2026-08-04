package dev.bee.kanjianki.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.GamesRuntime
import dev.bee.kanjianki.StudyRouteRender
import dev.bee.kanjianki.StudyRuntime
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SetLocalSuspensionCommand
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.home.BrowseScreen
import dev.bee.kanjianki.home.FocusQueuePanel
import dev.bee.kanjianki.home.KanjiDetailScreen
import dev.bee.kanjianki.games.GamesScreenView
import dev.bee.kanjianki.games.rememberGamesCopy
import dev.bee.kanjianki.hostpresentation.DesktopDetailModel
import dev.bee.kanjianki.hostpresentation.DesktopHomeModels
import dev.bee.kanjianki.hostpresentation.DesktopMenuBar
import dev.bee.kanjianki.hostpresentation.DesktopMenuModel
import dev.bee.kanjianki.hostpresentation.HostProviderStatus
import dev.bee.kanjianki.hostpresentation.DesktopGamesModel
import dev.bee.kanjianki.hostpresentation.DesktopSettingsModel
import dev.bee.kanjianki.hostpresentation.DesktopStatsModel
import dev.bee.kanjianki.hostpresentation.DesktopStudyModel
import dev.bee.kanjianki.settings.SettingsScreenView
import dev.bee.kanjianki.settings.rememberSettingsCopy
import dev.bee.kanjianki.stats.StatsDashboardScreen
import dev.bee.kanjianki.study.StudySessionScreen
import dev.bee.kanjianki.study.rememberStudyCopy
import dev.bee.kanjianki.home.HomeDeckOverview
import dev.bee.kanjianki.home.HomeMetricRow
import dev.bee.kanjianki.home.HomeNoticeCard
import dev.bee.kanjianki.home.HomePrimaryAction
import dev.bee.kanjianki.home.HomeTodayCard
import dev.bee.kanjianki.home.OnboardingCard
import dev.bee.kanjianki.home.ProviderStatusRow
import dev.bee.kanjianki.home.RepairedHandoffCard
import dev.bee.kanjianki.home.SyncProgressCard
import dev.bee.kanjianki.home.rememberBrowseCopy
import dev.bee.kanjianki.home.rememberDashboardCopy
import dev.bee.kanjianki.home.rememberHomeCopy
import dev.bee.kanjianki.home.rememberHomeCountedCopy
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.HomeNoticePolicy
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.StudyInputContext
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.data.desktop.DesktopBackupRestoreValidator
import dev.bee.kanjianki.data.desktop.DesktopBackupSnapshotter
import dev.bee.kanjianki.data.desktop.DesktopStagedRestoreApplier
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.desktop.DesktopClipboardService
import dev.bee.kanjianki.platform.desktop.DesktopFilePicker
import dev.bee.kanjianki.platform.desktop.DesktopExternalNavigator
import dev.bee.kanjianki.shell.KaniShell
import dev.bee.kanjianki.shell.LiteralUiTextResolver
import dev.bee.kanjianki.shell.ShellBackAffordanceMode
import dev.bee.kanjianki.shell.ShellEffectHandler
import dev.bee.kanjianki.shell.ShellRouteContent
import dev.bee.kanjianki.shell.rememberShellCopy
import dev.bee.kanjianki.shell.shellRouteTestTag
import dev.bee.kanjianki.ui.KaniThemeId
import dev.bee.kanjianki.ui.KaniTheme
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** The tag the desktop placeholder body renders under, mirroring the shell's. */
internal const val DESKTOP_PLACEHOLDER_TEST_TAG: String = "kani-desktop-placeholder"

/** The tags the three wired routes render their own column under. */
internal const val DESKTOP_HOME_TEST_TAG: String = "kani-desktop-home"
internal const val DESKTOP_FOCUS_QUEUE_TEST_TAG: String = "kani-desktop-focus-queue"
internal const val DESKTOP_BROWSE_TEST_TAG: String = "kani-desktop-browse"
internal const val DESKTOP_DETAIL_TEST_TAG: String = "kani-desktop-detail"
internal const val DESKTOP_STUDY_TEST_TAG: String = "kani-desktop-study"
internal const val DESKTOP_STATS_TEST_TAG: String = "kani-desktop-stats"
internal const val DESKTOP_GAMES_TEST_TAG: String = "kani-desktop-games"
internal const val DESKTOP_SETTINGS_TEST_TAG: String = "kani-desktop-settings"

private val ROUTE_PADDING = 24.dp
private val SURFACE_SPACING = 16.dp

private const val MINUTE_MILLIS = 60_000L

/**
 * The shared shell, wired to a live desktop container.
 *
 * Structured so that everything the user sees comes from `:feature-shell` and
 * everything platform-specific is an argument. That is not a style preference: it
 * is the checkable form of Goal 193's claim that both hosts render the same shell
 * states. If a layout, a loading surface, or a failure banner were written here, the
 * claim would quietly stop being true.
 *
 * [ShellBackAffordanceMode.IN_SHELL] is the one deliberate divergence from Android.
 * A desktop window has no system back gesture, so the shell has to draw the button;
 * Android's shell must not, because it already has the gesture and adding a button
 * would change the shipped app's appearance.
 */
@Composable
internal fun DesktopShellScaffold(
    container: DesktopKaniContainer,
    /**
     * Receives the window's menu bar, and the dispatcher to send its actions to.
     *
     * The menu is `Window`-scoped in Compose Desktop and this is not — but every input the
     * menu needs (the shell state, the visible session, the loaded keybindings, and the
     * one `dispatch`) is here. Handing the built bar and that same dispatcher outward is
     * what makes a menu choice indistinguishable from a click on the equivalent control;
     * a window holding its own copy of the host would be a second path into the reducer.
     */
    onMenuBarChange: (DesktopMenuBar, (KaniAction) -> Unit) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val provider = remember(container) {
        DesktopProviderProbe.forLoopbackEndpoint(container.secretStore)
    }
    // One runtime per session, holding the scheduler-driven Study state across grade,
    // continue, and undo. It is the source of truth for the Study route: its render
    // maps to the portable model the shared surface draws, so the generic per-action
    // reload does not drive Study.
    // Desktop declares no WRITING_RECOGNITION (ADR 0005): no offline Japanese
    // recognizer has passed the quality/licensing gate, so the runtime re-routes a
    // scheduled writing task to core recognition rather than present an ungradeable
    // card. The capability is read from the resolved host set, not hard-coded, so a
    // future desktop recognizer flips this without touching the runtime.
    val writingRecognition = PlatformCapability.WRITING_RECOGNITION in
        desktopHostCapabilities(persistsSecrets = container.persistsSecrets)
    val studyRuntime = remember(container) {
        StudyRuntime(container.studyUseCases, writingRecognitionAvailable = writingRecognition)
    }
    var studyRender by remember { mutableStateOf<StudyRouteRender?>(null) }
    // One games session per container, holding the round/score state across answers.
    val gamesRuntime = remember(container) { GamesRuntime(container.homeUseCases) }
    var gamesRender by remember { mutableStateOf<GamesRender?>(null) }
    val host = remember(container) {
        DesktopShellHost(
            capabilities = PlatformCapabilities(
                desktopHostCapabilities(persistsSecrets = container.persistsSecrets),
            ),
            loadRoute = { destination ->
                // Entering Study with no render yet loads the first card from the
                // committed queue; a re-entry keeps the in-progress session.
                if (destination is KaniDestination.Study && studyRender == null) {
                    studyRender = studyRuntime.load(System.currentTimeMillis())
                }
                // Entering Games with no session yet loads the menu; a re-entry keeps
                // the round in progress.
                if (destination == KaniDestination.Games && gamesRender == null) {
                    gamesRender = gamesRuntime.menu()
                }
                loadDesktopRoute(container, provider, destination, studyRender, gamesRender)
            },
        )
    }

    // The reducers are pure and their outputs are plain values, so recomposition
    // needs an explicit signal. A revision counter is the smallest one that works:
    // the alternative, mirroring every reducer output into its own `mutableStateOf`,
    // gives two sources of truth for the same state.
    var revision by remember { mutableStateOf(0) }
    val shellState = remember(revision) { host.shell }
    val routeState = remember(revision) { host.route(shellState.current) }

    val dispatch: (KaniAction) -> Unit = { action ->
        // The rows the action is about, read before the reducer marks the route
        // reloading. `SetAllStudied` names no kanji — it means "every row currently
        // listed" — so the list it applies to has to come from the visible content.
        val listed = routeState.content.valueOrNull?.browse?.rows.orEmpty().map { it.kanji }
        val pending = host.dispatch(action)
        revision++
        if (pending != null) {
            scope.launch {
                // A Kani-side write is a write and then a reload, in that order and in
                // one launch. `RouteReducer` already turns the action into a reload;
                // what it cannot do is persist the change, and reloading first would
                // re-read the state the user just wrote. A Study action drives the
                // runtime instead of persisting inline — the runtime commits the review
                // itself — and its render becomes the reloaded route's content.
                when (action) {
                    is KaniAction.Browse -> persistBrowseChoice(container, action, listed)
                    is KaniAction.SaveMnemonic -> persistMnemonic(container, action)
                    is KaniAction.Study -> studyRender = driveStudy(studyRuntime, action, studyRender)
                    is KaniAction.Game -> gamesRender = driveGames(gamesRuntime, action)
                    is KaniAction.Settings -> persistSettings(container, action)
                    else -> Unit
                }
                host.perform(pending)
                revision++
            }
        }
    }

    // Entering is dispatched per destination rather than once: `RouteReducer` only
    // loads an Idle route, so returning to an already-loaded screen is free, and a
    // newly revealed one loads without the host tracking which is which.
    LaunchedEffect(shellState.current) {
        dispatch(KaniAction.Lifecycle.Entered)
    }

    // What holds the keyboard on the Study route, reported by the surface itself. The
    // menu's grade items must be inert while a card is face down, and the reveal state is
    // the surface's local state — so it is read from there rather than guessed at here.
    var studyInputContext by remember { mutableStateOf(StudyInputContext()) }
    val content = routeState.content.valueOrNull
    val menuBar = DesktopMenuModel.bar(
        shell = shellState,
        // Only on the Study route: elsewhere there is no visible card, so a grade item
        // that resolved off a stale session would grade a card the user cannot see.
        session = content?.study?.takeIf { shellState.current is KaniDestination.Study },
        bindings = content?.studyKeybindings ?: StudyKeybindings.DEFAULT,
        platform = container.keyboardPlatform,
        context = studyInputContext,
    )
    LaunchedEffect(menuBar) {
        onMenuBarChange(menuBar, dispatch)
    }

    val effectHandler = remember(container, provider) {
        desktopEffectHandler(container = container, provider = provider)
    }

    // The theme follows whatever the last load reported, and defaults until then.
    // Deriving it from route content rather than a separate read means the window
    // cannot show one theme while Settings believes another.
    KaniTheme(theme = KaniThemeId.fromStorageKey(routeState.content.valueOrNull?.themeChoice?.storageKey)) {
        KaniShell(
            state = shellState,
            resolver = LiteralUiTextResolver,
            effectHandler = effectHandler,
            dispatch = dispatch,
            backAffordance = ShellBackAffordanceMode.IN_SHELL,
        ) { destination ->
            DesktopRouteBody(
                destination = destination,
                state = routeState,
                capabilities = shellState.capabilities,
                dispatch = dispatch,
                onStudyInputContextChange = { studyInputContext = it },
            )
        }
    }
}

/**
 * One route's body: Home, Browse, and the focus queue for real; the rest still stubs.
 *
 * Every branch goes through [ShellRouteContent] rather than rendering its own loading
 * and error states, which is what makes the desktop spinner, refresh hint, failure
 * banner, and retry button the ones the shell's own tests already cover. It also
 * keeps [shellRouteTestTag] on every route, which `:feature-shell`'s render
 * assertions depend on.
 */
@Composable
private fun DesktopRouteBody(
    destination: KaniDestination,
    state: RouteState<DesktopRouteContent>,
    capabilities: PlatformCapabilities,
    dispatch: (KaniAction) -> Unit,
    onStudyInputContextChange: (StudyInputContext) -> Unit,
) {
    ShellRouteContent(
        state = state,
        copy = rememberShellCopy(),
        resolver = LiteralUiTextResolver,
        dispatch = dispatch,
        modifier = Modifier.testTag(shellRouteTestTag(destination)),
    ) { content ->
        when (destination) {
            KaniDestination.Home -> DesktopHomeRoute(
                content = content,
                capabilities = capabilities,
                dispatch = dispatch,
            )
            KaniDestination.FocusQueue -> DesktopFocusQueueRoute(
                content = content,
                dispatch = dispatch,
            )
            is KaniDestination.Browse -> DesktopBrowseRoute(
                content = content,
                dispatch = dispatch,
            )
            is KaniDestination.Detail -> DesktopDetailRoute(
                content = content,
                dispatch = dispatch,
            )
            KaniDestination.Study -> DesktopStudyRoute(
                content = content,
                dispatch = dispatch,
                onInputContextChange = onStudyInputContextChange,
            )
            KaniDestination.Stats -> DesktopStatsRoute(
                content = content,
                dispatch = dispatch,
            )
            KaniDestination.Games -> DesktopGamesRoute(
                content = content,
                dispatch = dispatch,
            )
            is KaniDestination.Settings -> DesktopSettingsRoute(
                content = content,
                dispatch = dispatch,
            )
            else -> DesktopRoutePlaceholder(destination = destination, content = content)
        }
    }
}

/**
 * Home, entirely from `:feature-home`.
 *
 * Nothing is laid out here beyond the column that stacks the shared surfaces, and
 * that restraint is the deliverable: the onboarding card, the metric row, the Today
 * card, the deck overview, the capability notice, and the focus preview are the same
 * composables the Android host shows, fed from the same portable models. A layout
 * written here would be the point at which "both hosts render the same Home" stopped
 * being checkable.
 *
 * The notice list comes from [HomeNoticePolicy] against the live capability set
 * rather than a desktop constant, so it says what this connection actually lacks —
 * which on AnkiConnect is FSRS memory state, and so reduced early-interval precision.
 */
@Composable
private fun DesktopHomeRoute(
    content: DesktopRouteContent,
    capabilities: PlatformCapabilities,
    dispatch: (KaniAction) -> Unit,
) {
    val homeCopy = rememberHomeCopy()
    val dashboardCopy = rememberDashboardCopy()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_HOME_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(SURFACE_SPACING),
    ) {
        ProviderStatusRow(readiness = content.home.readiness, copy = homeCopy)
        OnboardingCard(
            plan = content.onboarding,
            copy = homeCopy,
            resolver = LiteralUiTextResolver,
            dispatch = dispatch,
            counted = rememberHomeCountedCopy(content.onboarding),
            // A sync cannot be requested while one is running, and desktop has no
            // sync engine wired yet (Goal 202), so this is always enabled today.
            // Threading it from the model rather than passing `true` means the flag
            // starts working the moment the engine reports a run.
            enabled = !content.home.syncing,
        )
        if (content.home.syncing) {
            SyncProgressCard(copy = homeCopy, dispatch = dispatch)
        }
        if (content.home.repairedKanjiCount > 0) {
            RepairedHandoffCard(
                count = content.home.repairedKanjiCount,
                copy = homeCopy,
                dispatch = dispatch,
            )
        }
        for (notice in HomeNoticePolicy.notices(capabilities)) {
            HomeNoticeCard(notice = notice, copy = dashboardCopy)
        }
        HomePrimaryAction(home = content.home, copy = dashboardCopy, dispatch = dispatch)
        HomeMetricRow(
            metrics = content.home.metrics,
            copy = dashboardCopy,
            resolver = LiteralUiTextResolver,
            dispatch = dispatch,
        )
        content.home.todayPlan?.let { plan ->
            HomeTodayCard(
                plan = plan,
                copy = dashboardCopy,
                resolver = LiteralUiTextResolver,
                dispatch = dispatch,
            )
        }
        HomeDeckOverview(
            rows = content.home.deckOverview,
            copy = dashboardCopy,
            resolver = LiteralUiTextResolver,
        )
        FocusQueuePanel(
            queue = content.home.focus,
            copy = dashboardCopy,
            resolver = LiteralUiTextResolver,
            dispatch = dispatch,
        )
    }
}

/**
 * The full focus queue.
 *
 * The same panel Home previews, without the preview cap — which is the whole
 * difference between the two routes, and the reason "View all" is a destination
 * rather than an expand toggle.
 */
@Composable
private fun DesktopFocusQueueRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_FOCUS_QUEUE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(SURFACE_SPACING),
    ) {
        FocusQueuePanel(
            queue = content.home.focus,
            copy = rememberDashboardCopy(),
            resolver = LiteralUiTextResolver,
            dispatch = dispatch,
        )
    }
}

/** Browse, from `:feature-home`'s own screen. */
@Composable
private fun DesktopBrowseRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
) {
    BrowseScreen(
        results = content.browse,
        copy = rememberBrowseCopy(),
        resolver = LiteralUiTextResolver,
        dispatch = dispatch,
        modifier = Modifier.fillMaxSize().testTag(DESKTOP_BROWSE_TEST_TAG),
    )
}

/**
 * One kanji's detail, from `:feature-home`'s own screen.
 *
 * Scrollable because the full card — hero, panels, neighbours, timeline, examples —
 * is taller than the window, the same wrapping the surface's own tests use. A detail
 * that has not loaded yet has no [KanjiDetail], and the shell's loading surface is
 * already on screen above this, so nothing is drawn until it arrives.
 */
@Composable
private fun DesktopDetailRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
) {
    val detail = content.detail ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_DETAIL_TEST_TAG),
    ) {
        KanjiDetailScreen(detail = detail, resolver = LiteralUiTextResolver, dispatch = dispatch)
    }
}

/**
 * The study session, from `:feature-study`'s own screen.
 *
 * The runtime is the source of truth; its render mapped to [content]'s study session
 * feeds the shared surface. Scrollable because a card plus its grades and answer
 * details is taller than the window, the same wrapping the surface's tests use. A
 * session that has not loaded yet has no model, and the shell's loading surface is
 * above this, so nothing is drawn until it arrives.
 */
@Composable
private fun DesktopStudyRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
    onInputContextChange: (StudyInputContext) -> Unit,
) {
    val session = content.study ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_STUDY_TEST_TAG),
    ) {
        StudySessionScreen(
            session = session,
            copy = rememberStudyCopy(),
            resolver = LiteralUiTextResolver,
            dispatch = dispatch,
            keybindings = content.studyKeybindings,
            // The menu bar's grade items follow the card's reveal state, which is the
            // surface's own local state; this is how it reaches the window.
            onInputContextChange = onInputContextChange,
        )
    }
}

/**
 * The progress-analytics dashboard, from `:feature-stats`'s own screen.
 *
 * Scrollable because six sections of charts are far taller than the window. The
 * analytics are computed by `:progress-core` and mapped to the portable dashboard;
 * this only lays them out. A dashboard that has not loaded yet is null, and the
 * shell's loading surface is above this.
 */
@Composable
private fun DesktopStatsRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
) {
    val dashboard = content.stats ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_STATS_TEST_TAG),
    ) {
        StatsDashboardScreen(dashboard = dashboard, dispatch = dispatch)
    }
}

/**
 * The kanji games, from `:feature-games`'s own screen.
 *
 * Runtime-driven like Study: the engine state maps to the portable screen, which the
 * shared surface renders. Scrollable because a round's prompt plus choices can exceed
 * the window.
 */
@Composable
private fun DesktopGamesRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
) {
    val screen = content.games ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_GAMES_TEST_TAG),
    ) {
        GamesScreenView(screen = screen, copy = rememberGamesCopy(), dispatch = dispatch)
    }
}

/**
 * Settings, from `:feature-settings`'s own screen.
 *
 * The root category menu is real — the same titles and summaries the Android host
 * shows, from `SettingsSectionTextCopy` — and each leaf section is the shared surface's
 * honest placeholder until later Goal 198 slices port it. Scrollable because the
 * category menu is taller than a short window. A route that has not loaded yet has no
 * model, and the shell's loading surface is above this.
 */
@Composable
private fun DesktopSettingsRoute(
    content: DesktopRouteContent,
    dispatch: (KaniAction) -> Unit,
) {
    val screen = content.settings ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(DESKTOP_SETTINGS_TEST_TAG),
    ) {
        SettingsScreenView(screen = screen, copy = rememberSettingsCopy(), dispatch = dispatch)
    }
}

/**
 * A placeholder body for the routes Goals 195+ still own.
 *
 * Kept rather than replaced with an empty box because it is the cheapest evidence
 * that a route loaded through the real startup lifecycle: it reports what Anki said
 * and how much of the collection is admitted, which a stub could not produce.
 */
@Composable
private fun DesktopRoutePlaceholder(
    destination: KaniDestination,
    content: DesktopRouteContent,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(ROUTE_PADDING)
            .testTag(DESKTOP_PLACEHOLDER_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = destination.route, style = MaterialTheme.typography.titleLarge)
        Text(text = content.providerMessage, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${content.studyItemCount} kanji admitted, ${content.dueCount} due",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Loads what [destination] needs, from one profile snapshot and one provider probe.
 *
 * On IO, because the provider probe is a blocking HTTP round trip and the profile
 * read is a blocking SQL query, and doing either on the Compose dispatcher stalls
 * the window. This is the whole reason [DesktopShellHost.perform] is suspending
 * rather than plain.
 *
 * One snapshot for every route rather than a per-route query: the routes share a
 * `DesktopRouteContent`, the reads are one transaction each, and the alternative —
 * a route-shaped query per destination — is the second host harness Goal 193
 * forbids. Browse is the one route with an extra read, because its rows come from a
 * query the destination itself parameterizes.
 */
private suspend fun loadDesktopRoute(
    container: DesktopKaniContainer,
    provider: DesktopProviderProbe,
    destination: KaniDestination,
    studyRender: StudyRouteRender?,
    gamesRender: GamesRender?,
): ContentResult<DesktopRouteContent> = withContext(Dispatchers.IO) {
    // The provider probe is desktop's (an AnkiConnect handshake); everything after it
    // is the shared KaniRouteLoader, so both hosts assemble route content identically.
    val status = provider.probe()
    ContentResult.Success(
        container.routeLoader.load(
            destination = destination,
            status = HostProviderStatus(
                readiness = status.readiness,
                message = status.message,
                isReady = status.isReady,
                capabilities = status.capabilities,
            ),
            nowMillis = System.currentTimeMillis(),
            studyRender = studyRender,
            gamesRender = gamesRender,
        ),
    )
}

/**
 * Drives the games runtime for one action, returning the new render.
 *
 * Start begins a mode, Answer scores the round, Continue advances — all in-memory in
 * the engine, nothing persisted. The menu is loaded on route entry, so this never
 * needs the suspend `menu()` path.
 */
private fun driveGames(runtime: GamesRuntime, action: KaniAction.Game): GamesRender {
    val now = System.currentTimeMillis()
    return when (action) {
        is KaniAction.Game.Start -> runtime.start(action.modeId, now)
        is KaniAction.Game.Answer -> runtime.answer(action.answer, now)
        KaniAction.Game.Continue -> runtime.advance(now)
    }
}

/**
 * Drives the study runtime for one action, returning the new render.
 *
 * Grade/Continue/Undo are the runtime's; Reveal is pure UI the surface holds locally,
 * so it does not touch the runtime and the current render carries through. The runtime
 * commits the review itself, so there is no separate persist step here.
 */
private suspend fun driveStudy(
    runtime: StudyRuntime,
    action: KaniAction.Study,
    current: StudyRouteRender?,
): StudyRouteRender = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    when (action) {
        is KaniAction.Study.Grade -> runtime.grade(action.rating, now)
        KaniAction.Study.Continue -> runtime.continueCard(now)
        KaniAction.Study.Undo -> runtime.undo(now)
        KaniAction.Study.Reveal -> current ?: runtime.render()
    }
}

/**
 * Persists a saved mnemonic before the route reloads.
 *
 * The trimming is the surface's; an empty note is a clear, which the store records as
 * an empty mnemonic. Kani-side content — the note lives in Kani's own store, never in
 * the collection.
 */
private suspend fun persistMnemonic(
    container: DesktopKaniContainer,
    action: KaniAction.SaveMnemonic,
) {
    container.homeUseCases.saveMnemonic(
        SaveMnemonicCommand(
            kanji = action.kanji,
            note = action.note,
            updatedAtMillis = System.currentTimeMillis(),
        ),
    )
}

/**
 * Persists a Browse checkbox before the route reloads.
 *
 * `studied` and `suspended` are opposites: marking a kanji for study clears its local
 * suspension. The polarity is Android's — its detail path writes
 * `SetLocalSuspensionCommand(kanji, !suspended, …)` — and inverting it here would
 * silently retire every kanji the user ticked.
 *
 * This is Kani-side queue state. Nothing here reaches the collection: CLAUDE.md's
 * write surface is note tags plus the additive Missing Kanji flow, and suspension is
 * explicitly not on it.
 */
private suspend fun persistBrowseChoice(
    container: DesktopKaniContainer,
    action: KaniAction.Browse,
    listed: List<String>,
) {
    val (kanji, studied) = when (action) {
        is KaniAction.Browse.SetStudied -> listOf(action.kanji) to action.studied
        is KaniAction.Browse.SetAllStudied -> listed to action.studied
    }
    if (kanji.isEmpty()) return
    container.homeUseCases.setLocalSuspension(
        SetLocalSuspensionCommand(
            kanji = kanji,
            suspended = !studied,
            updatedAtMillis = System.currentTimeMillis(),
        ),
    )
}

/**
 * Persists a settings edit before the section reloads.
 *
 * The action carries a stable key/id; [DesktopSettingsModel.settingsCommandFor] maps it
 * to the concrete `SettingsSaveCommand`. Only edits the desktop app currently ports map
 * to a command — an un-ported edit produces null and is ignored, which cannot happen in
 * practice because only ported sections render a control that dispatches one. Kani-side
 * device state; nothing here reaches the collection.
 */
private suspend fun persistSettings(
    container: DesktopKaniContainer,
    action: KaniAction.Settings,
) {
    // Keybindings are device-local, not portable collection settings, so they take the
    // device-settings store rather than a SettingsSaveCommand. A null edit means the
    // platform or another command holds the key, or nothing would change — either way
    // there is nothing to write, and the reload re-renders the unchanged set.
    val keybindings = DesktopSettingsModel.keybindingEditFor(
        action = action,
        stored = container.deviceSettingsStore.read(DeviceSettingKeys.studyKeybindings),
        platform = container.keyboardPlatform,
    )
    if (keybindings != null) {
        container.deviceSettingsStore.edit {
            put(DeviceSettingKeys.studyKeybindings, keybindings)
        }
        return
    }
    // The current snapshot resolves paired commands (a ladder threshold carries both
    // values, so the untouched one is read here rather than clobbered).
    val current = container.settingsUseCases.load()
    val command = DesktopSettingsModel.settingsCommandFor(action, current) ?: return
    container.settingsUseCases.save(command)
}

/**
 * The four effects the shell cannot perform itself, over the desktop adapters.
 *
 * The AWT calls are supplied as lambdas rather than reached from inside
 * `:platform-desktop`, which is why those adapters stay unit-testable headlessly:
 * `Toolkit.getDefaultToolkit()` throws with no display, and a test for "a blank
 * query is refused" should not need one.
 */
private fun desktopEffectHandler(
    container: DesktopKaniContainer,
    provider: DesktopProviderProbe,
): ShellEffectHandler {
    val navigator = DesktopExternalNavigator(
        browse = { uri ->
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                false
            } else {
                desktop.browse(uri)
                true
            }
        },
        // Anki's own browser, not a web one. The callback exists because
        // `:platform-desktop` must not depend on `:provider-ankiconnect`; the
        // composition root is the only place allowed to see both.
        guiBrowse = provider::browse,
    )
    val clipboard = DesktopClipboardService { text ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }
    // The backup-export flow: an AWT SAVE dialog through DesktopFilePicker registers the
    // chosen path with DesktopFileAccess, then DesktopBackupSnapshotter writes the
    // VACUUM-INTO gzip there. The dialog is the one part that needs a display, so it is
    // the injected seam DesktopFilePicker takes.
    val saveFilePicker = DesktopFilePicker(container.fileAccess, ::awtSaveDialog)
    val openFilePicker = DesktopFilePicker(container.fileAccess, ::awtOpenDialog)
    val backupExport = DesktopBackupExport(
        picker = saveFilePicker,
        databaseFile = container.databaseFile,
        pathOf = container.fileAccess::resolve,
        snapshot = DesktopBackupSnapshotter::snapshot,
    )
    val backupRestore = DesktopBackupRestore(
        picker = openFilePicker,
        restoreDir = container.appDirectories.directories().cache,
        profileDir = container.profileDir,
        openInput = container.fileAccess::openInput,
        validate = { restoreDir, sourceName, input ->
            DesktopBackupRestoreValidator.validate(restoreDir, sourceName, input)
        },
        stage = DesktopStagedRestoreApplier::stage,
    )
    return object : ShellEffectHandler {
        override fun openUrl(url: String) {
            runCatching { navigator.openUrl(URI(url)) }
        }

        override fun copyToClipboard(text: String) {
            clipboard.setText(label = "Kani", text = text)
        }

        // Backup export is wired; restore and the Missing Kanji CSV need the same
        // dialog plus their own consumers and land next. An unhandled purpose stays a
        // no-op rather than a dialog that cannot deliver.
        override fun pickFile(purpose: KaniEffect.PickFile) {
            when (purpose.purpose) {
                KaniEffect.FilePurpose.BACKUP_EXPORT -> backupExport.run()
                KaniEffect.FilePurpose.BACKUP_RESTORE -> backupRestore.run()
                // The Missing Kanji CSV needs the Goal 183 dictionary candidates to
                // export; until those assets land its report is empty, so a picker here
                // would save an empty file. Stays a no-op rather than a misleading save.
                KaniEffect.FilePurpose.MISSING_KANJI_CSV_EXPORT -> Unit
            }
        }

        // Focus targets are registered by the feature composables that own the
        // fields, and none exist yet. An unknown target is a no-op by contract.
        override fun requestFocus(target: String) = Unit
    }
}

/**
 * Shows an AWT SAVE [java.awt.FileDialog] and returns the chosen path, or null if the
 * user cancelled.
 *
 * The one piece of the backup flow that needs a display, kept a top-level function so
 * DesktopFilePicker's own logic stays headlessly testable (the dialog is its injected
 * seam). The request's suggested name pre-fills the field; its filters are advisory on
 * a SAVE dialog, which AWT does not enforce, so they are not applied here.
 */
private fun awtSaveDialog(request: dev.bee.kanjianki.platform.FilePickerRequest): java.nio.file.Path? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Save Kani backup", java.awt.FileDialog.SAVE)
    request.suggestedName?.let { dialog.file = it }
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return java.nio.file.Path.of(directory, file)
}

/**
 * Shows an AWT LOAD [java.awt.FileDialog] and returns the chosen path, or null on
 * cancel. The OPEN counterpart to [awtSaveDialog], the injected seam for restore's
 * DesktopFilePicker.
 */
private fun awtOpenDialog(@Suppress("UNUSED_PARAMETER") request: dev.bee.kanjianki.platform.FilePickerRequest): java.nio.file.Path? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Choose a Kani backup", java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return java.nio.file.Path.of(directory, file)
}
