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
    public void importsOnlyRanksWorseThanCutoffAndUnknownRanks() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        assertEquals(3000, settings.suspendedRankCutoff);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("Kanji,Rank\n日,1\n提,254\n示,237\n裂,3600\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        note(1, "提示", "ていじ"),
                        note(2, "裂ける謎", "さける")
                ),
                Arrays.asList(
                        card(10, 1, true),
                        card(20, 2, true)
                )
        );

        List<Records.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankCutoff).importFrom(snapshot, settings);

        assertEquals(2, imports.size());
        assertEquals("裂", imports.get(0).kanji);
        assertEquals(Integer.valueOf(3600), imports.get(0).jitenRank);
        assertEquals("謎", imports.get(1).kanji);
        assertFalse(imports.get(1).rankKnown);
        assertEquals(20, imports.get(1).sources.get(0).cardId);
    }

    @Test
    public void deduplicatesKanjiButKeepsMultipleSourceCards() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,3900\n"));
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(note(1, "裂ける", "さける"), note(2, "裂傷", "れっしょう")),
                Arrays.asList(card(10, 1, true), card(20, 2, true))
        );

        List<Records.SuspendedImport> imports = new SuspendedKanjiImporter(ranks, settings.suspendedRankCutoff).importFrom(snapshot, settings);

        assertEquals(2, imports.get(0).sources.size());
    }

    static Records.Note note(long id, String expression, String reading) {
        Map<String, String> fields = new LinkedHashMap<>();
        Records.Settings settings = Records.Settings.kikuDefaults();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, reading);
        fields.put(settings.meaningField, "<b>meaning</b>");
        fields.put(settings.sentenceField, expression + " sentence");
        fields.put(settings.frequencyField, "9999");
        fields.put(settings.frequencySortField, "9999");
        return new Records.Note(id, "Kiku", fields, Collections.emptyList());
    }

    static Records.Card card(long cardId, long noteId, boolean suspended) {
        return new Records.Card(cardId, noteId, 0, "例文マイニング", suspended ? -1 : 2, suspended ? 3 : 2, 0, suspended ? 0 : 30, 3, 0, suspended);
    }
}
