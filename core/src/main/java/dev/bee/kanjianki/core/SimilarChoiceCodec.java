package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class SimilarChoiceCodec {
    private static final String SEPARATOR = "\t";
    private static final Pattern TAB_SEPARATOR = Pattern.compile("\\t");

    private SimilarChoiceCodec() {
    }

    public static String serializeChoices(List<String> choices) {
        return String.join(SEPARATOR, choices == null ? Collections.emptyList() : choices);
    }

    public static List<String> deserializeChoices(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        String[] parts = TAB_SEPARATOR.split(encoded, -1);
        for (String part : parts) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }
}
