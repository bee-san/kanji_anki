package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AutoUpdateStatusPolicyTest {
    @Test
    public void defaultLastResultMatchesExistingStoreFallback() {
        assertEquals("No automatic update check has run yet.", AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT);
    }

    @Test
    public void normalizeKeepsFlagsAndTimestampsWhileNullSafeingText() {
        AutoUpdateStatusPolicy.StatusFields fields = AutoUpdateStatusPolicy.normalize(
                true,
                -42L,
                null,
                "v0.4.40",
                null,
                "ready"
        );

        assertTrue(fields.enabled());
        assertEquals(-42L, fields.lastCheckAtMillis());
        assertEquals("", fields.lastResult());
        assertEquals("v0.4.40", fields.lastVersion());
        assertEquals("", fields.pendingApkName());
        assertEquals("ready", fields.pendingMessage());
    }

    @Test
    public void hasPendingUpdatePreservesExistingEmptyStringSemantics() {
        assertFalse(AutoUpdateStatusPolicy.hasPendingUpdate(null));
        assertFalse(AutoUpdateStatusPolicy.hasPendingUpdate(""));
        assertTrue(AutoUpdateStatusPolicy.hasPendingUpdate("kani.apk"));
        assertTrue(AutoUpdateStatusPolicy.hasPendingUpdate("  "));
    }

    @Test
    public void textConvertsOnlyNullToEmpty() {
        assertEquals("", AutoUpdateStatusPolicy.text(null));
        assertEquals("", AutoUpdateStatusPolicy.text(""));
        assertEquals("  ", AutoUpdateStatusPolicy.text("  "));
    }
}
