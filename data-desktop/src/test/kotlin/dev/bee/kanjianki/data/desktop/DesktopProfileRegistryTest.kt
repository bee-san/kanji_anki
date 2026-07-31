package dev.bee.kanjianki.data.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProfileRegistryTest {
    private val a = "0f9a1b2c-3d4e-5f60-7182-93a4b5c6d7e8"
    private val b = "1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"

    @Test
    fun defaultRegistryHasOneSelectedProfile() {
        val registry = DesktopProfileRegistry.withDefault(a)
        assertEquals(1, registry.profiles.size)
        assertEquals(a, registry.selectedProfileId)
        assertEquals(DesktopProfileRegistry.DEFAULT_DISPLAY_NAME, registry.selected()?.displayName)
    }

    @Test
    fun addSelectRemoveRoundTrip() {
        var registry = DesktopProfileRegistry.withDefault(a)
        registry = registry.withProfile(DesktopProfileEntry(b, "Second"))
        assertEquals(setOf(a, b), registry.profiles.map { it.id }.toSet())
        assertEquals(b, registry.selectedProfileId)

        registry = registry.select(a)
        assertEquals(a, registry.selectedProfileId)

        registry = registry.withoutProfile(a)
        assertEquals(listOf(b), registry.profiles.map { it.id })
        assertEquals("re-selects the remaining profile", b, registry.selectedProfileId)

        registry = registry.withoutProfile(b)
        assertTrue(registry.profiles.isEmpty())
        assertNull(registry.selectedProfileId)
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val registry = DesktopProfileRegistry.withDefault(a).withProfile(DesktopProfileEntry(b, "Study B"))
        val decoded = DesktopProfileRegistry.decode(registry.encode())
        assertEquals(registry, decoded)
    }

    @Test
    fun decodeFallsOpenToEmptyOnMalformed() {
        assertEquals(DesktopProfileRegistry.empty(), DesktopProfileRegistry.decode(null))
        assertEquals(DesktopProfileRegistry.empty(), DesktopProfileRegistry.decode("{not json"))
        // A registry referencing an unknown selected id drops the selection.
        val decoded = DesktopProfileRegistry.decode(
            """{"version":1,"selectedProfileId":"$b","profiles":[{"id":"$a","displayName":"A"}]}""",
        )
        assertEquals(listOf(a), decoded.profiles.map { it.id })
        assertNull(decoded.selectedProfileId)
    }

    @Test
    fun decodeRejectsInvalidProfileIds() {
        val decoded = DesktopProfileRegistry.decode(
            """{"version":1,"selectedProfileId":null,"profiles":[{"id":"../evil","displayName":"x"},{"id":"$a","displayName":"ok"}]}""",
        )
        assertEquals("only the valid UUID profile survives", listOf(a), decoded.profiles.map { it.id })
    }

    @Test
    fun invariantsRejectDuplicateOrDanglingSelection() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopProfileRegistry(
                listOf(DesktopProfileEntry(a, "one"), DesktopProfileEntry(a, "dup")),
                a,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DesktopProfileRegistry(listOf(DesktopProfileEntry(a, "one")), b)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DesktopProfileEntry(a, "  ")
        }
    }
}
