package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkloadSettingsPolicyTest {
    @Test
    fun saveMaximumNormalizesMaxItemsAndPreservesCopy() {
        val request = WorkloadSettingsPolicy.saveMaximum(99)

        assertEquals(null, request.mode)
        assertEquals(null, request.workloadPercent)
        assertEquals(AdaptiveLoadPlanner.MAX_MAX_ITEMS, request.maxItems)
        assertEquals("Item limit saved.", request.message)
    }

    @Test
    fun enableManualModePreservesModeAndCopy() {
        val request = WorkloadSettingsPolicy.enableManualMode()

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode)
        assertEquals(null, request.workloadPercent)
        assertEquals(null, request.maxItems)
        assertEquals("Manual workload enabled.", request.message)
    }

    @Test
    fun enableAutomaticModePreservesModeAndCopy() {
        val request = WorkloadSettingsPolicy.enableAutomaticMode()

        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, request.mode)
        assertEquals(null, request.workloadPercent)
        assertEquals(null, request.maxItems)
        assertEquals("Automatic workload enabled.", request.message)
    }

    @Test
    fun saveManualWorkloadNormalizesValuesAndPreservesCopy() {
        val request = WorkloadSettingsPolicy.saveManualWorkload(98, -10)

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode)
        assertEquals(95, request.workloadPercent)
        assertEquals(AdaptiveLoadPlanner.MIN_MAX_ITEMS, request.maxItems)
        assertEquals("Workload saved. Study uses the new adaptive focus.", request.message)
    }
}
