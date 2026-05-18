package dev.bee.kanjianki.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JitenKanjiRanks {
    private static final java.util.regex.Pattern CSV_SEPARATOR = java.util.regex.Pattern.compile("[,\\t]");

    private static final JitenKanjiRanks EMPTY = new JitenKanjiRanks(new LinkedHashMap<>());

    private final Map<String, Integer> ranks;

    public JitenKanjiRanks(Map<String, Integer> ranks) {
        this.ranks = new LinkedHashMap<>(ranks);
    }

    public Integer rankOf(String kanji) {
        return ranks.get(kanji);
    }

    public int size() {
        return ranks.size();
    }

    public static JitenKanjiRanks empty() {
        return EMPTY;
    }

    public static JitenKanjiRanks parseCsv(Reader reader) throws IOException {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        while ((line = buffered.readLine()) != null) {
            RankEntry entry = parseLine(line);
            if (entry != null) {
                ranks.put(entry.kanji, entry.rank);
            }
        }
        return new JitenKanjiRanks(ranks);
    }

    private static RankEntry parseLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        String[] cells = CSV_SEPARATOR.split(line);
        if (cells.length < 2) {
            return null;
        }
        String first = cells[0].trim();
        String second = cells[1].trim();
        if (isInteger(first)) {
            return rankEntry(second, Integer.parseInt(first));
        }
        return isInteger(second) ? rankEntry(first, Integer.parseInt(second)) : null;
    }

    private static RankEntry rankEntry(String kanji, int rank) {
        return !kanji.isEmpty() && DictionaryTextUtil.isKanji(kanji.codePointAt(0)) ? new RankEntry(kanji, rank) : null;
    }

    private static boolean isInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 0 && c == '-') {
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private record RankEntry(String kanji, int rank) {
    }
}
