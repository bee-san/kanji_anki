package dev.bee.kanjianki.core

import java.util.Collections
import java.util.Locale

class KanjiInventorySearchQuery private constructor(terms: List<String>) {
    private val parsedTerms: List<String> = Collections.unmodifiableList(ArrayList(terms))

    fun terms(): List<String> = parsedTerms

    fun isEmpty(): Boolean = parsedTerms.isEmpty()

    fun matches(searchText: String?): Boolean {
        if (parsedTerms.isEmpty()) {
            return true
        }
        val normalized = normalize(searchText)
        return parsedTerms.all { term -> normalized.contains(term) }
    }

    companion object {
        @JvmStatic
        fun parse(query: String?): KanjiInventorySearchQuery {
            val normalized = normalize(query)
            if (normalized.isEmpty()) {
                return KanjiInventorySearchQuery(emptyList())
            }
            val terms = normalized.split(" ").filter { part -> part.isNotEmpty() }
            return KanjiInventorySearchQuery(terms)
        }

        private fun normalize(value: String?): String =
            TextUtil.normalizeJapanese(value).lowercase(Locale.ROOT)
    }
}
