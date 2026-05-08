package dev.bee.kanjianki.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class SimilarKanjiIndex {
    public static final String SOURCE_KIKU_VISUALLY_SIMILAR = "kiku:wk-visually-similar";

    private final Map<String, Set<String>> similarByKanji;
    private final List<Pair> pairs;

    private SimilarKanjiIndex(Map<String, Set<String>> similarByKanji, List<Pair> pairs) {
        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : similarByKanji.entrySet()) {
            immutable.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        this.similarByKanji = Collections.unmodifiableMap(immutable);
        this.pairs = Collections.unmodifiableList(new ArrayList<>(pairs));
    }

    public static SimilarKanjiIndex empty() {
        return new SimilarKanjiIndex(Collections.emptyMap(), Collections.emptyList());
    }

    public static SimilarKanjiIndex parseTsv(Reader reader) throws IOException {
        Map<String, Set<String>> similarByKanji = new HashMap<>();
        Map<String, Pair> pairsByKey = new LinkedHashMap<>();
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        while ((line = buffered.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            if (cells.length < 2 || "kanji_a".equals(cells[0])) {
                continue;
            }
            String kanjiA = cleanKanji(cells[0]);
            String kanjiB = cleanKanji(cells[1]);
            if (kanjiA.isEmpty() || kanjiB.isEmpty() || kanjiA.equals(kanjiB)) {
                continue;
            }
            String source = cells.length >= 3 && !cells[2].trim().isEmpty()
                    ? cells[2].trim()
                    : SOURCE_KIKU_VISUALLY_SIMILAR;
            Pair pair = Pair.canonical(kanjiA, kanjiB, source);
            String key = pair.key();
            if (!pairsByKey.containsKey(key)) {
                pairsByKey.put(key, pair);
                similarByKanji.computeIfAbsent(pair.kanjiA, ignored -> new TreeSet<>()).add(pair.kanjiB);
                similarByKanji.computeIfAbsent(pair.kanjiB, ignored -> new TreeSet<>()).add(pair.kanjiA);
            }
        }
        List<Pair> pairs = new ArrayList<>(pairsByKey.values());
        Collections.sort(pairs);
        return new SimilarKanjiIndex(similarByKanji, pairs);
    }

    public boolean areSimilar(String first, String second) {
        String a = cleanKanji(first);
        String b = cleanKanji(second);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
            return false;
        }
        Set<String> matches = similarByKanji.get(a);
        return matches != null && matches.contains(b);
    }

    public List<String> similarTo(String kanji) {
        String normalized = cleanKanji(kanji);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> matches = similarByKanji.get(normalized);
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(matches);
    }

    public List<Pair> pairsWithin(Collection<String> kanji) {
        if (kanji == null || kanji.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> local = new HashSet<>();
        for (String glyph : kanji) {
            String normalized = cleanKanji(glyph);
            if (!normalized.isEmpty()) {
                local.add(normalized);
            }
        }
        if (local.size() < 2) {
            return Collections.emptyList();
        }
        List<Pair> out = new ArrayList<>();
        for (Pair pair : pairs) {
            if (local.contains(pair.kanjiA) && local.contains(pair.kanjiB)) {
                out.add(pair);
            }
        }
        return out;
    }

    public int pairCount() {
        return pairs.size();
    }

    private static String cleanKanji(String value) {
        String normalized = TextUtil.normalizeJapanese(value);
        if (normalized.codePointCount(0, normalized.length()) != 1) {
            return "";
        }
        int cp = normalized.codePointAt(0);
        return TextUtil.isKanji(cp) ? normalized : "";
    }

    public static final class Pair implements Comparable<Pair> {
        public final String kanjiA;
        public final String kanjiB;
        public final String source;

        private Pair(String kanjiA, String kanjiB, String source) {
            this.kanjiA = kanjiA;
            this.kanjiB = kanjiB;
            this.source = source == null || source.trim().isEmpty()
                    ? SOURCE_KIKU_VISUALLY_SIMILAR
                    : source.trim();
        }

        public static Pair canonical(String first, String second, String source) {
            if (first.compareTo(second) <= 0) {
                return new Pair(first, second, source);
            }
            return new Pair(second, first, source);
        }

        private String key() {
            return kanjiA + "\u0000" + kanjiB + "\u0000" + source;
        }

        @Override
        public int compareTo(Pair other) {
            int a = kanjiA.compareTo(other.kanjiA);
            if (a != 0) {
                return a;
            }
            int b = kanjiB.compareTo(other.kanjiB);
            if (b != 0) {
                return b;
            }
            return source.compareTo(other.source);
        }
    }
}
