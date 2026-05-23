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
            if (isInteger(first)) {
                return rankEntry(second, first.toInt())
            }
            return if (isInteger(second)) rankEntry(first, second.toInt()) else null
        }

        private fun rankEntry(kanji: String, rank: Int): RankEntry? {
            return if (kanji.isNotEmpty() && DictionaryTextUtil.isKanji(kanji.codePointAt(0))) RankEntry(kanji, rank) else null
        }

        private fun isInteger(value: String): Boolean {
            if (value.isEmpty()) {
                return false
            }
            for (index in value.indices) {
                val c = value[index]
                if (index == 0 && c == '-') {
                    continue
                }
                if (!Character.isDigit(c)) {
                    return false
                }
            }
            return true
        }
    }

    private class RankEntry(val kanji: String, val rank: Int)
}
