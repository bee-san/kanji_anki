package dev.bee.kanjianki.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JitenKanjiRanks {
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

    public static JitenKanjiRanks parseCsv(Reader reader) throws IOException {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        BufferedReader buffered = new BufferedReader(reader);
        String line;
        while ((line = buffered.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("[,\\t]");
            if (cells.length < 2) {
                continue;
            }
            String kanji;
            int rank;
            if (isInteger(cells[0].trim())) {
                rank = Integer.parseInt(cells[0].trim());
                kanji = cells[1].trim();
            } else if (isInteger(cells[1].trim())) {
                kanji = cells[0].trim();
                rank = Integer.parseInt(cells[1].trim());
            } else {
                continue;
            }
            if (!kanji.isEmpty() && TextUtil.isKanji(kanji.codePointAt(0))) {
                ranks.put(kanji, rank);
            }
        }
        return new JitenKanjiRanks(ranks);
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
}
