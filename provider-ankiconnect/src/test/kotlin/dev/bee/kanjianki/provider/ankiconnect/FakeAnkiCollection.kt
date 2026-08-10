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
        fields: Map<String, String>,
        val tags: MutableSet<String> = linkedSetOf(MissingKanjiExportPlanner.TAG),
    ) {
        val fields: MutableMap<String, String> = LinkedHashMap(fields)
    }

    /**
     * One card's scheduling state, plus the note fields and tags a write could
     * disturb. Kani must never change any of it; [FakeAnkiCollection] keeps it so
     * that claim can be checked rather than assumed — see [schedulingSnapshot].
     */
    private class CardRow(
        val id: Long,
        val noteId: Long,
        var queue: Long = 2L,
        var due: Long = 500L,
        var interval: Long = 21L,
        var factor: Long = 2_500L,
        var reps: Long = 7L,
        var lapses: Long = 1L,
        var deckName: String = "Default",
    )

    private val models = LinkedHashMap<String, ModelDef>()
    private val decks = LinkedHashMap<String, Long>()
    private val filteredDecks = HashSet<String>()
    private val deckConfigIds = LinkedHashMap<String, Long>()
    private val notes = ArrayList<NoteRow>()
    private val cards = ArrayList<CardRow>()
    private val overrides = HashMap<String, Reply>()
    private var nextId = 1_000L

    /** How many times a full-collection `sync` was requested. Must stay zero. */
    var syncCount = 0
        private set

    /** Every action received, in order. */
    val log = mutableListOf<String>()

    /** Every request body received, in order. */
    val bodies = mutableListOf<String>()

    /** The optional actions `apiReflect` reports. Defaults to all of them. */
    var availableActions: Set<String> = AnkiConnectActions.optional

    /**
     * The media directory `getMediaDirPath` reports, which is how Kani identifies
     * the loaded profile. Blank means no collection is open, and the real server
     * *raises* in that case rather than returning null, because it resolves the
     * path through the open collection — see the dispatch below.
     */
    var profileIdentity: String = "/home/user/.local/share/Anki2/User 1/collection.media"

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
        deckConfigIds["Default"] = 1L
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
        deckConfigIds[name] = 1L
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

    /**
     * Adds a card with review history on [noteId], so a test can prove a write left
     * its scheduling state alone. [queue] is settable because a suspended card and an
     * active one are disturbed by different actions.
     */
    fun withCard(cardId: Long, noteId: Long, queue: Long = 2L): FakeAnkiCollection {
        cards.add(CardRow(cardId, noteId, queue = queue))
        return this
    }

    /**
     * Everything about the collection Kani promises never to change, keyed so a
     * before/after comparison can distinguish two very different things: a *changed*
     * or *vanished* entry, which is a broken promise, from a *new* entry, which is
     * the additive write working as intended.
     *
     * Covers card queue, due, interval, ease, reps, lapses, and deck placement; each
     * deck's options group id; and every note's fields.
     */
    fun schedulingSnapshot(): Map<String, String> {
        val snapshot = LinkedHashMap<String, String>()
        for (card in cards.sortedBy(CardRow::id)) {
            snapshot["card:${card.id}"] = listOf(
                card.noteId,
                card.queue,
                card.due,
                card.interval,
                card.factor,
                card.reps,
                card.lapses,
                card.deckName,
            ).joinToString("|")
        }
        for ((name, id) in deckConfigIds.entries.sortedBy { it.key }) {
            snapshot["config:$name"] = id.toString()
        }
        for (note in notes.sortedBy(NoteRow::id)) {
            snapshot["note:${note.id}"] =
                note.fields.entries.sortedBy { it.key }.joinToString(";")
        }
        return snapshot
    }

    /** The tags on [noteId], or an empty set when no such note exists. */
    fun tagsOf(noteId: Long): Set<String> =
        notes.firstOrNull { it.id == noteId }?.tags?.toSet().orEmpty()

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

    /**
     * Applies [action] to the collection without going through
     * [AnkiConnectEnvelope], whose allowlist would refuse it. This is the only way
     * to reach a denied action, and it exists for exactly one caller:
     * [AnkiConnectWriteSurfaceTest] proves the denied actions really would move
     * state that Kani's own flows leave alone, so that "nothing moved" is evidence
     * rather than an artifact of a fake that ignores them.
     */
    fun applyUnchecked(action: String) {
        dispatch(action, null)
    }

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
            // Matches the pinned server: the media directory is resolved through
            // the open collection, so with none open the action raises rather than
            // answering null.
            "getMediaDirPath" ->
                if (profileIdentity.isBlank()) error("collection is not available") else ok(quote(profileIdentity))
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
            "addTags" -> addTags(params)
            "findCards" -> ok(cards.map(CardRow::id).joinToString(",", "[", "]"))
            "cardsInfo" -> cardsInfo(params)
            "multi" -> multi(params)
            in SCHEDULING_ACTIONS -> applyScheduling(action, params)
            else -> error("unsupported action $action")
        }

    /**
     * Adds tags to notes, the one existing-note write Kani's normal sync surface
     * makes. Note fields are untouched: the whole point of the tag-only surface is
     * that Kani adds a label and changes nothing else.
     */
    private fun addTags(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val ids = (params?.entries?.get("notes") as? Json.Arr)?.items.orEmpty()
            .mapNotNull { (it as? Json.Num)?.value }
        val tags = stringParam(params, "tags").orEmpty().split(" ").filter(String::isNotBlank)
        for (id in ids) {
            val row = notes.firstOrNull { it.id == id } ?: return error("Note was not found: $id")
            row.tags.addAll(tags)
        }
        return ok("null")
    }

    private fun cardsInfo(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val ids = (params?.entries?.get("cards") as? Json.Arr)?.items.orEmpty()
            .mapNotNull { (it as? Json.Num)?.value }
        val rows = ids.mapNotNull { id -> cards.firstOrNull { it.id == id } }.map { card ->
            ScriptedAnkiConnectExchange.cardRow(
                cardId = card.id,
                noteId = card.noteId,
                modelName = notes.firstOrNull { it.id == card.noteId }?.modelName.orEmpty(),
                deckName = card.deckName,
                queue = card.queue,
                due = card.due,
                interval = card.interval,
                reps = card.reps,
                lapses = card.lapses,
            )
        }
        return ok(rows.joinToString(",", "[", "]"))
    }

    private fun multi(params: Json.Obj?): AnkiConnectTransport.HttpExchange.Result {
        val actions = (params?.entries?.get("actions") as? Json.Arr)?.items.orEmpty()
            .mapNotNull { it as? Json.Obj }
        val bodies = actions.map { nested ->
            val nestedAction = (nested.entries["action"] as? Json.Str)?.value.orEmpty()
            log += nestedAction
            when (val reply = overrides[nestedAction]) {
                is Reply.Result -> """{"result":${reply.json},"error":null}"""
                is Reply.Error -> """{"result":null,"error":${quote(reply.message)}}"""
                is Reply.Wire -> """{"result":null,"error":"nested transport failure"}"""
                null -> when (val result = dispatch(nestedAction, nested.entries["params"] as? Json.Obj)) {
                    is AnkiConnectTransport.HttpExchange.Result.Ok -> result.body
                    else -> """{"result":null,"error":"nested transport failure"}"""
                }
            }
        }
        return ok(bodies.joinToString(",", "[", "]"))
    }

    /**
     * The actions Kani must never send, implemented for real. A fake that simply
     * errored on them would prove nothing: the deny-list test asserts the
     * collection's scheduling state is byte-identical after a full run, and that
     * assertion is only meaningful if a scheduling write *would* have changed it.
     */
    @Suppress("kotlin:S3776")
    private fun applyScheduling(
        action: String,
        params: Json.Obj?,
    ): AnkiConnectTransport.HttpExchange.Result {
        val cardIds = (params?.entries?.get("cards") as? Json.Arr)?.items.orEmpty()
            .mapNotNull { (it as? Json.Num)?.value }
        val targeted = cards.filter { it.id in cardIds }.ifEmpty { cards }
        when (action) {
            "suspend" -> targeted.forEach { it.queue = -1L }
            "unsuspend" -> targeted.forEach { it.queue = 2L }
            "setDueDate" -> targeted.forEach { it.due = 0L }
            "forgetCards" -> targeted.forEach {
                it.queue = 0L
                it.interval = 0L
                it.reps = 0L
            }
            "relearnCards" -> targeted.forEach { it.queue = 1L }
            "answerCards", "guiAnswerCard" -> targeted.forEach {
                it.reps += 1L
                it.interval *= 2L
            }
            "changeDeck" -> targeted.forEach { it.deckName = "Somewhere Else" }
            "saveDeckConfig", "setDeckConfigId" -> deckConfigIds.keys.forEach {
                deckConfigIds[it] = 99L
            }
            "updateNoteFields" -> notes.forEach { note ->
                note.fields.keys.toList().forEach { key -> note.fields[key] = "clobbered" }
            }
            "sync" -> syncCount += 1
            else -> return error("unsupported action $action")
        }
        return ok("null")
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
            deckConfigIds.putIfAbsent(prefix, 1L)
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
        val matched = if (query.startsWith("note:")) {
            val modelName = query.removePrefix("note:\"").removeSuffix("\"")
            notes.filter { it.modelName == modelName }
        } else {
            // Any other query is treated as collection-wide, which is what the only
            // other search Kani sends (`deck:*`) means.
            notes
        }
        return ok(matched.map(NoteRow::id).joinToString(",", "[", "]"))
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

    companion object {
        /**
         * Real AnkiConnect actions that change scheduling, note content, deck
         * options, or the collection as a whole. Kani may never send any of them —
         * [AnkiConnectWriteSurfaceTest] is where that is enforced. They are listed
         * here rather than there because this fake implements them: an action the
         * deny-list names but the fake ignores would make the test pass for the
         * wrong reason.
         */
        val SCHEDULING_ACTIONS: Set<String> = linkedSetOf(
            "suspend",
            "unsuspend",
            "setDueDate",
            "forgetCards",
            "relearnCards",
            "answerCards",
            "guiAnswerCard",
            "changeDeck",
            "saveDeckConfig",
            "setDeckConfigId",
            "updateNoteFields",
            "sync",
        )
    }
}
