package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.RouteIntent
import dev.bee.kanjianki.presentation.RouteReducer
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.ShellReducer
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.applying
import dev.bee.kanjianki.syncapi.CollectionFailure

/**
 * The desktop host's presentation state, and the only place actions become state.
 *
 * Deliberately not a screen model per route. One reducer pair drives every
 * destination, exactly as `:presentation-api` intends: [ShellReducer] owns the back
 * stack and the shell's effect queue, [RouteReducer] owns each route's load state,
 * and this class does nothing but hold their outputs and perform the [RouteIntent]
 * they return. A second place deriving state from a snapshot would be the "second
 * host harness" Goal 193 forbids, and the reason the reducers are pure is so both
 * hosts can share this shape.
 *
 * Route states are keyed by [KaniDestination.route] rather than by the destination
 * itself, so `Browse("水")` and `Browse("火")` share one route's load state — which
 * is what the user sees, one Browse screen whose query changed.
 *
 * Loading is a suspending call the caller schedules; nothing here touches a
 * dispatcher or a clock. That keeps the whole class testable without a window, and
 * it is why the launch state is a parameter rather than read from disk.
 */
internal class DesktopShellHost(
    launch: KaniLaunchRequest? = null,
    restored: KaniDestination? = null,
    capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    private val loadRoute: suspend (KaniDestination) -> ContentResult<DesktopRouteContent>,
) {
    var shell: ShellState = ShellReducer.launch(launch, restored).copy(capabilities = capabilities)
        private set

    private val routes = LinkedHashMap<String, RouteState<DesktopRouteContent>>()

    /** The state for [destination], created idle on first sight. */
    fun route(destination: KaniDestination): RouteState<DesktopRouteContent> =
        routes[destination.route] ?: RouteState(destination)

    /**
     * Applies [action] to the shell and to the visible route, returning the load
     * the caller must perform.
     *
     * Both reducers see every action, because the split is by concern and not by
     * action type: [KaniAction.Lifecycle.Refresh] is a route reload *and* leaves the
     * shell's stack alone, and a navigation is a stack change that the route reducer
     * correctly ignores. Routing actions to one reducer or the other by hand is how
     * a host ends up with a screen that never reloads.
     */
    fun dispatch(action: KaniAction): PendingLoad? {
        val before = shell.current
        shell = ShellReducer.reduce(shell, action)

        // Navigation is applied to the route the user was on, not the one they
        // arrived at: `Exited` clears the old route's effects, and a newly revealed
        // route gets its own `Entered` from the caller.
        val target = if (action is KaniAction.Navigation) before else shell.current
        val (next, intent) = RouteReducer.reduce(route(target), action)
        routes[target.route] = next
        return intent?.let { PendingLoad(target) }
    }

    /** Records the outcome of a [PendingLoad] the caller performed. */
    fun apply(destination: KaniDestination, result: ContentResult<DesktopRouteContent>) {
        routes[destination.route] = route(destination).applying(result)
    }

    /**
     * Performs [pending] and records its outcome.
     *
     * A thrown exception becomes a [PresentationFailure] rather than propagating:
     * a repository that fails is a screen with a retry button, and letting it reach
     * the composition would take the window down with it. The message is
     * deliberately generic and the throwable's text goes to [diagnostic], which is
     * logs-only — a SQL error string is not user-facing copy.
     *
     * A [CollectionFailure] already knows what went wrong, so its kind is carried
     * across rather than flattened to [PresentationFailure.Kind.UNKNOWN]. That is
     * what makes the retry button honest: `UNKNOWN` is retryable by design, and
     * offering "try again" for an unopened profile or a too-old AnkiConnect sends the
     * user in a circle. Only the kind crosses; the exception's own message stays in
     * [diagnostic] because "Sync cancelled." is engine text, not copy.
     */
    suspend fun perform(pending: PendingLoad) {
        val result = try {
            loadRoute(pending.destination)
        } catch (failure: Throwable) {
            val kind = (failure as? CollectionFailure)
                ?.let { DesktopHomeModels.failureKind(it.kind) }
                ?: PresentationFailure.Kind.UNKNOWN
            ContentResult.Failure(
                PresentationFailure(
                    kind = kind,
                    message = UiText.Literal("Kani could not load this screen."),
                    diagnostic = failure.toString(),
                ),
            )
        }
        apply(pending.destination, result)
    }

    /** A load the host owes a route, returned so the caller chooses the scope. */
    internal data class PendingLoad(val destination: KaniDestination)
}

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
 */
internal data class DesktopRouteContent(
    val provider: DesktopProviderStatus,
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
)
