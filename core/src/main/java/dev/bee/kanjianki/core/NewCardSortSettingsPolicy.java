package dev.bee.kanjianki.core;

public final class NewCardSortSettingsPolicy {
    public static final String SAVED_MESSAGE = "New card sort saved.";

    private NewCardSortSettingsPolicy() {
    }

    public static SaveRequest saveRequest(String selectedMode) {
        return new SaveRequest(RecordsSyncModels.Settings.normalizeNewCardSortMode(selectedMode), SAVED_MESSAGE);
    }

    public static final class SaveRequest {
        public final String mode;
        public final String message;

        private SaveRequest(String mode, String message) {
            this.mode = mode;
            this.message = message;
        }
    }
}
