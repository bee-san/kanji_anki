package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.RouteIntent
import dev.bee.kanjianki.presentation.RouteReducer
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.ShellReducer
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.applying

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
     */
    suspend fun perform(pending: PendingLoad) {
        val result = try {
            loadRoute(pending.destination)
        } catch (failure: Throwable) {
            ContentResult.Failure(
                PresentationFailure(
                    kind = PresentationFailure.Kind.UNKNOWN,
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
 * One type for every route because the feature routes are placeholders until Goals
 * 194+ replace them one at a time. It carries the two facts the placeholder screens
 * actually report — what Anki said, and how much of the collection is admitted — so
 * "the composition root reaches a provider-status placeholder through the real
 * startup lifecycle" is something the screen demonstrates rather than asserts.
 */
internal data class DesktopRouteContent(
    val provider: DesktopProviderStatus,
    val studyItemCount: Int,
    val dueCount: Int,
    val themeChoice: KaniThemeChoice,
)
