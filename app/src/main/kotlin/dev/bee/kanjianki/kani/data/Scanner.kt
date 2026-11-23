package dev.bee.kanjianki.kani.data

import dev.bee.kanjianki.kani.anki.AnkiSource
import dev.bee.kanjianki.kani.core.AnkiText
import dev.bee.kanjianki.kani.core.FieldPicker
import dev.bee.kanjianki.kani.core.ScanAnalyzer

/** Finds what to read in AnkiDroid, then reads it. */
class Scanner(private val anki: AnkiSource, private val ref: Reference) {

    data class ModelPick(val name: String, val mapping: FieldPicker.Mapping, val notes: Int)

    data class Detected(
        val models: List<ModelPick>,
        /** Deck name → note count, largest first. */
        val decks: List<Pair<String, Int>>,
        val exampleExpression: String,
        val exampleReading: String,
        val exampleMeaning: String,
    ) {
        fun toSource() = KaniPrefs.Source(
            modelNames = models.map { it.name },
            expressionField = models.associate { it.name to it.mapping.expression },
            readingField = models.mapNotNull { m -> m.mapping.reading?.let { m.name to it } }.toMap(),
            meaningField = models.mapNotNull { m -> m.mapping.meaning?.let { m.name to it } }.toMap(),
        )
    }

    /** Picks every note type that has a word field with kanji in it. */
    fun detect(): Detected {
        val picks = ArrayList<ModelPick>()
        var example: Triple<String, String, String>? = null
        for (model in anki.models()) {
            val mapping = FieldPicker.pick(model.fields) ?: continue
            val search = AnkiSource.noteSearch(listOf(model.name))
            val count = anki.noteCount(search)
            if (count == 0) continue
            val notes = anki.notes(search, limit = DETECT_SAMPLE)
            val exprIndex = model.fields.indexOf(mapping.expression)
            val withKanji = notes.count { n -> AnkiText.kanjiIn(AnkiText.expression(n.fields.getOrNull(exprIndex))).isNotEmpty() }
            // Skip note types where the "word" field is mostly not Japanese (e.g. a Spanish deck's Front).
            if (withKanji * 2 < notes.size) continue
            picks += ModelPick(model.name, mapping, count)
            if (example == null) {
                // A short, ordinary word makes the clearest example of the picked fields.
                example = notes.asSequence().map { n ->
                    Triple(
                        AnkiText.expression(n.fields.getOrNull(exprIndex)),
                        mapping.reading?.let { AnkiText.reading(n.fields.getOrNull(model.fields.indexOf(it))) }.orEmpty(),
                        mapping.meaning?.let { AnkiText.shortMeaning(n.fields.getOrNull(model.fields.indexOf(it))) }.orEmpty(),
                    )
                }.firstOrNull { (e, r, m) -> e.length in 2..4 && AnkiText.kanjiIn(e).isNotEmpty() && r.isNotEmpty() && m.isNotEmpty() }
            }
        }
        picks.sortByDescending { it.notes }
        val decks = if (picks.isEmpty()) {
            emptyList()
        } else {
            anki.noteCountsByDeck(AnkiSource.noteSearch(picks.map { it.name }))
        }
        val (e, r, m) = example ?: Triple("", "", "")
        return Detected(picks, decks, e, r, m)
    }

    data class Scan(val result: ScanAnalyzer.Result, val words: List<KaniStore.Word>)

    fun scan(source: KaniPrefs.Source, onProgress: (Int) -> Unit = {}): Scan {
        val models = anki.models().filter { it.name in source.modelNames }.associateBy { it.id }
        val base = AnkiSource.noteSearch(source.modelNames)
        // Sorted primitive id arrays: a big collection must not become millions of boxed Longs.
        val leech = anki.noteIds("$base tag:leech")
        val failed = anki.noteIds("$base prop:lapses>=2")
        val suspended = anki.noteIds("$base is:suspended")
        val healthy = anki.noteIds("$base prop:ivl>=21 prop:lapses=0")
        val fresh = anki.noteIds("$base is:new")

        val words = ArrayList<ScanAnalyzer.Word>()
        val stored = ArrayList<KaniStore.Word>()
        // Notes are streamed and dropped as they are read; only the small word records stay.
        anki.forEachNote(base, onCount = onProgress) { n ->
            val model = models[n.modelId] ?: return@forEachNote
            fun field(name: String?): String? = name?.let { n.fields.getOrNull(model.fields.indexOf(it)) }
            val expression = AnkiText.expression(field(source.expressionField[model.name]))
            if (AnkiText.kanjiIn(expression).isEmpty()) return@forEachNote
            val reading = AnkiText.reading(field(source.readingField[model.name]))
            val problem = when {
                n.id in leech || n.tags.any { it.equals("leech", true) } -> ScanAnalyzer.Reason.LEECH
                n.id in failed -> ScanAnalyzer.Reason.FAILED
                n.id in suspended -> ScanAnalyzer.Reason.SUSPENDED
                else -> null
            }
            words += ScanAnalyzer.Word(n.id, expression, problem, healthy = n.id in healthy, isNew = n.id in fresh)
            if (expression.length <= MAX_WORD_LENGTH && reading.isNotEmpty()) {
                // Definitions can run to kilobytes of HTML. Keep a short plain prefix; it is
                // cleaned into a gloss only when a word is shown (see KaniStore).
                val meaning = AnkiText.plain(field(source.meaningField[model.name])?.take(RAW_MEANING_PREFIX)).take(MEANING_PREFIX)
                stored += KaniStore.Word(n.id, expression, reading, meaning)
            }
        }
        return Scan(ScanAnalyzer.analyze(words, ref::similar), stored)
    }

    private operator fun LongArray.contains(id: Long) = java.util.Arrays.binarySearch(this, id) >= 0

    private companion object {
        const val MAX_WORD_LENGTH = 8
        const val RAW_MEANING_PREFIX = 2000
        const val MEANING_PREFIX = 400
        const val DETECT_SAMPLE = 50
    }
}
