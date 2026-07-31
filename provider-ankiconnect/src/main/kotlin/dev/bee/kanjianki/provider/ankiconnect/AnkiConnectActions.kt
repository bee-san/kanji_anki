package dev.bee.kanjianki.provider.ankiconnect

/**
 * The positive outbound action allowlist for AnkiConnect. Kani may only ever
 * send one of these actions; anything else is refused before a request is
 * built, so a bug or a compromised response can never cause Kani to invoke an
 * unlisted (and possibly destructive) AnkiConnect action. The list is split
 * into the actions strictly required for the handshake/read path and the
 * optional ones used for richer reads and the additive Missing-Kanji write
 * flow. Every action Kani supports is enumerated here exactly once.
 */
object AnkiConnectActions {
    /** Actions that must be present for Kani to operate at all. */
    val required: Set<String> = linkedSetOf(
        "requestPermission",
        "version",
        "apiReflect",
        "getActiveProfile",
        "modelNamesAndIds",
        "modelFieldNames",
        "deckNamesAndIds",
        "findNotes",
        "notesInfo",
        "findCards",
        "cardsInfo",
        "multi",
    )

    /** Actions used when available but not required for the core read path. */
    val optional: Set<String> = linkedSetOf(
        "modelTemplates",
        "modelFieldsOnTemplates",
        "retrieveMediaFile",
        "guiBrowse",
        "addTags",
        "createDeck",
        "createModel",
        "addNotes",
    )

    /** The full outbound allowlist (required ∪ optional). */
    val allowlist: Set<String> = LinkedHashSet<String>(required.size + optional.size).apply {
        addAll(required)
        addAll(optional)
    }

    /** True when [action] may be sent to AnkiConnect. */
    fun isAllowed(action: String): Boolean = action in allowlist

    /**
     * Enforces the allowlist for an outbound [action].
     * @throws IllegalArgumentException if the action is not on the allowlist.
     */
    fun requireAllowed(action: String) {
        require(isAllowed(action)) { "AnkiConnect action is not on the outbound allowlist: $action" }
    }

    /**
     * Given the set of actions an AnkiConnect reported via `apiReflect`, the
     * required actions it is missing. Empty means the server can support Kani.
     */
    fun missingRequired(reportedActions: Collection<String>): Set<String> {
        val reported = reportedActions.toHashSet()
        return required.filterNotTo(LinkedHashSet()) { it in reported }
    }

    /** The optional actions a reporting server supports. */
    fun availableOptional(reportedActions: Collection<String>): Set<String> {
        val reported = reportedActions.toHashSet()
        return optional.filterTo(LinkedHashSet()) { it in reported }
    }
}
