package dev.bee.kanjianki.presentation

/**
 * The host-agnostic bridge from actions to state, for any route-content type [T].
 *
 * One reducer pair drives every destination: [ShellReducer] owns the back stack and
 * the shell's effect queue, [RouteReducer] owns each route's load state, and this
 * holds their outputs and performs the [RouteIntent] they return. Both hosts share it
 * — the desktop window and the Android activity differ in how they *render* [T] and in
 * what a load *does*, not in how an action becomes state. A second copy of this logic
 * would be the "second host harness" Goal 193 forbids; making it generic is how the
 * Android host in Goal 199 reuses exactly what desktop already proved.
 *
 * Route states are keyed by [KaniDestination.route] rather than the destination, so
 * `Browse("水")` and `Browse("火")` share one route's load state — one Browse screen
 * whose query changed, which is what the user sees.
 *
 * Loading is a suspending call the caller schedules; nothing here touches a dispatcher
 * or a clock, so the whole class is testable without a window. [classifyFailure] lets a
 * host keep a domain failure's own kind (a provider that is not configured is not
 * retryable) without this module depending on that host's error types.
 */
class KaniShellHost<T>(
    launch: KaniLaunchRequest? = null,
    restored: KaniDestination? = null,
    capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    private val classifyFailure: (Throwable) -> PresentationFailure.Kind = { PresentationFailure.Kind.UNKNOWN },
    private val loadRoute: suspend (KaniDestination) -> ContentResult<T>,
) {
    var shell: ShellState = ShellReducer.launch(launch, restored).copy(capabilities = capabilities)
        private set

    private val routes = LinkedHashMap<String, RouteState<T>>()

    /** The state for [destination], created idle on first sight. */
    fun route(destination: KaniDestination): RouteState<T> =
        routes[destination.route] ?: RouteState(destination)

    /**
     * Applies [action] to the shell and to the visible route, returning the load the
     * caller must perform.
     *
     * Both reducers see every action, because the split is by concern and not by action
     * type: [KaniAction.Lifecycle.Refresh] is a route reload *and* leaves the shell's
     * stack alone, and a navigation is a stack change the route reducer correctly
     * ignores. Routing actions to one reducer by hand is how a host ends up with a
     * screen that never reloads.
     */
    fun dispatch(action: KaniAction): PendingLoad? {
        val before = shell.current
        shell = ShellReducer.reduce(shell, action)

        // Navigation is applied to the route the user was on, not the one they arrived
        // at: `Exited` clears the old route's effects, and a newly revealed route gets
        // its own `Entered` from the caller.
        val target = if (action is KaniAction.Navigation) before else shell.current
        val (next, intent) = RouteReducer.reduce(route(target), action)
        routes[target.route] = next
        return intent?.let { PendingLoad(target) }
    }

    /** Records the outcome of a [PendingLoad] the caller performed. */
    fun apply(destination: KaniDestination, result: ContentResult<T>) {
        routes[destination.route] = route(destination).applying(result)
    }

    /**
     * Performs [pending] and records its outcome.
     *
     * A thrown exception becomes a [PresentationFailure] rather than propagating: a
     * repository that fails is a screen with a retry button, and letting it reach the
     * composition would take the host down with it. The message is deliberately generic
     * and the throwable's text goes to [PresentationFailure.diagnostic], which is
     * logs-only — a SQL error string is not user-facing copy. [classifyFailure] decides
     * the kind, so a host can keep a domain failure's own retryability.
     */
    suspend fun perform(pending: PendingLoad) {
        val result = try {
            loadRoute(pending.destination)
        } catch (failure: Throwable) {
            ContentResult.Failure(
                PresentationFailure(
                    kind = classifyFailure(failure),
                    message = UiText.Literal("Kani could not load this screen."),
                    diagnostic = failure.toString(),
                ),
            )
        }
        apply(pending.destination, result)
    }

    /** A load the host owes a route, returned so the caller chooses the scope. */
    data class PendingLoad(val destination: KaniDestination)
}
