package dev.bee.kanjianki;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class AttributionTextsTest {
    @Test
    public void dictionarySourcesUsesSafeFallbackWithoutAndroidResources() {
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySources(null)
        );
        assertEquals("KanjiVG stroke data, CC BY-SA 3.0.", AttributionTexts.kanjiVg(null));
        assertEquals("", AttributionTexts.rawResourceText(null, 0));
    }

    @Test
    public void manifestFormattingIncludesGeneratedAtSourcesVersionFallbackAndNotes() throws Exception {
        JSONObject kanjidic = object(
                "name", "KANJIDIC2",
                "license", "Creative Commons Attribution-ShareAlike 4.0",
                "upstream_url", "https://example.invalid/kanjidic2.xml",
                "source_path", "kanjidic2.xml",
                "fetch_date", "2026-05-14",
                "database_version", "2026-05-01",
                "version", "ignored because database_version wins",
                "date_of_creation", "ignored because database_version wins",
                "source_sha256", "abcd"
        );
        JSONObject jiten = object(
                "id", "jiten",
                "license", "  ",
                "upstream_url", "",
                "source_path", "jiten.tsv",
                "fetch_date", "2026-05-13",
                "database_version", "",
                "version", "2026-rank",
                "source_sha256", "efgh"
        );
        JSONArray notes = array(
                "Dictionary updates ship as a DB, manifest, and checksum.",
                "Rerun the generator after refreshing source exports."
        );

        List<String> lines = new ArrayList<>();
        lines.add("Generated: 2026-05-15T08:30:00Z");
        appendSource(lines, kanjidic);
        appendSource(lines, jiten);
        appendNotes(lines, notes);

        assertEquals(
                String.join("\n", Arrays.asList(
                        "Generated: 2026-05-15T08:30:00Z",
                        "",
                        "KANJIDIC2",
                        "License: Creative Commons Attribution-ShareAlike 4.0",
                        "URL: https://example.invalid/kanjidic2.xml",
                        "Source: kanjidic2.xml",
                        "Fetched: 2026-05-14",
                        "Version: 2026-05-01",
                        "SHA-256: abcd",
                        "",
                        "jiten",
                        "Source: jiten.tsv",
                        "Fetched: 2026-05-13",
                        "Version: 2026-rank",
                        "SHA-256: efgh",
                        "",
                        "Dictionary updates ship as a DB, manifest, and checksum.",
                        "Rerun the generator after refreshing source exports."
                )),
                String.join("\n", lines).trim()
        );
    }

    @Test
    public void appendNotesIgnoresMissingAndEmptyNoteArrays() throws Exception {
        List<String> lines = new ArrayList<>();

        appendNotes(lines, null);
        appendNotes(lines, array());

        assertEquals("", String.join("\n", lines));
    }

    @Test
    public void dictionaryManifestTextFallsBackForInvalidJsonInJvmUnitTests() {
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySourcesFromManifestText("not json")
        );
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySourcesFromManifestText("{\"sources\":[]}")
        );
    }

    @Test
    public void sourceFormattingFallsBackToIdAndDateOfCreationWhenVersionFieldsAreBlank() throws Exception {
        List<String> lines = new ArrayList<>();
        appendSource(lines, object(
                "id", "legacy-source",
                "license", "",
                "upstream_url", "   ",
                "source_path", "\t",
                "fetch_date", "",
                "database_version", " ",
                "version", "",
                "date_of_creation", " 2025-12-31 ",
                "source_sha256", ""
        ));

        assertEquals(
                String.join("\n", Arrays.asList(
                        "legacy-source",
                        "Version: 2025-12-31"
                )),
                String.join("\n", lines).trim()
        );
    }

    @Test
    public void sourceFormattingUsesVersionWhenDatabaseVersionIsMissing() throws Exception {
        List<String> lines = new ArrayList<>();
        appendSource(lines, objectWithNulls(
                "id", "rank-source",
                "database_version", null,
                "version", " 2026-rank ",
                "date_of_creation", "ignored"
        ));

        assertEquals(
                String.join("\n", Arrays.asList(
                        "rank-source",
                        "Version: 2026-rank"
                )),
                String.join("\n", lines).trim()
        );
    }

    @Test
    public void sourceFormattingOmitsNullAndBlankOptionalValues() throws Exception {
        List<String> lines = new ArrayList<>();
        appendSource(lines, objectWithNulls(
                "name", null,
                "id", "null-heavy",
                "license", null,
                "upstream_url", " ",
                "source_path", null,
                "fetch_date", "",
                "database_version", null,
                "version", null,
                "date_of_creation", null,
                "source_sha256", null
        ));

        assertEquals("null-heavy", String.join("\n", lines).trim());
    }

    private static void appendSource(List<String> lines, JSONObject source) throws Exception {
        AttributionTexts.appendSource(lines, source);
    }

    private static void appendNotes(List<String> lines, JSONArray notes) throws Exception {
        AttributionTexts.appendNotes(lines, notes);
    }

    private static JSONObject object(String... entries) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put(entries[i], entries[i + 1]);
        }
        return new FakeJsonObject(values);
    }

    private static JSONObject objectWithNulls(String... entries) throws Exception {
        return object(entries);
    }

    private static JSONArray array(String... values) {
        return new FakeJsonArray(Arrays.asList(values));
    }

    private static final class FakeJsonObject extends JSONObject {
        private final Map<String, String> values;

        private FakeJsonObject(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String optString(String name) {
            return optString(name, "");
        }

        @Override
        public String optString(String name, String fallback) {
            String value = values.get(name);
            return value == null ? fallback : value;
        }
    }

    private static final class FakeJsonArray extends JSONArray {
        private final List<String> values;

        private FakeJsonArray(List<String> values) {
            this.values = values;
        }

        @Override
        public int length() {
            return values.size();
        }

        @Override
        public String optString(int index) {
            return values.get(index);
        }
    }
}
