package dev.bee.kanjianki.core

import java.util.Locale

class KanjiInventoryBuilder(nowMillis: Long, settings: RecordsSyncModels.Settings?) {
    private val nowMillis: Long = maxOf(0L, nowMillis)
    private val settings: RecordsSyncModels.Settings = settings ?: RecordsSyncModels.Settings.kikuDefaults()
    private val items = LinkedHashMap<String, MutableItem>()

    fun addSnapshotNote(note: RecordsSyncModels.Note?) {
        if (note == null) {
            return
        }
        val expression = TextUtil.normalizeJapanese(note.expression(settings))
        val reading = TextUtil.normalizeJapanese(note.reading(settings))
        val meaning = TextUtil.firstMeaningLine(note.meaning(settings))
        val sentence = TextUtil.normalizeJapanese(note.sentence(settings))
        addSourceText(TextUtil.extractKanji("$expression $sentence"), meaning, reading, expression, sentence)
    }

    fun addSuspendedImport(imported: RecordsImportModels.SuspendedImport?) {
        if (imported == null) {
            return
        }
        val item = itemFor(imported.kanji)
        for (source in safeList(imported.sources)) {
            item.add(source.meaning, source.reading, source.expression, source.sentence)
        }
    }

    fun addDashboardRow(row: RecordsImportModels.DashboardRow?) {
        if (row == null) {
            return
        }
        val item = itemFor(row.kanji)
        item.add(row.primaryMeaning, row.reading, row.reasonText, row.browserSearch)
        item.browserSearch = nullToEmpty(row.browserSearch)
        for (example in safeList(row.examples)) {
            item.exampleCount++
            item.add(example.meaning, example.reading, example.expression, example.sentence)
        }
    }

    fun addKnownKanji(kanji: String?) {
        itemFor(kanji)
    }

    fun addSourceText(
        kanji: List<String?>?,
        meaning: String?,
        reading: String?,
        expression: String?,
        sentence: String?,
    ) {
        for (glyph in safeList(kanji)) {
            itemFor(glyph).add(meaning, reading, expression, sentence)
        }
    }

    fun build(previousItems: Map<String, PreviousItem>?): List<BuiltItem> {
        val previous = previousItems ?: emptyMap()
        val out = ArrayList<BuiltItem>()
        for (item in items.values) {
            if (item.kanji.isEmpty()) {
                continue
            }
            val old = previous[item.kanji]
            out.add(
                BuiltItem(
                    item.kanji,
                    firstNonEmpty(item.primaryMeaning, old?.primaryMeaning() ?: ""),
                    item.readingsText(old?.readings() ?: ""),
                    firstNonEmpty(
                        item.browserSearch,
                        old?.browserSearch() ?: TextUtil.browserSearchForKanji(item.kanji, settings),
                    ),
                    item.searchText(old),
                    maxOf(item.sourceCount, old?.sourceCount() ?: 0),
                    maxOf(item.exampleCount, old?.exampleCount() ?: 0),
                    old?.firstSeenAtMillis() ?: nowMillis,
                    nowMillis,
                ),
            )
        }
        return out
    }

    private fun itemFor(kanji: String?): MutableItem {
        val normalized = nullToEmpty(kanji)
        return items.getOrPut(normalized) { MutableItem(normalized) }
    }

    class PreviousItem(
        primaryMeaning: String?,
        readings: String?,
        browserSearch: String?,
        sourceCount: Int,
        exampleCount: Int,
        firstSeenAtMillis: Long,
        lastSeenAtMillis: Long,
    ) {
        private val primaryMeaning = nullToEmpty(primaryMeaning)
        private val readings = nullToEmpty(readings)
        private val browserSearch = nullToEmpty(browserSearch)
        private val sourceCount = maxOf(0, sourceCount)
        private val exampleCount = maxOf(0, exampleCount)
        private val firstSeenAtMillis = maxOf(0L, firstSeenAtMillis)
        private val lastSeenAtMillis = maxOf(0L, lastSeenAtMillis)

        fun primaryMeaning(): String = primaryMeaning
        fun readings(): String = readings
        fun browserSearch(): String = browserSearch
        fun sourceCount(): Int = sourceCount
        fun exampleCount(): Int = exampleCount
        fun firstSeenAtMillis(): Long = firstSeenAtMillis
        fun lastSeenAtMillis(): Long = lastSeenAtMillis
    }

