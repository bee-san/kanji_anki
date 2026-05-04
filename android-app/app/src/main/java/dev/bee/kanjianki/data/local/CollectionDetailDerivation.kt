package dev.bee.kanjianki.data.local

import dev.bee.kanjianki.data.ankidroid.AnkiDroidNoteSnapshot
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.SettingsSnapshot
import java.text.Normalizer

internal object CollectionDetailDerivation {
    fun derive(
        settings: SettingsSnapshot,
        notes: List<AnkiDroidNoteSnapshot>,
    ): Map<String, KanjiDetailSnapshot> {
        val accumulators = linkedMapOf<String, MutableKanjiDetail>()
        notes.forEach { note ->
            val normalizedExpression = normalizeText(note.expression)
            if (normalizedExpression.isBlank()) {
                return@forEach
            }
            val kanjiChars = extractKanjiChars(normalizedExpression).distinct()
            if (kanjiChars.isEmpty()) {
                return@forEach
            }
            val meanings = splitMeaningTokens(note.meaning)
            val readings = splitReadingTokens(note.reading)
            val onReadings = readings.filter(::looksLikeOnReading)
            val kunReadings = readings.filterNot(::looksLikeOnReading)
            kanjiChars.forEach { kanji ->
                val accumulator = accumulators.getOrPut(kanji) { MutableKanjiDetail(kanji) }
                accumulator.collectionExamples.add(normalizedExpression)
                meanings.forEach(accumulator.meanings::add)
                onReadings.forEach(accumulator.onReadings::add)
                kunReadings.forEach(accumulator.kunReadings::add)
            }
        }
        return accumulators.mapValues { (kanji, entry) ->
            val examples = entry.collectionExamples.toList()
            val meanings = entry.meanings.toList().take(MAX_DETAIL_TERMS)
            val onReadings = entry.onReadings.toList().take(MAX_DETAIL_TERMS)
            val kunReadings = entry.kunReadings.toList().take(MAX_DETAIL_TERMS)
            val keyword = meanings.firstOrNull() ?: examples.firstOrNull() ?: kanji
            KanjiDetailSnapshot(
                kanji = kanji,
                jitenRank = null,
                keyword = keyword,
                meanings = meanings.ifEmpty { listOf("Collection-derived detail") },
                onReadings = onReadings,
                kunReadings = kunReadings,
                components = emptyList(),
                componentHint = "",
                strokeCount = 0,
                browserSearch = buildBrowserSearch(
                    kanji = kanji,
                    modelNames = settings.noteModels,
                    searchFieldName = settings.expressionField,
                ),
                collectionExamples = examples.take(MAX_DETAIL_EXAMPLES),
                suspendedExamples = emptyList(),
                activeRecurringExamples = emptyList(),
                matureExamples = emptyList(),
            )
        }
    }

    private fun extractKanjiChars(text: String): List<String> =
        normalizeText(text)
            .asSequence()
            .map(Char::toString)
            .filter { value ->
                value.singleOrNull()?.let { char ->
                    Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN
                } == true
            }
            .toList()

    private fun normalizeText(text: String): String {
        val stripped = HTML_TAG_REGEX.replace(text, "")
        val normalized = Normalizer.normalize(stripped, Normalizer.Form.NFKC)
        return WHITESPACE_REGEX.replace(normalized, " ").trim()
    }

    private fun splitMeaningTokens(raw: String): List<String> =
        normalizeText(raw)
            .split(MEANING_SPLIT_REGEX)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun splitReadingTokens(raw: String): List<String> =
        normalizeText(raw)
            .split(READING_SPLIT_REGEX)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun looksLikeOnReading(reading: String): Boolean =
        reading.isNotBlank() && reading.all { char ->
            Character.UnicodeScript.of(char.code) == Character.UnicodeScript.KATAKANA ||
                char == 'ー' || char == '・'
        }

    private fun buildBrowserSearch(
        kanji: String,
        modelNames: List<String>,
        searchFieldName: String,
    ): String {
        val modelQuery = modelNames
            .filter(String::isNotBlank)
            .joinToString(" or ") { "note:\"$it\"" }
            .takeIf { it.isNotBlank() }
            ?.let { "($it)" }
            .orEmpty()
        val fieldQuery = "\"$searchFieldName:*$kanji*\""
        return if (modelQuery.isNotBlank()) {
            "$modelQuery $fieldQuery"
        } else {
            fieldQuery
        }
    }
}

private data class MutableKanjiDetail(
    val kanji: String,
    val collectionExamples: LinkedHashSet<String> = linkedSetOf(),
    val meanings: LinkedHashSet<String> = linkedSetOf(),
    val onReadings: LinkedHashSet<String> = linkedSetOf(),
    val kunReadings: LinkedHashSet<String> = linkedSetOf(),
)

private const val MAX_DETAIL_TERMS = 6
private const val MAX_DETAIL_EXAMPLES = 6
private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val WHITESPACE_REGEX = Regex("\\s+")
private val MEANING_SPLIT_REGEX = Regex("[;/,、・]+|\\s+-\\s+")
private val READING_SPLIT_REGEX = Regex("[\\s/,;、・]+")
