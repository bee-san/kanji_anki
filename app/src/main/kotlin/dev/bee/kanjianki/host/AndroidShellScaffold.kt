package dev.bee.kanjianki.host

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
import dev.bee.kanjianki.hostpresentation.HostProviderStatus
import dev.bee.kanjianki.hostpresentation.KaniRouteContent
import dev.bee.kanjianki.home.BrowseScreen
import dev.bee.kanjianki.home.FocusQueuePanel
import dev.bee.kanjianki.home.HomeDeckOverview
import dev.bee.kanjianki.home.HomeMetricRow
import dev.bee.kanjianki.home.HomeNoticeCard
import dev.bee.kanjianki.home.HomePrimaryAction
import dev.bee.kanjianki.home.HomeTodayCard
import dev.bee.kanjianki.home.KanjiDetailScreen
import dev.bee.kanjianki.home.OnboardingCard
import dev.bee.kanjianki.home.ProviderStatusRow
import dev.bee.kanjianki.home.RepairedHandoffCard
import dev.bee.kanjianki.home.SyncProgressCard
import dev.bee.kanjianki.home.rememberBrowseCopy
import dev.bee.kanjianki.home.rememberDashboardCopy
import dev.bee.kanjianki.home.rememberHomeCopy
import dev.bee.kanjianki.home.rememberHomeCountedCopy
import dev.bee.kanjianki.games.GamesScreenView
import dev.bee.kanjianki.games.rememberGamesCopy
import dev.bee.kanjianki.settings.SettingsScreenView
import dev.bee.kanjianki.settings.rememberSettingsCopy
import dev.bee.kanjianki.stats.StatsDashboardScreen
import dev.bee.kanjianki.study.StudySessionScreen
import dev.bee.kanjianki.study.rememberStudyCopy
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.HomeNoticePolicy
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.KaniShellHost
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.shell.KaniShell
import dev.bee.kanjianki.shell.LiteralUiTextResolver
import dev.bee.kanjianki.shell.ShellBackAffordanceMode
import dev.bee.kanjianki.shell.ShellRouteContent
import dev.bee.kanjianki.shell.rememberShellCopy
import dev.bee.kanjianki.shell.shellRouteTestTag
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val ANDROID_HOST_TEST_TAG: String = "kani-android-host"
private val ROUTE_PADDING = 16.dp
private val SURFACE_SPACING = 12.dp

/**
 * The thin Android host: the shared shell over the shared feature graph.
 *
 * The Android twin of `DesktopShellScaffold`. Everything the user sees comes from
 * `:feature-shell`, content is assembled by the shared [dev.bee.kanjianki.hostpresentation.KaniRouteLoader]
 * over [AndroidKaniContainer]'s use-cases, and actions run through the shared
 * [KaniShellHost] — so the two hosts render the same shell states from one code path.
 * The one deliberate divergence is [ShellBackAffordanceMode.SYSTEM]: Android has the
 * system back gesture and must not draw the button desktop's window needs.
 *
 * The [capabilities] and [status] the host supplies are Android's — a real AnkiDroid
 * gateway rather than an AnkiConnect handshake — but the loader takes only their
 * portable projection, so nothing here is a second derivation of the presentation.
 */
@Composable
internal fun AndroidShellScaffold(
    host: AndroidShellHost,
) {
    val scope = rememberCoroutineScope()
    val studyRuntime = host.studyRuntime
    var studyRender by remember { mutableStateOf<StudyRouteRender?>(null) }
    val gamesRuntime = host.gamesRuntime
    var gamesRender by remember { mutableStateOf<GamesRender?>(null) }

    val shellHost = remember(host) {
        KaniShellHost<KaniRouteContent>(
            launch = host.launch,
            capabilities = host.capabilities,
            classifyFailure = host::classifyFailure,
            loadRoute = { destination ->
                if (destination is KaniDestination.Study && studyRender == null) {
                    studyRender = studyRuntime.load(host.now())
                }
                if (destination == KaniDestination.Games && gamesRender == null) {
                    gamesRender = gamesRuntime.menu()
                }
                ContentResult.Success(host.load(destination, studyRender, gamesRender))
            },
        )
    }

    var revision by remember { mutableStateOf(0) }
    val shellState = remember(revision) { shellHost.shell }
    val routeState = remember(revision) { shellHost.route(shellState.current) }

    val dispatch: (KaniAction) -> Unit = { action ->
        val listed = routeState.content.valueOrNull?.browse?.rows.orEmpty().map { it.kanji }
        val pending = shellHost.dispatch(action)
        revision++
        if (pending != null) {
            scope.launch {
                when (action) {
                    is KaniAction.Browse -> host.persistBrowseChoice(action, listed)
                    is KaniAction.SaveMnemonic -> host.persistMnemonic(action)
                    is KaniAction.Study -> studyRender = host.driveStudy(action, studyRender)
                    is KaniAction.Game -> gamesRender = host.driveGames(action)
                    is KaniAction.Settings -> host.persistSettings(action)
                    else -> Unit
                }
                shellHost.perform(pending)
                revision++
            }
        }
    }

    LaunchedEffect(shellState.current) {
        dispatch(KaniAction.Lifecycle.Entered)
    }

    KaniTheme(theme = KaniThemeId.fromStorageKey(routeState.content.valueOrNull?.themeChoice?.storageKey)) {
        KaniShell(
            state = shellState,
            resolver = LiteralUiTextResolver,
            effectHandler = host.effectHandler,
            dispatch = dispatch,
            backAffordance = ShellBackAffordanceMode.SYSTEM,
        ) { destination ->
            AndroidRouteBody(
                destination = destination,
                state = routeState,
                capabilities = shellState.capabilities,
                dispatch = dispatch,
            )
        }
    }
}

