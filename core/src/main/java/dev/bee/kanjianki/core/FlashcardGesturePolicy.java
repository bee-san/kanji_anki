package dev.bee.kanjianki.core;

public final class FlashcardGesturePolicy {
    private static final float HORIZONTAL_DOMINANCE = 1.25f;

    private FlashcardGesturePolicy() {
    }

    public static Decision release(
            float startX,
            float startY,
            float endX,
            float endY,
            int touchSlop,
            int minimumSwipeDistance,
            boolean answerRevealed
    ) {
        float dx = endX - startX;
        float dy = endY - startY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        int safeTouchSlop = Math.max(0, touchSlop);
        if (absX <= safeTouchSlop && absY <= safeTouchSlop) {
            return answerRevealed ? Decision.none() : Decision.reveal();
        }
        int swipeThreshold = Math.max(Math.max(0, minimumSwipeDistance), safeTouchSlop * 6);
        if (absX >= swipeThreshold && absX > absY * HORIZONTAL_DOMINANCE && answerRevealed) {
            return Decision.review(dx > 0 ? StudyRatings.GOOD : StudyRatings.AGAIN);
        }
        return Decision.none();
    }

    public static final class Decision {
        public enum Action {
            NONE,
            REVEAL,
            REVIEW
        }

        public final Action action;
        public final String rating;

        private Decision(Action action, String rating) {
            this.action = action;
            this.rating = rating;
        }

        public static Decision none() {
            return new Decision(Action.NONE, "");
        }

        public static Decision reveal() {
            return new Decision(Action.REVEAL, "");
        }

        public static Decision review(String rating) {
            return new Decision(Action.REVIEW, rating);
        }
    }
}
