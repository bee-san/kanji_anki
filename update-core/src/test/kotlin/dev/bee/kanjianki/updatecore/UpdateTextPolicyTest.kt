package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateTextPolicyTest {
    @Test
    fun readableMessageUsesExceptionMessageWhenPresent() {
        assertEquals("HTTP 403", UpdateTextPolicy.readableMessage(RuntimeException("HTTP 403")))
    }

    @Test
    fun readableMessageFallsBackToClassOrUnknown() {
        assertEquals("RuntimeException", UpdateTextPolicy.readableMessage(RuntimeException()))
        assertEquals("IllegalStateException", UpdateTextPolicy.readableMessage(IllegalStateException("   ")))
        assertEquals("unknown error", UpdateTextPolicy.readableMessage(null))
    }

    @Test
    fun notificationBodyPrefersVerifiedVersion() {
        assertEquals(
            "Version 0.4.3 is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
        )
    }

    @Test
    fun notificationBodyFallsBackToMessageOrDefault() {
        assertEquals("Kani update is ready. Open Kani to install it.", UpdateTextPolicy.DEFAULT_PENDING_UPDATE_MESSAGE)
        assertEquals(
            "Checksum verified. Open Kani to install it.",
            UpdateTextPolicy.notificationBody("", "Checksum verified.")
        )
        assertEquals(
            "Kani update is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody(null, "  ")
        )
        assertEquals(
            "Kani update is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody(null, null)
        )
    }
}
