package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.SyncProgressCopy;

public final class SyncProgress {
    public static final Listener NONE = progress -> {
    };

    public final Stage stage;
    public final int scannedCards;
    public final int totalCards;

    private SyncProgress(Stage stage, int scannedCards, int totalCards) {
        this.stage = stage;
        this.scannedCards = Math.max(0, scannedCards);
        this.totalCards = totalCards;
    }

    public static SyncProgress atStage(Stage stage) {
        return new SyncProgress(stage, 0, -1);
    }

    public static SyncProgress cardsScanned(int scannedCards, int totalCards) {
        return new SyncProgress(Stage.SCANNING_CARDS, scannedCards, Math.max(0, totalCards));
    }

    public boolean totalKnown() {
        return totalCards >= 0;
    }

    public SyncProgressCopy.Stage coreStage() {
        return coreStage(stage);
    }

    public static SyncProgressCopy.Stage coreStage(Stage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case FINDING_NOTE_TYPE -> SyncProgressCopy.Stage.FINDING_NOTE_TYPE;
            case READING_NOTES -> SyncProgressCopy.Stage.READING_NOTES;
            case SCANNING_CARDS -> SyncProgressCopy.Stage.SCANNING_CARDS;
            case PROCESSING_IMPORTED_CARDS -> SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS;
            case BUILDING_PRACTICE_QUEUE -> SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE;
            case ARCHIVING_IMPORTED_CARDS -> SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS;
        };
    }

    public interface Listener {
        void onSyncProgress(SyncProgress progress);
    }

    public enum Stage {
        FINDING_NOTE_TYPE,
        READING_NOTES,
        SCANNING_CARDS,
        PROCESSING_IMPORTED_CARDS,
        BUILDING_PRACTICE_QUEUE,
        ARCHIVING_IMPORTED_CARDS
    }
}
