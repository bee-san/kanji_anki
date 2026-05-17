package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SuspendedKanjiImporterTest {
    @Test
    public void importsOnlyKnownRanksInsideConfiguredRange() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        assertEquals(100, settings.suspendedRankMin);
        assertEquals(3000, settings.suspendedRankMax);
        assertEquals(3000, settings.suspendedRankCutoff);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("Kanji,Rank\n日,1\n提,99\n示,100\n裂,3000\n遅,3001\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        note(1, "提示", "ていじ"),
                        note(2, "裂ける謎", "さける"),
                        note(3, "遅い", "おそい")
                ),
                Arrays.asList(
                        card(10, 1, true),
                        card(20, 2, true),
                        card(30, 3, true)
                )
        );

        List<RecordsImportModels.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings);

        assertEquals(2, imports.size());
        assertEquals("示", imports.get(0).kanji);
        assertEquals(Integer.valueOf(100), imports.get(0).jitenRank);
        assertEquals("裂", imports.get(1).kanji);
        assertEquals(Integer.valueOf(3000), imports.get(1).jitenRank);
        boolean importedContainsMysteryKanji = imports.stream().anyMatch(item -> "謎".equals(item.kanji));
        assertFalse(importedContainsMysteryKanji);
        assertEquals(20, imports.get(1).sources.get(0).cardId);
    }

    @Test
    public void deduplicatesKanjiButKeepsMultipleSourceCards() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,2900\n傷,2900\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(note(1, "裂ける", "さける"), note(2, "裂傷", "れっしょう")),
                Arrays.asList(card(10, 1, true), card(20, 2, true))
        );

        List<RecordsImportModels.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings);

        assertEquals("傷", imports.get(0).kanji);
        assertEquals("裂", imports.get(1).kanji);
        assertEquals(2, imports.get(1).sources.size());
    }

    @Test
    public void cutoffConstructorAndSwappedRangeStillImportConfiguredRanks() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("示,100\n裂,3000\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(note(1, "提示", "ていじ"), note(2, "裂ける", "さける")),
                Arrays.asList(card(10, 1, true), card(20, 2, true))
        );

        List<RecordsImportModels.SuspendedImport> cutoffImports = new SuspendedKanjiImporter(ranks, 3000).importFrom(snapshot, settings);
        List<RecordsImportModels.SuspendedImport> swappedRangeImports = new SuspendedKanjiImporter(ranks, 3000, 100).importFrom(snapshot, settings);

        assertEquals(Arrays.asList("示", "裂"), kanjiList(cutoffImports));
        assertEquals(Arrays.asList("示", "裂"), kanjiList(swappedRangeImports));
    }

    @Test
    public void ignoresActiveCardsAndSuspendedCardsWithoutNotes() throws Exception {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,2900\n"));
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Collections.singletonList(note(1, "裂ける", "さける")),
                Arrays.asList(card(10, 1, false), card(20, 999, true))
        );

        assertEquals(0, new SuspendedKanjiImporter(ranks, 3000).importFrom(snapshot, settings).size());
    }

    private static List<String> kanjiList(List<RecordsImportModels.SuspendedImport> imports) {
        List<String> out = new java.util.ArrayList<>();
        for (RecordsImportModels.SuspendedImport item : imports) {
            out.add(item.kanji);
        }
        return out;
    }

    static RecordsSyncModels.Note note(long id, String expression, String reading) {
        Map<String, String> fields = new LinkedHashMap<>();
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, reading);
        fields.put(settings.meaningField, "<b>meaning</b>");
        fields.put(settings.sentenceField, expression + " sentence");
        fields.put(settings.frequencyField, "9999");
        fields.put(settings.frequencySortField, "9999");
        return new RecordsSyncModels.Note(id, "Kiku", fields, Collections.emptyList());
    }

    static RecordsSyncModels.Card card(long cardId, long noteId, boolean suspended) {
        return new RecordsSyncModels.Card(cardId, noteId, 0, "例文マイニング", suspended ? -1 : 2, suspended ? 3 : 2, 0, suspended ? 0 : 30, 3, 0, suspended);
    }
}
