package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateStatusPolicyTest {
    @Test
    fun defaultLastResultMatchesExistingStoreFallback() {
        assertEquals("No automatic update check has run yet.", AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT)
    }

    @Test
    fun normalizeKeepsFlagsAndTimestampsWhileNullSafeingText() {
        val fields = AutoUpdateStatusPolicy.normalize(
            enabled = true,
            lastCheckAtMillis = -42L,
            lastResult = null,
            lastVersion = "v0.4.40",
            pendingApkName = null,
            pendingMessage = "ready",
        )

        assertTrue(fields.enabled())
        assertEquals(-42L, fields.lastCheckAtMillis())
        assertEquals("", fields.lastResult())
        assertEquals("v0.4.40", fields.lastVersion())
        assertEquals("", fields.pendingApkName())
        assertEquals("ready", fields.pendingMessage())
    }

    @Test
    fun hasPendingUpdatePreservesExistingEmptyStringSemantics() {
        assertFalse(AutoUpdateStatusPolicy.hasPendingUpdate(null))
        assertFalse(AutoUpdateStatusPolicy.hasPendingUpdate(""))
        assertTrue(AutoUpdateStatusPolicy.hasPendingUpdate("kani.apk"))
        assertTrue(AutoUpdateStatusPolicy.hasPendingUpdate("  "))
    }

    @Test
    fun textConvertsOnlyNullToEmpty() {
        assertEquals("", AutoUpdateStatusPolicy.text(null))
        assertEquals("", AutoUpdateStatusPolicy.text(""))
        assertEquals("  ", AutoUpdateStatusPolicy.text("  "))
    }
}
