package dev.bee.kanjianki.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDefaultsTest {
    @Test
    fun `default settings use production-safe polling baseline`() {
        val settings = AndroidDefaults.settings()

        assertFalse(settings.pollingEnabled)
        assertEquals(15 * 60, settings.pollingIntervalSeconds)
        assertEquals(listOf("Kiku"), settings.noteModels)
    }

    @Test
    fun `empty dashboard keeps threshold and deduplicates warnings`() {
        val settings = AndroidDefaults.settings().copy(kanjiSupportThreshold = 4)

        val dashboard = AndroidDefaults.emptyDashboard(
            settings = settings,
            warnings = listOf("Grant permission", "Grant permission", "Run sync"),
        )

        assertEquals(4, dashboard.summary.matureSupportThreshold)
        assertEquals(listOf("Grant permission", "Run sync"), dashboard.warnings)
        assertTrue(dashboard.rows.isEmpty())
    }
}
