package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json

/**
 * Builds AnkiConnect request envelopes and parses response envelopes for API
 * v6. Every outbound action is checked against [AnkiConnectActions]; the
 * `version` field is always 6; the API `key` is attached only when present and
 * never logged. A `multi` request repeats the version and key inside every
 * nested action and, on the way back, validates every nested response envelope
 * too. Response parsing is fail-closed: a `200 OK` body that is not a
 * well-formed `{result, error}` envelope is a protocol error, not a success.
 */
object AnkiConnectEnvelope {
    const val API_VERSION = 6L

    /** A request an outbound transport will serialize and POST. */
    data class Request(val action: String, val json: String)

    /** The parsed result of one AnkiConnect call. */
    sealed interface Response {
        /** `error` was null; [result] is the raw result value (may be Json.Null). */
        data class Ok(val result: Json) : Response

        /** `error` was a non-null string reported by AnkiConnect. */
        data class Failed(val message: String) : Response

        /** The body was not a valid `{result, error}` envelope. */
        data object ProtocolError : Response
    }

    /**
     * Builds a single-action request envelope. [params] is the action's params
     * object (omit or pass an empty object for paramless actions). [apiKey] is
     * attached only when non-null.
     * @throws IllegalArgumentException if [action] is not on the allowlist.
     */
    fun request(action: String, params: Json.Obj? = null, apiKey: String? = null): Request {
        AnkiConnectActions.requireAllowed(action)
        val fields = LinkedHashMap<String, Json>()
        fields["action"] = AnkiConnectJson.str(action)
        fields["version"] = AnkiConnectJson.num(API_VERSION)
        if (apiKey != null) fields["key"] = AnkiConnectJson.str(apiKey)
        if (params != null) fields["params"] = params
        return Request(action, AnkiConnectJson.encode(Json.Obj(fields)))
    }

    /**
     * Builds a `multi` request that repeats the version and key in every nested
     * action. Each nested action is allowlist-checked.
     */
    fun multiRequest(actions: List<Pair<String, Json.Obj?>>, apiKey: String? = null): Request {
        val nested = actions.map { (action, params) ->
            AnkiConnectActions.requireAllowed(action)
            val fields = LinkedHashMap<String, Json>()
            fields["action"] = AnkiConnectJson.str(action)
            fields["version"] = AnkiConnectJson.num(API_VERSION)
            if (apiKey != null) fields["key"] = AnkiConnectJson.str(apiKey)
            if (params != null) fields["params"] = params
            Json.Obj(fields) as Json
        }
        val params = AnkiConnectJson.obj("actions" to AnkiConnectJson.arr(nested))
        return request("multi", params, apiKey)
    }

    /** Parses a top-level response body into a [Response]. */
    fun parse(body: String): Response {
        val json = AnkiConnectJson.decode(body) ?: return Response.ProtocolError
        return parseEnvelope(json)
    }

    /**
     * Parses a `multi` response: the top-level result must be an array, and each
     * element must itself be a valid `{result, error}` envelope. Returns one
     * [Response] per nested action, or a single [Response.ProtocolError] element
     * list if the outer shape is wrong.
     */
    fun parseMulti(body: String): List<Response> {
        val outer = parse(body)
        if (outer !is Response.Ok) return listOf(Response.ProtocolError)
        val array = outer.result as? Json.Arr ?: return listOf(Response.ProtocolError)
        return array.items.map(::parseEnvelope)
    }

    private fun parseEnvelope(json: Json): Response {
        val obj = json as? Json.Obj ?: return Response.ProtocolError
        // A valid envelope has exactly the keys "result" and "error".
        if (obj.entries.keys != setOf("result", "error")) return Response.ProtocolError
        return when (val error = obj.entries["error"]) {
            is Json.Str -> Response.Failed(error.value)
            Json.Null -> Response.Ok(obj.entries["result"] ?: Json.Null)
            else -> Response.ProtocolError
        }
    }
}
