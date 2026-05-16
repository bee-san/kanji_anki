package dev.bee.fsrs;

/**
 * FSRS review ratings in their upstream numeric order.
 */
public enum FsrsRating {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    private final int value;

    FsrsRating(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static FsrsRating fromValue(int value) {
        for (FsrsRating rating : values()) {
            if (rating.value == value) {
                return rating;
            }
        }
        throw new IllegalArgumentException("Unknown FSRS rating value: " + value);
    }
}
