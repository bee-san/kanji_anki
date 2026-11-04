package dev.bee.kanjianki.backup.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RestoreMarkerCodecTest {
    @Test
    fun readyMarkerEncodesAndReclassifiesAsSafetyReady() {
        val marker = RestoreMarkerCodec.ReadyMarker("kanji_anki_simple_20260731_010203.db.gz", 1_770_000_000_000L)
        val text = RestoreMarkerCodec.encodeReady(marker)
        assertEquals(
            RestoreMarkerCodec.MarkerState.SAFETY_READY,
            RestoreMarkerCodec.classify(present = true, tooLargeOrUnreadable = false, rawText = text),
        )
        assertEquals(marker, RestoreMarkerCodec.readReady(text))
    }

    @Test
    fun missingAndInvalidStates() {
        assertEquals(
            RestoreMarkerCodec.MarkerState.MISSING,
            RestoreMarkerCodec.classify(present = false, tooLargeOrUnreadable = false, rawText = null),
        )
        assertEquals(
            RestoreMarkerCodec.MarkerState.INVALID,
            RestoreMarkerCodec.classify(present = true, tooLargeOrUnreadable = true, rawText = "format=2"),
        )
        assertEquals(
            RestoreMarkerCodec.MarkerState.INVALID,
            RestoreMarkerCodec.classify(present = true, tooLargeOrUnreadable = false, rawText = null),
        )
        // Ready shape but empty source_name is invalid.
        assertEquals(
            RestoreMarkerCodec.MarkerState.INVALID,
            RestoreMarkerCodec.classify(true, false, "format=2\nphase=safety_ready\nsource_name=\nstaged_at=1"),
        )
        // Ready shape but negative staged_at is invalid.
        assertEquals(
            RestoreMarkerCodec.MarkerState.INVALID,
            RestoreMarkerCodec.classify(true, false, "format=2\nphase=safety_ready\nsource_name=x\nstaged_at=-1"),
        )
        assertNull(RestoreMarkerCodec.readReady("garbage"))
    }

    @Test
    fun legacyMarkerIsRecognized() {
        val legacy = "source_name=old.db\nstaged_at=123"
        assertEquals(
            RestoreMarkerCodec.MarkerState.LEGACY,
            RestoreMarkerCodec.classify(present = true, tooLargeOrUnreadable = false, rawText = legacy),
        )
        // A legacy marker is not a ready marker.
        assertNull(RestoreMarkerCodec.readReady(legacy))
        // Adding a format key to legacy fields makes it invalid (neither shape).
        assertEquals(
            RestoreMarkerCodec.MarkerState.INVALID,
            RestoreMarkerCodec.classify(true, false, "source_name=old.db\nstaged_at=123\nformat=9"),
        )
    }

    @Test
    fun readyMarkerRejectsBlankSourceAndNegativeTime() {
        assertThrows(IllegalArgumentException::class.java) {
            RestoreMarkerCodec.ReadyMarker("", 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestoreMarkerCodec.ReadyMarker("x", -1L)
        }
    }
}
