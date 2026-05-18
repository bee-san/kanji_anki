package dev.bee.kanjianki.core;

public final class StudyMoreNewCardsPolicy {
    public static final String NO_NEW_CARDS_AVAILABLE_MESSAGE = "No new cards are available.";
    public static final String WHOLE_NUMBER_ERROR_MESSAGE = "Use a whole number of new cards.";
    public static final String POSITIVE_COUNT_ERROR_MESSAGE = "Use at least 1 new card.";

    private StudyMoreNewCardsPolicy() {
    }

    public static int defaultRequestCount(int availableCount) {
        return Math.max(1, Math.min(5, availableCount));
    }

    public static RequestDecision requestedCount(String rawText) {
        int requested;
        try {
            requested = Integer.parseInt(rawText.trim());
        } catch (NumberFormatException error) {
            return RequestDecision.rejected(WHOLE_NUMBER_ERROR_MESSAGE);
        }
        if (requested <= 0) {
            return RequestDecision.rejected(POSITIVE_COUNT_ERROR_MESSAGE);
        }
        return RequestDecision.accepted(requested);
    }

    public static String partialAvailabilityMessage(int admittedCount) {
        return "Only " + StudyTextCopy.countText(admittedCount, "new card was", "new cards were") + " available.";
    }

    public record RequestDecision(int requestedCount, String message) {
        public boolean accepted() {
            return requestedCount > 0;
        }

        private static RequestDecision accepted(int requestedCount) {
            return new RequestDecision(requestedCount, "");
        }

        private static RequestDecision rejected(String message) {
            return new RequestDecision(-1, message);
        }
    }
}
