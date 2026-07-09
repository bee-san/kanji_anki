package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendedImportPolicyTest {
    @Test
    fun activeRowsReturnsOriginalWhenNothingIsSuspendedAndFiltersLocalSuspensions() {
        val rows = listOf(row("拉"), row("裂"))

        val unchanged = SuspendedImportPolicy.activeRows(rows, emptySet())
        val filtered = SuspendedImportPolicy.activeRows(rows, setOf("裂"))

        assertSame(rows, unchanged)
        assertEquals(1, filtered.size)
        assertEquals("拉", filtered[0].kanji)
        assertTrue(SuspendedImportPolicy.activeRows(null, emptySet()).isEmpty())
    }

    @Test
    fun importRangeAcceptsUnknownRankButRejectsOutOfRange() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()

        // Unknown-rank kanji (not in Jiten) import by default: they are rare
        // characters the user deliberately suspended. Only known ranks are gated
        // by the frequency window.
        assertTrue(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("謎", null, 1L), settings))
        assertFalse(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("謎", 99, 2L), settings))
        assertFalse(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("謎", 3001, 3L), settings))
        assertTrue(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("箱", 2500, 4L), settings))
        assertFalse(SuspendedImportPolicy.importInFrequencyRange(null, settings))
    }

    @Test
    fun mergeSkipsOutOfRangeEntriesKeepsInRangeAndUnknownRanks() {
        val merged = SuspendedImportPolicy.mergeSuspendedImports(
            null,
            listOf(
                suspendedImport("低", 50, 1L),
                suspendedImport("箱", 2500, 2L),
                suspendedImport("疎", 5000, 3L),
                suspendedImport("謎", null, 4L)
            ),
            RecordsSyncModels.Settings.kikuDefaults()
        )

        // Out-of-range known ranks (低=50, 疎=5000) are skipped; the in-range
        // 箱 and the unknown-rank 謎 are both kept.
        assertEquals(2, merged.size)
        val kanji = merged.map { it.kanji }.toSet()
        assertTrue(kanji.contains("箱"))
        assertTrue(kanji.contains("謎"))
    }

    @Test
    fun suspendedImportsOnlyDropsActiveSourcesAndEmptyImports() {
        val mixed = RecordsImportModels.SuspendedImport(
            "箱",
            2500,
            true,
            3000,
            listOf(
                suspendedImport("箱", 2500, 1L).sources[0],
                activeSource("箱", 2L)
            )
        )
        val activeOnly = RecordsImportModels.SuspendedImport(
            "認",
            200,
            true,
            3000,
            listOf(activeSource("認", 3L))
        )

        val filtered = SuspendedImportPolicy.suspendedImportsOnly(listOf(mixed, activeOnly))

        assertEquals(1, filtered.size)
        assertEquals("箱", filtered[0].kanji)
        assertEquals(1, filtered[0].sources.size)
        assertTrue(filtered[0].sources[0].suspended)
        assertTrue(SuspendedImportPolicy.suspendedImportsOnly(null).isEmpty())
    }

    @Test
    fun mergeDeduplicatesSourcesAndUsesLargestCutoff() {
        val merged = SuspendedImportPolicy.mergeSuspendedImports(
            listOf(suspendedImport("箱", 2500, 1L, 1200)),
            listOf(
                suspendedImport("箱", 2500, 1L, 1200),
                suspendedImport("箱", 2500, 2L, 3000)
            ),
            RecordsSyncModels.Settings.kikuDefaults()
        )

        assertEquals(1, merged.size)
        val built = merged[0]
        assertEquals(Integer.valueOf(2500), built.jitenRank)
        assertTrue(built.rankKnown)
        assertEquals(3000, built.cutoffUsed)
        assertEquals(2, built.sources.size)
    }

    @Test
    fun mergeKeepsInitialKnownRankWhenLaterSourcesAlsoHaveRanks() {
        val merged = SuspendedImportPolicy.mergeSuspendedImports(
            listOf(suspendedImport("箱", 1800, 1L, 1200)),
            listOf(
                suspendedImport("箱", 1800, 1L, 1200),
                suspendedImport("箱", 2500, 2L, 3000)
            ),
            RecordsSyncModels.Settings.kikuDefaults()
        )

        assertEquals(1, merged.size)
        val built = merged[0]
        assertEquals(Integer.valueOf(1800), built.jitenRank)
        assertTrue(built.rankKnown)
        assertEquals(3000, built.cutoffUsed)
        assertEquals(2, built.sources.size)
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            100,
            "meaning",
            "reading",
            "browser",
            0,
            "reason",
            "reason",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )

    private fun suspendedImport(kanji: String, rank: Int?, cardId: Long): RecordsImportModels.SuspendedImport =
        suspendedImport(kanji, rank, cardId, 3000)

    private fun suspendedImport(
        kanji: String,
        rank: Int?,
        cardId: Long,
        cutoff: Int
    ): RecordsImportModels.SuspendedImport =
        RecordsImportModels.SuspendedImport(
            kanji,
            rank,
            rank != null,
            cutoff,
            listOf(
                RecordsImportModels.SuspendedSource(
                    kanji,
                    cardId,
                    cardId,
                    kanji,
                    "かな",
                    "meaning",
                    RecordsImportModels.SuspendedSourceDetails.builder("${kanji}を見た。").build()
                )
            )
        )

    private fun activeSource(kanji: String, cardId: Long): RecordsImportModels.SuspendedSource =
        RecordsImportModels.SuspendedSource(
            kanji,
            cardId,
            cardId,
            kanji,
            "かな",
            "meaning",
            RecordsImportModels.SuspendedSourceDetails.builder("${kanji}を見た。")
                .suspended(false)
                .sourceType(RecordsBase.SOURCE_ACTIVE)
                .build()
        )
}
