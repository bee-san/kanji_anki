package dev.bee.kanjianki.presentation

/**
 * The load state of one piece of screen content.
 *
 * Android screen models currently spell this as a `data class` with
 * `isLoading`/`error`/`value` fields, which admits states the product never
 * has — loading *and* failed, or loaded with a stale error still showing. The
 * four cases here are exclusive, so a screen cannot render a contradiction.
 *
 * [Refreshing] is distinct from [Loading] because they look different: a first
 * load shows a skeleton and a refresh keeps the previous content visible with a
 * progress hint. Collapsing them makes every pull-to-refresh flash empty.
 */
sealed interface Loadable<out T> {
    /** Nothing requested yet. Distinct from [Loading]: no spinner belongs here. */
    data object Idle : Loadable<Nothing>

    data object Loading : Loadable<Nothing>

    data class Refreshing<out T>(val previous: T) : Loadable<T>

    data class Loaded<out T>(val value: T) : Loadable<T>

    data class Failed(val failure: PresentationFailure) : Loadable<Nothing>

    /** The most recent value if one exists, whether or not a load is in flight. */
    val valueOrNull: T?
        get() = when (this) {
            is Loaded -> value
            is Refreshing -> previous
            Idle, Loading, is Failed -> null
        }

    /** True while work is in flight, for either kind of load. */
    val isBusy: Boolean
        get() = this is Loading || this is Refreshing
}

/**
 * Starts a load, keeping any content already on screen.
 *
 * This is the transition a screen model wants nine times out of ten, and writing
 * it by hand is where "refresh clears the list" bugs come from.
 */
fun <T> Loadable<T>.reloading(): Loadable<T> =
    valueOrNull?.let { Loadable.Refreshing(it) } ?: Loadable.Loading

/**
 * A failure in terms a screen can render, rather than a thrown exception.
 *
 * Common presentation code cannot catch platform exception types, and should not
 * be deciding what a stack trace means. Hosts map their own throwables into a
 * [kind] plus already-resolved [message] copy; the reducer only chooses layout
 * and whether to offer a retry.
 */
data class PresentationFailure(
    val kind: Kind,
    val message: UiText = UiText.EMPTY,
    /**
     * Opaque diagnostic detail for logs only.
     *
     * Never shown to the user and never matched on: it exists so a host can
     * carry "what actually happened" alongside displayable copy without
     * tempting common code into parsing it.
     */
    val diagnostic: String = "",
) {
    /** True when offering the user a retry button is honest. */
    val isRetryable: Boolean
        get() = kind.retryable

    enum class Kind(val retryable: Boolean) {
        /** The collection source is not reachable right now. */
        PROVIDER_UNAVAILABLE(true),

        /** The provider needs credentials or a permission the user must grant. */
        PROVIDER_AUTH_REQUIRED(false),

        /** Configuration is wrong; retrying the same request cannot help. */
        CONFIGURATION(false),

        /**
         * The host cannot do this at all — no tray, no closed-app scheduling.
         *
         * Separate from [CONFIGURATION] because the remedy differs: a missing
         * capability is explained, not fixed in Settings.
         */
        CAPABILITY_MISSING(false),

        /** Transient: a timeout, a busy database, a lost connection. */
        TRANSIENT(true),

        /** The user or the host cancelled the work. */
        CANCELLED(true),

        /** The write lost a compare-and-set race and the caller should re-read. */
        CONFLICT(true),

        /** Anything unclassified. Retryable, because refusing a retry on an unknown cause strands the user. */
        UNKNOWN(true),
    }
}
