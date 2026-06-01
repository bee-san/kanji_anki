package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncProgressCopyTest {
    @Test
    fun stageTitlesAndBodiesPreserveSyncProgressCopy() {
        assertEquals("Finding note type", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.FINDING_NOTE_TYPE))
        assertEquals("Reading notes", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.READING_NOTES))
        assertEquals("Scanning cards", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.SCANNING_CARDS))
        assertEquals(
            "Processing imported cards",
            SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS),
        )
        assertEquals("Saving local data", SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.SAVING_LOCAL_DATA))
        assertEquals(
            "Building practice queue",
            SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE),
        )
        assertEquals(
            "Archiving imported suspended cards",
            SyncProgressCopy.stageTitle(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS),
        )
        assertEquals("Syncing cards", SyncProgressCopy.stageTitle(null))

        assertEquals("Checking collection shape.", SyncProgressCopy.stageBody(SyncProgressCopy.Stage.FINDING_NOTE_TYPE))
        assertEquals(
            "Reading notes before the card total is known.",
            SyncProgressCopy.stageBody(SyncProgressCopy.Stage.READING_NOTES),
        )
        assertEquals(
            "Preparing card scan.",
            SyncProgressCopy.stageBody(SyncProgressCopy.Stage.SCANNING_CARDS),
        )
        assertEquals(
            "AnkiDroid read finished. Processing imported cards locally.",
            SyncProgressCopy.stageBody(SyncProgressCopy.Stage.PROCESSING_IMPORTED_CARDS),
        )
        assertEquals(
            "Saving the Anki snapshot and import evidence.",
            SyncProgressCopy.stageBody(SyncProgressCopy.Stage.SAVING_LOCAL_DATA),
        )
        assertEquals(
            "Saving the practice queue.",
            SyncProgressCopy.stageBody(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE),
        )
        assertEquals(
            "Updating archived suspended cards.",
            SyncProgressCopy.stageBody(SyncProgressCopy.Stage.ARCHIVING_IMPORTED_CARDS),
        )
        assertEquals("Preparing card scan.", SyncProgressCopy.stageBody(null))
    }

    @Test
    fun progressAndCardTextClampCounts() {
        assertEquals(1000, SyncProgressCopy.progressPermille(0, 0))
        assertEquals(0, SyncProgressCopy.progressPermille(-5, 10))
        assertEquals(500, SyncProgressCopy.progressPermille(5, 10))
        assertEquals(1000, SyncProgressCopy.progressPermille(15, 10))
        assertEquals("0 / 10 cards scanned", SyncProgressCopy.cardProgressText(-2, 10))
        assertEquals("7 / 12 cards scanned", SyncProgressCopy.cardProgressText(7, 12))
        assertEquals("7 / 0 cards scanned", SyncProgressCopy.cardProgressText(7, -12))
    }

    @Test
    fun scanRateTextPreservesEtaCopy() {
        assertEquals(
            "Saving the practice queue.",
            SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.BUILDING_PRACTICE_QUEUE, 10, 20, 1000),
        )
        assertEquals("Scanning cards.", SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 0, 20, 1000))
        assertEquals(
            "2.0 cards/sec - estimating time left",
            SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 2, 20, 1000),
        )
        assertEquals(
            "5.0 cards/sec - about 3 sec left",
            SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 5, 20, 1000),
        )
        assertEquals(
            "25 cards/sec - about 4 sec left",
            SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 25, 125, 1000),
        )
        assertEquals(
            "10 cards/sec - finishing up",
            SyncProgressCopy.scanRateText(SyncProgressCopy.Stage.SCANNING_CARDS, 10, 10, 1000),
        )
    }

    @Test
    fun shortDurationRoundsToCompactUnits() {
        assertEquals("1 sec", SyncProgressCopy.shortDuration(0))
        assertEquals("59 sec", SyncProgressCopy.shortDuration(59_000))
        assertEquals("1 min", SyncProgressCopy.shortDuration(60_000))
        assertEquals("2 min", SyncProgressCopy.shortDuration(90_000))
        assertEquals("1 hr", SyncProgressCopy.shortDuration(3_600_000))
        assertEquals("2 hr", SyncProgressCopy.shortDuration(5_400_000))
    }
}
