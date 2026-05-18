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
    public void dictionaryManifestTextFallsBackForInvalidJsonInJvmUnitTests() {
        assertEquals(
                "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
                AttributionTexts.dictionarySourcesFromManifestText("not json")
        );
    }

    @Test
    public void sourceAndNoteAdaptersDelegateToCoreFormatter() throws Exception {
        List<String> lines = new ArrayList<>();

        AttributionTexts.appendSource(lines, object(
                "name", "KANJIDIC2",
                "license", "CC BY-SA",
                "source_path", "kanjidic2.xml",
                "database_version", "2026-05-01"
        ));
        AttributionTexts.appendNotes(lines, array("note one", "note two"));

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
