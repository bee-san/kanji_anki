package dev.bee.kanjianki.core

class KanjiAnalyzer {
    fun rebuild(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        suspendedImports: List<RecordsImportModels.SuspendedImport>,
        ranks: JitenKanjiRanks,
        settings: RecordsSyncModels.Settings,
    ): List<RecordsImportModels.DashboardRow> {
        return rebuild(snapshot, suspendedImports, ranks, settings, false)
    }

    fun rebuildSelectedSources(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        ranks: JitenKanjiRanks,
        settings: RecordsSyncModels.Settings,
    ): List<RecordsImportModels.DashboardRow> {
        return rebuild(snapshot, imports, ranks, settings, true)
    }

    private fun rebuild(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        imports: List<RecordsImportModels.SuspendedImport>,
        ranks: JitenKanjiRanks,
        settings: RecordsSyncModels.Settings,
        selectedOnly: Boolean,
    ): List<RecordsImportModels.DashboardRow> {
        val notesById = snapshot.notesById()
        val rows = LinkedHashMap<String, MutableRow>()
        val cardIdsWithExamples = LinkedHashSet<Long>()
        val importIndex = ImportSourceIndex(imports, selectedOnly)

        addCardExamples(snapshot, notesById, rows, cardIdsWithExamples, settings, importIndex)
        addImportedSources(imports, rows, cardIdsWithExamples)

        val out = dashboardRows(rows, ranks, settings)
        out.sortWith(
            compareByDescending<RecordsImportModels.DashboardRow> { it.weaknessScore }
                .thenByDescending { it.suspendedExampleCount }
                .thenBy { it.jitenRank ?: Int.MAX_VALUE }
                .thenBy { it.kanji },
        )
        return out
    }

    private class MutableRow(private val kanji: String) {
        private val examples = ArrayList<RecordsImportModels.Example>()
        private var forcePractice = false

        fun addExample(example: RecordsImportModels.Example) {
            examples.add(example)
        }

        fun markForcePractice(force: Boolean) {
            forcePractice = forcePractice || force
        }

        fun shouldInclude(built: RecordsImportModels.DashboardRow): Boolean {
            return built.weaknessScore > 0 || forcePractice
        }

        fun build(ranks: JitenKanjiRanks, settings: RecordsSyncModels.Settings): RecordsImportModels.DashboardRow {
            val summary = summarize(settings)
            val supportDeficit = maxOf(0, settings.matureSupportThreshold - summary.mature)
            val weakness = summary.suspended * 12 +
                supportDeficit * 5 +
                minOf(8, summary.lapses * 2) +
                minOf(6, summary.intervalPressure * 2) +
                minOf(12, summary.fsrsPressure)
            val reason = reasonFor(summary, supportDeficit)
            return RecordsImportModels.DashboardRow(
                kanji,
                ranks.rankOf(kanji),
                summary.meaning,
                summary.reading,
                TextUtil.browserSearchForKanji(kanji, settings),
                weakness,
                reason.code,
                reason.text,
                summary.active,
                summary.suspended,
                summary.mature,
                summary.trimmed,
            )
        }

        private fun summarize(settings: RecordsSyncModels.Settings): RowSummary {
            val summary = RowSummary()
            val seenCards = LinkedHashSet<Long>()
            for (example in examples) {
                summary.addExample(example, fsrsPressure(example, settings), seenCards)
            }
            return summary
        }

        private fun reasonFor(summary: RowSummary, supportDeficit: Int): Reason {
            return if (summary.suspended > 0) {
                Reason(
                    "suspended_archive",
                    "${summary.suspended} missed example${if (summary.suspended == 1) "" else "s"} made this a writing-practice target.",
                )
            } else if (summary.fsrsPressure > 0) {
                Reason("fsrs_weak_memory", "Anki FSRS memory state marks this kanji as fragile.")
            } else if (supportDeficit > 0) {
                Reason(
                    "weak_support",
                    "Only ${summary.mature} known example${if (summary.mature == 1) "" else "s"} support this kanji.",
                )
            } else if (summary.intervalPressure > 0) {
                Reason(
                    "anki_scheduler_weakness",
                    "Anki has ${summary.reps} active reviews but little mature support for this kanji.",
                )
            } else if (summary.lapses > 0) {
                Reason(
                    "anki_lapses",
                    "Your active Anki cards containing this kanji have ${summary.lapses} lapse${if (summary.lapses == 1) "" else "s"}.",
                )
            } else {
                Reason("watch", "This kanji appears in your active cards and is ready for examples.")
            }
        }

