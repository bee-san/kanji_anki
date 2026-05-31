package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkloadSettingsPolicyTest {
    @Test
    fun saveMaximumNormalizesMaxItemsAndPreservesCopy() {
        val request = WorkloadSettingsPolicy.saveMaximum(99)

        assertNull(request.mode)
        assertNull(request.workloadPercent)
        assertEquals(Integer.valueOf(AdaptiveLoadPlanner.MAX_MAX_ITEMS), request.maxItems)
        assertEquals("Pareto maximum saved.", request.message)
    }

    @Test
    fun enableManualModePreservesModeAndCopy() {
        val request = WorkloadSettingsPolicy.enableManualMode()

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode)
        assertNull(request.workloadPercent)
        assertNull(request.maxItems)
        assertEquals("Manual workload enabled.", request.message)
    }

    @Test
    fun enableAutomaticModePreservesModeAndCopy() {
        val request = WorkloadSettingsPolicy.enableAutomaticMode()

        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, request.mode)
        assertNull(request.workloadPercent)
        assertNull(request.maxItems)
        assertEquals("Automatic Pareto workload enabled.", request.message)
    }

    @Test
    fun saveManualWorkloadNormalizesValuesAndPreservesCopy() {
        val request = WorkloadSettingsPolicy.saveManualWorkload(98, -10)

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode)
        assertEquals(Integer.valueOf(95), request.workloadPercent)
        assertEquals(Integer.valueOf(AdaptiveLoadPlanner.MIN_MAX_ITEMS), request.maxItems)
        assertEquals("Workload saved. Study uses the new adaptive focus.", request.message)
    }
}
