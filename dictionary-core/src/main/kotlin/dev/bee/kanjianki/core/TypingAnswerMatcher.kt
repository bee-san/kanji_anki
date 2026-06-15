package dev.bee.kanjianki.core

import java.util.Locale
import java.util.regex.Pattern

object TypingAnswerMatcher {
    private val NON_ALPHA_NUMERIC_PATTERN: Pattern = Pattern.compile("[^a-z0-9\\s]")
    private val MULTI_WHITESPACE_PATTERN: Pattern = Pattern.compile("\\s+")
    private val ACCEPTED_MEANING_SPLIT: Pattern = Pattern.compile("[,;/]")

    @JvmStatic
    fun matches(
        lookup: DictionaryLookup?,
        kanji: String?,
        typedAnswer: String?,
        collectionMeaning: String?,
    ): Boolean {
        val normalizedAnswer = normalizeAnswer(typedAnswer)
        if (normalizedAnswer.isEmpty()) {
            return false
        }
        return acceptedMeanings(lookup, kanji, collectionMeaning)
            .any { normalizedAnswer == normalizeAnswer(it) }
    }

    @JvmStatic
    fun acceptedMeanings(
        lookup: DictionaryLookup?,
        kanji: String?,
        collectionMeaning: String?,
    ): List<String> {
        val accepted = mutableListOf<String>()
        val safeLookup = lookup ?: DictionaryLookup.empty()
        val entry = safeLookup.lookupKanji(kanji)
        if (entry != null) {
            for (meaning in entry.meanings) {
                addMeaningVariants(accepted, meaning)
            }
        }
        addMeaningVariants(accepted, StudyCueFormatter.cleanFallbackMeaning(collectionMeaning, "", 160))
        return accepted
    }

    private fun addMeaningVariants(accepted: MutableList<String>, raw: String?) {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || StudyCueFormatter.isCollectionClue(value)) {
            return
        }
        for (part in ACCEPTED_MEANING_SPLIT.split(value)) {
            val normalized = normalizeAnswer(part)
            if (normalized.isNotEmpty() && !containsNormalized(accepted, normalized)) {
                accepted.add(part.trim())
            }
        }
    }

    private fun containsNormalized(values: List<String>, normalizedNeedle: String): Boolean =
        values.any { normalizeAnswer(it) == normalizedNeedle }

    private fun normalizeAnswer(value: String?): String {
        if (value == null) {
            return ""
        }
        val cleaned = NON_ALPHA_NUMERIC_PATTERN
            .matcher(value.lowercase(Locale.ROOT))
            .replaceAll(" ")
        return MULTI_WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim()
    }
}
