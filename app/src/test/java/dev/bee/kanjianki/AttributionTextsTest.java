package dev.bee.kanjianki;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import dev.bee.kanjianki.core.AttributionCopy;

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
    public void dictionaryManifestTextFallsBackForInvalidJsonInJvmUnitTests() {
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySourcesFromManifestText("not json")
        );
    }

    @Test
    public void parsedDictionaryManifestDistinguishesMissingSourcesFromEmptySources() {
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySourcesFromManifest(new FakeManifest("2026-05-15", null, null))
        );
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySourcesFromManifest(new FakeNonArraySourcesManifest("2026-05-15"))
        );
        assertEquals(
                "Dictionary manifest is empty.",
                AttributionTexts.dictionarySourcesFromManifest(new FakeManifest("2026-05-15", sourceArray(), null))
        );
    }

    @Test
    public void parsedDictionaryManifestDelegatesSourcesAndNotesToCoreFormatter() {
        JSONArray sources = array(object(
                "name", "KANJIDIC2",
                "license", "CC BY-SA",
                "source_path", "kanjidic2.xml",
                "database_version", "2026-05-01"
        ));
        JSONArray notes = array("note one", "note two");

        assertEquals(
                "Generated: 2026-05-15\n\nKANJIDIC2\nLicense: CC BY-SA\nSource: kanjidic2.xml\nVersion: 2026-05-01\n\nnote one\nnote two",
                AttributionTexts.dictionarySourcesFromManifest(new FakeManifest("2026-05-15", sources, notes))
        );
    }

    @Test
    public void sourceAndNoteAdaptersDelegateToCoreFormatter() throws Exception {
        List<String> lines = new ArrayList<>();

        AttributionCopy.appendSource(lines, new AttributionCopy.Source(
                null,
                "KANJIDIC2",
                "CC BY-SA",
                null,
                "kanjidic2.xml",
                null,
                "2026-05-01",
                null,
                null,
                null
        ));
        AttributionCopy.appendNotes(lines, Arrays.asList("note one", "note two"));

        assertEquals(
                "\nKANJIDIC2\nLicense: CC BY-SA\nSource: kanjidic2.xml\nVersion: 2026-05-01\n\nnote one\nnote two",
                String.join("\n", lines)
        );
    }

    private static JSONObject object(String... entries) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put(entries[i], entries[i + 1]);
        }
        return new FakeJsonObject(values);
    }

    private static JSONArray array(String... values) {
        return new FakeStringArray(Arrays.asList(values));
    }

    private static JSONArray array(JSONObject... values) {
        return new FakeObjectArray(Arrays.asList(values));
    }

    private static JSONArray sourceArray() {
        return new FakeObjectArray(java.util.Collections.emptyList());
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

    private static final class FakeManifest extends JSONObject {
        private final String generatedAt;
        private final JSONArray sources;
        private final JSONArray notes;

        private FakeManifest(String generatedAt, JSONArray sources, JSONArray notes) {
            this.generatedAt = generatedAt;
            this.sources = sources;
            this.notes = notes;
        }

        @Override
        public String optString(String name) {
            return "generated_at".equals(name) ? generatedAt : "";
        }

        @Override
        public JSONArray optJSONArray(String name) {
            if ("sources".equals(name)) {
                return sources;
            }
            if ("notes".equals(name)) {
                return notes;
            }
            return null;
        }
    }

    private static final class FakeStringArray extends JSONArray {
        private final List<String> values;

        private FakeStringArray(List<String> values) {
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

    private static final class FakeNonArraySourcesManifest extends JSONObject {
        private final String generatedAt;

        private FakeNonArraySourcesManifest(String generatedAt) {
            this.generatedAt = generatedAt;
        }

        @Override
        public String optString(String name) {
            if ("generated_at".equals(name)) {
                return generatedAt;
            }
            if ("sources".equals(name)) {
                return "not an array";
            }
            return "";
        }

        @Override
        public JSONArray optJSONArray(String name) {
            return null;
        }
    }

    private static final class FakeObjectArray extends JSONArray {
        private final List<JSONObject> values;

        private FakeObjectArray(List<JSONObject> values) {
            this.values = values;
        }

        @Override
        public int length() {
            return values.size();
        }

        @Override
        public JSONObject getJSONObject(int index) {
            return values.get(index);
        }
    }
}
