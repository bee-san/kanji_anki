package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingKanjiAnalyzerTest {
    @Test
    fun emptyCollectionReturnsEveryEligibleUniqueCandidateAsMissing() {
        val result = success(
            candidates(
                candidate("日", 1),
                candidate("月", 2),
                candidate("日", 99),
                candidate("外", 101),
            ),
            emptyList(),
            MissingKanjiFrequencyRange(1, 100),
        )

        assertEquals(listOf("日", "月"), result.missing.map { it.literal })
        assertEquals(0, result.uniqueObservedKanjiCount)
        assertEquals(3, result.uniqueDictionaryKanjiCount)
        assertEquals(2, result.eligibleDictionaryKanjiCount)
        assertEquals(2, result.missingKanjiCount)
    }

    @Test
    fun fullCoverageAndDuplicatesCollapseToUniqueLiterals() {
        val result = success(
            candidates(candidate("日", 1), candidate("月", 2)),
            listOf("日", "日", "月"),
            MissingKanjiFrequencyRange(1, 2),
        )

        assertTrue(result.missing.isEmpty())
        assertEquals(2, result.uniqueObservedKanjiCount)
        assertEquals(2, result.presentEligibleKanjiCount)
    }

    @Test
    fun rankBoundsAreInclusiveAndUnrankedRequiresExplicitInclusion() {
        val dictionary = candidates(
            candidate("一", 9),
            candidate("二", 10),
            candidate("三", 20),
            candidate("四", 21),
            candidate("無", null),
        )

        val rankedOnly = success(
            dictionary,
            emptyList(),
            MissingKanjiFrequencyRange(10, 20),
        )
        val withUnranked = success(
            dictionary,
            emptyList(),
            MissingKanjiFrequencyRange(10, 20, includeUnranked = true),
        )

        assertEquals(listOf("二", "三"), rankedOnly.missing.map { it.literal })
        assertEquals(2, rankedOnly.eligibleRankedKanjiCount)
        assertEquals(0, rankedOnly.eligibleUnrankedKanjiCount)
        assertEquals(listOf("二", "三", "無"), withUnranked.missing.map { it.literal })
        assertEquals(1, withUnranked.eligibleUnrankedKanjiCount)
    }

    @Test
    fun supplementaryPlaneLiteralRemainsOneSelectionAndSortKey() {
        val supplementary = "\uD842\uDFB7"
        val result = success(
            candidates(
                candidate(supplementary, 1),
                candidate("吉", 1),
            ),
            listOf(supplementary),
            MissingKanjiFrequencyRange(1, 1),
        )

        assertEquals(1, supplementary.codePointCount(0, supplementary.length))
        assertEquals(listOf("吉"), result.missing.map { it.literal })
        assertEquals(1, result.uniqueObservedKanjiCount)
    }

    @Test
    fun equalRanksSortByUnicodeCodePointRatherThanInputOrder() {
        val result = success(
            candidates(
                candidate("月", 50),
                candidate("一", 50),
                candidate("日", 50),
            ),
            emptyList(),
            MissingKanjiFrequencyRange(50, 50),
        )

        assertEquals(listOf("一", "日", "月"), result.missing.map { it.literal })
    }

    @Test
    fun duplicateCandidateChoiceIsStableAcrossInputOrder() {
        val first = MissingKanjiCandidate("日", meanings = listOf("sun"), jitenRank = 20)
        val second = MissingKanjiCandidate("日", meanings = listOf("day"), jitenRank = 10)

        val forward = success(
            candidates(first, second),
            emptyList(),
            MissingKanjiFrequencyRange(1, 20),
        )
        val reverse = success(
            candidates(second, first),
            emptyList(),
            MissingKanjiFrequencyRange(1, 20),
        )

        assertEquals(forward.missing, reverse.missing)
        assertEquals(10, forward.missing.single().jitenRank)
        assertEquals("day", forward.missing.single().primaryMeaning)
    }

    @Test
    fun invalidInputsAreCountedWithoutPollutingTheReport() {
        val result = success(
            candidates(
                candidate("", 1),
                candidate("日本", 2),
                candidate("日", 0),
                candidate("月", 2),
            ),
            listOf("", "日本", "\uD800", "月"),
            MissingKanjiFrequencyRange(1, 10),
        )

        assertTrue(result.missing.isEmpty())
        assertEquals(3, result.invalidDictionaryCandidateCount)
        assertEquals(3, result.invalidObservedValueCount)
        assertEquals(1, result.uniqueObservedKanjiCount)
    }

    @Test
    fun invalidRangesReturnExplicitErrors() {
        assertRangeError(
            MissingKanjiFrequencyRange(0, 100),
            MissingKanjiRangeError.MINIMUM_BELOW_ONE,
        )
        assertRangeError(
            MissingKanjiFrequencyRange(1, 0),
            MissingKanjiRangeError.MAXIMUM_BELOW_ONE,
        )
        assertRangeError(
            MissingKanjiFrequencyRange(100, 10),
            MissingKanjiRangeError.MINIMUM_ABOVE_MAXIMUM,
        )
    }

    private fun assertRangeError(
        range: MissingKanjiFrequencyRange,
        expected: MissingKanjiRangeError,
    ) {
        val result = MissingKanjiAnalyzer.analyze(emptyList(), emptyList(), range)
        assertTrue(result is MissingKanjiAnalysisResult.InvalidRange)
        assertEquals(expected, (result as MissingKanjiAnalysisResult.InvalidRange).error)
    }

    private fun success(
        dictionary: List<MissingKanjiCandidate>,
        observed: List<String>,
        range: MissingKanjiFrequencyRange,
    ): MissingKanjiReport {
        val result = MissingKanjiAnalyzer.analyze(dictionary, observed, range)
        assertTrue(result is MissingKanjiAnalysisResult.Success)
        return (result as MissingKanjiAnalysisResult.Success).report
    }

    private fun candidate(literal: String, rank: Int?): MissingKanjiCandidate {
        return MissingKanjiCandidate(literal, meanings = listOf("meaning-$literal"), jitenRank = rank)
    }

    private fun candidates(vararg values: MissingKanjiCandidate): List<MissingKanjiCandidate> {
        return values.toList()
    }
}
