package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KanjiAnalyzerTest {
    @Test
    public void buildsRowsFromActiveAndSuspendedCollectionEvidence() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,1500\n謎,1600\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "裂ける", "さける"),
                        SuspendedKanjiImporterTest.note(2, "謎", "なぞ")
                ),
                Arrays.asList(
                        SuspendedKanjiImporterTest.card(10, 1, false),
                        SuspendedKanjiImporterTest.card(20, 2, true)
                )
        );
        List<RecordsImportModels.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings);

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, imports, ranks, settings);

        RecordsImportModels.DashboardRow top = rows.get(0);
        assertEquals("謎", top.kanji);
        assertEquals(1, top.suspendedExampleCount);
        assertEquals("suspended_archive", top.reasonCode);
        assertTrue(top.reasonText.contains("writing-practice target"));
        assertEquals("note:Kiku Expression:*謎*", top.browserSearch);
    }

    @Test
    public void activeMatureSupportReducesWeakness() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,3600\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(SuspendedKanjiImporterTest.note(1, "裂ける", "さける")),
                Arrays.asList(SuspendedKanjiImporterTest.card(10, 1, false))
        );

        RecordsImportModels.DashboardRow row = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings).get(0);

        assertEquals(1, row.matureSupportCount);
        assertEquals("weak_support", row.reasonCode);
    }

    @Test
    public void rowsWithMissingRankSortAfterRankedRows() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,3600\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "裂ける", "さける"),
                        SuspendedKanjiImporterTest.note(2, "謎", "なぞ")
                ),
                Arrays.asList(
                        SuspendedKanjiImporterTest.card(10, 1, false),
                        SuspendedKanjiImporterTest.card(20, 2, false)
                )
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);

        assertEquals("裂", rows.get(0).kanji);
        assertEquals("謎", rows.get(1).kanji);
    }

    @Test
    public void rowsWithEqualScoresAndRanksSortByKanji() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("謎,3600\n裂,3600\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "謎", "なぞ"),
                        SuspendedKanjiImporterTest.note(2, "裂ける", "さける")
                ),
                Arrays.asList(
                        SuspendedKanjiImporterTest.card(10, 1, false),
                        SuspendedKanjiImporterTest.card(20, 2, false)
                )
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);

        assertEquals("裂", rows.get(0).kanji);
        assertEquals("謎", rows.get(1).kanji);
    }

    @Test
    public void fsrsWeakMemoryRaisesAnalyzerRanking() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("弱,3600\n強,3700\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "弱点", "じゃくてん"),
                        SuspendedKanjiImporterTest.note(2, "強み", "つよみ")
                ),
                Arrays.asList(
                        card(10, 1, 30, 12, 0, 3.0, 8.0, 0.35),
                        card(20, 2, 30, 12, 0, 60.0, 3.0, 0.95)
                )
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);
        RecordsImportModels.DashboardRow weak = find(rows, "弱");
        RecordsImportModels.DashboardRow strong = find(rows, "強");

        assertEquals("弱", rows.get(0).kanji);
        assertEquals("fsrs_weak_memory", weak.reasonCode);
        assertTrue(weak.weaknessScore > strong.weaknessScore);
    }

    @Test
    public void missingFsrsFallsBackToSchedulerWeaknessSignals() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("浅,3600\n深,3700\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "浅い", "あさい"),
                        SuspendedKanjiImporterTest.note(2, "深い", "ふかい")
                ),
                Arrays.asList(
                        card(10, 1, 5, 12, 1, null, null, null),
                        card(20, 2, 30, 12, 0, null, null, null)
                )
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);
        RecordsImportModels.DashboardRow shallow = find(rows, "浅");
        RecordsImportModels.DashboardRow deep = find(rows, "深");

        assertEquals("浅", rows.get(0).kanji);
        assertTrue(shallow.weaknessScore > deep.weaknessScore);
    }

    @Test
    public void fallsBackToSentenceKanjiAndSkipsDuplicateSuspendedImports() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,1500\n外,1600\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
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
        List<RecordsImportModels.SuspendedImport> imports = Arrays.asList(new RecordsImportModels.SuspendedImport(
                "外",
                1600,
                true,
                3000,
                Arrays.asList(
                        source("外", 20, 2),
                        source("外", 30, 3)
                )
        ));

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, imports, ranks, settings);
        RecordsImportModels.DashboardRow sentenceFallback = find(rows, "裂");
        RecordsImportModels.DashboardRow imported = find(rows, "外");

        assertEquals(1, sentenceFallback.activeExampleCount);
        assertEquals("かな", sentenceFallback.examples.get(0).expression);
        assertEquals(2, imported.suspendedExampleCount);
    }

    @Test
    public void activeReasonCodesPreferSchedulerWeaknessThenLapses() throws Exception {
        RecordsSyncModels.Settings supportSatisfied = settingsWithMatureSupport(0);
        RecordsSyncModels.Settings oneMatureRequired = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("浅,1500\n深,1600\n"));
        RecordsImportModels.DashboardRow schedulerWeak = new KanjiAnalyzer().rebuild(
                new RecordsSyncModels.CollectionSnapshot(
                        Arrays.asList(SuspendedKanjiImporterTest.note(1, "浅い", "あさい")),
                        Arrays.asList(card(10, 1, 5, 12, 0, null, null, null))
                ),
                Arrays.asList(),
                ranks,
                supportSatisfied
        ).get(0);
        RecordsImportModels.DashboardRow lapsed = new KanjiAnalyzer().rebuild(
                new RecordsSyncModels.CollectionSnapshot(
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
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1600\n"));

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(
                new RecordsSyncModels.CollectionSnapshot(
                        Arrays.asList(SuspendedKanjiImporterTest.note(1, "深い", "ふかい")),
                        Arrays.asList(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
                ),
                Arrays.asList(),
                ranks,
                settings
        );

        assertTrue(rows.isEmpty());
    }

    @Test
    public void selectedTaggedActiveSourceCanForceCleanPracticeRow() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1600\n"));
        List<RecordsImportModels.SuspendedImport> imports = Arrays.asList(new RecordsImportModels.SuspendedImport(
                "深",
                1600,
                true,
                3000,
                Arrays.asList(new RecordsImportModels.SuspendedSource(
                        "深",
                        10,
                        1,
                        "深い",
                        "ふかい",
                        "deep",
                        RecordsImportModels.SuspendedSourceDetails.builder("深い。")
                                .sourceType(RecordsBase.SOURCE_ACTIVE)
                                .suspended(false)
                                .forcePractice(true)
                                .mature(true)
                                .reviewStats(0, 45, 12)
                                .fsrs(60.0, 3.0, 0.95)
                                .build()
                ))
        ));

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuildSelectedSources(
                new RecordsSyncModels.CollectionSnapshot(
                        Arrays.asList(note(1, "深い", "ふかい", "deep", "深い。")),
                        Arrays.asList(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
                ),
                imports,
                ranks,
                settings
        );

        assertEquals(1, rows.size());
        assertEquals("深", rows.get(0).kanji);
        assertEquals(1, rows.get(0).activeExampleCount);
        assertEquals("watch", rows.get(0).reasonCode);
    }

    @Test
    public void selectedSourcesPreserveExistingForcePracticeAcrossMultipleCards() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1600\n"));
        List<RecordsImportModels.SuspendedSource> sources = Arrays.asList(
                new RecordsImportModels.SuspendedSource("深", 10, 1, "深い", "ふかい", "deep",
                        activePracticeDetails("深い。")),
                new RecordsImportModels.SuspendedSource("深", 20, 2, "深み", "ふかみ", "depth",
                        activePracticeDetails("深み。"))
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuildSelectedSources(
                new RecordsSyncModels.CollectionSnapshot(
                        Arrays.asList(
                                note(1, "深い", "ふかい", "deep", "深い。"),
                                note(2, "深み", "ふかみ", "depth", "深み。")
                        ),
                        Arrays.asList(
                                card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95),
                                card(20, 2, 45, 12, 0, 60.0, 3.0, 0.95)
                        )
                ),
                Collections.singletonList(new RecordsImportModels.SuspendedImport("深", 1600, true, 3000, sources)),
                ranks,
                settings
        );

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).activeExampleCount);
    }

    @Test
    public void selectedSourcesPreserveBrowserQuerySourceTypeForMatchedActiveCards() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("橋,1600\n"));
        RecordsImportModels.SuspendedSource source = new RecordsImportModels.SuspendedSource(
                "橋",
                10,
                1,
                "橋",
                "はし",
                "bridge",
                RecordsImportModels.SuspendedSourceDetails.builder("橋を渡る。")
                        .sourceType(RecordsBase.SOURCE_BROWSER_QUERY)
                        .suspended(false)
                        .forcePractice(true)
                        .mature(true)
                        .reviewStats(0, 45, 12)
                        .fsrs(60.0, 3.0, 0.95)
                        .build()
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuildSelectedSources(
                new RecordsSyncModels.CollectionSnapshot(
                        Collections.singletonList(note(1, "橋", "はし", "bridge", "橋を渡る。")),
                        Collections.singletonList(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
                ),
                Collections.singletonList(new RecordsImportModels.SuspendedImport("橋", 1600, true, 3000, Collections.singletonList(source))),
                ranks,
                settings
        );

        assertEquals(1, rows.size());
        assertEquals(RecordsBase.SOURCE_BROWSER_QUERY, rows.get(0).examples.get(0).sourceType);
        assertEquals(1, rows.get(0).activeExampleCount);
    }

    @Test
    public void selectedSourcesSkipUnselectedCardsAndUnimportedKanji() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(0);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1500\n外,1600\n"));
        List<RecordsImportModels.SuspendedImport> imports = Arrays.asList(new RecordsImportModels.SuspendedImport(
                "深",
                1500,
                true,
                3000,
                Arrays.asList(new RecordsImportModels.SuspendedSource(
                        "深",
                        10,
                        1,
                        "深外",
                        "ふかい",
                        "deep",
                        activePracticeDetails("深外。")
                ))
        ));

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuildSelectedSources(
                new RecordsSyncModels.CollectionSnapshot(
                        Arrays.asList(
                                note(1, "深外", "ふかい", "deep", "深外。"),
                                note(2, "外", "そと", "outside", "外。")
                        ),
                        Arrays.asList(
                                card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95),
                                card(20, 2, 3, 1, 0, null, null, null)
                        )
                ),
                imports,
                ranks,
                settings
        );

        assertEquals(1, rows.size());
        assertEquals("深", rows.get(0).kanji);
    }

    @Test
    public void selectedSourceWithoutForcePracticeDoesNotKeepCleanRows() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1500\n"));
        RecordsImportModels.SuspendedSource source = new RecordsImportModels.SuspendedSource(
                "深",
                10,
                1,
                "深い",
                "ふかい",
                "deep",
                RecordsImportModels.SuspendedSourceDetails.builder("深い。")
                        .sourceType(RecordsBase.SOURCE_ACTIVE)
                        .suspended(false)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(0, 45, 12)
                        .fsrs(60.0, 3.0, 0.95)
                        .build()
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuildSelectedSources(
                new RecordsSyncModels.CollectionSnapshot(
                        Collections.singletonList(note(1, "深い", "ふかい", "deep", "深い。")),
                        Collections.singletonList(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
                ),
                Collections.singletonList(new RecordsImportModels.SuspendedImport("深", 1500, true, 3000, Collections.singletonList(source))),
                ranks,
                settings
        );

        assertTrue(rows.isEmpty());
    }

    @Test
    public void importedRowsTrimExamplesAndUseFirstNonEmptyMeaningReading() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("集,1500\n"));
        List<RecordsImportModels.SuspendedSource> sources = new java.util.ArrayList<>();
        sources.add(new RecordsImportModels.SuspendedSource("集", 1L, 1L, "集合", "", "",
                suspendedDetails("集合。", false, 0)));
        sources.add(new RecordsImportModels.SuspendedSource("集", 1L, 1L, "集まる", "あつまる", "gather",
                suspendedDetails("集まる。", true, 1)));
        for (int i = 2; i <= 10; i++) {
            sources.add(new RecordsImportModels.SuspendedSource("集", i, i, "集" + i, "よみ" + i, "meaning" + i,
                    suspendedDetails("文" + i, false, 0)));
        }

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(
                new RecordsSyncModels.CollectionSnapshot(Arrays.asList(), Arrays.asList()),
                Arrays.asList(new RecordsImportModels.SuspendedImport("集", 1500, true, 3000, sources)),
                ranks,
                settings
        );

        RecordsImportModels.DashboardRow row = rows.get(0);
        assertEquals("集", row.kanji);
        assertEquals("あつまる", row.reading);
        assertEquals("gather", row.primaryMeaning);
        assertEquals(8, row.examples.size());
        assertEquals("suspended_archive", row.reasonCode);
    }

    @Test
    public void cleansDictionaryMetadataFromAnkiMeanings() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("動,1500\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Collections.singletonList(note(
                        1,
                        "動く",
                        "うごく",
                        "Meaning: Jitendex (noun) movement|word-level fallback",
                        "動く。"
                )),
                Collections.singletonList(card(10, 1, 0, 0, 0, null, null, null))
        );

        RecordsImportModels.DashboardRow row = new KanjiAnalyzer().rebuild(snapshot, Collections.emptyList(), ranks, settings).get(0);

        assertEquals("movement", row.primaryMeaning);
        assertEquals("movement", row.examples.get(0).meaning);
    }

    @Test
    public void fsrsRetrievabilityNormalizesPercentAndRejectsInvalidValues() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(2);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("弱,1500\n中,1550\n百,1600\n過,1700\n負,1800\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "弱い", "よわい"),
                        SuspendedKanjiImporterTest.note(2, "中", "なか"),
                        SuspendedKanjiImporterTest.note(3, "百", "ひゃく"),
                        SuspendedKanjiImporterTest.note(4, "過ぎる", "すぎる"),
                        SuspendedKanjiImporterTest.note(5, "負ける", "まける")
                ),
                Arrays.asList(
                        card(10, 1, 30, 12, 0, 3.0, 4.0, 45.0),
                        card(20, 2, 30, 12, 0, 3.0, 4.0, 0.60),
                        card(30, 3, 30, 12, 0, 30.0, 4.0, 75.0),
                        card(40, 4, 30, 12, 0, 50.0, 4.0, 101.0),
                        card(50, 5, 30, 12, 0, 50.0, 4.0, -0.1)
                )
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);

        assertEquals("fsrs_weak_memory", find(rows, "弱").reasonCode);
        assertEquals("fsrs_weak_memory", find(rows, "中").reasonCode);
        assertEquals("weak_support", find(rows, "百").reasonCode);
        assertEquals("weak_support", find(rows, "過").reasonCode);
        assertEquals("weak_support", find(rows, "負").reasonCode);
    }

    @Test
    public void fsrsStabilityPressureHonorsRepsAndExistingPressureGuards() throws Exception {
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(2);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("少,1500\n難,1600\n守,1700\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        SuspendedKanjiImporterTest.note(1, "少ない", "すくない"),
                        SuspendedKanjiImporterTest.note(2, "難しい", "むずかしい"),
                        SuspendedKanjiImporterTest.note(3, "守る", "まもる")
                ),
                Arrays.asList(
                        card(10, 1, 30, 4, 0, 3.0, 4.0, 0.95),
                        card(20, 2, 30, 12, 0, 50.0, 8.0, 0.95),
                        card(30, 3, 30, 12, 0, 50.0, 4.0, 0.95)
                )
        );

        List<RecordsImportModels.DashboardRow> rows = new KanjiAnalyzer().rebuild(snapshot, Arrays.asList(), ranks, settings);

        assertEquals("weak_support", find(rows, "少").reasonCode);
        assertEquals("fsrs_weak_memory", find(rows, "難").reasonCode);
        assertEquals("weak_support", find(rows, "守").reasonCode);
    }

    @Test
    public void singularLapseReasonTextIsReadable() throws Exception {
        RecordsSyncModels.Settings oneMatureRequired = settingsWithMatureSupport(1);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("深,1600\n"));
        RecordsImportModels.DashboardRow lapsed = new KanjiAnalyzer().rebuild(
                new RecordsSyncModels.CollectionSnapshot(
                        Collections.singletonList(SuspendedKanjiImporterTest.note(2, "深い", "ふかい")),
                        Collections.singletonList(card(20, 2, 30, 12, 1, null, null, null))
                ),
                Arrays.asList(),
                ranks,
                oneMatureRequired
        ).get(0);

        assertTrue(lapsed.reasonText.contains("1 lapse"));
    }

    private RecordsSyncModels.Note note(long id, String expression, String reading, String meaning, String sentence) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, reading);
        fields.put(settings.meaningField, meaning);
        fields.put(settings.sentenceField, sentence);
        fields.put(settings.frequencyField, "9999");
        fields.put(settings.frequencySortField, "9999");
        return new RecordsSyncModels.Note(id, "Kiku", fields, java.util.Collections.emptyList());
    }

    private RecordsImportModels.SuspendedSource source(String kanji, long cardId, long noteId) {
        return new RecordsImportModels.SuspendedSource(kanji, cardId, noteId, kanji, "reading", "meaning", kanji + " sentence");
    }

    private RecordsImportModels.SuspendedSourceDetails activePracticeDetails(String sentence) {
        return RecordsImportModels.SuspendedSourceDetails.builder(sentence)
                .sourceType(RecordsBase.SOURCE_ACTIVE)
                .suspended(false)
                .forcePractice(true)
                .mature(true)
                .reviewStats(0, 45, 12)
                .fsrs(60.0, 3.0, 0.95)
                .build();
    }

    private RecordsImportModels.SuspendedSourceDetails suspendedDetails(String sentence, boolean forcePractice, int lapses) {
        return RecordsImportModels.SuspendedSourceDetails.builder(sentence)
                .sourceType(RecordsBase.SOURCE_SUSPENDED)
                .suspended(true)
                .forcePractice(forcePractice)
                .reviewStats(lapses, 0, 0)
                .build();
    }

    private RecordsSyncModels.Settings settingsWithMatureSupport(int matureSupportThreshold) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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

    private RecordsSyncModels.Card card(
            long cardId,
            long noteId,
            int intervalDays,
            int reps,
            int lapses,
            Double fsrsStability,
            Double fsrsDifficulty,
            Double fsrsRetrievability
    ) {
        return new RecordsSyncModels.Card(cardId, noteId, 0, "Kiku", 2, 2, 0, intervalDays, reps, lapses, false, fsrsStability, fsrsDifficulty, fsrsRetrievability);
    }

    private RecordsImportModels.DashboardRow find(List<RecordsImportModels.DashboardRow> rows, String kanji) {
        for (RecordsImportModels.DashboardRow row : rows) {
            if (row.kanji.equals(kanji)) {
                return row;
            }
        }
        throw new AssertionError("Missing row for " + kanji);
    }
}
