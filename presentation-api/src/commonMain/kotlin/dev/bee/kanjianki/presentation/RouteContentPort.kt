package dev.bee.kanjianki.presentation

/**
 * How a route gets its content, without naming who produces it.
 *
 * Each host implements this over its own `:application` use cases —
 * `HomeUseCases.loadRoute`, `StudyUseCases.loadQueue`, and so on. Those live in
 * JVM modules this one cannot see, which is the point: `:presentation-api` has
 * zero project dependencies, so the only way content crosses into portable code
 * is through a port like this.
 *
 * Failures come back as a [PresentationFailure], not a thrown exception. Common
 * code cannot catch a platform exception type and has no business deciding what
 * one means; mapping is the adapter's job, and [ContentResult] makes the two
 * outcomes equally explicit.
 */
fun interface RouteContentPort<out T> {
    suspend fun load(): ContentResult<T>
}

/**
 * The outcome of a port call.
 *
 * A sealed pair rather than `T?`, because "no content" and "failed to load
 * content" want different screens, and a nullable return makes them the same
 * value.
 */
sealed interface ContentResult<out T> {
    data class Success<out T>(val value: T) : ContentResult<T>

    data class Failure(val failure: PresentationFailure) : ContentResult<Nothing>
}

/**
 * Folds a port result into a route's state.
 *
 * The one place success and failure become state, so a screen model cannot record
 * a success that clears a real failure, or a failure that discards content the
 * user can still usefully see.
 */
fun <T> RouteState<T>.applying(result: ContentResult<T>): RouteState<T> = when (result) {
    is ContentResult.Success -> withContent(result.value)
    is ContentResult.Failure -> withFailure(result.failure)
}

/**
 * A command a route sends back to `:application`, and the answer.
 *
 * Kani's writes are not "set this field": committing a review is a token-first,
 * revision-CAS transaction, and a tag write-back is manual-confirm-only. The
 * presentation layer must be able to *ask* for those and render the outcome
 * without being able to perform or reinterpret them — so a command port returns
 * an [ActionOutcome], not a domain result the UI could second-guess.
 */
fun interface RouteCommandPort<in C> {
    suspend fun submit(command: C): ActionOutcome
}

/**
 * What happened to a submitted command, in terms a screen can render.
 *
 * [Applied] and [Rejected] are separate from [Failed] because they are not
 * errors: a review commit that lost a revision CAS race, or a confirmation the
 * user declined, is the system working. Collapsing them into a failure produces
 * error copy for a non-error.
 */
sealed interface ActionOutcome {
    /** The command took effect. UI/session state may advance. */
    data object Applied : ActionOutcome

    /**
     * The command was refused for a stated reason, and did not take effect.
     *
     * The caller should re-read rather than retry blindly — a stale-token commit
     * is the canonical case.
     */
    data class Rejected(val reason: UiText) : ActionOutcome

    /** The command did not complete. Nothing was applied. */
    data class Failed(val failure: PresentationFailure) : ActionOutcome
}
