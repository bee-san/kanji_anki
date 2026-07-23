package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Objects
import java.util.regex.Pattern

abstract class DictionaryLookup {
    abstract fun lookupKanji(literal: String?): KanjiEntry?

    abstract fun kanjiCount(): Int

    open fun jitenRanks(): JitenKanjiRanks = JitenKanjiRanks.empty()

    open fun searchKanji(query: String?, limit: Int): List<KanjiEntry> = emptyList()

    open fun eligibleKanjiCount(range: JitenRankRange): Int = 0

    open fun kanjiByJitenRank(
        range: JitenRankRange,
        offset: Int,
        limit: Int,
    ): KanjiEntryPage = KanjiEntryPage.empty()

    fun studyCue(
        kanji: String?,
        ankiMeaning: String?,
        rowReading: String?,
        sourceExpression: String?,
        sourceReading: String?,
    ): StudyCue {
        val kanjiEntry = lookupKanji(normalize(kanji))
        val cueReading = firstNonEmpty(
            sourceReading,
            rowReading,
            if (kanjiEntry == null) "" else kanjiEntry.firstReading(),
        )
        val fromExpression = normalize(sourceExpression)
        if (kanjiEntry != null) {
            return StudyCue(
                StudyCueFormatter.displayGlosses(kanjiEntry.meanings, 2),
                cueReading,
                fromExpression,
                SOURCE_KANJIDIC2,
            )
        }
        return StudyCue(
            StudyCueFormatter.cleanFallbackMeaning(ankiMeaning, "", 96),
            cueReading,
            fromExpression,
            SOURCE_ANKI,
        )
    }

    private class MemoryDictionaryLookup(kanji: List<KanjiEntry>?) : DictionaryLookup() {
        private val kanjiByLiteral: Map<String, KanjiEntry>

        init {
            val byLiteral = HashMap<String, KanjiEntry>()
            for (entry in kanji ?: emptyList()) {
                byLiteral[entry.literal] = entry
            }
            kanjiByLiteral = Collections.unmodifiableMap(byLiteral)
        }

        override fun lookupKanji(literal: String?): KanjiEntry? = kanjiByLiteral[normalize(literal)]

        override fun kanjiCount(): Int = kanjiByLiteral.size

        override fun eligibleKanjiCount(range: JitenRankRange): Int {
            if (!range.isValid()) {
                return 0
            }
            return kanjiByLiteral.values.count { entry -> range.includes(entry.jitenRank) }
        }

        override fun kanjiByJitenRank(
            range: JitenRankRange,
            offset: Int,
            limit: Int,
        ): KanjiEntryPage {
            if (!range.isValid() || offset < 0 || limit < 1) {
                return KanjiEntryPage.empty()
            }
            val eligible = kanjiByLiteral.values
                .asSequence()
                .filter { entry -> range.includes(entry.jitenRank) }
                .sortedWith(KANJI_RANK_COMPARATOR)
                .toList()
            val boundedLimit = limit.coerceAtMost(MAX_KANJI_PAGE_SIZE)
            val page = eligible.drop(offset).take(boundedLimit)
            val nextOffset = (offset + page.size).takeIf { next -> next < eligible.size }
            return KanjiEntryPage(page, eligible.size, nextOffset)
        }

        companion object {
            val EMPTY = MemoryDictionaryLookup(emptyList())
        }
    }

    data class JitenRankRange(
        val minimumRank: Int,
        val maximumRank: Int,
        val includeUnranked: Boolean = false,
    ) {
        fun isValid(): Boolean = minimumRank >= 1 && maximumRank >= minimumRank

        fun includes(rank: Int?): Boolean {
            return if (rank == null) includeUnranked else rank in minimumRank..maximumRank
        }
    }

    data class KanjiEntryPage(
        val entries: List<KanjiEntry>,
        val totalEligible: Int,
        val nextOffset: Int?,
    ) {
        companion object {
            fun empty(): KanjiEntryPage = KanjiEntryPage(emptyList(), 0, null)
        }
    }

    class KanjiEntry(fields: KanjiEntryFields?) {
        @JvmField val literal: String
        @JvmField val meanings: List<String>
        @JvmField val onReadings: List<String>
        @JvmField val kunReadings: List<String>
        @JvmField val nanoriReadings: List<String>
        @JvmField val strokeCount: Int
        @JvmField val grade: Int
        @JvmField val radical: Int
        @JvmField val kanjidicFrequency: Int
        @JvmField val jitenRank: Int?

        init {
            val safeFields = fields ?: KanjiEntryFields(
                "",
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                0,
                0,
                0,
                null,
            )
            literal = normalize(safeFields.literal)
            meanings = immutableList(safeFields.meanings)
            onReadings = immutableList(safeFields.onReadings)
            kunReadings = immutableList(safeFields.kunReadings)
            nanoriReadings = immutableList(safeFields.nanoriReadings)
            strokeCount = safeFields.strokeCount
            grade = safeFields.grade
            radical = safeFields.radical
            kanjidicFrequency = safeFields.kanjidicFrequency
            jitenRank = safeFields.jitenRank
        }

        fun firstReading(): String {
            if (kunReadings.isNotEmpty()) {
                return kunReadings[0]
            }
            if (onReadings.isNotEmpty()) {
                return onReadings[0]
            }
            return if (nanoriReadings.isEmpty()) "" else nanoriReadings[0]
        }

        companion object {
            private fun immutableList(values: List<String>?): List<String> {
                return Collections.unmodifiableList(ArrayList(Objects.requireNonNullElse(values, emptyList())))
            }
        }
    }

    @JvmRecord
    data class KanjiEntryFields(
        val literal: String?,
        val meanings: List<String>?,
        val onReadings: List<String>?,
        val kunReadings: List<String>?,
        val nanoriReadings: List<String>?,
        val strokeCount: Int,
        val grade: Int,
        val radical: Int,
        val kanjidicFrequency: Int,
        val jitenRank: Int?,
    )

    companion object {
        const val MAX_KANJI_PAGE_SIZE: Int = 10_000
        private val MULTI_WHITESPACE: Pattern = Pattern.compile("\\s+")
        private val KANJI_RANK_COMPARATOR =
            compareBy<KanjiEntry> { it.jitenRank ?: Int.MAX_VALUE }
                .thenBy { it.literal }

        const val SOURCE_KANJIDIC2: String = "KANJIDIC2"
        const val SOURCE_ANKI: String = "anki"

        @JvmStatic
        fun empty(): DictionaryLookup = MemoryDictionaryLookup.EMPTY

        @JvmStatic
        fun fromKanjiEntries(kanji: List<KanjiEntry>?): DictionaryLookup = MemoryDictionaryLookup(kanji)

        @JvmStatic
        fun normalize(value: String?): String {
            return MULTI_WHITESPACE.matcher((value ?: "").trim()).replaceAll(" ")
        }

        private fun firstNonEmpty(vararg values: String?): String {
            for (value in values) {
                val normalized = normalize(value)
                if (normalized.isNotEmpty()) {
                    return normalized
                }
            }
            return ""
        }
    }
}
