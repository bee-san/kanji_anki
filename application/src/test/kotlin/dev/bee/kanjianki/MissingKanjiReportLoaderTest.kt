package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared Missing Kanji report computation, driven from an in-memory dictionary.
 *
 * Pins the paging and analysis the loader owns: the report's missing set is the
 * eligible dictionary kanji the observed set does not cover, and paging that spans
 * more than one page still returns every candidate exactly once.
 */
class MissingKanjiReportLoaderTest {
    @Test
    fun theReportIsTheEligibleKanjiTheCollectionDoesNotCover() {
        val dictionary = dictionary("脱" to 100, "説" to 200, "税" to 300, "鋭" to 400)

        val report = MissingKanjiReportLoader.load(
            dictionary = dictionary,
            observedKanji = setOf("脱", "説"),
            range = MissingKanjiFrequencyRange.TOP_1000,
        )

        assertEquals(4, report.eligibleDictionaryKanjiCount)
        assertEquals(setOf("税", "鋭"), report.missing.map { it.literal }.toSet())
        assertEquals(2, report.missingKanjiCount)
    }

    @Test
    fun pagingSpansMultiplePagesWithoutDroppingOrDuplicating() {
        // A page size below the candidate count forces the paging loop to iterate; the
        // final count check would fire if a page were dropped or double-counted.
        val kanji = "一二三四五六七八九十百千万円日月火水木金土上下左右中".toCharArray().map { it.toString() }
        val entries = kanji.mapIndexed { i, k -> k to (i + 1) }
        val report = MissingKanjiReportLoader.load(
            dictionary = dictionary(*entries.toTypedArray()),
            observedKanji = emptySet(),
            range = MissingKanjiFrequencyRange(minimumRank = 1, maximumRank = 1_000),
            pageSize = 10,
        )
        assertEquals(kanji.size, report.missingKanjiCount)
        assertEquals(kanji.size, report.missing.map { it.literal }.toSet().size)
    }

    @Test
    fun everyDictionaryKanjiIsMissingWhenNothingIsObserved() {
        val report = MissingKanjiReportLoader.load(
            dictionary = dictionary("脱" to 100, "説" to 200),
            observedKanji = emptySet(),
            range = MissingKanjiFrequencyRange.TOP_1000,
        )
        assertEquals(2, report.missingKanjiCount)
    }

    @Test
    fun anInvalidRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MissingKanjiReportLoader.load(
                dictionary = DictionaryLookup.empty(),
                observedKanji = emptySet(),
                range = MissingKanjiFrequencyRange(minimumRank = 500, maximumRank = 100),
            )
        }
    }

    @Test
    fun anEmptyDictionaryHasNothingMissing() {
        val report = MissingKanjiReportLoader.load(
            dictionary = DictionaryLookup.empty(),
            observedKanji = setOf("脱"),
            range = MissingKanjiFrequencyRange.TOP_2000,
        )
        assertEquals(0, report.missingKanjiCount)
        assertTrue(report.missing.isEmpty())
    }

    private fun dictionary(vararg entries: Pair<String, Int>): DictionaryLookup =
        DictionaryLookup.fromKanjiEntries(
            entries.map { (literal, rank) ->
                DictionaryLookup.KanjiEntry(
                    DictionaryLookup.KanjiEntryFields(
                        literal = literal,
                        meanings = listOf("meaning-$literal"),
                        onReadings = listOf("オン"),
                        kunReadings = listOf("くん"),
                        nanoriReadings = emptyList(),
                        strokeCount = 5,
                        grade = 1,
                        radical = 1,
                        kanjidicFrequency = rank,
                        jitenRank = rank,
                    ),
                )
            },
        )
}
