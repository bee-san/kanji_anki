package dev.bee.kanjianki.core

import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.util.Collections
import java.util.TreeSet
import java.util.regex.Pattern

class SimilarKanjiIndex private constructor(
    similarByKanji: Map<String, Set<String>>,
    pairs: List<Pair>,
) {
    private val similarByKanji: Map<String, Set<String>>
    private val pairs: List<Pair>

    init {
        val immutable = LinkedHashMap<String, Set<String>>()
        for ((key, value) in similarByKanji) {
            immutable[key] = Collections.unmodifiableSet(LinkedHashSet(value))
        }
        this.similarByKanji = Collections.unmodifiableMap(immutable)
        this.pairs = Collections.unmodifiableList(ArrayList(pairs))
    }

    fun areSimilar(first: String?, second: String?): Boolean {
        val a = cleanKanji(first)
        val b = cleanKanji(second)
        if (a.isEmpty() || b.isEmpty() || a == b) {
            return false
        }
        val matches = similarByKanji[a]
        return matches != null && matches.contains(b)
    }

    fun similarTo(kanji: String?): List<String> {
        val normalized = cleanKanji(kanji)
        if (normalized.isEmpty()) {
            return emptyList()
        }
        val matches = similarByKanji[normalized] ?: return emptyList()
        return ArrayList(matches)
    }

    fun pairsWithin(kanji: Collection<String?>?): List<Pair> {
        if (kanji.isNullOrEmpty()) {
            return emptyList()
        }
        val local = HashSet<String>()
        for (glyph in kanji) {
            val normalized = cleanKanji(glyph)
            if (normalized.isNotEmpty()) {
                local.add(normalized)
            }
        }
        if (local.size < 2) {
            return emptyList()
        }
        val out = ArrayList<Pair>()
        for (pair in pairs) {
            if (local.contains(pair.kanjiA) && local.contains(pair.kanjiB)) {
                out.add(pair)
            }
        }
        return out
    }

    fun pairCount(): Int = pairs.size

    class Pair private constructor(
        @JvmField val kanjiA: String,
        @JvmField val kanjiB: String,
        source: String?,
    ) : Comparable<Pair> {
        @JvmField
        val source: String = if (source == null || source.trim().isEmpty()) {
            SOURCE_KIKU_VISUALLY_SIMILAR
        } else {
            source.trim()
        }

        private fun key(): String = SimilarKanjiStorageKeys.pairKey(kanjiA, kanjiB, source)

        override fun compareTo(other: Pair): Int {
            val a = kanjiA.compareTo(other.kanjiA)
            if (a != 0) {
                return a
            }
            val b = kanjiB.compareTo(other.kanjiB)
            if (b != 0) {
                return b
            }
            return source.compareTo(other.source)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is Pair) {
                return false
            }
            return kanjiA == other.kanjiA &&
                kanjiB == other.kanjiB &&
                source == other.source
        }

        override fun hashCode(): Int {
            return java.util.Objects.hash(kanjiA, kanjiB, source)
        }

        companion object {
            @JvmStatic
            fun canonical(first: String, second: String, source: String?): Pair {
                if (first.compareTo(second) <= 0) {
                    return Pair(first, second, source)
                }
                return Pair(second, first, source)
            }
        }
    }

    companion object {
        const val SOURCE_KIKU_VISUALLY_SIMILAR: String = "kiku:wk-visually-similar"
        private val TAB_SEPARATOR: Pattern = Pattern.compile("\\t")

        @JvmStatic
        fun empty(): SimilarKanjiIndex {
            return SimilarKanjiIndex(emptyMap(), emptyList())
        }

        @JvmStatic
        @Throws(IOException::class)
        fun parseTsv(reader: Reader): SimilarKanjiIndex {
            val similarByKanji = HashMap<String, MutableSet<String>>()
            val pairsByKey = LinkedHashMap<String, Pair>()
            val buffered = BufferedReader(reader)
            var line = buffered.readLine()
            while (line != null) {
                val pair = parsePair(line)
                if (pair != null) {
                    val key = SimilarKanjiStorageKeys.pairKey(pair.kanjiA, pair.kanjiB, pair.source)
                    if (pairsByKey.computeIfAbsent(key) { pair } === pair) {
                        similarByKanji.computeIfAbsent(pair.kanjiA) { TreeSet() }.add(pair.kanjiB)
                        similarByKanji.computeIfAbsent(pair.kanjiB) { TreeSet() }.add(pair.kanjiA)
                    }
                }
                line = buffered.readLine()
            }
            val pairs = ArrayList(pairsByKey.values)
            Collections.sort(pairs)
            return SimilarKanjiIndex(similarByKanji, pairs)
        }

        private fun parsePair(line: String): Pair? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return null
            }
            val cells = TAB_SEPARATOR.split(line, -1)
            if (cells.size < 2 || cells[0] == "kanji_a") {
                return null
            }
            val kanjiA = cleanKanji(cells[0])
            val kanjiB = cleanKanji(cells[1])
            if (kanjiA.isEmpty() || kanjiB.isEmpty() || kanjiA == kanjiB) {
                return null
            }
            val source = if (cells.size >= 3 && cells[2].trim().isNotEmpty()) {
                cells[2].trim()
            } else {
                SOURCE_KIKU_VISUALLY_SIMILAR
            }
            return Pair.canonical(kanjiA, kanjiB, source)
        }

        private fun cleanKanji(value: String?): String {
            return TextUtil.normalizeSingleKanji(value)
        }
    }
}
