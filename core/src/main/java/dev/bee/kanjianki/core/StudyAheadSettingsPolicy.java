package dev.bee.kanjianki.core;

public final class StudyAheadSettingsPolicy {
    private StudyAheadSettingsPolicy() {
    }

    public static SaveResult saveRequest(String minutesText) {
        int minutes;
        try {
            minutes = Integer.parseInt(minutesText.trim());
        } catch (NumberFormatException error) {
            return SaveResult.invalid(SettingsTextCopy.studyAheadWholeNumberErrorText());
        }
        if (minutes < SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES
                || minutes > SettingsInputRules.MAX_STUDY_AHEAD_MINUTES) {
            return SaveResult.invalid(SettingsTextCopy.studyAheadOutOfRangeErrorText());
        }
        return SaveResult.valid(minutes);
    }

    public static final class SaveResult {
        public final boolean valid;
        public final int minutes;
        public final String message;

        private SaveResult(boolean valid, int minutes, String message) {
            this.valid = valid;
            this.minutes = minutes;
            this.message = message;
        }

        static SaveResult valid(int minutes) {
            return new SaveResult(true, minutes, "");
        }

        static SaveResult invalid(String message) {
            return new SaveResult(false, 0, message);
        }
    }
}
