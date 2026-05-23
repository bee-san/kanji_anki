package dev.bee.kanjianki.core

import java.util.Locale

object SyncProgressCopy {
    @JvmStatic
    fun stageTitle(stage: Stage?): String {
        return when (stage) {
            Stage.FINDING_NOTE_TYPE -> "Finding note type"
            Stage.READING_NOTES -> "Reading notes"
            Stage.SCANNING_CARDS -> "Scanning cards"
            Stage.PROCESSING_IMPORTED_CARDS -> "Processing imported cards"
            Stage.SAVING_LOCAL_DATA -> "Saving local data"
            Stage.BUILDING_PRACTICE_QUEUE -> "Building practice queue"
            Stage.ARCHIVING_IMPORTED_CARDS -> "Archiving imported suspended cards"
            null -> "Syncing cards"
        }
    }

    @JvmStatic
    fun stageBody(stage: Stage?): String {
        return when (stage) {
            Stage.FINDING_NOTE_TYPE -> "Checking collection shape."
            Stage.READING_NOTES -> "Reading notes before the card total is known."
            Stage.PROCESSING_IMPORTED_CARDS -> "AnkiDroid read finished. Processing imported cards locally."
            Stage.SAVING_LOCAL_DATA -> "Saving the Anki snapshot and import evidence."
            Stage.BUILDING_PRACTICE_QUEUE -> "Saving the practice queue."
            Stage.ARCHIVING_IMPORTED_CARDS -> "Updating archived suspended cards."
            Stage.SCANNING_CARDS,
            null,
            -> "Preparing card scan."
        }
    }

    @JvmStatic
    fun progressPermille(scannedCards: Int, totalCards: Int): Int {
        if (totalCards <= 0) {
            return 1000
        }
        return minOf(1000, maxOf(0, Math.round((maxOf(0, scannedCards) * 1000f) / totalCards)))
    }

    @JvmStatic
    fun cardProgressText(scannedCards: Int, totalCards: Int): String {
        return maxOf(0, scannedCards).toString() + " / " + maxOf(0, totalCards) + " cards scanned"
    }

    @JvmStatic
    fun scanRateText(stage: Stage?, scannedCards: Int, totalCards: Int, elapsedMillis: Long): String {
        if (stage != Stage.SCANNING_CARDS) {
            return stageBody(stage)
        }
        val scanned = maxOf(0, scannedCards)
        if (scanned <= 0) {
            return "Scanning cards."
        }
        val elapsed = maxOf(1L, elapsedMillis)
        val perSecond = scanned * 1000.0 / elapsed
        val rateText = String.format(
            Locale.US,
            if (perSecond >= 10.0) "%.0f cards/sec" else "%.1f cards/sec",
            perSecond,
        )
        val remaining = maxOf(0, maxOf(0, totalCards) - scanned)
        if (remaining == 0) {
            return "$rateText - finishing up"
        }
        if (scanned >= 3 && elapsed >= 1000L) {
            val etaMillis = Math.round((remaining / perSecond) * 1000.0)
            return "$rateText - about ${shortDuration(etaMillis)} left"
        }
        return "$rateText - estimating time left"
    }

    @JvmStatic
    fun shortDuration(millis: Long): String {
        val seconds = maxOf(1L, Math.round(millis / 1000.0))
        if (seconds < 60L) {
            return "$seconds sec"
        }
        val minutes = maxOf(1L, Math.round(seconds / 60.0))
        if (minutes < 60L) {
            return "$minutes min"
        }
        val hours = maxOf(1L, Math.round(minutes / 60.0))
        return "$hours hr"
    }

    enum class Stage {
        FINDING_NOTE_TYPE,
        READING_NOTES,
        SCANNING_CARDS,
        PROCESSING_IMPORTED_CARDS,
        SAVING_LOCAL_DATA,
        BUILDING_PRACTICE_QUEUE,
        ARCHIVING_IMPORTED_CARDS,
    }
}
