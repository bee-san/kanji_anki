package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class SuspendedImportPolicyTest {
    @Test
    public void activeRowsReturnsOriginalWhenNothingIsSuspendedAndFiltersLocalSuspensions() {
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("拉"), row("裂"));

        List<RecordsImportModels.DashboardRow> unchanged = SuspendedImportPolicy.activeRows(rows, Collections.emptySet());
        List<RecordsImportModels.DashboardRow> filtered = SuspendedImportPolicy.activeRows(rows, Collections.singleton("裂"));

        assertSame(rows, unchanged);
        assertEquals(1, filtered.size());
        assertEquals("拉", filtered.get(0).kanji);
        assertTrue(SuspendedImportPolicy.activeRows(null, Collections.emptySet()).isEmpty());
    }

    @Test
    public void importRangeChecksRejectUnknownAndOutOfRangeRanks() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();

        assertFalse(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("謎", null, 1L), settings));
        assertFalse(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("謎", 99, 2L), settings));
        assertFalse(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("謎", 3001, 3L), settings));
        assertTrue(SuspendedImportPolicy.importInFrequencyRange(suspendedImport("箱", 2500, 4L), settings));
        assertFalse(SuspendedImportPolicy.importInFrequencyRange(null, settings));
    }

    @Test
    public void mergeSkipsOutOfRangeEntriesAndKeepsInRangeEntries() {
        List<RecordsImportModels.SuspendedImport> merged = SuspendedImportPolicy.mergeSuspendedImports(
                null,
                Arrays.asList(
                        suspendedImport("低", 50, 1L),
                        suspendedImport("箱", 2500, 2L),
                        suspendedImport("謎", null, 3L)
                ),
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(1, merged.size());
        assertEquals("箱", merged.get(0).kanji);
    }

    @Test
    public void suspendedImportsOnlyDropsActiveSourcesAndEmptyImports() {
        RecordsImportModels.SuspendedImport mixed = new RecordsImportModels.SuspendedImport(
                "箱",
                2500,
                true,
                3000,
                Arrays.asList(
                        suspendedImport("箱", 2500, 1L).sources.get(0),
                        activeSource("箱", 2L)
                )
        );
        RecordsImportModels.SuspendedImport activeOnly = new RecordsImportModels.SuspendedImport(
                "認",
                200,
                true,
                3000,
                Collections.singletonList(activeSource("認", 3L))
        );

        List<RecordsImportModels.SuspendedImport> filtered = SuspendedImportPolicy.suspendedImportsOnly(Arrays.asList(mixed, activeOnly));

        assertEquals(1, filtered.size());
        assertEquals("箱", filtered.get(0).kanji);
        assertEquals(1, filtered.get(0).sources.size());
        assertTrue(filtered.get(0).sources.get(0).suspended);
        assertTrue(SuspendedImportPolicy.suspendedImportsOnly(null).isEmpty());
    }

    @Test
    public void mergeDeduplicatesSourcesAndUsesLargestCutoff() {
        List<RecordsImportModels.SuspendedImport> merged = SuspendedImportPolicy.mergeSuspendedImports(
                Collections.singletonList(suspendedImport("箱", 2500, 1L, 1200)),
                Arrays.asList(
                        suspendedImport("箱", 2500, 1L, 1200),
                        suspendedImport("箱", 2500, 2L, 3000)
                ),
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(1, merged.size());
        RecordsImportModels.SuspendedImport built = merged.get(0);
        assertEquals(Integer.valueOf(2500), built.jitenRank);
        assertTrue(built.rankKnown);
        assertEquals(3000, built.cutoffUsed);
        assertEquals(2, built.sources.size());
    }

    @Test
    public void mergeKeepsInitialKnownRankWhenLaterSourcesAlsoHaveRanks() {
        List<RecordsImportModels.SuspendedImport> merged = SuspendedImportPolicy.mergeSuspendedImports(
                Collections.singletonList(suspendedImport("箱", 1800, 1L, 1200)),
                Arrays.asList(
                        suspendedImport("箱", 1800, 1L, 1200),
                        suspendedImport("箱", 2500, 2L, 3000)
                ),
                RecordsSyncModels.Settings.kikuDefaults()
        );

        assertEquals(1, merged.size());
        RecordsImportModels.SuspendedImport built = merged.get(0);
        assertEquals(Integer.valueOf(1800), built.jitenRank);
        assertTrue(built.rankKnown);
        assertEquals(3000, built.cutoffUsed);
        assertEquals(2, built.sources.size());
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
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
                Collections.emptyList()
        );
    }

    private static RecordsImportModels.SuspendedImport suspendedImport(String kanji, Integer rank, long cardId) {
        return suspendedImport(kanji, rank, cardId, 3000);
    }

    private static RecordsImportModels.SuspendedImport suspendedImport(String kanji, Integer rank, long cardId, int cutoff) {
        return new RecordsImportModels.SuspendedImport(
                kanji,
                rank,
                rank != null,
                cutoff,
                Collections.singletonList(new RecordsImportModels.SuspendedSource(
                        kanji,
                        cardId,
                        cardId,
                        kanji,
                        "かな",
                        "meaning",
                        RecordsImportModels.SuspendedSourceDetails.builder(kanji + "を見た。").build()
                ))
        );
    }

    private static RecordsImportModels.SuspendedSource activeSource(String kanji, long cardId) {
        return new RecordsImportModels.SuspendedSource(
                kanji,
                cardId,
                cardId,
                kanji,
                "かな",
                "meaning",
                RecordsImportModels.SuspendedSourceDetails.builder(kanji + "を見た。")
                        .suspended(false)
                        .sourceType(RecordsBase.SOURCE_ACTIVE)
                        .build()
        );
    }
}
