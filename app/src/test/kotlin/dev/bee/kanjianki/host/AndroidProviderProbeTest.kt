package dev.bee.kanjianki.host

import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionSourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProviderProbeTest {
    @Test
    fun aReadyProviderProjectsToReadyWithConnectivityAndTagWrite() {
        val probe = AndroidProviderProbe.of {
            CollectionSourceStatus(
                availability = CollectionAvailability.READY,
                capabilities = setOf(
                    CollectionCapability.READ_COLLECTION,
                    CollectionCapability.NOTE_TAG_WRITE,
                    CollectionCapability.COLLECTION_INVENTORY,
                ),
                message = "AnkiDroid is ready for live note sync.",
            )
        }

        val status = probe.probe()

        assertEquals(ProviderReadiness.READY, status.readiness)
        assertTrue(status.isReady)
        assertTrue(PlatformCapability.PROVIDER_CONNECTIVITY in status.capabilities)
        assertTrue(PlatformCapability.PROVIDER_NOTE_TAG_WRITE in status.capabilities)
        // AnkiDroid never offers the AnkiConnect-only capabilities.
        assertFalse(PlatformCapability.PROVIDER_BROWSER_HANDOFF in status.capabilities)
        assertFalse(PlatformCapability.PROVIDER_MISSING_KANJI_WRITE in status.capabilities)
        assertEquals("AnkiDroid is ready for live note sync.", status.message)
    }

    @Test
    fun aMissingPermissionIsUnauthorizedRatherThanAbsent() {
        val probe = AndroidProviderProbe.of {
            CollectionSourceStatus(
                availability = CollectionAvailability.AUTH_REQUIRED,
                capabilities = emptySet(),
                message = "Allow AnkiDroid access.",
            )
        }

        val status = probe.probe()

        assertEquals(ProviderReadiness.UNAUTHORIZED, status.readiness)
        assertFalse(status.isReady)
        assertTrue(status.capabilities.isEmpty())
    }

    @Test
    fun anUninstalledOrUnusableProviderIsAbsent() {
        for (availability in listOf(
            CollectionAvailability.NOT_AVAILABLE,
            CollectionAvailability.INVALID_CONFIGURATION,
        )) {
            val probe = AndroidProviderProbe.of {
                CollectionSourceStatus(availability, emptySet(), "Install AnkiDroid.")
            }
            assertEquals(availability.name, ProviderReadiness.ABSENT, probe.probe().readiness)
        }
    }

    @Test
    fun connectivityAndTagWriteAreDerivedIndependently() {
        // Read without tag-write still projects connectivity; tag-write alone (which
        // AnkiDroid never emits without read, but the projection must not assume) maps
        // only PROVIDER_NOTE_TAG_WRITE.
        val readOnly = AndroidProviderProbe.capabilitiesFor(
            CollectionSourceStatus(
                CollectionAvailability.READY,
                setOf(CollectionCapability.READ_COLLECTION),
                "",
            ),
        )
        assertEquals(setOf(PlatformCapability.PROVIDER_CONNECTIVITY), readOnly)

        val tagOnly = AndroidProviderProbe.capabilitiesFor(
            CollectionSourceStatus(
                CollectionAvailability.READY,
                setOf(CollectionCapability.NOTE_TAG_WRITE),
                "",
            ),
        )
        assertEquals(setOf(PlatformCapability.PROVIDER_NOTE_TAG_WRITE), tagOnly)
    }
}
