package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json

/**
 * A stateful in-process Anki behind the AnkiConnect wire protocol: it holds
 * models, decks, and notes, and mutates them as write actions arrive.
 *
 * [ScriptedAnkiConnectExchange] answers each action from a fixed script, which is
 * right for read tests but cannot express what the Missing Kanji writer is built
 * around: that a write *changes what a later read returns*. Idempotence, "the
 * server committed but the client never heard", and zero-duplicate reconciliation
 * are all claims about the collection after a write, so they need a collection.
 *
 * Failure injection is deliberate and narrow — an override per action, a per-note
 * `addNotes` outcome, and an after-the-write wire failure — so a test says which
 * of AnkiConnect's real misbehaviors it is reproducing.
 */
class FakeAnkiCollection : AnkiConnectTransport.HttpExchange {
    /** What the fake should do with one requested note. */
    enum class AddOutcome {
        /** Created, and reported with its id. The normal case. */
        CREATED,

        /** Refused (a duplicate or invalid note): reported as a null entry. */
        REFUSED,

        /**
         * Created, but reported as a null entry. AnkiConnect should not do this,
         * and that is the point: only reconciliation can catch it.
         */
        CREATED_REPORTED_NULL,

        /** Created, but reported as something that is not a note id at all. */
        CREATED_REPORTED_GARBAGE,
    }

    /** One action's scripted answer, replacing the fake's own behavior. */
    sealed interface Reply {
        data class Result(val json: String) : Reply

        data class Error(val message: String) : Reply

        data class Wire(val result: AnkiConnectTransport.HttpExchange.Result) : Reply
    }

    private class ModelDef(
        val id: Long,
        val fields: List<String>,
        val css: String,
        val templates: Map<String, Pair<String, String>>,
    )

    private class NoteRow(
        val id: Long,
        val modelName: String,
        val fields: Map<String, String>,
    )

    private val models = LinkedHashMap<String, ModelDef>()
    private val decks = LinkedHashMap<String, Long>()
    private val filteredDecks = HashSet<String>()
    private val notes = ArrayList<NoteRow>()
    private val overrides = HashMap<String, Reply>()
    private var nextId = 1_000L

    /** Every action received, in order. */
    val log = mutableListOf<String>()

    /** Every request body received, in order. */
    val bodies = mutableListOf<String>()

    /** The optional actions `apiReflect` reports. Defaults to all of them. */
    var availableActions: Set<String> = AnkiConnectActions.optional

    /** The profile `getActiveProfile` reports; blank means no collection open. */
    var activeProfile: String = "User 1"

    /** Decides what happens to the note at each `addNotes` position. */
    var addOutcome: (Int) -> AddOutcome = { AddOutcome.CREATED }

    /**
     * Consulted after an `addNotes` has already mutated the collection. A non-null
     * result replaces the response, which is how "Anki committed the notes and then
     * the connection dropped" is reproduced.
     */
    var afterAddNotes: () -> AnkiConnectTransport.HttpExchange.Result? = { null }

    init {
        decks["Default"] = 1L
    }

    /** Scripts [action], overriding the fake's own behavior. */
    fun on(action: String, reply: Reply): FakeAnkiCollection {
        overrides[action] = reply
        return this
    }

    /** Installs Kani's own Missing Kanji model, as an earlier run would have left it. */
    fun withKaniModel(id: Long = 500L): FakeAnkiCollection = withModel(
        name = MissingKanjiExportPlanner.MODEL_NAME,
        id = id,
        fields = MissingKanjiExportPlanner.FIELD_NAMES,
        css = MissingKanjiExportPlanner.CSS,
        templates = mapOf(
            MissingKanjiExportPlanner.TEMPLATE_NAME to
                (MissingKanjiExportPlanner.QUESTION_FORMAT to MissingKanjiExportPlanner.ANSWER_FORMAT),
        ),
    )

    @Suppress("LongParameterList")
    fun withModel(
        name: String,
        id: Long = 500L,
        fields: List<String> = MissingKanjiExportPlanner.FIELD_NAMES,
        css: String = MissingKanjiExportPlanner.CSS,
        templates: Map<String, Pair<String, String>> = mapOf(
            MissingKanjiExportPlanner.TEMPLATE_NAME to
                (MissingKanjiExportPlanner.QUESTION_FORMAT to MissingKanjiExportPlanner.ANSWER_FORMAT),
        ),
    ): FakeAnkiCollection {
        models[name] = ModelDef(id, fields, css, templates)
        return this
    }

    fun withDeck(name: String, id: Long = 2L, filtered: Boolean = false): FakeAnkiCollection {
        decks[name] = id
        if (filtered) filteredDecks.add(name) else filteredDecks.remove(name)
        return this
    }

