package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class MissingKanjiScreenModelTest {
    @Test
    fun customRangeValidationRejectsBlankNonPositiveAndInvertedBounds() {
        assertEquals(
            MissingKanjiRangeInputResult.Invalid("positive"),
            parseMissingKanjiRange("", "1000", false),
        )
        assertEquals(
            MissingKanjiRangeInputResult.Invalid("positive"),
            parseMissingKanjiRange("0", "1000", false),
        )
        assertEquals(
            MissingKanjiRangeInputResult.Invalid("inverted"),
            parseMissingKanjiRange("2000", "1000", false),
        )
        assertEquals(
            MissingKanjiRangeInputResult.Valid(
                MissingKanjiFrequencyRange(200, 1_000, includeUnranked = true),
            ),
            parseMissingKanjiRange("200", "1000", true),
        )
    }

    @Test
    fun reportLoaderPagesCandidatesAndSubtractsObservedKanji() {
        val entries = (1..1_200).map { rank ->
            entry(
                literal = literal(rank),
                rank = rank,
                meaning = "meaning $rank",
            )
        }
        val lookup = TrackingLookup(entries)

        val report = MissingKanjiReportLoader.load(
            dictionary = lookup,
            observedKanji = setOf(literal(1), literal(1_200), "外"),
            range = MissingKanjiFrequencyRange(1, 1_200),
            pageSize = 128,
        )

        assertEquals(10, lookup.pageCalls)
        assertEquals(1_200, report.eligibleDictionaryKanjiCount)
        assertEquals(3, report.uniqueObservedKanjiCount)
        assertEquals(2, report.presentEligibleKanjiCount)
        assertEquals(1_198, report.missingKanjiCount)
        assertEquals(2, report.missing.first().jitenRank)
        assertEquals(1_199, report.missing.last().jitenRank)
    }

    @Test
    fun reportLoaderIncludesUnrankedOnlyWhenExplicitlyRequested() {
        val lookup = TrackingLookup(
            listOf(
                entry("一", 1, "one"),
                entry("二", 2, "two"),
                entry("𠮷", null, "good fortune"),
            ),
        )

        val ranked = MissingKanjiReportLoader.load(
            dictionary = lookup,
            observedKanji = emptySet(),
            range = MissingKanjiFrequencyRange(1, 2),
        )
        val withUnranked = MissingKanjiReportLoader.load(
            dictionary = lookup,
            observedKanji = emptySet(),
            range = MissingKanjiFrequencyRange(1, 2, includeUnranked = true),
        )

        assertEquals(listOf("一", "二"), ranked.missing.map(MissingKanjiCandidate::literal))
        assertEquals(listOf("一", "二", "𠮷"), withUnranked.missing.map(MissingKanjiCandidate::literal))
    }

    @Test
    fun reportLoaderRejectsNonAdvancingDictionaryPages() {
        val lookup = object : DictionaryLookup() {
            override fun lookupKanji(literal: String?): KanjiEntry? = null

            override fun kanjiCount(): Int = 1

            override fun kanjiByJitenRank(
                range: JitenRankRange,
                offset: Int,
                limit: Int,
            ): KanjiEntryPage = KanjiEntryPage(
                entries = listOf(entry("一", 1, "one")),
                totalEligible = 2,
                nextOffset = offset,
            )
        }

        assertThrows(IllegalStateException::class.java) {
            MissingKanjiReportLoader.load(
                dictionary = lookup,
                observedKanji = emptySet(),
                range = MissingKanjiFrequencyRange.TOP_1000,
            )
        }
    }

    @Test
    fun reportLoaderStopsBeforeQueryingAnInterruptedThread() {
        val lookup = TrackingLookup(listOf(entry("一", 1, "one")))

        Thread.currentThread().interrupt()
        try {
            assertThrows(IllegalStateException::class.java) {
                MissingKanjiReportLoader.load(
                    dictionary = lookup,
                    observedKanji = emptySet(),
                    range = MissingKanjiFrequencyRange.TOP_1000,
                )
            }
            assertEquals(0, lookup.pageCalls)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun localSearchMatchesLiteralMeaningReadingAndRankUsingAllTerms() {
        val rows = missingKanjiRows(
            listOf(
                MissingKanjiCandidate(
                    literal = "語",
                    meanings = listOf("language", "word"),
                    onReadings = listOf("ゴ"),
                    kunReadings = listOf("かた.る"),
                    jitenRank = 301,
                ),
                MissingKanjiCandidate(
                    literal = "凪",
                    meanings = listOf("calm"),
                    kunReadings = listOf("なぎ"),
                    jitenRank = 2_641,
                ),
            ),
        )

        assertEquals(listOf("語"), filterMissingKanjiRows(rows, "language 301").map { it.literal })
        assertEquals(listOf("語"), filterMissingKanjiRows(rows, "ゴ").map { it.literal })
        assertEquals(listOf("凪"), filterMissingKanjiRows(rows, "なぎ").map { it.literal })
        assertEquals(rows, filterMissingKanjiRows(rows, "  "))
        assertTrue(filterMissingKanjiRows(rows, "language 999").isEmpty())
    }

    @Test
    fun fiveThousandRowModelAndFilterStayBounded() {
        val candidates = (1..5_000).map { rank ->
            MissingKanjiCandidate(
                literal = literal(rank),
                meanings = listOf(if (rank == 4_999) "needle target" else "meaning $rank"),
                onReadings = listOf("reading-$rank"),
                jitenRank = rank,
            )
        }
        lateinit var rows: List<MissingKanjiRowModel>

        val elapsed = measureTimeMillis {
            rows = missingKanjiRows(candidates)
            repeat(10) {
                assertEquals(1, filterMissingKanjiRows(rows, "needle 4999").size)
            }
        }

        assertEquals(5_000, rows.size)
        assertTrue("5,000-row model/filter took ${elapsed}ms", elapsed < 1_000L)
    }

    @Test
    fun scanProgressCoercesCountsAndTracksCancellation() {
        val progress = MissingKanjiScanProgressState()

        progress.update(notesScanned = 42, uniqueKanjiCount = 19, skippedNotes = -1)
        progress.markCancelling()

        assertEquals(42, progress.notesScanned)
        assertEquals(19, progress.uniqueKanjiCount)
        assertEquals(0, progress.skippedNotes)
        assertTrue(progress.isCancelling)
    }

    @Test
    fun screenshotFixtureIsAReadyReadOnlyReport() {
        val model = screenshotMissingKanjiScreenModel()
        val report = (model.content as MissingKanjiContentModel.Report).report

        assertEquals(MissingKanjiProviderAvailability.READY, model.providerAvailability)
        assertEquals(MissingKanjiPreset.TOP_5000, model.frequency.preset)
        assertEquals(4, report.rows.size)
        assertFalse(model.destinations.addToKaniEnabled)
        assertFalse(model.destinations.createAnkiDeckEnabled)
    }

    private class TrackingLookup(entries: List<DictionaryLookup.KanjiEntry>) : DictionaryLookup() {
        private val delegate = DictionaryLookup.fromKanjiEntries(entries)
        var pageCalls: Int = 0
            private set

        override fun lookupKanji(literal: String?): KanjiEntry? = delegate.lookupKanji(literal)

        override fun kanjiCount(): Int = delegate.kanjiCount()

        override fun eligibleKanjiCount(range: JitenRankRange): Int =
            delegate.eligibleKanjiCount(range)

        override fun kanjiByJitenRank(
            range: JitenRankRange,
            offset: Int,
            limit: Int,
        ): KanjiEntryPage {
            pageCalls += 1
            return delegate.kanjiByJitenRank(range, offset, limit)
        }
    }

    private companion object {
        fun literal(index: Int): String = String(Character.toChars(0x4E00 + index))

        fun entry(
            literal: String,
            rank: Int?,
            meaning: String,
        ): DictionaryLookup.KanjiEntry = DictionaryLookup.KanjiEntry(
            DictionaryLookup.KanjiEntryFields(
                literal = literal,
                meanings = listOf(meaning),
                onReadings = listOf("オン"),
                kunReadings = listOf("くん"),
                nanoriReadings = emptyList(),
                strokeCount = 1,
                grade = 1,
                radical = 1,
                kanjidicFrequency = rank ?: 0,
                jitenRank = rank,
            ),
        )
    }
}
