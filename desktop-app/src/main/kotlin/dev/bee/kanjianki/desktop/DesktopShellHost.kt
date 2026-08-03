package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.KaniShellHost
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.syncapi.CollectionFailure

/**
 * The desktop host's presentation state.
 *
 * The reducer-driving machinery is the shared [KaniShellHost]; this factory only
 * supplies the desktop-specific failure classification. A [CollectionFailure] already
 * knows what went wrong, so its kind is carried across rather than flattened to
 * [PresentationFailure.Kind.UNKNOWN] — which makes the retry button honest, since
 * `UNKNOWN` is retryable by design and offering "try again" for an unopened profile or
 * a too-old AnkiConnect sends the user in a circle. Only the kind crosses; the
 * exception's own message stays in the failure's diagnostic field.
 */
internal typealias DesktopShellHost = KaniShellHost<DesktopRouteContent>

internal fun DesktopShellHost(
    launch: KaniLaunchRequest? = null,
    restored: KaniDestination? = null,
    capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    loadRoute: suspend (KaniDestination) -> ContentResult<DesktopRouteContent>,
): DesktopShellHost = KaniShellHost(
    launch = launch,
    restored = restored,
    capabilities = capabilities,
    classifyFailure = { failure ->
        (failure as? CollectionFailure)
            ?.let { DesktopHomeModels.failureKind(it.kind) }
            ?: PresentationFailure.Kind.UNKNOWN
    },
    loadRoute = loadRoute,
)

/**
 * What a desktop route currently has to show.
 *
 * Still one type for every route, and still for the reason it started as: the routes
 * past Home are placeholders until Goals 195+ replace them one at a time, and a
 * per-route sealed hierarchy would be six empty branches. What has changed is that
 * the Home fields are no longer placeholder facts — [home], [onboarding], and
 * [browse] are the same portable models `:feature-home` renders on Android, so the
 * two hosts show Home from one set of types.
 *
 * [studyItemCount] and [dueCount] stay because the placeholder routes still report
 * them, and because they are the cheapest evidence that a route was loaded through
 * the real startup lifecycle rather than from a stub.
 *
 * The Home models carry defaults so a route that is not Home — and a test that only
 * cares about load state — can construct this without assembling a dashboard.
 *
 * [detail] is `null` off the detail route, the same way [browse] is empty off Browse:
 * one snapshot type serves every route, and a route only fills the fields it draws.
 *
 * [providerMessage] is the provider's own status line as plain copy rather than the
 * `DesktopProviderStatus` it came from. The stored content only ever displays that one
 * string (the placeholder routes' status line); onboarding and sync-availability read
 * the *fresh* probe, not this snapshot. Holding the projection rather than the
 * AnkiConnect probe object is what lets this type become host-neutral in Goal 199 —
 * Android has no `DesktopProviderStatus` to give it.
 */
internal data class DesktopRouteContent(
    val providerMessage: String,
    val studyItemCount: Int,
    val dueCount: Int,
    val themeChoice: KaniThemeChoice,
    val home: HomeDashboard = HomeDashboard(),
    val onboarding: OnboardingPlan = OnboardingPlan(
        step = OnboardingStep.CONNECT_PROVIDER,
        binding = CollectionBinding(noteType = ""),
    ),
    val browse: BrowseResults = BrowseResults(),
    val detail: KanjiDetail? = null,
    val study: dev.bee.kanjianki.presentation.StudySession? = null,
    val stats: dev.bee.kanjianki.presentation.StatsDashboard? = null,
    val games: dev.bee.kanjianki.presentation.GamesScreen? = null,
    val settings: dev.bee.kanjianki.presentation.SettingsScreen? = null,
)