    /** Adds a note to Kani's model as an earlier export would have left it. */
    fun withExportedNote(literal: String, noteId: Long): FakeAnkiCollection {
        val note = MissingKanjiExportPlanner.plan(
            listOf(MissingKanjiCandidate(literal, listOf("meaning"))),
        ).notes.single()
        notes.add(
            NoteRow(
                noteId,
                MissingKanjiExportPlanner.MODEL_NAME,
                MissingKanjiExportPlanner.FIELD_NAMES.zip(note.fields).toMap(),
            ),
        )
        return this
    }

    /** Adds a note carrying [fields] verbatim, for shape-drift cases. */
    fun withRawNote(noteId: Long, modelName: String, fields: Map<String, String>): FakeAnkiCollection {
        notes.add(NoteRow(noteId, modelName, fields))
        return this
    }

    /** The `SourceId` of every note currently in Kani's model, in insertion order. */
    fun exportedSourceIds(): List<String> = notes
        .filter { it.modelName == MissingKanjiExportPlanner.MODEL_NAME }
        .map { it.fields["SourceId"].orEmpty() }

    /** Note ids in Kani's model, in insertion order. */
    fun exportedNoteIds(): List<Long> = notes
        .filter { it.modelName == MissingKanjiExportPlanner.MODEL_NAME }
        .map(NoteRow::id)

    fun deckNames(): Set<String> = decks.keys.toSet()

    fun modelNames(): Set<String> = models.keys.toSet()

    /** How many times [action] was received. */
    fun countOf(action: String): Int = log.count { it == action }

    fun transport(): AnkiConnectTransport =
        AnkiConnectTransport(
            endpoint = (
                AnkiConnectEndpoint.parse(AnkiConnectEndpoint.DEFAULT_URL)
                    as AnkiConnectEndpoint.Result.Valid
                ).endpoint,
            exchange = this,
            addressResolver = { arrayOf(java.net.InetAddress.getByName("127.0.0.1")) },
        )

    override fun post(
        endpoint: AnkiConnectEndpoint,
        body: String,
        maxResponseBytes: Long,
    ): AnkiConnectTransport.HttpExchange.Result {
        bodies += body
        val request = AnkiConnectJson.decode(body) as? Json.Obj
            ?: return wire("""{"result":null,"error":"unparseable request"}""")
        val action = (request.entries["action"] as? Json.Str)?.value.orEmpty()
        log += action
        val params = request.entries["params"] as? Json.Obj
        overrides[action]?.let { reply ->
            return when (reply) {
                is Reply.Result -> wire("""{"result":${reply.json},"error":null}""")
                is Reply.Error -> wire(
                    """{"result":null,"error":${AnkiConnectJson.encode(AnkiConnectJson.str(reply.message))}}""",
                )
                is Reply.Wire -> reply.result
            }
        }
        return dispatch(action, params)
    }

