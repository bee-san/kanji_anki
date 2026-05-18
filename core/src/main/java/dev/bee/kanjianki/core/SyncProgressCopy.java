package dev.bee.kanjianki.core;

import java.util.Locale;

public final class SyncProgressCopy {
    private SyncProgressCopy() {
    }

    public static String stageTitle(Stage stage) {
        if (stage == Stage.FINDING_NOTE_TYPE) {
            return "Finding note type";
        }
        if (stage == Stage.READING_NOTES) {
            return "Reading notes";
        }
        if (stage == Stage.SCANNING_CARDS) {
            return "Scanning cards";
        }
        if (stage == Stage.PROCESSING_IMPORTED_CARDS) {
            return "Processing imported cards";
        }
        if (stage == Stage.BUILDING_PRACTICE_QUEUE) {
            return "Building practice queue";
        }
        if (stage == Stage.ARCHIVING_IMPORTED_CARDS) {
            return "Archiving imported suspended cards";
        }
        return "Syncing cards";
    }

    public static String stageBody(Stage stage) {
        if (stage == Stage.FINDING_NOTE_TYPE) {
            return "Checking collection shape.";
        }
        if (stage == Stage.READING_NOTES) {
            return "Reading notes before the card total is known.";
        }
        if (stage == Stage.PROCESSING_IMPORTED_CARDS) {
            return "AnkiDroid read finished. Processing imported cards locally.";
        }
        if (stage == Stage.BUILDING_PRACTICE_QUEUE) {
            return "Saving the practice queue.";
        }
        if (stage == Stage.ARCHIVING_IMPORTED_CARDS) {
            return "Updating archived suspended cards.";
        }
        return "Preparing card scan.";
    }

    public static int progressPermille(int scannedCards, int totalCards) {
        if (totalCards <= 0) {
            return 1000;
        }
        return Math.min(1000, Math.max(0, Math.round((Math.max(0, scannedCards) * 1000f) / totalCards)));
    }

    public static String cardProgressText(int scannedCards, int totalCards) {
        return Math.max(0, scannedCards) + " / " + Math.max(0, totalCards) + " cards scanned";
    }

    public static String scanRateText(Stage stage, int scannedCards, int totalCards, long elapsedMillis) {
        if (stage != Stage.SCANNING_CARDS) {
            return stageBody(stage);
        }
        int scanned = Math.max(0, scannedCards);
        if (scanned <= 0) {
            return "Scanning cards.";
        }
        long elapsed = Math.max(1L, elapsedMillis);
        double perSecond = scanned * 1000.0 / elapsed;
        String rateText = String.format(Locale.US, perSecond >= 10.0 ? "%.0f cards/sec" : "%.1f cards/sec", perSecond);
        int remaining = Math.max(0, Math.max(0, totalCards) - scanned);
        if (remaining == 0) {
            return rateText + " - finishing up";
        }
        if (scanned >= 3 && elapsed >= 1000L) {
            long etaMillis = Math.round((remaining / perSecond) * 1000.0);
            return rateText + " - about " + shortDuration(etaMillis) + " left";
        }
        return rateText + " - estimating time left";
    }

    public static String shortDuration(long millis) {
        long seconds = Math.max(1L, Math.round(millis / 1000.0));
        if (seconds < 60L) {
            return seconds + " sec";
        }
        long minutes = Math.max(1L, Math.round(seconds / 60.0));
        if (minutes < 60L) {
            return minutes + " min";
        }
        long hours = Math.max(1L, Math.round(minutes / 60.0));
        return hours + " hr";
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
