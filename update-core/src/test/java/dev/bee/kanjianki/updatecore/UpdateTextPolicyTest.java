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
                "Version 0.4.3 is verified and ready.",
                UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
        );
    }

    @Test
    public void notificationBodyFallsBackToMessageOrDefault() {
        assertEquals("Checksum verified.", UpdateTextPolicy.notificationBody("", "Checksum verified."));
        assertEquals(
                UpdateTextPolicy.DEFAULT_PENDING_UPDATE_MESSAGE,
                UpdateTextPolicy.notificationBody(null, "  ")
        );
        assertEquals(
                UpdateTextPolicy.DEFAULT_PENDING_UPDATE_MESSAGE,
                UpdateTextPolicy.notificationBody(null, null)
        );
    }
}
