package dev.bee.kanjianki;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.bee.kanjianki.core.AttributionCopy;
import dev.bee.kanjianki.data.DictionaryStore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AttributionTexts {
    private AttributionTexts() {
    }

    static String kanjiVg(Context context) {
        String text = rawResourceText(context, R.raw.kanjivg_attribution).trim();
        return text.isEmpty() ? AttributionCopy.KANJIVG_FALLBACK : text;
    }

    static String dictionarySources(Context context) {
        try {
            return dictionarySourcesFromManifestText(DictionaryStore.activeManifestText(context));
        } catch (Exception error) {
            return AttributionCopy.DICTIONARY_FALLBACK;
        }
    }

    static String dictionarySourcesFromManifestText(String manifestText) {
        try {
            return dictionarySourcesFromManifest(new JSONObject(manifestText));
        } catch (Exception error) {
            return AttributionCopy.DICTIONARY_FALLBACK;
        }
    }

    static String dictionarySourcesFromManifest(JSONObject manifest) {
        try {
            JSONArray sources = manifest == null ? null : manifest.optJSONArray("sources");
            if (sources == null) {
                return AttributionCopy.DICTIONARY_FALLBACK;
            }
            if (sources.length() == 0) {
                return AttributionCopy.dictionarySources("", Collections.emptyList(), Collections.emptyList());
            }
            return AttributionCopy.dictionarySources(
                    manifest.optString("generated_at"),
                    sourcesFromJson(sources),
                    notesFromJson(manifest.optJSONArray("notes"))
            );
        } catch (Exception error) {
            return AttributionCopy.DICTIONARY_FALLBACK;
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

    static void appendSource(List<String> lines, JSONObject source) {
        AttributionCopy.appendSource(lines, sourceFromJson(source));
    }

    static void appendNotes(List<String> lines, JSONArray notes) {
        AttributionCopy.appendNotes(lines, notesFromJson(notes));
    }

    private static List<AttributionCopy.Source> sourcesFromJson(JSONArray sources) throws Exception {
        List<AttributionCopy.Source> parsed = new ArrayList<>();
        for (int i = 0; i < sources.length(); i++) {
            parsed.add(sourceFromJson(sources.getJSONObject(i)));
        }
        return parsed;
    }

    private static AttributionCopy.Source sourceFromJson(JSONObject source) {
        if (source == null) {
            return null;
        }
        return new AttributionCopy.Source(
                source.optString("id"),
                source.optString("name"),
                source.optString("license"),
                source.optString("upstream_url"),
                source.optString("source_path"),
                source.optString("fetch_date"),
                source.optString("database_version"),
                source.optString("version"),
                source.optString("date_of_creation"),
                source.optString("source_sha256")
        );
    }

    private static List<String> notesFromJson(JSONArray notes) {
        if (notes == null || notes.length() == 0) {
            return Collections.emptyList();
        }
        List<String> parsed = new ArrayList<>();
        for (int i = 0; i < notes.length(); i++) {
            parsed.add(notes.optString(i));
        }
        return parsed;
    }
}
