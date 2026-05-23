package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FrequencyRetentionRanges {
    public static final double MIN_RETENTION = 0.10;
    public static final double MAX_RETENTION = 0.99;
    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 20000;
    public static final String EXAMPLE_TEXT = "1-500=95%\n501-2000=90%\n2001-20000=85%";

    private FrequencyRetentionRanges() {
    }

    public static String exampleText() {
        return EXAMPLE_TEXT;
    }

    public static Double retentionForRank(String text, Integer rank) {
        if (rank == null || rank < MIN_RANK || rank > MAX_RANK) {
            return null;
        }
        for (Rule rule : parse(text)) {
            if (rule.contains(rank)) {
                return rule.retention;
            }
        }
        return null;
    }

    public static List<Rule> parse(String text) {
        String raw = text == null ? "" : text;
        List<Rule> rules = new ArrayList<>();
        String[] lines = raw.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (!line.isEmpty()) {
                rules.add(parseLine(line, i + 1));
            }
        }
        rules.sort(Comparator.comparingInt(rule -> rule.minRank));
        validateNoOverlaps(rules);
        return Collections.unmodifiableList(rules);
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static Rule parseLine(String line, int lineNumber) {
        String[] pieces = line.split("=", -1);
        if (pieces.length != 2) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Use rank-range=retention.");
        }
        int[] range = parseRange(pieces[0].trim(), lineNumber);
        double retention = parseRetention(pieces[1].trim(), lineNumber);
        return new Rule(range[0], range[1], retention);
    }

    private static int[] parseRange(String value, int lineNumber) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Rank range is empty.");
        }
        String normalized = value.replace("..", "-");
        String[] pieces = normalized.split("-", -1);
        int min;
        int max;
        try {
            if (pieces.length == 1) {
                min = Integer.parseInt(pieces[0].trim());
                max = min;
            } else if (pieces.length == 2) {
                min = Integer.parseInt(pieces[0].trim());
                max = Integer.parseInt(pieces[1].trim());
            } else {
                throw new NumberFormatException("too many range separators");
            }
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Use numeric Jiten ranks.", error);
        }
        if (min < MIN_RANK || max > MAX_RANK || min > max) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Use ranks 1-20000 in ascending order.");
        }
        return new int[]{min, max};
    }

    private static double parseRetention(String value, int lineNumber) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Retention is empty.");
        }
        boolean percent = value.endsWith("%");
        String numeric = percent ? value.substring(0, value.length() - 1).trim() : value;
        double parsed;
        try {
            parsed = Double.parseDouble(numeric);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Use numeric retention.", error);
        }
        double retention = percent || parsed > 1.0 ? parsed / 100.0 : parsed;
        if (!Double.isFinite(retention) || retention < MIN_RETENTION || retention > MAX_RETENTION) {
            throw new IllegalArgumentException(errorPrefix(lineNumber) + "Use retention from 10% to 99%.");
        }
        return retention;
    }

    private static void validateNoOverlaps(List<Rule> rules) {
        int previousMax = 0;
        for (Rule rule : rules) {
            if (rule.minRank <= previousMax) {
                throw new IllegalArgumentException(String.format(
                        Locale.ROOT,
                        "Rank range %d-%d overlaps an earlier range.",
                        rule.minRank,
                        rule.maxRank
                ));
            }
            previousMax = rule.maxRank;
        }
    }

    private static String errorPrefix(int lineNumber) {
        return "Line " + lineNumber + ": ";
    }

    /*
     * Intentional Java compatibility exception for the Kotlin rewrite.
     * Rule's only constructor must remain genuinely private to Java reflection;
     * the Kotlin equivalent emits an additional synthetic DefaultConstructorMarker
     * constructor. FrequencyRetentionRangesTest locks this down.
     */
    public static final class Rule {
        public final int minRank;
        public final int maxRank;
        public final double retention;

        private Rule(int minRank, int maxRank, double retention) {
            this.minRank = minRank;
            this.maxRank = maxRank;
            this.retention = retention;
        }

        public boolean contains(int rank) {
            return rank >= minRank && rank <= maxRank;
        }
    }
}
