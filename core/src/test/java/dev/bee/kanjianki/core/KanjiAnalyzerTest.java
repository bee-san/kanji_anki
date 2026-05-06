package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KanjiAnalyzerTest {
    @Test
    public void buildsRowsFromActiveAndSuspendedCollectionEvidence() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,3600\n謎,3700\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "裂ける", "さける"),
                        SuspendedKanjiImporterTest.note(2, "謎", "なぞ")
                ),
                Arrays.asList(
                        SuspendedKanjiImporterTest.card(10, 1, false),
                        SuspendedKanjiImporterTest.card(20, 2, true)
                )
        );
        List<Records.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankCutoff).importFrom(snapshot, settings);

        List<Records.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, imports, ranks, settings);

        Records.DashboardRow top = rows.get(0);
        assertEquals("謎", top.kanji);
        assertEquals(1, top.suspendedExampleCount);
        assertEquals("suspended_archive", top.reasonCode);
        assertTrue(top.reasonText.contains("writing-practice target"));
        assertEquals("note:Kiku Expression:*謎*", top.browserSearch);
    }

    @Test
    public void activeMatureSupportReducesWeakness() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,3600\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(SuspendedKanjiImporterTest.note(1, "裂ける", "さける")),
                Arrays.asList(SuspendedKanjiImporterTest.card(10, 1, false))
        );

        Records.DashboardRow row = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings).get(0);

        assertEquals(1, row.matureSupportCount);
        assertEquals("weak_support", row.reasonCode);
    }

    @Test
    public void fsrsWeakMemoryRaisesAnalyzerRanking() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("弱,3600\n強,3700\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "弱点", "じゃくてん"),
                        SuspendedKanjiImporterTest.note(2, "強み", "つよみ")
                ),
                Arrays.asList(
                        card(10, 1, 30, 12, 0, 3.0, 8.0, 0.35),
                        card(20, 2, 30, 12, 0, 60.0, 3.0, 0.95)
                )
        );

        List<Records.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);

        assertEquals("弱", rows.get(0).kanji);
        assertEquals("fsrs_weak_memory", rows.get(0).reasonCode);
        assertTrue(rows.get(0).weaknessScore > rows.get(1).weaknessScore);
    }

    @Test
    public void missingFsrsFallsBackToSchedulerWeaknessSignals() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("浅,3600\n深,3700\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "浅い", "あさい"),
                        SuspendedKanjiImporterTest.note(2, "深い", "ふかい")
                ),
                Arrays.asList(
                        card(10, 1, 5, 12, 1, null, null, null),
                        card(20, 2, 30, 12, 0, null, null, null)
                )
        );

        List<Records.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);

        assertEquals("浅", rows.get(0).kanji);
        assertTrue(rows.get(0).weaknessScore > rows.get(1).weaknessScore);
    }

    private Records.Card card(
            long cardId,
            long noteId,
            int intervalDays,
            int reps,
            int lapses,
            Double fsrsStability,
            Double fsrsDifficulty,
            Double fsrsRetrievability
    ) {
        return new Records.Card(cardId, noteId, 0, "Kiku", 2, 2, 0, intervalDays, reps, lapses, false, fsrsStability, fsrsDifficulty, fsrsRetrievability);
    }
}
