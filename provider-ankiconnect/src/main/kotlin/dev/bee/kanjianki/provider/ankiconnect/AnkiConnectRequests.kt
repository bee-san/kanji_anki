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

    private fun idArray(ids: List<Long>): Json.Arr =
        AnkiConnectJson.arr(ids.map(AnkiConnectJson::num))
}
