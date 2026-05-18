package dev.bee.kanjianki.syncdomain;

import java.util.regex.Pattern;

public final class ImportSettingsRepairPolicy {
    private static final Pattern IMPORT_TAG_SEPARATOR = Pattern.compile("[,\\s]+");
    private static final int OLD_DEFAULT_IMPORT_ACTIVE_CARDS = 1;

    private ImportSettingsRepairPolicy() {
    }

    public static RepairDecision oldDefaultRepair(
            StoredImportSettings settings,
            double expectedWeakFsrsDifficulty,
            int expectedWeakLapses,
            int expectedMinMatchingCards
    ) {
        StoredImportSettings safeSettings = StoredImportSettings.safe(settings);
        if (!hasAnyImportSetting(safeSettings)
                || !matchesOldDefaultImportSettings(
                        safeSettings,
                        expectedWeakFsrsDifficulty,
                        expectedWeakLapses,
                        expectedMinMatchingCards
                )) {
            return RepairDecision.none();
        }
        return RepairDecision.suspendedOnly();
    }

    private static boolean hasAnyImportSetting(StoredImportSettings settings) {
        return settings.importActiveCards != null
                || settings.importSuspendedCards != null
                || settings.importTaggedCards != null
                || settings.importTags != null
                || settings.importWeakCards != null
                || settings.importWeakFsrsDifficulty != null
                || settings.importWeakLapses != null
                || settings.importMinMatchingCards != null;
    }

    private static boolean matchesOldDefaultImportSettings(
            StoredImportSettings settings,
            double expectedWeakFsrsDifficulty,
            int expectedWeakLapses,
            int expectedMinMatchingCards
    ) {
        return intMatchesOrAbsent(settings.importActiveCards, OLD_DEFAULT_IMPORT_ACTIVE_CARDS)
                && intMatchesOrAbsent(settings.importSuspendedCards, 1)
                && intMatchesOrAbsent(settings.importTaggedCards, 0)
                && importTagsEmptyOrAbsent(settings.importTags)
                && intMatchesOrAbsent(settings.importWeakCards, 0)
                && doubleMatchesOrAbsent(settings.importWeakFsrsDifficulty, expectedWeakFsrsDifficulty)
                && intMatchesOrAbsent(settings.importWeakLapses, expectedWeakLapses)
                && intMatchesOrAbsent(settings.importMinMatchingCards, expectedMinMatchingCards);
    }

    private static boolean intMatchesOrAbsent(Integer value, int expected) {
        return value == null || value == expected;
    }

    private static boolean doubleMatchesOrAbsent(Double value, double expected) {
        return value == null || Math.abs(value - expected) < 0.0001;
    }

    private static boolean importTagsEmptyOrAbsent(String value) {
        return value == null
                || value.trim().isEmpty()
                || !hasImportTags(value);
    }

    private static boolean hasImportTags(String value) {
        for (String part : IMPORT_TAG_SEPARATOR.split(value.trim())) {
            if (!part.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static final class StoredImportSettings {
        private Integer importActiveCards;
        private Integer importSuspendedCards;
        private Integer importTaggedCards;
        private String importTags;
        private Integer importWeakCards;
        private Double importWeakFsrsDifficulty;
        private Integer importWeakLapses;
        private Integer importMinMatchingCards;

        public StoredImportSettings importActiveCards(Integer importActiveCards) {
            this.importActiveCards = importActiveCards;
            return this;
        }

        public StoredImportSettings importSuspendedCards(Integer importSuspendedCards) {
            this.importSuspendedCards = importSuspendedCards;
            return this;
        }

        public StoredImportSettings importTaggedCards(Integer importTaggedCards) {
            this.importTaggedCards = importTaggedCards;
            return this;
        }

        public StoredImportSettings importTags(String importTags) {
            this.importTags = importTags;
            return this;
        }

        public StoredImportSettings importWeakCards(Integer importWeakCards) {
            this.importWeakCards = importWeakCards;
            return this;
        }

        public StoredImportSettings importWeakFsrsDifficulty(Double importWeakFsrsDifficulty) {
            this.importWeakFsrsDifficulty = importWeakFsrsDifficulty;
            return this;
        }

        public StoredImportSettings importWeakLapses(Integer importWeakLapses) {
            this.importWeakLapses = importWeakLapses;
            return this;
        }

        public StoredImportSettings importMinMatchingCards(Integer importMinMatchingCards) {
            this.importMinMatchingCards = importMinMatchingCards;
            return this;
        }

        private static StoredImportSettings safe(StoredImportSettings settings) {
            return settings == null ? new StoredImportSettings() : settings;
        }
    }

    public static final class RepairDecision {
        private final boolean shouldRepair;
        private final int importActiveCards;
        private final int importSuspendedCards;

        private RepairDecision(boolean shouldRepair, int importActiveCards, int importSuspendedCards) {
            this.shouldRepair = shouldRepair;
            this.importActiveCards = importActiveCards;
            this.importSuspendedCards = importSuspendedCards;
        }

        private static RepairDecision none() {
            return new RepairDecision(false, 0, 1);
        }

        private static RepairDecision suspendedOnly() {
            return new RepairDecision(true, 0, 1);
        }

        public boolean shouldRepair() {
            return shouldRepair;
        }

        public int importActiveCards() {
            return importActiveCards;
        }

        public int importSuspendedCards() {
            return importSuspendedCards;
        }
    }
}
