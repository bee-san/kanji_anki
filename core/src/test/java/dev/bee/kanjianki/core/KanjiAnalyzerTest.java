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
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,1500\n謎,1600\n"));
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
        List<Records.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings);

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
        Records.DashboardRow weak = find(rows, "弱");
        Records.DashboardRow strong = find(rows, "強");

        assertEquals("弱", rows.get(0).kanji);
        assertEquals("fsrs_weak_memory", weak.reasonCode);
        assertTrue(weak.weaknessScore > strong.weaknessScore);
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
        Records.DashboardRow shallow = find(rows, "浅");
        Records.DashboardRow deep = find(rows, "深");

        assertEquals("浅", rows.get(0).kanji);
        assertTrue(shallow.weaknessScore > deep.weaknessScore);
    }

    @Test
    public void fallsBackToSentenceKanjiAndSkipsDuplicateSuspendedImports() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,1500\n外,1600\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        note(1, "かな", "かな", "meaning", "裂を見た。"),
                        SuspendedKanjiImporterTest.note(2, "外", "そと")
                ),
                Arrays.asList(
                        card(10, 1, 30, 8, 0, null, null, null),
                        SuspendedKanjiImporterTest.card(20, 2, true),
                        SuspendedKanjiImporterTest.card(999, 999, false)
                )
        );
        List<Records.SuspendedImport> imports = Arrays.asList(new Records.SuspendedImport(
                "外",
                1600,
                true,
                3000,
                Arrays.asList(
                        source("外", 20, 2),
                        source("外", 30, 3)
                )
        ));

        List<Records.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, imports, ranks, settings);
        Records.DashboardRow sentenceFallback = find(rows, "裂");
        Records.DashboardRow imported = find(rows, "外");

        assertEquals(1, sentenceFallback.activeExampleCount);
        assertEquals("かな", sentenceFallback.examples.get(0).expression);
        assertEquals(2, imported.suspendedExampleCount);
    }

    @Test
    public void activeReasonCodesPreferSchedulerWeaknessThenLapses() throws Exception {
        Records.Settings supportSatisfied = settingsWithMatureSupport(0);
        Records.Settings oneMatureRequired = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("浅,1500\n深,1600\n"));
        Records.DashboardRow schedulerWeak = new KanjiAnalyzer().rebuild(
                new Records.CollectionSnapshot(
                        Arrays.asList(SuspendedKanjiImporterTest.note(1, "浅い", "あさい")),
                        Arrays.asList(card(10, 1, 5, 12, 0, null, null, null))
                ),
                Arrays.asList(),
                ranks,
                supportSatisfied
        ).get(0);
        Records.DashboardRow lapsed = new KanjiAnalyzer().rebuild(
                new Records.CollectionSnapshot(
                        Arrays.asList(SuspendedKanjiImporterTest.note(2, "深い", "ふかい")),
                        Arrays.asList(card(20, 2, 30, 12, 2, null, null, null))
                ),
                Arrays.asList(),
                ranks,
                oneMatureRequired
        ).get(0);

        assertEquals("anki_scheduler_weakness", schedulerWeak.reasonCode);
        assertEquals("anki_lapses", lapsed.reasonCode);
    }

    @Test
    public void fullySupportedCleanActiveRowsAreOmitted() throws Exception {
        Records.Settings settings = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1600\n"));

        List<Records.DashboardRow> rows = new KanjiAnalyzer().rebuild(
                new Records.CollectionSnapshot(
                        Arrays.asList(SuspendedKanjiImporterTest.note(1, "深い", "ふかい")),
                        Arrays.asList(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
                ),
                Arrays.asList(),
                ranks,
                settings
        );

        assertTrue(rows.isEmpty());
    }

    private Records.Note note(long id, String expression, String reading, String meaning, String sentence) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        Records.Settings settings = Records.Settings.kikuDefaults();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, reading);
        fields.put(settings.meaningField, meaning);
        fields.put(settings.sentenceField, sentence);
        fields.put(settings.frequencyField, "9999");
        fields.put(settings.frequencySortField, "9999");
        return new Records.Note(id, "Kiku", fields, java.util.Collections.emptyList());
    }

    private Records.SuspendedSource source(String kanji, long cardId, long noteId) {
        return new Records.SuspendedSource(kanji, cardId, noteId, kanji, "reading", "meaning", kanji + " sentence");
    }

    private Records.Settings settingsWithMatureSupport(int matureSupportThreshold) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        return new Records.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays
        );
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

    private Records.DashboardRow find(List<Records.DashboardRow> rows, String kanji) {
        for (Records.DashboardRow row : rows) {
            if (row.kanji.equals(kanji)) {
                return row;
            }
        }
        throw new AssertionError("Missing row for " + kanji);
    }
}
