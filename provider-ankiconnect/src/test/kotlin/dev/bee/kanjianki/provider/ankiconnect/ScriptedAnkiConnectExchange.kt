package dev.bee.kanjianki.provider.ankiconnect

/**
 * A deterministic [AnkiConnectTransport.HttpExchange] that answers each request
 * from a per-action handler, so collection-read tests can vary the response by
 * batch (which the static-reply [FakeAnkiConnectServer] cannot). No sockets are
 * involved; the transport's own network behavior is covered by the failure
 * matrix against the real loopback server.
 */
class ScriptedAnkiConnectExchange : AnkiConnectTransport.HttpExchange {
    /** Every top-level request body received, in order. */
    val received = mutableListOf<String>()

    /**
     * Every nested action envelope dispatched inside an unscripted `multi`, in
     * order. Kept separate from [received] because the two answer different
     * questions: [received] is what went over the wire (so a test can count round
     * trips), while this is what was *asked for* (so a test can assert an action
     * that only ever rides inside a batch, like `addTags`).
     */
    val nested = mutableListOf<String>()

    private val handlers = HashMap<String, (String) -> AnkiConnectTransport.HttpExchange.Result>()

    /** Answers [action] with a success envelope wrapping [result] JSON text. */
    fun onResult(action: String, result: (String) -> String): ScriptedAnkiConnectExchange = onRaw(action) { body ->
        AnkiConnectTransport.HttpExchange.Result.Ok(
            200,
            """{"result":${result(body)},"error":null}""",
        )
    }

    /** Answers [action] with a fixed success envelope. */
    fun onResult(action: String, result: String): ScriptedAnkiConnectExchange = onResult(action) { result }

    /** Answers [action] with an AnkiConnect error envelope. */
    fun onError(action: String, message: String): ScriptedAnkiConnectExchange = onRaw(action) {
        AnkiConnectTransport.HttpExchange.Result.Ok(
            200,
            """{"result":null,"error":${AnkiConnectJson.encode(AnkiConnectJson.str(message))}}""",
        )
    }

    /** Answers [action] with an arbitrary transport result. */
    fun onRaw(
        action: String,
        handler: (String) -> AnkiConnectTransport.HttpExchange.Result,
    ): ScriptedAnkiConnectExchange {
        handlers[action] = handler
        return this
    }

    /** The action names seen so far, in request order. */
    fun actions(): List<String> = received.mapNotNull(::actionOf)

    /** Top-level request bodies for [action], in order. */
    fun bodiesFor(action: String): List<String> = received.filter { actionOf(it) == action }

    /**
     * Bodies for [action] wherever they appeared — top level or nested inside a
     * `multi`. Use this for an action the caller may batch; use [bodiesFor] when
     * the assertion is about round trips.
     */
    fun anyBodiesFor(action: String): List<String> =
        (received + nested).filter { actionOf(it) == action }

    fun transport(): AnkiConnectTransport =
        AnkiConnectTransport(
            endpoint = (
                AnkiConnectEndpoint.parse(AnkiConnectEndpoint.DEFAULT_URL)
                    as AnkiConnectEndpoint.Result.Valid
                ).endpoint,
            exchange = this,
            // The scripted exchange never opens a socket, so resolution is stubbed
            // to loopback; real resolution enforcement is tested on the transport.
            addressResolver = { arrayOf(java.net.InetAddress.getByName("127.0.0.1")) },
        )

    override fun post(
        endpoint: AnkiConnectEndpoint,
        body: String,
        maxResponseBytes: Long,
    ): AnkiConnectTransport.HttpExchange.Result {
        received += body
        val action = actionOf(body)
        val handler = handlers[action]
            ?: if (action == "multi") {
                return dispatchMulti(body)
            } else {
                return AnkiConnectTransport.HttpExchange.Result.Ok(
                    200,
                    """{"result":null,"error":"unscripted action $action"}""",
                )
            }
        return handler(body)
    }

    /**
     * Answers an unscripted `multi` by dispatching each nested action to its own
     * registered handler, so a test scripts `modelFieldNames` once whether the
     * reader sends it singly or inside a `multi` group. Scripting `"multi"`
     * explicitly still overrides this.
     */
    private fun dispatchMulti(body: String): AnkiConnectTransport.HttpExchange.Result {
        val nestedBodies = nestedActions(body)
        nested += nestedBodies
        val results = nestedBodies.map { nestedBody ->
            val nestedAction = actionOf(nestedBody)
            val handler = handlers[nestedAction]
                ?: return@map """{"result":null,"error":"unscripted action $nestedAction"}"""
            when (val result = handler(nestedBody)) {
                is AnkiConnectTransport.HttpExchange.Result.Ok -> result.body
                else -> """{"result":null,"error":"nested transport failure"}"""
            }
        }
        return AnkiConnectTransport.HttpExchange.Result.Ok(
            200,
            """{"result":[${results.joinToString(",")}],"error":null}""",
        )
    }

    /** The nested action envelopes of a `multi` body, re-encoded individually. */
    private fun nestedActions(body: String): List<String> {
        val params = (AnkiConnectJson.decode(body) as? AnkiConnectJson.Json.Obj)
            ?.entries?.get("params") as? AnkiConnectJson.Json.Obj
            ?: return emptyList()
        val actions = params.entries["actions"] as? AnkiConnectJson.Json.Arr ?: return emptyList()
        return actions.items.map(AnkiConnectJson::encode)
    }

    private fun actionOf(body: String): String? =
        (AnkiConnectJson.decode(body) as? AnkiConnectJson.Json.Obj)
            ?.entries?.get("action")
            ?.let { (it as? AnkiConnectJson.Json.Str)?.value }

    companion object {
        /** Renders a `notesInfo` row for [noteId] on [modelName]. */
        fun noteRow(
            noteId: Long,
            modelName: String,
            fields: List<Pair<String, String>>,
            tags: List<String> = emptyList(),
        ): String {
            val fieldsJson = fields.withIndex().joinToString(",") { (index, entry) ->
                """${quote(entry.first)}:{"value":${quote(entry.second)},"order":$index}"""
            }
            val tagsJson = tags.joinToString(",", "[", "]") { quote(it) }
            return """{"noteId":$noteId,"modelName":${quote(modelName)},""" +
                """"tags":$tagsJson,"fields":{$fieldsJson}}"""
        }

        /** Renders a `cardsInfo` row. */
        @Suppress("LongParameterList")
        fun cardRow(
            cardId: Long,
            noteId: Long,
            modelName: String,
            deckName: String = "Default",
            ord: Long = 0,
            queue: Long = 2,
            type: Long = 2,
            due: Long = 100,
            interval: Long = 30,
            reps: Long = 5,
            lapses: Long = 1,
        ): String =
            """{"cardId":$cardId,"note":$noteId,"deckName":${quote(deckName)},""" +
                """"modelName":${quote(modelName)},"ord":$ord,"queue":$queue,"type":$type,""" +
                """"due":$due,"interval":$interval,"reps":$reps,"lapses":$lapses}"""

        private fun quote(value: String): String = AnkiConnectJson.encode(AnkiConnectJson.str(value))
    }
}
