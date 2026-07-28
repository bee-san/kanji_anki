package dev.bee.kanjianki.core

import java.util.LinkedHashMap
import java.util.LinkedHashSet

object MissingKanjiAnalyzer {
    fun analyze(
        dictionaryCandidates: Iterable<MissingKanjiCandidate>,
        observedKanji: Iterable<String>,
        range: MissingKanjiFrequencyRange,
    ): MissingKanjiAnalysisResult {
        validateRange(range)?.let { error ->
            return MissingKanjiAnalysisResult.InvalidRange(range, error)
        }

        val observed = LinkedHashSet<String>()
        var invalidObserved = 0
        for (rawLiteral in observedKanji) {
            val literal = normalizeLiteral(rawLiteral)
            if (literal == null) {
                invalidObserved += 1
            } else {
                observed.add(literal)
            }
        }

        val dictionary = LinkedHashMap<String, MissingKanjiCandidate>()
        var invalidCandidates = 0
        for (rawCandidate in dictionaryCandidates) {
            val candidate = normalizeCandidate(rawCandidate)
            if (candidate == null) {
                invalidCandidates += 1
                continue
            }
            val existing = dictionary[candidate.literal]
            dictionary[candidate.literal] = if (existing == null) {
                candidate
            } else {
                preferredCandidate(existing, candidate)
            }
        }

        val eligible = dictionary.values
            .asSequence()
            .filter { candidate -> isEligible(candidate, range) }
            .sortedWith(CANDIDATE_COMPARATOR)
            .toList()
        val missing = eligible.filterNot { candidate -> observed.contains(candidate.literal) }
        val eligibleRanked = eligible.count { candidate -> candidate.jitenRank != null }
        val eligibleUnranked = eligible.size - eligibleRanked

        return MissingKanjiAnalysisResult.Success(
            MissingKanjiReport(
                range = range,
                missing = missing,
                uniqueObservedKanjiCount = observed.size,
                uniqueDictionaryKanjiCount = dictionary.size,
                eligibleDictionaryKanjiCount = eligible.size,
                eligibleRankedKanjiCount = eligibleRanked,
                eligibleUnrankedKanjiCount = eligibleUnranked,
                presentEligibleKanjiCount = eligible.size - missing.size,
                invalidObservedValueCount = invalidObserved,
                invalidDictionaryCandidateCount = invalidCandidates,
            ),
        )
    }

    fun validateRange(range: MissingKanjiFrequencyRange): MissingKanjiRangeError? {
        return when {
            range.minimumRank < 1 -> MissingKanjiRangeError.MINIMUM_BELOW_ONE
            range.maximumRank < 1 -> MissingKanjiRangeError.MAXIMUM_BELOW_ONE
            range.minimumRank > range.maximumRank -> MissingKanjiRangeError.MINIMUM_ABOVE_MAXIMUM
            else -> null
        }
    }

    internal fun normalizeLiteral(rawLiteral: String?): String? {
        val literal = rawLiteral?.trim().orEmpty()
        if (literal.isEmpty() || literal.codePointCount(0, literal.length) != 1) {
            return null
        }
        val codePoint = literal.codePointAt(0)
        if (!Character.isValidCodePoint(codePoint)) {
            return null
        }
        if (literal.length == 1 && Character.isSurrogate(literal[0])) {
            return null
        }
        return String(Character.toChars(codePoint))
    }

    private fun normalizeCandidate(candidate: MissingKanjiCandidate): MissingKanjiCandidate? {
        val literal = normalizeLiteral(candidate.literal) ?: return null
        val rank = candidate.jitenRank
        if (rank != null && rank < 1) {
            return null
        }
        return MissingKanjiCandidate(
            literal = literal,
            meanings = normalizeTextList(candidate.meanings),
            onReadings = normalizeTextList(candidate.onReadings),
            kunReadings = normalizeTextList(candidate.kunReadings),
            jitenRank = rank,
        )
    }

    private fun normalizeTextList(values: List<String>): List<String> {
        return values
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }

    private fun preferredCandidate(
        first: MissingKanjiCandidate,
        second: MissingKanjiCandidate,
    ): MissingKanjiCandidate {
        return if (CANONICAL_DUPLICATE_COMPARATOR.compare(first, second) <= 0) first else second
    }

    private fun isEligible(
        candidate: MissingKanjiCandidate,
        range: MissingKanjiFrequencyRange,
    ): Boolean {
        val rank = candidate.jitenRank
        return if (rank == null) {
            range.includeUnranked
        } else {
            rank in range.minimumRank..range.maximumRank
        }
    }

    private val CANDIDATE_COMPARATOR = Comparator<MissingKanjiCandidate> { left, right ->
        val rankComparison = compareValues(left.jitenRank ?: Int.MAX_VALUE, right.jitenRank ?: Int.MAX_VALUE)
        if (rankComparison != 0) {
            rankComparison
        } else {
            compareLiterals(left.literal, right.literal)
        }
    }

    private val CANONICAL_DUPLICATE_COMPARATOR = Comparator<MissingKanjiCandidate> { left, right ->
        val rankComparison = compareValues(left.jitenRank ?: Int.MAX_VALUE, right.jitenRank ?: Int.MAX_VALUE)
        if (rankComparison != 0) {
            rankComparison
        } else {
            canonicalText(left).compareTo(canonicalText(right))
        }
    }

    private fun canonicalText(candidate: MissingKanjiCandidate): String {
        return buildString {
            append(candidate.meanings.joinToString("\u0000"))
            append('\u0001')
            append(candidate.onReadings.joinToString("\u0000"))
            append('\u0001')
            append(candidate.kunReadings.joinToString("\u0000"))
        }
    }

    private fun compareLiterals(left: String, right: String): Int {
        val codePointComparison = compareValues(left.codePointAt(0), right.codePointAt(0))
        return if (codePointComparison != 0) codePointComparison else left.compareTo(right)
    }
}
