package dev.bee.kanjianki.core

import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Builds the canonical payload shared by direct AnkiDroid writes and CSV export.
 */
object MissingKanjiExportPlanner {
    const val DEFAULT_DECK_NAME: String = "Kani::Missing Kanji"
    const val MODEL_NAME: String = "Kani Missing Kanji"
    const val TEMPLATE_NAME: String = "Recognition"
    const val TAG: String = "kani_missing_kanji"
    const val SOURCE_ID_PREFIX: String = "kani-missing:"

    val FIELD_NAMES: List<String> = listOf(
        "Kanji",
        "Meaning",
        "OnReading",
        "KunReading",
        "JitenRank",
        "SourceId",
    )

    const val QUESTION_FORMAT: String =
        """<div class="kani-kanji">{{Kanji}}</div>"""

    const val ANSWER_FORMAT: String =
        """{{FrontSide}}<hr id="answer">""" +
            """{{#Meaning}}<div class="kani-meaning">{{Meaning}}</div>{{/Meaning}}""" +
            """{{#OnReading}}<div class="kani-reading"><span>On:</span> {{OnReading}}</div>{{/OnReading}}""" +
            """{{#KunReading}}<div class="kani-reading"><span>Kun:</span> {{KunReading}}</div>{{/KunReading}}""" +
            """{{#JitenRank}}<div class="kani-rank">Jiten rank {{JitenRank}}</div>{{/JitenRank}}"""

    const val CSS: String =
        """.card { font-family: sans-serif; font-size: 22px; text-align: center; color: #202124; background: #ffffff; }""" +
            """.kani-kanji { font-size: 72px; line-height: 1.2; margin: 16px 0; }""" +
            """.kani-meaning { font-size: 28px; font-weight: 600; margin: 12px 0; }""" +
            """.kani-reading { margin: 6px 0; }""" +
            """.kani-reading span, .kani-rank { color: #5f6368; }""" +
            """.kani-rank { font-size: 14px; margin-top: 14px; }""" +
            """.nightMode .card { color: #f1f3f4; background: #202124; }""" +
            """.nightMode .kani-reading span, .nightMode .kani-rank { color: #bdc1c6; }"""

    data class ExportNote(
        val literal: String,
        val meaning: String,
        val onReading: String,
        val kunReading: String,
        val jitenRank: Int?,
        val sourceId: String,
    ) {
        val fields: List<String>
            get() = listOf(
                literal,
                meaning,
                onReading,
                kunReading,
                jitenRank?.toString().orEmpty(),
                sourceId,
            )
    }

    data class Plan(
        val requestedCount: Int,
        val notes: List<ExportNote>,
        val invalidLiterals: Set<String>,
        val invalidCount: Int,
        val duplicateCount: Int,
    )

    @JvmStatic
    fun plan(candidates: Iterable<MissingKanjiCandidate>): Plan {
        val accumulated = LinkedHashMap<String, Accumulator>()
        val invalid = LinkedHashSet<String>()
        var requestedCount = 0
        var invalidCount = 0
        var duplicateCount = 0
        for (candidate in candidates) {
            requestedCount += 1
            val literal = TextUtil.normalizeSingleKanji(candidate.literal)
            if (literal.isEmpty()) {
                invalidCount += 1
                invalid.add(candidate.literal.trim())
                continue
            }
            val current = accumulated[literal]
            if (current == null) {
                accumulated[literal] = Accumulator(literal).apply { merge(candidate) }
            } else {
                duplicateCount += 1
                current.merge(candidate)
            }
        }
        val notes = accumulated.values
            .map(Accumulator::toExportNote)
            .sortedWith(NOTE_COMPARATOR)
        return Plan(
            requestedCount = requestedCount,
            notes = Collections.unmodifiableList(notes),
            invalidLiterals = Collections.unmodifiableSet(invalid),
            invalidCount = invalidCount,
            duplicateCount = duplicateCount,
        )
    }

    @JvmStatic
    fun sourceId(literal: String): String {
        val normalized = TextUtil.normalizeSingleKanji(literal)
        require(normalized.isNotEmpty()) { "Source ID requires one kanji literal." }
        return SOURCE_ID_PREFIX + normalized
    }

    private class Accumulator(private val literal: String) {
        private val meanings = LinkedHashSet<String>()
        private val onReadings = LinkedHashSet<String>()
        private val kunReadings = LinkedHashSet<String>()
        private var rank: Int? = null

        fun merge(candidate: MissingKanjiCandidate) {
            normalizeValues(candidate.meanings, meanings)
            normalizeValues(candidate.onReadings, onReadings)
            normalizeValues(candidate.kunReadings, kunReadings)
            candidate.jitenRank?.takeIf { it > 0 }?.let { candidateRank ->
                rank = minOf(rank ?: candidateRank, candidateRank)
            }
        }

        fun toExportNote(): ExportNote = ExportNote(
            literal = literal,
            meaning = htmlEscape(meanings.joinToString("; ")),
            onReading = htmlEscape(onReadings.joinToString("; ")),
            kunReading = htmlEscape(kunReadings.joinToString("; ")),
            jitenRank = rank,
            sourceId = sourceId(literal),
        )
    }

    private fun normalizeValues(values: Iterable<String>, output: MutableSet<String>) {
        values.asSequence()
            .map(DictionaryLookup::normalize)
            .filter(String::isNotEmpty)
            .forEach(output::add)
    }

    private fun htmlEscape(value: String): String = buildString(value.length) {
        for ((index, character) in value.withIndex()) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                '=', '+', '-', '@' -> {
                    if (index == 0) {
                        append("&#")
                        append(character.code)
                        append(';')
                    } else {
                        append(character)
                    }
                }
                else -> append(character)
            }
        }
    }

    private val NOTE_COMPARATOR = Comparator<ExportNote> { left, right ->
        val rankComparison = compareValues(
            left.jitenRank ?: Int.MAX_VALUE,
            right.jitenRank ?: Int.MAX_VALUE,
        )
        if (rankComparison != 0) {
            rankComparison
        } else {
            compareValues(left.literal.codePointAt(0), right.literal.codePointAt(0))
        }
    }
}