    class BuiltItem(
        kanji: String?,
        primaryMeaning: String?,
        readings: String?,
        browserSearch: String?,
        searchText: String?,
        sourceCount: Int,
        exampleCount: Int,
        firstSeenAtMillis: Long,
        lastSeenAtMillis: Long,
    ) {
        private val kanji = nullToEmpty(kanji)
        private val primaryMeaning = nullToEmpty(primaryMeaning)
        private val readings = nullToEmpty(readings)
        private val browserSearch = nullToEmpty(browserSearch)
        private val searchText = nullToEmpty(searchText)
        private val sourceCount = maxOf(0, sourceCount)
        private val exampleCount = maxOf(0, exampleCount)
        private val firstSeenAtMillis = maxOf(0L, firstSeenAtMillis)
        private val lastSeenAtMillis = maxOf(0L, lastSeenAtMillis)

        fun kanji(): String = kanji
        fun primaryMeaning(): String = primaryMeaning
        fun readings(): String = readings
        fun browserSearch(): String = browserSearch
        fun searchText(): String = searchText
        fun sourceCount(): Int = sourceCount
        fun exampleCount(): Int = exampleCount
        fun firstSeenAtMillis(): Long = firstSeenAtMillis
        fun lastSeenAtMillis(): Long = lastSeenAtMillis
    }

    private class MutableItem(kanji: String?) {
        val kanji: String = nullToEmpty(kanji)
        var primaryMeaning = ""
        var browserSearch = ""
        var sourceCount = 0
        var exampleCount = 0
        private val readings = LinkedHashSet<String>()
        private val searchParts = HashSet<String>()

        init {
            searchParts.add(this.kanji.lowercase(Locale.ROOT))
        }

        fun add(meaning: String?, reading: String?, expression: String?, sentence: String?) {
            sourceCount++
            if (primaryMeaning.isEmpty() && meaning != null && meaning.isNotEmpty()) {
                primaryMeaning = meaning
            }
            if (!reading.isNullOrEmpty()) {
                readings.add(reading)
            }
            addSearch(meaning)
            addSearch(reading)
            addSearch(expression)
            addSearch(sentence)
        }

        private fun addSearch(value: String?) {
            val normalized = TextUtil.normalizeJapanese(value)
            if (normalized.isNotEmpty()) {
                searchParts.add(normalized.lowercase(Locale.ROOT))
            }
        }

        fun readingsText(previous: String?): String {
            if (readings.isEmpty()) {
                return previous ?: ""
            }
            val display = ArrayList<String>()
            var hidden = 0
            for (reading in readings) {
                if (display.size < MAX_DISPLAYED_READINGS) {
                    display.add(reading)
                } else {
                    hidden++
                }
            }
            val text = display.joinToString(" / ")
            return if (hidden == 0) text else "$text +$hidden more"
        }

        fun searchText(previous: PreviousItem?): String {
            if (previous != null) {
                addSearch(previous.primaryMeaning())
                addSearch(previous.readings())
                addSearch(previous.browserSearch())
            }
            return searchParts.joinToString(" ")
        }
    }

    companion object {
        private const val MAX_DISPLAYED_READINGS = 3

        private fun firstNonEmpty(first: String?, second: String?): String {
            if (!first.isNullOrEmpty()) {
                return first
            }
            return second ?: ""
        }

        private fun nullToEmpty(value: String?): String {
            return value ?: ""
        }

        private fun <T> safeList(values: List<T>?): List<T> {
            return values ?: emptyList()
        }
    }
}
