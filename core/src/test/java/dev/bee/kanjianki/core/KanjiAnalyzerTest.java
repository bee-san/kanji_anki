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
        assertTrue(top.reasonText.contains("local suspended"));
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
}