    @Suppress("kotlin:S3776")
    private fun dispatch(action: String, params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result =
        when (action) {
            "requestPermission" -> ok("""{"permission":"granted","requireApikey":false}""")
            "version" -> ok("6")
            "apiReflect" -> ok(
                """{"scopes":["actions"],"actions":${
                    (AnkiConnectActions.required + availableActions)
                        .joinToString(",", "[", "]") { quote(it) }
                }}""",
            )
            "getActiveProfile" -> ok(if (activeProfile.isBlank()) "null" else quote(activeProfile))
            "modelNamesAndIds" -> ok(
                models.entries.joinToString(",", "{", "}") { "${quote(it.key)}:${it.value.id}" },
            )
            "deckNamesAndIds" -> ok(decks.entries.joinToString(",", "{", "}") { "${quote(it.key)}:${it.value}" })
            "modelFieldNames" -> model(params)
                ?.let { ok(it.fields.joinToString(",", "[", "]", transform = ::quote)) }
                ?: error("model was not found")
            "modelStyling" -> model(params)
                ?.let { ok("""{"css":${quote(it.css)}}""") }
                ?: error("model was not found")
            "modelTemplates" -> model(params)
                ?.let { definition ->
                    ok(
                        definition.templates.entries.joinToString(",", "{", "}") { entry ->
                            """${quote(entry.key)}:{"Front":${quote(entry.value.first)},""" +
                                """"Back":${quote(entry.value.second)}}"""
                        },
                    )
                }
                ?: error("model was not found")
            "getDeckConfig" -> deckConfig(params)
            "createDeck" -> createDeck(params)
            "createModel" -> createModel(params)
            "addNotes" -> addNotes(params)
            "findNotes" -> findNotes(params)
            "notesInfo" -> notesInfo(params)
            else -> error("unsupported action $action")
        }

    private fun deckConfig(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val name = stringParam(params, "deck") ?: return error("deck was not found")
        return when {
            name !in decks -> error("deck was not found")
            // A filtered deck has no options group, which is exactly why this read
            // is how Kani tells the two apart.
            name in filteredDecks -> error("deck was not found")
            else -> ok("""{"name":"Default","new":{"perDay":20}}""")
        }
    }

    private fun createDeck(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val name = stringParam(params, "deck") ?: return error("deck name required")
        // Anki's createDeck is idempotent and creates the parent chain.
        var prefix: String? = null
        for (segment in name.split("::")) {
            prefix = if (prefix == null) segment else "$prefix::$segment"
            decks.putIfAbsent(prefix, ++nextId)
        }
        return ok(decks.getValue(name).toString())
    }

    private fun createModel(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val name = stringParam(params, "modelName") ?: return error("modelName required")
        if (name in models) return error("Model name already exists")
        val fields = (params?.entries?.get("inOrderFields") as? Json.Arr)
            ?.items?.mapNotNull { (it as? Json.Str)?.value }
            ?: return error("inOrderFields required")
        val css = stringParam(params, "css").orEmpty()
        val templates = (params.entries["cardTemplates"] as? Json.Arr)?.items.orEmpty()
            .mapNotNull { it as? Json.Obj }
            .associate { template ->
                (template.entries["Name"] as? Json.Str)?.value.orEmpty() to
                    (
                        (template.entries["Front"] as? Json.Str)?.value.orEmpty() to
                            (template.entries["Back"] as? Json.Str)?.value.orEmpty()
                        )
            }
        val id = ++nextId
        models[name] = ModelDef(id, fields, css, templates)
        return ok("""{"id":$id,"name":${quote(name)}}""")
    }

    private fun addNotes(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val requested = (params?.entries?.get("notes") as? Json.Arr)?.items.orEmpty()
            .mapNotNull { it as? Json.Obj }
        val reported = ArrayList<String>(requested.size)
        requested.forEachIndexed { index, note ->
            val deckName = (note.entries["deckName"] as? Json.Str)?.value.orEmpty()
            val modelName = (note.entries["modelName"] as? Json.Str)?.value.orEmpty()
            val fields = (note.entries["fields"] as? Json.Obj)?.entries.orEmpty()
                .mapValues { (_, value) -> (value as? Json.Str)?.value.orEmpty() }
            val sourceId = fields["SourceId"].orEmpty()
            val duplicate = notes.any { row ->
                row.modelName == modelName && row.fields["SourceId"] == sourceId
            }
            val outcome = if (duplicate) AddOutcome.REFUSED else addOutcome(index)
            val id = ++nextId
            if (outcome != AddOutcome.REFUSED && deckName in decks && modelName in models) {
                notes.add(NoteRow(id, modelName, fields))
            }
            reported += when (outcome) {
                AddOutcome.CREATED -> id.toString()
                AddOutcome.REFUSED, AddOutcome.CREATED_REPORTED_NULL -> "null"
                AddOutcome.CREATED_REPORTED_GARBAGE -> quote("not-an-id")
            }
        }
        afterAddNotes()?.let { return it }
        return ok(reported.joinToString(",", "[", "]"))
    }

    private fun findNotes(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val query = stringParam(params, "query").orEmpty()
        val modelName = query.removePrefix("note:\"").removeSuffix("\"")
        val matched = notes.filter { it.modelName == modelName }.map(NoteRow::id)
        return ok(matched.joinToString(",", "[", "]"))
    }

    private fun notesInfo(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val ids = (params?.entries?.get("notes") as? Json.Arr)?.items.orEmpty()
            .mapNotNull { (it as? Json.Num)?.value }
        val rows = ids.mapNotNull { id -> notes.firstOrNull { it.id == id } }.map { row ->
            ScriptedAnkiConnectExchange.noteRow(
                row.id,
                row.modelName,
                row.fields.entries.map { it.key to it.value },
                tags = listOf(MissingKanjiExportPlanner.TAG),
            )
        }
        return ok(rows.joinToString(",", "[", "]"))
    }

    private fun model(params: Json.Obj?): ModelDef? = stringParam(params, "modelName")?.let(models::get)

    private fun stringParam(params: Json.Obj?, key: String): String? =
        (params?.entries?.get(key) as? Json.Str)?.value

    private fun ok(result: String): AnkiConnectTransport.HttpExchange.Result =
        wire("""{"result":$result,"error":null}""")

    private fun error(message: String): AnkiConnectTransport.HttpExchange.Result =
        wire("""{"result":null,"error":${quote(message)}}""")

    private fun wire(body: String): AnkiConnectTransport.HttpExchange.Result =
        AnkiConnectTransport.HttpExchange.Result.Ok(200, body)

    private fun quote(value: String): String = AnkiConnectJson.encode(AnkiConnectJson.str(value))
}
