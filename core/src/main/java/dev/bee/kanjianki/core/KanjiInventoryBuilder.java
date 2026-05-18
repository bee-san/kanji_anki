package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class KanjiInventoryBuilder {
    private static final int MAX_DISPLAYED_READINGS = 3;

    private final long nowMillis;
    private final RecordsSyncModels.Settings settings;
    private final Map<String, MutableItem> items = new LinkedHashMap<>();

    public KanjiInventoryBuilder(long nowMillis, RecordsSyncModels.Settings settings) {
        this.nowMillis = Math.max(0L, nowMillis);
        this.settings = settings == null ? RecordsSyncModels.Settings.kikuDefaults() : settings;
    }

    public void addSnapshotNote(RecordsSyncModels.Note note) {
        if (note == null) {
            return;
        }
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        String reading = TextUtil.normalizeJapanese(note.reading(settings));
        String meaning = TextUtil.firstMeaningLine(note.meaning(settings));
        String sentence = TextUtil.normalizeJapanese(note.sentence(settings));
        addSourceText(TextUtil.extractKanji(expression + " " + sentence), meaning, reading, expression, sentence);
    }

    public void addSuspendedImport(RecordsImportModels.SuspendedImport imported) {
        if (imported == null) {
            return;
        }
        MutableItem item = itemFor(imported.kanji);
        for (RecordsImportModels.SuspendedSource source : safeList(imported.sources)) {
            item.add(source.meaning, source.reading, source.expression, source.sentence);
        }
    }

    public void addDashboardRow(RecordsImportModels.DashboardRow row) {
        if (row == null) {
            return;
        }
        MutableItem item = itemFor(row.kanji);
        item.add(row.primaryMeaning, row.reading, row.reasonText, row.browserSearch);
        item.browserSearch = nullToEmpty(row.browserSearch);
        for (RecordsImportModels.Example example : safeList(row.examples)) {
            item.exampleCount++;
            item.add(example.meaning, example.reading, example.expression, example.sentence);
        }
    }

    public void addKnownKanji(String kanji) {
        itemFor(kanji);
    }

    public void addSourceText(
            List<String> kanji,
            String meaning,
            String reading,
            String expression,
            String sentence
    ) {
        for (String glyph : safeList(kanji)) {
            itemFor(glyph).add(meaning, reading, expression, sentence);
        }
    }

    public List<BuiltItem> build(Map<String, PreviousItem> previousItems) {
        Map<String, PreviousItem> previous = previousItems == null ? Collections.emptyMap() : previousItems;
        List<BuiltItem> out = new ArrayList<>();
        for (MutableItem item : items.values()) {
            if (item.kanji.isEmpty()) {
                continue;
            }
            PreviousItem old = previous.get(item.kanji);
            out.add(new BuiltItem(
                    item.kanji,
                    firstNonEmpty(item.primaryMeaning, old == null ? "" : old.primaryMeaning()),
                    item.readingsText(old == null ? "" : old.readings()),
                    firstNonEmpty(
                            item.browserSearch,
                            old == null ? TextUtil.browserSearchForKanji(item.kanji, settings) : old.browserSearch()
                    ),
                    item.searchText(old),
                    Math.max(item.sourceCount, old == null ? 0 : old.sourceCount()),
                    Math.max(item.exampleCount, old == null ? 0 : old.exampleCount()),
                    old == null ? nowMillis : old.firstSeenAtMillis(),
                    nowMillis
            ));
        }
        return out;
    }

    private MutableItem itemFor(String kanji) {
        String normalized = nullToEmpty(kanji);
        MutableItem item = items.get(normalized);
        if (item == null) {
            item = new MutableItem(normalized);
            items.put(normalized, item);
        }
        return item;
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    public record PreviousItem(
            String primaryMeaning,
            String readings,
            String browserSearch,
            int sourceCount,
            int exampleCount,
            long firstSeenAtMillis,
            long lastSeenAtMillis
    ) {
        public PreviousItem {
            primaryMeaning = nullToEmpty(primaryMeaning);
            readings = nullToEmpty(readings);
            browserSearch = nullToEmpty(browserSearch);
            sourceCount = Math.max(0, sourceCount);
            exampleCount = Math.max(0, exampleCount);
            firstSeenAtMillis = Math.max(0L, firstSeenAtMillis);
            lastSeenAtMillis = Math.max(0L, lastSeenAtMillis);
        }
    }

    public record BuiltItem(
            String kanji,
            String primaryMeaning,
            String readings,
            String browserSearch,
            String searchText,
            int sourceCount,
            int exampleCount,
            long firstSeenAtMillis,
            long lastSeenAtMillis
    ) {
        public BuiltItem {
            kanji = nullToEmpty(kanji);
            primaryMeaning = nullToEmpty(primaryMeaning);
            readings = nullToEmpty(readings);
            browserSearch = nullToEmpty(browserSearch);
            searchText = nullToEmpty(searchText);
            sourceCount = Math.max(0, sourceCount);
            exampleCount = Math.max(0, exampleCount);
            firstSeenAtMillis = Math.max(0L, firstSeenAtMillis);
            lastSeenAtMillis = Math.max(0L, lastSeenAtMillis);
        }
    }

    private static final class MutableItem {
        private final String kanji;
        private String primaryMeaning = "";
        private String browserSearch = "";
        private int sourceCount;
        private int exampleCount;
        private final Set<String> readings = new LinkedHashSet<>();
        private final Set<String> searchParts = new HashSet<>();

        private MutableItem(String kanji) {
            this.kanji = nullToEmpty(kanji);
            searchParts.add(this.kanji.toLowerCase(Locale.ROOT));
        }

        private void add(String meaning, String reading, String expression, String sentence) {
            sourceCount++;
            if (primaryMeaning.isEmpty() && meaning != null && !meaning.isEmpty()) {
                primaryMeaning = meaning;
            }
            if (reading != null && !reading.isEmpty()) {
                readings.add(reading);
            }
            addSearch(meaning);
            addSearch(reading);
            addSearch(expression);
            addSearch(sentence);
        }

        private void addSearch(String value) {
            String normalized = TextUtil.normalizeJapanese(value);
            if (!normalized.isEmpty()) {
                searchParts.add(normalized.toLowerCase(Locale.ROOT));
            }
        }

        private String readingsText(String previous) {
            if (readings.isEmpty()) {
                return previous == null ? "" : previous;
            }
            List<String> display = new ArrayList<>();
            int hidden = 0;
            for (String reading : readings) {
                if (display.size() < MAX_DISPLAYED_READINGS) {
                    display.add(reading);
                } else {
                    hidden++;
                }
            }
            String text = String.join(" / ", display);
            return hidden == 0 ? text : text + " +" + hidden + " more";
        }

        private String searchText(PreviousItem previous) {
            if (previous != null) {
                addSearch(previous.primaryMeaning());
                addSearch(previous.readings());
                addSearch(previous.browserSearch());
            }
            return String.join(" ", searchParts);
        }
    }
}
