package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KanjiInventoryBuilderTest {
    @Test
    public void buildsInventoryFromSnapshotImportsDashboardAndKnownKanji() {
        KanjiInventoryBuilder builder = new KanjiInventoryBuilder(2000L, RecordsSyncModels.Settings.kikuDefaults());
        builder.addSnapshotNote(note("語学", "ごがく", "language study", "語を学ぶ"));
        builder.addSuspendedImport(new RecordsImportModels.SuspendedImport(
                "外",
                2000,
                true,
                3000,
                Collections.singletonList(new RecordsImportModels.SuspendedSource(
                        "外",
                        10L,
                        20L,
                        "外国",
                        "がいこく",
                        "foreign country",
                        "外へ行く"
                ))
        ));
        builder.addDashboardRow(new RecordsImportModels.DashboardRow(
                "語",
                100,
                "words",
                "ご",
                "browser語",
                10,
                "reason",
                "Needs 語 support",
                1,
                0,
                0,
                Collections.singletonList(new RecordsImportModels.Example(
                        "active",
                        1L,
                        2L,
                        "語彙",
                        "ごい",
                        "vocabulary",
                        "語彙を増やす",
                        false,
                        0
                ))
        ));
        builder.addKnownKanji("済");

        Map<String, KanjiInventoryBuilder.BuiltItem> items = byKanji(builder.build(Collections.emptyMap()));
        KanjiInventoryBuilder.BuiltItem language = items.get("語");
        assertEquals("language study", language.primaryMeaning());
        assertEquals("browser語", language.browserSearch());
        assertEquals("ごがく / ご / ごい", language.readings());
        assertEquals(3, language.sourceCount());
        assertEquals(1, language.exampleCount());
        assertEquals(2000L, language.firstSeenAtMillis());
        assertEquals(2000L, language.lastSeenAtMillis());
        assertTrue(language.searchText().contains("語彙"));

        assertEquals("foreign country", items.get("外").primaryMeaning());
        assertEquals("", items.get("済").primaryMeaning());
    }

    @Test
    public void preservesPreviousIdentityWhenCurrentBuildHasOnlyKnownKanji() {
        KanjiInventoryBuilder builder = new KanjiInventoryBuilder(9000L, RecordsSyncModels.Settings.kikuDefaults());
        builder.addKnownKanji("旧");
        Map<String, KanjiInventoryBuilder.PreviousItem> previous = new HashMap<>();
        previous.put("旧", new KanjiInventoryBuilder.PreviousItem(
                "old meaning",
                "きゅう",
                "old search",
                5,
                6,
                1234L,
                5678L
        ));

        KanjiInventoryBuilder.BuiltItem item = builder.build(previous).get(0);

        assertEquals("旧", item.kanji());
        assertEquals("old meaning", item.primaryMeaning());
        assertEquals("きゅう", item.readings());
        assertEquals("old search", item.browserSearch());
        assertEquals(5, item.sourceCount());
        assertEquals(6, item.exampleCount());
        assertEquals(1234L, item.firstSeenAtMillis());
        assertEquals(9000L, item.lastSeenAtMillis());
        assertTrue(item.searchText().contains("old meaning"));
        assertTrue(item.searchText().contains("old search"));
    }

    @Test
    public void capsDisplayedReadingsAndReportsHiddenCount() {
        KanjiInventoryBuilder builder = new KanjiInventoryBuilder(1L, RecordsSyncModels.Settings.kikuDefaults());
        builder.addSourceText(Collections.singletonList("多"), "one", "a", "多", "");
        builder.addSourceText(Collections.singletonList("多"), "two", "b", "多", "");
        builder.addSourceText(Collections.singletonList("多"), "three", "c", "多", "");
        builder.addSourceText(Collections.singletonList("多"), "four", "d", "多", "");

        assertEquals("a / b / c +1 more", builder.build(Collections.emptyMap()).get(0).readings());
    }

    private static RecordsSyncModels.Note note(String expression, String reading, String meaning, String sentence) {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        Map<String, String> fields = new HashMap<>();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, reading);
        fields.put(settings.meaningField, meaning);
        fields.put(settings.sentenceField, sentence);
        return new RecordsSyncModels.Note(1L, settings.modelName, fields, Collections.emptyList());
    }

    private static Map<String, KanjiInventoryBuilder.BuiltItem> byKanji(List<KanjiInventoryBuilder.BuiltItem> items) {
        Map<String, KanjiInventoryBuilder.BuiltItem> out = new HashMap<>();
        for (KanjiInventoryBuilder.BuiltItem item : items) {
            out.put(item.kanji(), item);
        }
        return out;
    }
}
