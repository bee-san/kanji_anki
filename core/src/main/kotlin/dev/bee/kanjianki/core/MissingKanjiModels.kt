package dev.bee.kanjianki.core

data class MissingKanjiCandidate(
    val literal: String,
    val meanings: List<String> = emptyList(),
    val onReadings: List<String> = emptyList(),
    val kunReadings: List<String> = emptyList(),
    val jitenRank: Int? = null,
) {
    val primaryMeaning: String
        get() = meanings.firstOrNull().orEmpty()

    val primaryReading: String
        get() = kunReadings.firstOrNull() ?: onReadings.firstOrNull().orEmpty()
}

data class MissingKanjiFrequencyRange(
    val minimumRank: Int,
    val maximumRank: Int,
    val includeUnranked: Boolean = false,
) {
    companion object {
        val TOP_1000 = MissingKanjiFrequencyRange(1, 1_000)
        val TOP_2000 = MissingKanjiFrequencyRange(1, 2_000)
        val TOP_3000 = MissingKanjiFrequencyRange(1, 3_000)
        val TOP_5000 = MissingKanjiFrequencyRange(1, 5_000)
    }
}

enum class MissingKanjiRangeError {
    MINIMUM_BELOW_ONE,
    MAXIMUM_BELOW_ONE,
    MINIMUM_ABOVE_MAXIMUM,
}

sealed interface MissingKanjiAnalysisResult {
    data class Success(val report: MissingKanjiReport) : MissingKanjiAnalysisResult

    data class InvalidRange(
        val range: MissingKanjiFrequencyRange,
        val error: MissingKanjiRangeError,
    ) : MissingKanjiAnalysisResult
}

data class MissingKanjiReport(
    val range: MissingKanjiFrequencyRange,
    val missing: List<MissingKanjiCandidate>,
    val uniqueObservedKanjiCount: Int,
    val uniqueDictionaryKanjiCount: Int,
    val eligibleDictionaryKanjiCount: Int,
    val eligibleRankedKanjiCount: Int,
    val eligibleUnrankedKanjiCount: Int,
    val presentEligibleKanjiCount: Int,
    val invalidObservedValueCount: Int,
    val invalidDictionaryCandidateCount: Int,
) {
    val missingKanjiCount: Int
        get() = missing.size
}
