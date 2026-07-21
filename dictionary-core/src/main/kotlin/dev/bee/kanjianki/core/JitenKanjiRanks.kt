package dev.bee.kanjianki.core

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.util.LinkedHashMap
import java.util.regex.Pattern

class JitenKanjiRanks(ranks: Map<String, Int>) {
    private val ranks: Map<String, Int> = LinkedHashMap(ranks)

    fun rankOf(kanji: String?): Int? = ranks[kanji]

    fun size(): Int = ranks.size

    companion object {
        private val CSV_SEPARATOR: Pattern = Pattern.compile("[,\\t]")
        private val EMPTY = JitenKanjiRanks(LinkedHashMap())

        @JvmStatic
        fun empty(): JitenKanjiRanks = EMPTY

        @JvmStatic
        @Throws(IOException::class)
        fun parseCsv(reader: Reader): JitenKanjiRanks {
            val ranks = LinkedHashMap<String, Int>()
            val buffered = BufferedReader(reader)
            var line = buffered.readLine()
            while (line != null) {
                val entry = parseLine(line)
                if (entry != null) {
                    ranks[entry.kanji] = entry.rank
                }
                line = buffered.readLine()
            }
            return JitenKanjiRanks(ranks)
        }

        private fun parseLine(rawLine: String): RankEntry? {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return null
            }
            val cells = CSV_SEPARATOR.split(line)
            if (cells.size < 2) {
                return null
            }
            val first = cells[0].trim()
            val second = cells[1].trim()
            val firstRank = parseInteger(first)
            if (firstRank != null) {
                return rankEntry(second, firstRank)
            }
            val secondRank = parseInteger(second)
            return if (secondRank != null) rankEntry(first, secondRank) else null
        }

        private fun rankEntry(kanji: String, rank: Int): RankEntry? {
            if (kanji.isEmpty()) {
                return null
            }
            val codePoint = kanji.codePointAt(0)
            val isSingleKanji = Character.charCount(codePoint) == kanji.length &&
                DictionaryTextUtil.isKanji(codePoint)
            return if (isSingleKanji) RankEntry(kanji, rank) else null
        }

        private fun parseInteger(value: String): Int? {
            if (value.isEmpty()) {
                return null
            }
            val digitStart = if (value[0] == '-') 1 else 0
            if (digitStart == value.length) {
                return null
            }
            for (index in value.indices) {
                val c = value[index]
                if (index == 0 && c == '-') {
                    continue
                }
                if (!Character.isDigit(c)) {
                    return null
                }
            }
            return value.toIntOrNull()
        }
    }

    private class RankEntry(val kanji: String, val rank: Int)
}