        private fun fsrsPressure(
            example: RecordsImportModels.Example,
            settings: RecordsSyncModels.Settings,
        ): Int {
            var pressure = 0
            val retrievability = normalizedRetrievability(example.fsrsRetrievability)
            if (retrievability != null && retrievability < 0.75) {
                pressure += if (retrievability < 0.50) 6 else 3
            }
            if (example.fsrsDifficulty != null && example.fsrsDifficulty >= 7.0) {
                pressure += 3
            }
            if (example.fsrsStability != null && example.reps >= 5 && example.fsrsStability < settings.matureDays) {
                pressure += 3
            }
            if (
                example.mature &&
                example.fsrsStability != null &&
                example.fsrsStability >= settings.matureDays * 2.0 &&
                pressure == 0
            ) {
                pressure -= 2
            }
            return maxOf(0, pressure)
        }

        private fun normalizedRetrievability(value: Double?): Double? {
            if (value == null || value < 0.0) {
                return null
            }
            if (value > 1.0 && value <= 100.0) {
                return value / 100.0
            }
            return if (value > 1.0) null else value
        }
    }

    private class RowSummary {
        var active = 0
        var suspended = 0
        var mature = 0
        var lapses = 0
        var reps = 0
        var intervalPressure = 0
        var fsrsPressure = 0
        var meaning = ""
        var reading = ""
        val trimmed = ArrayList<RecordsImportModels.Example>()

        fun addExample(
            example: RecordsImportModels.Example,
            fsrsPressureValue: Int,
            seenCards: MutableSet<Long>,
        ) {
            if (seenCards.add(example.cardId) && trimmed.size < 8) {
                trimmed.add(example)
            }
            if (SOURCE_SUSPENDED == example.sourceType) {
                suspended++
            } else {
                addActiveExample(example, fsrsPressureValue)
            }
            if (meaning.isEmpty() && example.meaning.isNotEmpty()) {
                meaning = example.meaning
            }
            if (reading.isEmpty() && example.reading.isNotEmpty()) {
                reading = example.reading
            }
        }

        private fun addActiveExample(example: RecordsImportModels.Example, fsrsPressureValue: Int) {
            active++
            if (example.mature) {
                mature++
            }
            lapses += example.lapses
            reps += example.reps
            if (example.reps >= 8 && !example.mature) {
                intervalPressure++
            }
            fsrsPressure += fsrsPressureValue
        }
    }

    private data class Reason(val code: String, val text: String)

    private class ImportSourceIndex(
        imports: List<RecordsImportModels.SuspendedImport>,
        private val selectedOnly: Boolean,
    ) {
        private val importedKanji = LinkedHashSet<String>()
        private val forcePracticeKanji = LinkedHashSet<String>()
        private val selectedCardIds = LinkedHashSet<Long>()
        private val sourcesByKanji = LinkedHashMap<String, MutableMap<Long, RecordsImportModels.SuspendedSource>>()

        init {
            for (imported in imports) {
                importedKanji.add(imported.kanji)
                val sources = sourcesByKanji.computeIfAbsent(imported.kanji) { LinkedHashMap() }
                for (source in imported.sources) {
                    selectedCardIds.add(source.cardId)
                    sources[source.cardId] = source
                    if (source.forcePractice) {
                        forcePracticeKanji.add(imported.kanji)
                    }
                }
            }
        }

        fun shouldReadCard(cardId: Long): Boolean {
            return !selectedOnly || selectedCardIds.contains(cardId)
        }

        fun shouldReadKanji(kanji: String): Boolean {
            return !selectedOnly || importedKanji.contains(kanji)
        }

        fun forcePractice(kanji: String, cardId: Long): Boolean {
            val sources = sourcesByKanji[kanji]
            val source = sources?.get(cardId)
            return source != null && source.forcePractice
        }

        fun sourceFor(kanji: String, cardId: Long): RecordsImportModels.SuspendedSource? {
            return sourcesByKanji[kanji]?.get(cardId)
        }
    }

