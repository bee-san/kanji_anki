package dev.bee.kanjianki.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class GitHubUpdaterTest {
    @Test
    public void readableMessageFallsBackToExceptionClassWhenMessageIsNull() {
        assertEquals("RuntimeException", GitHubUpdater.readableMessage(new RuntimeException()));
    }

    @Test
    public void readableMessageKeepsSpecificExceptionMessage() {
        assertEquals("HTTP 403", GitHubUpdater.readableMessage(new RuntimeException("HTTP 403")));
    }
}
