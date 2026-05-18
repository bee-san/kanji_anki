package dev.bee.kanjianki.core.study;

public enum StudyRating {
    AGAIN("again", 0),
    HARD("hard", 1),
    GOOD("good", 2),
    EASY("easy", 3);

    private final String code;
    private final int strength;

    StudyRating(String code, int strength) {
        this.code = code;
        this.strength = strength;
    }

    public String code() {
        return code;
    }

    public boolean strongerThan(StudyRating other) {
        return strength > other.strength;
    }

    public StudyRating cappedAt(StudyRating ceiling) {
        return strongerThan(ceiling) ? ceiling : this;
    }

    public static StudyRating fromCode(String code) {
        if (code == null) {
            return AGAIN;
        }
        for (StudyRating rating : values()) {
            if (rating.code.equals(code)) {
                return rating;
            }
        }
        return AGAIN;
    }
}
