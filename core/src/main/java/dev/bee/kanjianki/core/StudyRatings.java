package dev.bee.kanjianki.core;

/** Public scheduler rating wire names. */
public final class StudyRatings {
    public static final String AGAIN = "again";
    public static final String HARD = "hard";
    public static final String GOOD = "good";
    public static final String EASY = "easy";

    private StudyRatings() {
    }

    public static String normalize(String rating) {
        if (rating == null) {
            return AGAIN;
        }
        return switch (rating) {
            case AGAIN, HARD, GOOD, EASY -> rating;
            default -> AGAIN;
        };
    }
}
