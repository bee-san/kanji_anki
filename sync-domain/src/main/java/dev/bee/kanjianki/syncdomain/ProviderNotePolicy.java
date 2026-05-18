package dev.bee.kanjianki.syncdomain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProviderNotePolicy {
    public static final String ARCHIVED_TAG = "kani_archived";
    private static final String LEGACY_ARCHIVED_TAG = "kanji_anki_archived";
    private static final String NOTE_MODEL_QUERY_PREFIX = "note:\"";

    private ProviderNotePolicy() {
    }

    public static boolean isArchivedTagPresent(List<String> tags) {
        return tags != null && (tags.contains(ARCHIVED_TAG) || tags.contains(LEGACY_ARCHIVED_TAG));
    }

    public static Map<String, String> selectRequiredFields(List<String> modelFields, List<String> values, List<String> requiredFields) {
        Map<String, String> fieldMap = new LinkedHashMap<>();
        for (String field : requiredFields) {
            int index = modelFields.indexOf(field);
            fieldMap.put(field, index >= 0 && index < values.size() ? values.get(index) : "");
        }
        return fieldMap;
    }

    public static String configuredBrowserQuerySearch(String modelName, String normalizedBrowserQuery) {
        return modelSearch(modelName) + " (" + normalizedBrowserQuery + ")";
    }

    public static String modelSearch(String modelName) {
        return NOTE_MODEL_QUERY_PREFIX + modelName + "\"";
    }
}
