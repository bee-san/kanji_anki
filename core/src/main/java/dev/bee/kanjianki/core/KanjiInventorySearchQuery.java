package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class KanjiInventorySearchQuery {
    private final List<String> terms;

    private KanjiInventorySearchQuery(List<String> terms) {
        this.terms = Collections.unmodifiableList(new ArrayList<>(terms));
    }

    public static KanjiInventorySearchQuery parse(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return new KanjiInventorySearchQuery(Collections.emptyList());
        }
        String[] parts = normalized.split(" ");
        ArrayList<String> terms = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                terms.add(part);
            }
        }
        return new KanjiInventorySearchQuery(terms);
    }

    public List<String> terms() {
        return terms;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public boolean matches(String searchText) {
        if (terms.isEmpty()) {
            return true;
        }
        String normalized = normalize(searchText);
        for (String term : terms) {
            if (!normalized.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return TextUtil.normalizeJapanese(value).toLowerCase(Locale.ROOT);
    }
}
