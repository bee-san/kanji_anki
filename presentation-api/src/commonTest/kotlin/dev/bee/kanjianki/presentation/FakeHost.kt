package dev.bee.kanjianki.presentation

/**
 * A host with no platform underneath it.
 *
 * This is the thing Goal 192 is actually for: if the portable contracts are
 * complete, then driving every route — navigating, loading, failing, retrying,
 * showing a dialog, gating on a capability — needs no Activity, no window, no
 * dispatcher, and no clock. Anything this fake cannot express is a gap in the
 * contracts, not a gap in the fake.
 *
 * Content is `String` here on purpose. The real snapshots live in `:application`,
 * which this module cannot see; a host that only ever hands the state tree an
 * opaque `T` is exactly the arrangement being tested.
 */
class FakeHost(
    capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    private val content: Map<String, ContentResult<String>> = emptyMap(),
    /**
     * The destination a restored session resumes on, with no history beneath it.
     *
     * Both hosts have this case — Android from saved instance state, desktop from
     * a session file — and it is the one where back has no stack to pop, so the
     * fake has to be able to start there rather than always from Home.
     */
    restoredAt: KaniDestination? = null,
) : ActionDispatcher {
    var shell: ShellState = ShellState(
        backStack = restoredAt?.let(::listOf) ?: listOf(KaniDestination.Home),
        capabilities = capabilities,
    )
        private set

    /** One state per visited destination, keyed by route, as a real host would keep it. */
    private val routes = mutableMapOf<String, RouteState<String>>()

    /** Every effect this host was asked to perform, in delivery order. */
    val delivered = mutableListOf<KaniEffect>()

    /** How many times each route was asked to load, to catch redundant reloads. */
    val loads = mutableMapOf<String, Int>()

    val current: RouteState<String>
        get() = route(shell.current)

    fun route(destination: KaniDestination): RouteState<String> =
        routes.getOrPut(destination.route) { RouteState(destination) }

    override fun dispatch(action: KaniAction) {
        shell = ShellReducer.reduce(shell, action)
        val destination = shell.current
        val (next, intent) = RouteReducer.reduce(route(destination), action)
        routes[destination.route] = next
        if (intent == RouteIntent.Load) {
            load(destination)
        }
    }

    /**
     * Opens a destination and lets it settle, the way a real host does.
     *
     * Navigating and then entering are two actions because they are two events;
     * bundling them would hide the case where a route is entered without being
     * navigated to (a restored session).
     */
    fun open(destination: KaniDestination) {
        dispatch(KaniAction.Navigation.Open(destination))
        dispatch(KaniAction.Lifecycle.Entered)
    }

    /** Delivers the oldest pending effect and acknowledges it. */
    fun deliverOneEffect(): KaniEffect? {
        val shellHead = shell.effects.head
        if (shellHead != null) {
            delivered += shellHead.effect
            shell = ShellReducer.reduce(shell, KaniAction.Consume.Effect(shellHead.id))
            return shellHead.effect
        }
        val routeHead = current.effects.head ?: return null
        delivered += routeHead.effect
        routes[shell.current.route] = current.consumeEffect(routeHead.id)
        return routeHead.effect
    }

    fun deliverAllEffects() {
        while (deliverOneEffect() != null) {
            // Drain; each delivery is acknowledged, so this terminates.
        }
    }

    fun enqueue(effect: KaniEffect) {
        routes[shell.current.route] = current.enqueue(effect)
    }

    fun gate(capability: PlatformCapability, action: KaniAction, explanation: UiText) {
        val (gated, allowed) = ShellReducer.gate(shell, capability, action, explanation)
        shell = gated
        allowed?.let(::dispatch)
    }

    /**
     * Resolves content through a port, exactly as the real hosts will.
     *
     * Synchronous here because the port's suspension is the adapter's concern; the
     * state transitions being tested are the same either way.
     */
    private fun load(destination: KaniDestination) {
        loads[destination.route] = (loads[destination.route] ?: 0) + 1
        val result = content[destination.route] ?: ContentResult.Success(destination.route)
        routes[destination.route] = route(destination).applying(result)
    }
}

/** Resolves [UiText] the way a host's resource table would, without one. */
class FakeUiTextResolver(
    private val strings: Map<String, String> = emptyMap(),
) : UiTextResolver {
    override fun resolve(text: UiText): String = when (text) {
        is UiText.Literal -> text.text
        is UiText.Key -> format(text.key, text.arguments)
        is UiText.Quantity -> format(
            key = "${text.key}${if (text.count == 1) ".one" else ".other"}",
            arguments = text.arguments,
        ).replace("%d", text.count.toString())
    }

    private fun format(key: String, arguments: List<UiText>): String {
        val template = strings[key] ?: key
        return arguments.foldIndexed(template) { index, accumulator, argument ->
            accumulator.replace("{$index}", resolve(argument))
        }
    }
}
