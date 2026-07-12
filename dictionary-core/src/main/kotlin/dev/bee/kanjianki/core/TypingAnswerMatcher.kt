package dev.bee.kanjianki.core

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object TypingAnswerMatcher {
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
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        val cleaned = StringBuilder(decomposed.length)
        var pendingSpace = false
        var baseScript: Character.UnicodeScript? = null
        var offset = 0
        while (offset < decomposed.length) {
            val codePoint = decomposed.codePointAt(offset)
            when {
                Character.isLetterOrDigit(codePoint) -> {
                    if (pendingSpace && cleaned.isNotEmpty()) {
                        cleaned.append(' ')
                    }
                    cleaned.appendCodePoint(codePoint)
                    baseScript = if (Character.isLetter(codePoint)) {
                        Character.UnicodeScript.of(codePoint)
                    } else {
                        null
                    }
                    pendingSpace = false
                }
                isCombiningMark(codePoint) -> {
                    // Latin accents are answer-optional, but marks such as the
                    // Japanese dakuten carry meaning and must remain distinct.
                    if (baseScript != null && baseScript != Character.UnicodeScript.LATIN) {
                        cleaned.appendCodePoint(codePoint)
                    }
                }
                else -> {
                    pendingSpace = cleaned.isNotEmpty()
                    baseScript = null
                }
            }
            offset += Character.charCount(codePoint)
        }
        return Normalizer.normalize(cleaned.toString(), Normalizer.Form.NFC)
    }

    private fun isCombiningMark(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
            -> true
        else -> false
    }
}
