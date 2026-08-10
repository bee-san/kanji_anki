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
    /**
     * Actions that must be present for Kani to operate at all.
     *
     * `getMediaDirPath` is here as the profile-identity probe. It is not an
     * obvious choice, so it is worth recording why it is the right one:
     * AnkiConnect has no action that names the loaded profile. `getProfiles`
     * lists every profile on the machine regardless of which is open, so it
     * cannot answer "which collection am I bound to". `getMediaDirPath` returns
     * the *loaded* profile's media directory, which both names the profile and
     * fails when no collection is open, because AnkiConnect resolves it through
     * the open collection. Verified against a real pinned host by
     * `ci/scripts/run_anki_desktop_fixture.sh`: launching the fixture on a
     * second profile moves the answer with it.
     */
    val required: Set<String> = linkedSetOf(
        "requestPermission",
        "version",
        "apiReflect",
        "getMediaDirPath",
        "modelNamesAndIds",
        "modelFieldNames",
        "deckNamesAndIds",
        "findNotes",
        "notesInfo",
        "findCards",
        "cardsInfo",
        "multi",
    )

    /**
     * Actions used when available but not required for the core read path.
     *
     * `modelStyling` and `getDeckConfig` are reads, and they are here for one
     * reason: they are the only allowlisted way to *prove* an existing
     * `Kani Missing Kanji` model and `Kani::Missing Kanji` deck are the ones Kani
     * would have created. Without them the additive writer cannot distinguish its
     * own destination from a same-named model or a filtered deck the user built,
     * and it must fall back to CSV rather than write into something it cannot
     * identify — see [AnkiConnectMissingKanjiWriter].
     */
    val optional: Set<String> = linkedSetOf(
        "modelTemplates",
        "modelStyling",
        "modelFieldsOnTemplates",
        "getDeckConfig",
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
