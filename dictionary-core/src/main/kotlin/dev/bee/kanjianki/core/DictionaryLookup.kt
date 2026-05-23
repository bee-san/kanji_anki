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

        companion object {
            val EMPTY = MemoryDictionaryLookup(emptyList())
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
        private val MULTI_WHITESPACE: Pattern = Pattern.compile("\\s+")

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
