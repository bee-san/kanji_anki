package dev.bee.kanjianki.backup.core

import dev.bee.kanjianki.backup.core.PortableBackupMetadata.Origin
import org.junit.Assert.assertEquals
import org.junit.Test

class PortableBackupMetadataTest {
    @Test
    fun stampsRoundTripThroughDecode() {
        val rows = PortableBackupMetadata.rowsFor(Origin.DESKTOP, schemaVersion = 34)
        val decoded = PortableBackupMetadata.decode(rows)
        assertEquals(Origin.DESKTOP, decoded.origin)
        assertEquals(PortableBackupMetadata.CURRENT_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(34, decoded.schemaVersion)
    }

    @Test
    fun androidRowsRoundTrip() {
        val decoded = PortableBackupMetadata.decode(
            PortableBackupMetadata.rowsFor(Origin.ANDROID, schemaVersion = 34),
        )
        assertEquals(Origin.ANDROID, decoded.origin)
    }

    @Test
    fun legacyBackupWithoutMetadataDecodesAsUnknown() {
        val decoded = PortableBackupMetadata.decode(mapOf("study_ladder_order" to "a,b"))
        assertEquals(Origin.UNKNOWN, decoded.origin)
        assertEquals(0, decoded.formatVersion)
        assertEquals(0, decoded.schemaVersion)
    }

    @Test
    fun malformedVersionsDecodeToZero() {
        val decoded = PortableBackupMetadata.decode(
            mapOf(
                PortableBackupMetadata.ORIGIN_KEY to "desktop",
                PortableBackupMetadata.FORMAT_VERSION_KEY to "not-a-number",
                PortableBackupMetadata.SCHEMA_VERSION_KEY to "",
            ),
        )
        assertEquals(Origin.DESKTOP, decoded.origin)
        assertEquals(0, decoded.formatVersion)
        assertEquals(0, decoded.schemaVersion)
    }

    @Test
    fun unknownOriginWireNameFallsOpen() {
        assertEquals(Origin.UNKNOWN, Origin.fromWire("windows-phone"))
        assertEquals(Origin.UNKNOWN, Origin.fromWire(null))
    }

    @Test
    fun originWireNamesAreCaseAndSpaceInsensitive() {
        assertEquals(Origin.ANDROID, Origin.fromWire("  ANDROID "))
    }

    @Test
    fun reservedKeysCoverAllStampedRows() {
        val rows = PortableBackupMetadata.rowsFor(Origin.ANDROID, schemaVersion = 1)
        assertEquals(PortableBackupMetadata.reservedKeys, rows.keys)
    }

    @Test
    fun originMapsToPlannerHost() {
        assertEquals(
            CrossPlatformRestorePlanner.Host.ANDROID,
            PortableBackupMetadata.host(Origin.ANDROID),
        )
        assertEquals(
            CrossPlatformRestorePlanner.Host.DESKTOP,
            PortableBackupMetadata.host(Origin.DESKTOP),
        )
        assertEquals(
            CrossPlatformRestorePlanner.Host.UNKNOWN,
            PortableBackupMetadata.host(Origin.UNKNOWN),
        )
    }
}
