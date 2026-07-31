package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json

/**
 * Convenience builders for the AnkiConnect read requests, so the params object
 * shapes live next to the [AnkiConnectReads] parsers that consume the results.
 * Every builder routes through [AnkiConnectEnvelope.request], which pins
 * version 6, attaches the key only when present, and allowlist-checks the
 * action.
 */
object AnkiConnectRequests {
    /**
     * Hard ceiling on notes in one `addNotes` action. Anki applies the whole
     * action inside one transaction-ish step but reports per-note results, so an
     * unbounded batch would make a partial failure both more likely and more
     * expensive to reconcile — see [AnkiConnectMissingKanjiWriter].
     */
    const val MAX_ADD_NOTES = 100

    /** One card template as `createModel` names its sides. */
    data class CardTemplate(val name: String, val front: String, val back: String)

    /** One note to create, as `addNotes` shapes it. */
    data class NewNote(
        val deckName: String,
        val modelName: String,
        /** Field name → value, for the fields of [modelName]. */
        val fields: Map<String, String>,
        val tags: List<String>,
    )

    fun modelNamesAndIds(apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request("modelNamesAndIds", apiKey = apiKey)

    fun deckNamesAndIds(apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request("deckNamesAndIds", apiKey = apiKey)

    /**
     * Asks which profile Anki currently has open. This is a read, but it is a
     * read Kani makes for identity rather than content: the loopback endpoint is
     * the same for every profile on the machine, so the profile name is what
     * distinguishes one collection source from another.
     */
    fun getActiveProfile(apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request("getActiveProfile", apiKey = apiKey)

    fun modelFieldNames(modelName: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "modelFieldNames",
            AnkiConnectJson.obj("modelName" to AnkiConnectJson.str(modelName)),
            apiKey,
        )

    fun findNotes(query: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "findNotes",
            AnkiConnectJson.obj("query" to AnkiConnectJson.str(query)),
            apiKey,
        )

    fun findCards(query: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "findCards",
            AnkiConnectJson.obj("query" to AnkiConnectJson.str(query)),
            apiKey,
        )

    fun notesInfo(noteIds: List<Long>, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "notesInfo",
            AnkiConnectJson.obj("notes" to idArray(noteIds)),
            apiKey,
        )

    fun cardsInfo(cardIds: List<Long>, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "cardsInfo",
            AnkiConnectJson.obj("cards" to idArray(cardIds)),
            apiKey,
        )

    /**
     * Opens Anki's card browser on [query]. This is a UI handoff, not a read: it
     * returns the matched card ids but Kani ignores them, because the point is
     * that the *user* looks at their own collection in Anki's own browser. The
     * query must be the exact search Kani would show the user, so what they see
     * is what Kani claimed.
     */
    fun guiBrowse(query: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "guiBrowse",
            AnkiConnectJson.obj("query" to AnkiConnectJson.str(query)),
            apiKey,
        )

    /**
     * Fetches one media file from the collection's media directory by name.
     * AnkiConnect answers with base64, so a large file costs roughly 4/3 its size
     * on the wire and again in memory — the caller must bound both the name and
     * the size before asking ([AnkiConnectMediaReader]).
     */
    fun retrieveMediaFile(filename: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "retrieveMediaFile",
            AnkiConnectJson.obj("filename" to AnkiConnectJson.str(filename)),
            apiKey,
        )

    /**
     * Adds [tag] to one note. Kani only ever writes its own tags, and always one
     * note per action, so a partial failure inside a `multi` batch is attributable
     * to a specific note — see [AnkiConnectTagWriter].
     */
    fun addTags(noteId: Long, tag: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request("addTags", addTagsParams(noteId, tag), apiKey)

    /**
     * One bounded `multi` of one-note `addTags` actions, in [noteIds] order. The
     * response array is read position-for-position against that order.
     */
    fun addTagsMulti(
        noteIds: List<Long>,
        tag: String,
        apiKey: String? = null,
    ): AnkiConnectEnvelope.Request {
        require(noteIds.isNotEmpty()) { "multi request needs at least one action" }
        require(noteIds.size <= AnkiConnectReadPlanner.MAX_MULTI_ACTIONS) {
            "multi request exceeds the ${AnkiConnectReadPlanner.MAX_MULTI_ACTIONS}-action cap"
        }
        return AnkiConnectEnvelope.multiRequest(
            noteIds.map { noteId -> "addTags" to addTagsParams(noteId, tag) },
            apiKey,
        )
    }

    /**
     * Asks for [modelName]'s card templates: `{templateName: {Front, Back}}`.
     * A read, used only to prove an existing model's template contract is the one
     * Kani would have created.
     */
    fun modelTemplates(modelName: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "modelTemplates",
            AnkiConnectJson.obj("modelName" to AnkiConnectJson.str(modelName)),
            apiKey,
        )

    /** Asks for [modelName]'s CSS, for the same shape proof as [modelTemplates]. */
    fun modelStyling(modelName: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "modelStyling",
            AnkiConnectJson.obj("modelName" to AnkiConnectJson.str(modelName)),
            apiKey,
        )

    /**
     * Asks for [deckName]'s options group. This is the only standard-action way to
     * tell an ordinary deck from a filtered one: AnkiConnect's `deckNamesAndIds`
     * reports no such flag, and `getDeckConfig` fails on a filtered deck because a
     * filtered deck has no options group. Kani writes nothing here — the config is
     * read and discarded.
     */
    fun getDeckConfig(deckName: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "getDeckConfig",
            AnkiConnectJson.obj("deck" to AnkiConnectJson.str(deckName)),
            apiKey,
        )

    /**
     * Creates a deck by name. Additive and idempotent in Anki: an existing deck of
     * the same name is returned rather than replaced, and `::` creates the parent
     * chain. Kani never sets deck options.
     */
    fun createDeck(deckName: String, apiKey: String? = null): AnkiConnectEnvelope.Request =
        AnkiConnectEnvelope.request(
            "createDeck",
            AnkiConnectJson.obj("deck" to AnkiConnectJson.str(deckName)),
            apiKey,
        )

    /**
     * Creates Kani's own note type. Only ever called after discovery proved no
     * model of this name exists; a name collision is never rewritten.
     */
    fun createModel(
        modelName: String,
        fieldNames: List<String>,
        css: String,
        templates: List<CardTemplate>,
        apiKey: String? = null,
    ): AnkiConnectEnvelope.Request {
        require(fieldNames.isNotEmpty()) { "a model needs at least one field" }
        require(templates.isNotEmpty()) { "a model needs at least one card template" }
        return AnkiConnectEnvelope.request(
            "createModel",
            AnkiConnectJson.obj(
                "modelName" to AnkiConnectJson.str(modelName),
                "inOrderFields" to AnkiConnectJson.arr(fieldNames.map(AnkiConnectJson::str)),
                "css" to AnkiConnectJson.str(css),
                // Kani's note type is a plain forward model; `isCloze` false is
                // explicit rather than defaulted so the shape proof can rely on it.
                "isCloze" to AnkiConnectJson.bool(false),
                "cardTemplates" to AnkiConnectJson.arr(
                    templates.map { template ->
                        AnkiConnectJson.obj(
                            "Name" to AnkiConnectJson.str(template.name),
                            "Front" to AnkiConnectJson.str(template.front),
                            "Back" to AnkiConnectJson.str(template.back),
                        )
                    },
                ),
            ),
            apiKey,
        )
    }

    /**
     * Adds up to [MAX_ADD_NOTES] notes in one action. `allowDuplicate` is left
     * false and `duplicateScope` is deliberately unset: Kani's own idempotence
     * comes from reconciling by `SourceId`, and asking Anki to allow duplicates
     * would defeat it.
     */
    fun addNotes(notes: List<NewNote>, apiKey: String? = null): AnkiConnectEnvelope.Request {
        require(notes.isNotEmpty()) { "addNotes needs at least one note" }
        require(notes.size <= MAX_ADD_NOTES) { "addNotes exceeds the $MAX_ADD_NOTES-note cap" }
        return AnkiConnectEnvelope.request(
            "addNotes",
            AnkiConnectJson.obj(
                "notes" to AnkiConnectJson.arr(
                    notes.map { note ->
                        AnkiConnectJson.obj(
                            "deckName" to AnkiConnectJson.str(note.deckName),
                            "modelName" to AnkiConnectJson.str(note.modelName),
                            "fields" to AnkiConnectJson.obj(
                                *note.fields
                                    .map { (name, value) -> name to AnkiConnectJson.str(value) }
                                    .toTypedArray(),
                            ),
                            "tags" to AnkiConnectJson.arr(note.tags.map(AnkiConnectJson::str)),
                            "options" to AnkiConnectJson.obj(
                                "allowDuplicate" to AnkiConnectJson.bool(false),
                            ),
                        )
                    },
                ),
            ),
            apiKey,
        )
    }

    /**
     * One bounded `multi` request that asks for several models' field names in a
     * single round trip. The group must already be sized by
     * [AnkiConnectReadPlanner.multiGroups]: `multi` shares one response body and
     * one deadline across every nested action.
     */
    fun modelFieldNamesMulti(
        modelNames: List<String>,
        apiKey: String? = null,
    ): AnkiConnectEnvelope.Request {
        require(modelNames.isNotEmpty()) { "multi request needs at least one action" }
        require(modelNames.size <= AnkiConnectReadPlanner.MAX_MULTI_ACTIONS) {
            "multi request exceeds the ${AnkiConnectReadPlanner.MAX_MULTI_ACTIONS}-action cap"
        }
        return AnkiConnectEnvelope.multiRequest(
            modelNames.map { name ->
                "modelFieldNames" to AnkiConnectJson.obj(
                    "modelName" to AnkiConnectJson.str(name),
                )
            },
            apiKey,
        )
    }

    private fun addTagsParams(noteId: Long, tag: String): Json.Obj = AnkiConnectJson.obj(
        "notes" to idArray(listOf(noteId)),
        "tags" to AnkiConnectJson.str(tag),
    )

    private fun idArray(ids: List<Long>): Json.Arr =
        AnkiConnectJson.arr(ids.map(AnkiConnectJson::num))
}
