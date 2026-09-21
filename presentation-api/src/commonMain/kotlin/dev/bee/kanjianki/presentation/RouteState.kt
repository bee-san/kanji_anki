package dev.bee.kanjianki.presentation

/**
 * Everything one route needs to render, and nothing that renders it.
 *
 * [content] is the authoritative snapshot the host's `:application` layer
 * produced — a Study queue, a Home dashboard — held opaquely here. This module
 * cannot name those types (it has no project dependencies, by design), and it
 * should not: a reducer that understood the Study snapshot would be tempted to
 * derive from it, and session selection, reveal progression, commit, recovery,
 * and undo all belong to `:application`. So `T` stays a type parameter.
 *
 * What this class *does* own is the ephemeral part: what is loading, what failed,
 * which effects are waiting, and where the user is. That is the whole mandate of
 * common presentation state.
 */
data class RouteState<out T>(
    val destination: KaniDestination,
    val content: Loadable<T> = Loadable.Idle,
    val effects: EffectQueue = EffectQueue(),
    /**
     * A failure shown in place, as opposed to one delivered as a message effect.
     *
     * Both exist because they are different products: a full-screen "cannot
     * reach AnkiDroid" with a retry button is state that survives
     * recomposition, and a "sync finished" snackbar is a one-shot. Storing the
     * transient one would show it forever; queueing the persistent one would
     * lose it.
     */
    val failure: PresentationFailure? = null,
) {
    /** True when a blocking spinner is the right thing to show. */
    val isInitialLoad: Boolean
        get() = content is Loadable.Loading

    val isBusy: Boolean
        get() = content.isBusy

    fun withContent(value: @UnsafeVariance T): RouteState<T> =
        copy(content = Loadable.Loaded(value), failure = null)

    /**
     * Records a failure without discarding content that is still on screen.
     *
     * A refresh that fails should leave the last good list visible with an error
     * shown; blanking the screen loses information the user already had.
     */
    fun withFailure(failure: PresentationFailure): RouteState<T> =
        copy(
            content = content.valueOrNull?.let { Loadable.Loaded(it) }
                ?: Loadable.Failed(failure),
            failure = failure,
        )

    fun loading(): RouteState<T> = copy(content = content.reloading(), failure = null)

    fun enqueue(effect: KaniEffect): RouteState<T> = copy(effects = effects.enqueue(effect))

    fun consumeEffect(id: Long): RouteState<T> = copy(effects = effects.consume(id))

    fun dismissFailure(): RouteState<T> = copy(failure = null)
}

/**
 * The shell around whichever route is showing.
 *
 * Separate from [RouteState] because its lifetime is different: the selected tab,
 * the back stack, and the capability set outlive any one route's content, and
 * folding them into the route would reload them on every navigation.
 */
data class ShellState(
    val backStack: List<KaniDestination> = listOf(KaniDestination.Home),
    val capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    val studyBadgeCount: Int = 0,
    val effects: EffectQueue = EffectQueue(),
) {
    init {
        require(backStack.isNotEmpty()) { "the back stack always has a current destination" }
        require(studyBadgeCount >= 0) { "study badge count must not be negative" }
    }

    val current: KaniDestination
        get() = backStack.last()

    /**
     * The tab to highlight.
     *
     * Read off the destination rather than stored, because storing it allows the
     * highlighted tab and the visible screen to disagree — which is exactly the
     * bug Android's `isSettingsRoute` check in the nav bar works around.
     */
    val selectedTab: KaniTab
        get() = current.tab

    val canGoBack: Boolean
        get() = backStack.size > 1 || current.parent != null
}
