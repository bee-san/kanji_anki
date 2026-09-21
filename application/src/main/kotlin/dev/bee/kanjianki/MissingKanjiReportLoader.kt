package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.MissingKanjiAnalysisResult
import dev.bee.kanjianki.core.MissingKanjiAnalyzer
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiReport

/**
 * Builds a Missing Kanji report by paging the dictionary and analysing it against the
 * observed kanji.
 *
 * Promoted from `:app` into `:application` so both hosts build the report identically;
 * it depends only on `:core` (the dictionary lookup and the analyzer), so nothing
 * Android-specific crossed with it. The paging invariants — a stable total, a strictly
 * advancing offset, a final count matching the promised total — are the same ones the
 * Android loader enforced.
 */
object MissingKanjiReportLoader {
    private const val DEFAULT_PAGE_SIZE = 500

    fun load(
        dictionary: DictionaryLookup,
        observedKanji: Set<String>,
        range: MissingKanjiFrequencyRange,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): MissingKanjiReport {
        require(MissingKanjiAnalyzer.validateRange(range) == null) {
            "Missing Kanji frequency range is invalid."
        }
        val boundedPageSize = pageSize.coerceIn(1, DictionaryLookup.MAX_KANJI_PAGE_SIZE)
        val dictionaryRange = DictionaryLookup.JitenRankRange(
            minimumRank = range.minimumRank,
            maximumRank = range.maximumRank,
            includeUnranked = range.includeUnranked,
        )
        val candidates = ArrayList<MissingKanjiCandidate>()
        var offset = 0
        var expectedTotal: Int? = null
        while (true) {
            check(!Thread.currentThread().isInterrupted) {
                "Missing Kanji report loading was cancelled."
            }
            val page = dictionary.kanjiByJitenRank(
                range = dictionaryRange,
                offset = offset,
                limit = boundedPageSize,
            )
            val knownTotal = expectedTotal
            if (knownTotal == null) {
                expectedTotal = page.totalEligible.coerceAtLeast(0)
                candidates.ensureCapacity(requireNotNull(expectedTotal))
            } else {
                check(page.totalEligible == knownTotal) {
                    "Dictionary candidate total changed while paging."
                }
            }
            page.entries.forEach { entry ->
                candidates.add(
                    MissingKanjiCandidate(
                        literal = entry.literal,
                        meanings = entry.meanings,
                        onReadings = entry.onReadings,
                        kunReadings = entry.kunReadings,
                        jitenRank = entry.jitenRank,
                    ),
                )
            }
            val nextOffset = page.nextOffset ?: break
            check(page.entries.isNotEmpty() && nextOffset > offset) {
                "Dictionary candidate paging did not advance."
            }
            offset = nextOffset
        }
        check(expectedTotal == candidates.size) {
            "Dictionary candidate paging returned ${candidates.size} of $expectedTotal rows."
        }
        return when (
            val analysis = MissingKanjiAnalyzer.analyze(
                dictionaryCandidates = candidates,
                observedKanji = observedKanji,
                range = range,
            )
        ) {
            is MissingKanjiAnalysisResult.Success -> analysis.report
            is MissingKanjiAnalysisResult.InvalidRange ->
                error("Validated Missing Kanji range was rejected: ${analysis.error}.")
        }
    }
}
