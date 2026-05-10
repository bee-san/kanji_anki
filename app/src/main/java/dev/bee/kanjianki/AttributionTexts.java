package dev.bee.kanjianki;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.bee.kanjianki.data.DictionaryStore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AttributionTexts {
    private static final String DICTIONARY_FALLBACK = "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.";
    private static final String KANJIVG_FALLBACK = "KanjiVG stroke data, CC BY-SA 3.0.";

    private AttributionTexts() {
    }

    static String kanjiVg(Context context) {
        String text = rawResourceText(context, R.raw.kanjivg_attribution).trim();
        return text.isEmpty() ? KANJIVG_FALLBACK : text;
    }

    static String dictionarySources(Context context) {
        try {
            JSONObject manifest = new JSONObject(DictionaryStore.activeManifestText(context));
            JSONArray sources = manifest.optJSONArray("sources");
            if (sources == null || sources.length() == 0) {
                return "Dictionary manifest is empty.";
            }
            List<String> lines = new ArrayList<>();
            String generatedAt = manifest.optString("generated_at");
            if (!generatedAt.isEmpty()) {
                lines.add("Generated: " + generatedAt);
            }
            for (int i = 0; i < sources.length(); i++) {
                appendSource(lines, sources.getJSONObject(i));
            }
            appendNotes(lines, manifest.optJSONArray("notes"));
            return String.join("\n", lines).trim();
        } catch (Exception error) {
            return DICTIONARY_FALLBACK;
        }
    }

    static String rawResourceText(Context context, int resourceId) {
        try (InputStream in = context.getResources().openRawResource(resourceId);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                out.append(buffer, 0, read);
            }
            return out.toString().trim();
        } catch (Exception error) {
            return "";
        }
    }

    private static void appendSource(List<String> lines, JSONObject source) {
        lines.add("");
        lines.add(source.optString("name", source.optString("id")));
        addSourceLine(lines, "License", source.optString("license"));
        addSourceLine(lines, "URL", source.optString("upstream_url"));
        addSourceLine(lines, "Source", source.optString("source_path"));
        addSourceLine(lines, "Fetched", source.optString("fetch_date"));
        addSourceLine(lines, "Version", firstNonEmpty(
                source.optString("database_version"),
                source.optString("version"),
                source.optString("date_of_creation")
        ));
        addSourceLine(lines, "SHA-256", source.optString("source_sha256"));
    }

    private static void appendNotes(List<String> lines, JSONArray notes) {
        if (notes == null || notes.length() == 0) {
            return;
        }
        lines.add("");
        for (int i = 0; i < notes.length(); i++) {
            lines.add(notes.optString(i));
        }
    }

    private static void addSourceLine(List<String> lines, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            lines.add(label + ": " + value.trim());
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
