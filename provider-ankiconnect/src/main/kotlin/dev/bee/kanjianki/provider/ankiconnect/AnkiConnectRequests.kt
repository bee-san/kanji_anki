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
