package dev.bee.kanjianki.syncdomain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProviderCardPolicy {
    private static final String STABILITY = "stability";
    private static final String DIFFICULTY = "difficulty";
    private static final String RETRIEVABILITY = "retrievability";
    private static final Pattern FSRS_DATA_VALUE = Pattern.compile(
            "(?:\"|')?(stability|difficulty|retrievability|s|d|r)(?:\"|')?\\s*[:=]\\s*\"?([-+]?[0-9]+(?:\\.[0-9]+)?)\"?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINITE_DOUBLE_VALUE = Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");

    private ProviderCardPolicy() {
    }

    public static boolean shouldReportCardProgress(int scanned, int total) {
        if (scanned <= 0 || scanned == total || total <= 100) {
            return true;
        }
        if (scanned <= 10) {
            return true;
        }
        return scanned % (total <= 1000 ? 10 : 50) == 0;
    }

    public static FsrsMemoryState fsrsMemoryState(
            String fsrsStability,
            String legacyStability,
            String fsrsDifficulty,
            String legacyDifficulty,
            String fsrsRetrievability,
            String legacyRetrievability,
            String serializedData
    ) {
        Double stability = firstFiniteDouble(fsrsStability, legacyStability);
        Double difficulty = firstFiniteDouble(fsrsDifficulty, legacyDifficulty);
        Double retrievability = firstFiniteDouble(fsrsRetrievability, legacyRetrievability);
        if (stability != null || difficulty != null || retrievability != null) {
            return new FsrsMemoryState(stability, difficulty, retrievability);
        }
        return parseFsrsData(serializedData);
    }

    static FsrsMemoryState parseFsrsData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return FsrsMemoryState.EMPTY;
        }
        Double stability = null;
        Double difficulty = null;
        Double retrievability = null;
        Matcher matcher = FSRS_DATA_VALUE.matcher(data);
        while (matcher.find()) {
            Double value = parseDouble(matcher.group(2));
            if (value == null) {
                continue;
            }
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            if (STABILITY.equals(key) || "s".equals(key)) {
                stability = value;
            } else if (DIFFICULTY.equals(key) || "d".equals(key)) {
                difficulty = value;
            } else {
                retrievability = value;
            }
        }
        return new FsrsMemoryState(stability, difficulty, retrievability);
    }

    static Double parseDouble(String value) {
        if (value == null || !FINITE_DOUBLE_VALUE.matcher(value).matches()) {
            return null;
        }
        double parsed = Double.parseDouble(value);
        return Double.isInfinite(parsed) ? null : parsed;
    }

    private static Double firstFiniteDouble(String firstValue, String fallbackValue) {
        Double value = parseDouble(trim(firstValue));
        return value == null ? parseDouble(trim(fallbackValue)) : value;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public record FsrsMemoryState(Double stability, Double difficulty, Double retrievability) {
        private static final FsrsMemoryState EMPTY = new FsrsMemoryState(null, null, null);
    }
}