@Composable
private fun AndroidRouteBody(
    destination: KaniDestination,
    state: RouteState<KaniRouteContent>,
    capabilities: PlatformCapabilities,
    dispatch: (KaniAction) -> Unit,
) {
    ShellRouteContent(
        state = state,
        copy = rememberShellCopy(),
        resolver = LiteralUiTextResolver,
        dispatch = dispatch,
        modifier = Modifier.testTag(shellRouteTestTag(destination)),
    ) { content ->
        when (destination) {
            KaniDestination.Home -> AndroidHomeRoute(content, capabilities, dispatch)
            KaniDestination.FocusQueue -> ScrollColumn {
                FocusQueuePanel(content.home.focus, rememberDashboardCopy(), LiteralUiTextResolver, dispatch)
            }
            is KaniDestination.Browse -> BrowseScreen(
                results = content.browse,
                copy = rememberBrowseCopy(),
                resolver = LiteralUiTextResolver,
                dispatch = dispatch,
                modifier = Modifier.fillMaxSize(),
            )
            is KaniDestination.Detail -> content.detail?.let { detail ->
                ScrollColumn { KanjiDetailScreen(detail = detail, resolver = LiteralUiTextResolver, dispatch = dispatch) }
            }
            KaniDestination.Study -> content.study?.let { session ->
                ScrollColumn {
                    StudySessionScreen(session = session, copy = rememberStudyCopy(), resolver = LiteralUiTextResolver, dispatch = dispatch)
                }
            }
            KaniDestination.Stats -> content.stats?.let { dashboard ->
                ScrollColumn { StatsDashboardScreen(dashboard = dashboard, dispatch = dispatch) }
            }
            KaniDestination.Games -> content.games?.let { screen ->
                ScrollColumn { GamesScreenView(screen = screen, copy = rememberGamesCopy(), dispatch = dispatch) }
            }
            is KaniDestination.Settings -> content.settings?.let { screen ->
                ScrollColumn { SettingsScreenView(screen = screen, copy = rememberSettingsCopy(), dispatch = dispatch) }
            }
            else -> ScrollColumn {
                Text(text = destination.route, style = MaterialTheme.typography.titleLarge)
                Text(text = content.providerMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AndroidHomeRoute(
    content: KaniRouteContent,
    capabilities: PlatformCapabilities,
    dispatch: (KaniAction) -> Unit,
) {
    val homeCopy = rememberHomeCopy()
    val dashboardCopy = rememberDashboardCopy()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(ROUTE_PADDING).testTag(ANDROID_HOST_TEST_TAG),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SURFACE_SPACING),
    ) {
        ProviderStatusRow(readiness = content.home.readiness, copy = homeCopy)
        OnboardingCard(
            plan = content.onboarding,
            copy = homeCopy,
            resolver = LiteralUiTextResolver,
            dispatch = dispatch,
            counted = rememberHomeCountedCopy(content.onboarding),
            enabled = !content.home.syncing,
        )
        if (content.home.syncing) {
            SyncProgressCard(copy = homeCopy, dispatch = dispatch)
        }
        if (content.home.repairedKanjiCount > 0) {
            RepairedHandoffCard(count = content.home.repairedKanjiCount, copy = homeCopy, dispatch = dispatch)
        }
        for (notice in HomeNoticePolicy.notices(capabilities)) {
            HomeNoticeCard(notice = notice, copy = dashboardCopy)
        }
        HomePrimaryAction(home = content.home, copy = dashboardCopy, dispatch = dispatch)
        HomeMetricRow(metrics = content.home.metrics, copy = dashboardCopy, resolver = LiteralUiTextResolver, dispatch = dispatch)
        content.home.todayPlan?.let { plan ->
            HomeTodayCard(plan = plan, copy = dashboardCopy, resolver = LiteralUiTextResolver, dispatch = dispatch)
        }
        HomeDeckOverview(rows = content.home.deckOverview, copy = dashboardCopy, resolver = LiteralUiTextResolver)
        FocusQueuePanel(queue = content.home.focus, copy = dashboardCopy, resolver = LiteralUiTextResolver, dispatch = dispatch)
    }
}

@Composable
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ROUTE_PADDING),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SURFACE_SPACING),
    ) {
        content()
    }
}
