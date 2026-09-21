package dev.bee.kanjianki.presentation

/**
 * Something that must happen exactly once, in the host, outside the state tree.
 *
 * Snackbars, dialogs, opening a URL, handing a file to the OS. These are not
 * state — re-rendering a snackbar shows it twice, and persisting one shows it
 * again after a restart. So they are queued, delivered, and explicitly consumed
 * via [KaniAction.Consume.Effect].
 *
 * The [id] is monotonic per session and is the whole reason at-most-once works: a
 * host that redelivers, or a recomposition that re-reads the queue, acknowledges
 * an id that is already gone and nothing happens twice.
 */
data class PendingEffect(
    val id: Long,
    val effect: KaniEffect,
) {
    init {
        require(id > 0L) { "effect id must be positive" }
    }
}

sealed interface KaniEffect {
    /** Transient confirmation or error copy, optionally with one action. */
    data class ShowMessage(
        val message: UiText,
        val actionLabel: UiText? = null,
        val action: KaniAction? = null,
        val isError: Boolean = false,
    ) : KaniEffect {
        init {
            require((actionLabel == null) == (action == null)) {
                "a message action needs both a label and an action"
            }
        }
    }

    /**
     * A blocking confirmation the user must answer.
     *
     * [confirm] is carried as an action rather than a callback so a test can
     * assert *what* confirming would do without performing it — which matters
     * most for the writes this gates (tag write-back, restore).
     */
    data class Confirm(
        val title: UiText,
        val body: UiText,
        val confirmLabel: UiText,
        val dismissLabel: UiText,
        val confirm: KaniAction,
        val isDestructive: Boolean = false,
    ) : KaniEffect

    /** Hand a URL to the host's browser. */
    data class OpenUrl(val url: String) : KaniEffect {
        init {
            require(url.isNotBlank()) { "open-url effect needs a url" }
        }
    }

    /** Copy text to the host clipboard, e.g. the repaired-cards Anki search. */
    data class CopyToClipboard(
        val text: String,
        val confirmation: UiText = UiText.EMPTY,
    ) : KaniEffect

    /**
     * Ask the host to pick a file for one of the [FilePurpose] flows.
     *
     * The result comes back as an action, not a return value: on Android the
     * picker is a separate activity result and the process may be recreated in
     * between, so a suspending call would be a lie.
     */
    data class PickFile(
        val purpose: FilePurpose,
        val suggestedName: String = "",
    ) : KaniEffect

    /** Move focus, for keyboard-driven desktop navigation and accessibility. */
    data class RequestFocus(val target: String) : KaniEffect {
        init {
            require(target.isNotBlank()) { "focus effect needs a target" }
        }
    }

    enum class FilePurpose {
        BACKUP_EXPORT,
        BACKUP_RESTORE,
        MISSING_KANJI_CSV_EXPORT,
    }
}

/**
 * The queue of effects awaiting delivery, oldest first.
 *
 * Immutable: enqueueing and consuming return a new queue, so a reducer stays a
 * pure function of its inputs and a test can hold both the before and after.
 */
data class EffectQueue(
    val pending: List<PendingEffect> = emptyList(),
    private val nextId: Long = 1L,
) {
    val head: PendingEffect?
        get() = pending.firstOrNull()

    val isEmpty: Boolean
        get() = pending.isEmpty()

    fun enqueue(effect: KaniEffect): EffectQueue =
        EffectQueue(
            pending = pending + PendingEffect(nextId, effect),
            nextId = nextId + 1L,
        )

    /**
     * Drops the effect with [id], if it is still queued.
     *
     * An unknown id is deliberately not an error. A host that acknowledges twice,
     * or acknowledges across a state reset, is the normal case this design is
     * built to absorb; failing there would turn a benign duplicate into a crash.
     */
    fun consume(id: Long): EffectQueue =
        EffectQueue(
            pending = pending.filterNot { it.id == id },
            nextId = nextId,
        )

    /** Clears the queue without delivering, for a route the user left. */
    fun cleared(): EffectQueue = EffectQueue(nextId = nextId)
}