    companion object {
        private const val SOURCE_ACTIVE = "active"
        private const val SOURCE_SUSPENDED = "suspended"

        private fun addCardExamples(
            snapshot: RecordsSyncModels.CollectionSnapshot,
            notesById: Map<Long, RecordsSyncModels.Note>,
            rows: MutableMap<String, MutableRow>,
            cardIdsWithExamples: MutableSet<Long>,
            settings: RecordsSyncModels.Settings,
            importIndex: ImportSourceIndex,
        ) {
            for (card in snapshot.cards) {
                if (!importIndex.shouldReadCard(card.cardId)) {
                    continue
                }
                val note = notesById[card.noteId]
                if (note != null) {
                    addCardExample(card, note, rows, cardIdsWithExamples, settings, importIndex)
                }
            }
        }

        private fun addCardExample(
            card: RecordsSyncModels.Card,
            note: RecordsSyncModels.Note,
            rows: MutableMap<String, MutableRow>,
            cardIdsWithExamples: MutableSet<Long>,
            settings: RecordsSyncModels.Settings,
            importIndex: ImportSourceIndex,
        ) {
            cardIdsWithExamples.add(card.cardId)
            val expression = TextUtil.normalizeJapanese(note.expression(settings))
            var kanjiList = TextUtil.extractKanji(expression)
            if (kanjiList.isEmpty()) {
                kanjiList = TextUtil.extractKanji(note.sentence(settings))
            }
            val example = exampleFromCard(card, note, expression, settings)
            for (kanji in kanjiList) {
                if (!importIndex.shouldReadKanji(kanji)) {
                    continue
                }
                val row = rows.computeIfAbsent(kanji) { MutableRow(it) }
                row.addExample(importIndex.sourceFor(kanji, card.cardId)?.let { exampleFromImportedSource(it) } ?: example)
                row.markForcePractice(importIndex.forcePractice(kanji, card.cardId))
            }
        }

        private fun exampleFromCard(
            card: RecordsSyncModels.Card,
            note: RecordsSyncModels.Note,
            expression: String,
            settings: RecordsSyncModels.Settings,
        ): RecordsImportModels.Example {
            return RecordsImportModels.Example(
                if (card.suspended) SOURCE_SUSPENDED else SOURCE_ACTIVE,
                card.cardId,
                note.noteId,
                expression,
                TextUtil.normalizeJapanese(note.reading(settings)),
                TextUtil.firstMeaningLine(note.meaning(settings)),
                TextUtil.normalizeJapanese(note.sentence(settings)),
                card.mature(settings.matureDays),
                card.lapses,
                card.intervalDays,
                card.reps,
                card.fsrsStability,
                card.fsrsDifficulty,
                card.fsrsRetrievability,
            )
        }

        private fun addImportedSources(
            imports: List<RecordsImportModels.SuspendedImport>,
            rows: MutableMap<String, MutableRow>,
            cardIdsWithExamples: Set<Long>,
        ) {
            for (imported in imports) {
                val row = rows.computeIfAbsent(imported.kanji) { MutableRow(it) }
                for (source in imported.sources) {
                    row.markForcePractice(source.forcePractice)
                    if (!cardIdsWithExamples.contains(source.cardId)) {
                        row.addExample(exampleFromImportedSource(source))
                    }
                }
            }
        }

        private fun exampleFromImportedSource(source: RecordsImportModels.SuspendedSource): RecordsImportModels.Example {
            return RecordsImportModels.Example(
                source.sourceType,
                source.cardId,
                source.noteId,
                source.expression,
                source.reading,
                source.meaning,
                source.sentence,
                source.mature,
                source.lapses,
                source.intervalDays,
                source.reps,
                source.fsrsStability,
                source.fsrsDifficulty,
                source.fsrsRetrievability,
            )
        }

        private fun dashboardRows(
            rows: Map<String, MutableRow>,
            ranks: JitenKanjiRanks,
            settings: RecordsSyncModels.Settings,
        ): ArrayList<RecordsImportModels.DashboardRow> {
            val out = ArrayList<RecordsImportModels.DashboardRow>()
            for (row in rows.values) {
                val built = row.build(ranks, settings)
                if (row.shouldInclude(built)) {
                    out.add(built)
                }
            }
            return out
        }
    }
}
