package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HomeImportOnboardingPolicy {
    private HomeImportOnboardingPolicy() {
    }

    public enum State {
        INSTALL_ANKIDROID,
        GRANT_PERMISSION,
        CHOOSE_SOURCE,
        READY_FIRST_SYNC,
        RECOVER_PERMISSION,
        RECOVER_SYNC,
        SYNCED
    }

    public static final class LastSync {
        public final String status;
        public final int importedKanji;
        public final String errorMessage;

        public LastSync(String status, int importedKanji, String errorMessage) {
            this.status = status == null ? "" : status;
            this.importedKanji = Math.max(0, importedKanji);
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    public static final class Plan {
        private final State state;
        private final String body;
        private final String primaryActionLabel;

        private Plan(State state, String body, String primaryActionLabel) {
            this.state = state;
            this.body = body;
            this.primaryActionLabel = primaryActionLabel;
        }

        public State state() {
            return state;
        }

        public String body() {
            return body;
        }

        public String primaryActionLabel() {
            return primaryActionLabel;
        }
    }

    public static Plan plan(
            boolean providerInstalled,
            boolean permissionGranted,
            boolean canSync,
            LastSync lastSync,
            String permissionName,
            RecordsSyncModels.Settings settings
    ) {
        if (settings == null) {
            throw new NullPointerException("settings");
        }
        if (!providerInstalled) {
            return new Plan(
                    State.INSTALL_ANKIDROID,
                    "Install AnkiDroid first, then return to Kani to import your local kanji cards.",
                    "Install AnkiDroid"
            );
        }
        if (!permissionGranted || !canSync) {
            return new Plan(
                    State.GRANT_PERMISSION,
                    permissionBody(permissionName),
                    "Grant permission"
            );
        }
        if (!settings.hasImportSourceEnabled()) {
            return new Plan(
                    State.CHOOSE_SOURCE,
                    "Choose AnkiDroid import sources before the first sync: enable suspended, active, tagged, weak, or browser-query import.",
                    "Review import settings"
            );
        }
        String status = lastSync == null ? "" : lastSync.status;
        if ("success".equals(status)) {
            return new Plan(
                    State.SYNCED,
                    "Last sync completed with " + StudyTextCopy.countText(lastSync.importedKanji, "kanji ready", "kanji ready") + ". " + sourceAndModelLine(settings),
                    "Sync again"
            );
        }
        if ("failed".equals(status)) {
            String error = lastSync.errorMessage.isEmpty() ? HomeTextCopy.syncFailureFallback() : lastSync.errorMessage;
            if (error.toLowerCase(Locale.ROOT).contains("permission")) {
                return new Plan(
                        State.RECOVER_PERMISSION,
                        "Kani could not read AnkiDroid because of permission: " + error + ". Grant the local AnkiDroid database permission, then try sync again.",
                        "Fix permission"
                );
            }
            return new Plan(
                    State.RECOVER_SYNC,
                    "The last AnkiDroid sync failed: " + error + ". Check deck/source selection and try sync again.",
                    "Try sync again"
            );
        }
        return new Plan(
                State.READY_FIRST_SYNC,
                "Ready for first sync. " + sourceAndModelLine(settings) + " Kani reads local AnkiDroid data only after you confirm.",
                "Sync cards"
        );
    }

    public static String sourceAndModelLine(RecordsSyncModels.Settings settings) {
        if (settings == null) {
            throw new NullPointerException("settings");
        }
        String browser = settings.browserQueryImportEnabled()
                ? " Deck/source query: " + settings.normalizedBrowserQuery() + "."
                : "";
        String sources = importSources(settings);
        return "Using note type " + settings.modelName + "; source selection: " + (sources.isEmpty() ? "none" : sources) + "." + browser;
    }

    private static String permissionBody(String permissionName) {
        String permission = permissionName == null || permissionName.isEmpty() ? "AnkiDroid database" : permissionName;
        return "Kani needs the " + permission + " permission to read your local AnkiDroid decks. This stays on your device and does not upload your AnkiDroid data.";
    }

    private static String importSources(RecordsSyncModels.Settings settings) {
        List<String> sources = new ArrayList<>();
        if (settings.importActiveCards) {
            sources.add("active cards");
        }
        if (settings.importSuspendedCards) {
            sources.add("suspended cards");
        }
        if (settings.importTaggedCardsEnabled()) {
            sources.add("tagged cards");
        }
        if (settings.importWeakCards) {
            sources.add("weak cards");
        }
        if (settings.browserQueryImportEnabled()) {
            sources.add("browser query");
        }
        return String.join(" + ", sources);
    }
}
