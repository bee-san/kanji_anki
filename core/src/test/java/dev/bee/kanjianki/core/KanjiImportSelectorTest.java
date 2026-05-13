package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KanjiImportSelectorTest {
    @Test
    public void defaultsIgnoreActiveCardsInsideRankRange() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = ranks("裂,1500\n謎,1600\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "裂ける", "さける"), note(2, "謎", "なぞ")),
                Arrays.asList(card(10, 1, false), card(20, 2, true))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax)
                .importFrom(snapshot, settings);

        assertEquals(Collections.singletonList("謎"), kanjiList(imports));
        assertTrue(imports.get(0).sources.get(0).forcePractice);
    }

    @Test
    public void defaultsImportSuspendedCardsInsideRankRange() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        JitenKanjiRanks ranks = ranks("謎,1600\n遅,3001\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "謎", "なぞ"), note(2, "遅い", "おそい")),
                Arrays.asList(card(10, 1, true), card(20, 2, true))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax)
                .importFrom(snapshot, settings);

        assertEquals(Collections.singletonList("謎"), kanjiList(imports));
        assertEquals(Integer.valueOf(1600), imports.get(0).jitenRank);
    }

    @Test
    public void suspendedOnlyExcludesActiveCards() throws Exception {
        Records.Settings settings = settings(false, true, false, "", false, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("裂,1500\n謎,1600\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "裂ける", "さける"), note(2, "謎", "なぞ")),
                Arrays.asList(card(10, 1, false), card(20, 2, true))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Collections.singletonList("謎"), kanjiList(imports));
    }

    @Test
    public void taggedCardsCombineWithSuspendedCardsUsingAnyMatchLogic() throws Exception {
        Records.Settings settings = settings(false, true, true, "wani target", false, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("裂,1500\n謎,1600\n外,1700\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(
                        note(1, "裂ける", "さける", "target"),
                        note(2, "謎", "なぞ"),
                        note(3, "外", "そと")
                ),
                Arrays.asList(card(10, 1, false), card(20, 2, true), card(30, 3, false))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Arrays.asList("裂", "謎"), kanjiList(imports));
        assertTrue(imports.get(0).sources.get(0).forcePractice);
    }

    @Test
    public void weakCardsMatchByFsrsDifficulty() throws Exception {
        Records.Settings settings = settings(false, false, false, "", true, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("弱,1500\n強,1600\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "弱点", "じゃくてん"), note(2, "強い", "つよい")),
                Arrays.asList(card(10, 1, false, 0, 8.0), card(20, 2, false, 0, 4.0))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Collections.singletonList("弱"), kanjiList(imports));
    }

    @Test
    public void weakCardsMatchByLapses() throws Exception {
        Records.Settings settings = settings(false, false, false, "", true, 9.0, 2, 1);
        JitenKanjiRanks ranks = ranks("浅,1500\n深,1600\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "浅い", "あさい"), note(2, "深い", "ふかい")),
                Arrays.asList(card(10, 1, false, 2, 3.0), card(20, 2, false, 1, 3.0))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Collections.singletonList("浅"), kanjiList(imports));
    }

    @Test
    public void weakCardsIgnoreMissingDifficultyAndLowLapses() throws Exception {
        Records.Settings settings = settings(false, false, false, "", true, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("浅,1500\n深,1600\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "浅い", "あさい"), note(2, "深い", "ふかい")),
                Arrays.asList(card(10, 1, false, 1, null), card(20, 2, false, 0, 3.0))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertTrue(imports.isEmpty());
    }

    @Test
    public void rankRangeFiltersImportedKanji() throws Exception {
        Records.Settings settings = settings(true, true, false, "", false, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("日,1\n示,100\n裂,3000\n遅,3001\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "日示裂遅", "にち")),
                Collections.singletonList(card(10, 1, false))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Arrays.asList("示", "裂"), kanjiList(imports));
    }

    @Test
    public void equalRanksSortByKanji() throws Exception {
        Records.Settings settings = settings(true, false, false, "", false, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("謎,1500\n裂,1500\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "謎裂", "なぞ")),
                Collections.singletonList(card(10, 1, false))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Arrays.asList("裂", "謎"), kanjiList(imports));
    }

    @Test
    public void minimumMatchingCardsCountsUniqueSourceCardsPerKanji() throws Exception {
        Records.Settings settings = settings(true, true, false, "", false, 7.0, 2, 2);
        JitenKanjiRanks ranks = ranks("裂,1500\n謎,1600\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "裂ける謎", "さける"), note(2, "裂傷", "れっしょう")),
                Arrays.asList(card(10, 1, false), card(20, 2, false))
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertEquals(Collections.singletonList("裂"), kanjiList(imports));
        assertEquals(2, imports.get(0).sources.size());
    }

    @Test
    public void constructorsNormalizeBoundsAndOneArgUsesDefaultMinimum() throws Exception {
        Records.Settings settings = settings(true, false, false, "", false, 7.0, 2, 1);
        JitenKanjiRanks ranks = ranks("示,100\n裂,1500\n遅,3001\n");
        Records.CollectionSnapshot snapshot = snapshot(
                Arrays.asList(note(1, "示裂遅", "しめす")),
                Collections.singletonList(card(10, 1, false))
        );

        List<Records.SuspendedImport> swapped = new KanjiImportSelector(ranks, 3000, 100).importFrom(snapshot, settings);
        List<Records.SuspendedImport> oneArg = new KanjiImportSelector(ranks, 3000).importFrom(snapshot, settings);

        assertEquals(Arrays.asList("示", "裂"), kanjiList(swapped));
        assertEquals(Arrays.asList("示", "裂"), kanjiList(oneArg));
    }

    @Test
    public void nullDisabledMissingAndUnmatchedInputsReturnNoImports() throws Exception {
        JitenKanjiRanks ranks = ranks("裂,1500\n外,1600\n");
        Records.Settings disabled = settings(false, false, false, "", false, 7.0, 2, 1);
        Records.Settings taggedOnly = settings(false, false, true, "target", false, 7.0, 2, 1);
        KanjiImportSelector selector = new KanjiImportSelector(ranks, 100, 3000);

        assertTrue(selector.importFrom(null, Records.Settings.kikuDefaults()).isEmpty());
        assertTrue(selector.importFrom(snapshot(Arrays.asList(), Arrays.asList()), null).isEmpty());
        assertTrue(selector.importFrom(snapshot(Arrays.asList(note(1, "裂", "れつ")), Collections.singletonList(card(10, 1, false))), disabled).isEmpty());
        assertTrue(selector.importFrom(snapshot(Arrays.asList(note(1, "裂", "れつ")), Collections.singletonList(card(10, 99, false))), Records.Settings.kikuDefaults()).isEmpty());
        assertTrue(selector.importFrom(snapshot(Arrays.asList(note(1, "裂", "れつ")), Collections.singletonList(card(10, 1, false))), taggedOnly).isEmpty());
        assertTrue(selector.importFrom(snapshot(Arrays.asList(note(1, "裂", "れつ", "other")), Collections.singletonList(card(10, 1, false))), taggedOnly).isEmpty());
        assertTrue(selector.importFrom(snapshot(Arrays.asList(note(1, "未", "み")), Collections.singletonList(card(10, 1, false))), settings(true, false, false, "", false, 7.0, 2, 1)).isEmpty());
    }

    private JitenKanjiRanks ranks(String csv) throws Exception {
        return JitenKanjiRanks.parseCsv(new StringReader(csv));
    }

    private Records.CollectionSnapshot snapshot(List<Records.Note> notes, List<Records.Card> cards) {
        return new Records.CollectionSnapshot(notes, cards);
    }

    private Records.Note note(long id, String expression, String reading, String... tags) {
        Map<String, String> fields = new LinkedHashMap<>();
        Records.Settings settings = Records.Settings.kikuDefaults();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, reading);
        fields.put(settings.meaningField, "meaning");
        fields.put(settings.sentenceField, expression + " sentence");
        fields.put(settings.frequencyField, "9999");
        fields.put(settings.frequencySortField, "9999");
        return new Records.Note(id, "Kiku", fields, Arrays.asList(tags));
    }

    private Records.Card card(long cardId, long noteId, boolean suspended) {
        return card(cardId, noteId, suspended, 0, null);
    }

    private Records.Card card(long cardId, long noteId, boolean suspended, int lapses, Double fsrsDifficulty) {
        return new Records.Card(
                cardId,
                noteId,
                0,
                "例文マイニング",
                suspended ? -1 : 2,
                suspended ? 3 : 2,
                0,
                suspended ? 0 : 30,
                3,
                lapses,
                suspended,
                null,
                fsrsDifficulty,
                null
        );
    }

    private Records.Settings settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            String tags,
            boolean weak,
            double weakDifficulty,
            int weakLapses,
            int minMatching
    ) {
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
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                active,
                suspended,
                tagged,
                Records.parseImportTags(tags),
                weak,
                weakDifficulty,
                weakLapses,
                minMatching
        );
    }

    private List<String> kanjiList(List<Records.SuspendedImport> imports) {
        List<String> out = new java.util.ArrayList<>();
        for (Records.SuspendedImport item : imports) {
            out.add(item.kanji);
        }
        return out;
    }
}
