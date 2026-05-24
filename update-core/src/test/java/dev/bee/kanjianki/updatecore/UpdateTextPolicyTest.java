package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class UpdateTextPolicyTest {
    @Test
    public void readableMessageUsesExceptionMessageWhenPresent() {
        assertEquals("HTTP 403", UpdateTextPolicy.readableMessage(new RuntimeException("HTTP 403")));
    }

    @Test
    public void readableMessageFallsBackToClassOrUnknown() {
        assertEquals("RuntimeException", UpdateTextPolicy.readableMessage(new RuntimeException()));
        assertEquals("IllegalStateException", UpdateTextPolicy.readableMessage(new IllegalStateException("   ")));
        assertEquals("unknown error", UpdateTextPolicy.readableMessage(null));
    }

    @Test
    public void notificationBodyPrefersVerifiedVersion() {
        assertEquals(
                "Version 0.4.3 is ready. Open Kani to install it.",
                UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
        );
    }

    @Test
    public void notificationBodyFallsBackToMessageOrDefault() {
        assertEquals("Kani update is ready. Open Kani to install it.", UpdateTextPolicy.DEFAULT_PENDING_UPDATE_MESSAGE);
        assertEquals(
                "Checksum verified. Open Kani to install it.",
                UpdateTextPolicy.notificationBody("", "Checksum verified.")
        );
        assertEquals(
                "Kani update is ready. Open Kani to install it.",
                UpdateTextPolicy.notificationBody(null, "  ")
        );
        assertEquals(
                "Kani update is ready. Open Kani to install it.",
                UpdateTextPolicy.notificationBody(null, null)
        );
    }
}
