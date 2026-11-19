package dev.bee.kanjianki.kani.core

/**
 * Picks the word, reading and meaning fields of a note type by name, the way Hachidori does,
 * so setup only asks the user to confirm.
 */
object FieldPicker {
    data class Mapping(
        val expression: String,
        val reading: String?,
        val meaning: String?,
    )

    private val EXPRESSION = listOf(
        "expression", "vocab", "vocabkanji", "word", "kanji", "japanese", "term", "target", "front", "key",
    )
    private val READING = listOf(
        "expressionreading", "reading", "vocabkana", "kana", "furigana", "vocabfurigana", "wordreading",
        "hiragana", "expressionfurigana", "pronunciation",
    )
    private val MEANING = listOf(
        "meaning", "maindefinition", "definition", "vocabdef", "vocabmeaning", "english", "glossary",
        "translation", "back", "glosses",
    )

    fun pick(fields: List<String>): Mapping? {
        val expression = best(fields, EXPRESSION, exclude = emptySet()) ?: return null
        val reading = best(fields, READING, exclude = setOf(expression))
        val meaning = best(fields, MEANING, exclude = setOfNotNull(expression, reading))
        return Mapping(expression, reading, meaning)
    }

    private fun best(fields: List<String>, wanted: List<String>, exclude: Set<String>): String? {
        var bestField: String? = null
        var bestRank = Int.MAX_VALUE
        for (field in fields) {
            if (field in exclude) continue
            val key = normalize(field)
            val rank = wanted.indexOf(key)
            if (rank in 0 until bestRank) {
                bestRank = rank
                bestField = field
            }
        }
        return bestField
    }

    private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
}
