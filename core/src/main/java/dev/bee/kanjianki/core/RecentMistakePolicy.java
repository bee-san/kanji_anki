package dev.bee.kanjianki.core;

public final class RecentMistakePolicy {
    private RecentMistakePolicy() {
    }

    public static int boundedLimit(int limit) {
        return Math.max(1, limit);
    }

    public static String[] mistakeRatings() {
        return new String[]{StudyRatings.AGAIN, StudyRatings.HARD};
    }

    public static RecentMistake mistake(String kanji, String rating, long reviewedAtMillis) {
        return new RecentMistake(kanji, rating, reviewedAtMillis);
    }

    public record RecentMistake(String kanji, String rating, long reviewedAtMillis) {
        public RecentMistake {
            kanji = kanji == null ? "" : kanji;
            rating = rating == null ? "" : rating;
        }
    }
}
