package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WorkloadSettingsPolicyTest {
    @Test
    public void saveMaximumNormalizesMaxItemsAndPreservesCopy() {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveMaximum(99);

        assertNull(request.mode);
        assertNull(request.workloadPercent);
        assertEquals(Integer.valueOf(AdaptiveLoadPlanner.MAX_MAX_ITEMS), request.maxItems);
        assertEquals("Pareto maximum saved.", request.message);
    }

    @Test
    public void enableManualModePreservesModeAndCopy() {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableManualMode();

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode);
        assertNull(request.workloadPercent);
        assertNull(request.maxItems);
        assertEquals("Manual workload enabled.", request.message);
    }

    @Test
    public void enableAutomaticModePreservesModeAndCopy() {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.enableAutomaticMode();

        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, request.mode);
        assertNull(request.workloadPercent);
        assertNull(request.maxItems);
        assertEquals("Automatic Pareto workload enabled.", request.message);
    }

    @Test
    public void saveManualWorkloadNormalizesValuesAndPreservesCopy() {
        WorkloadSettingsPolicy.SaveRequest request = WorkloadSettingsPolicy.saveManualWorkload(98, -10);

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode);
        assertEquals(Integer.valueOf(95), request.workloadPercent);
        assertEquals(Integer.valueOf(AdaptiveLoadPlanner.MIN_MAX_ITEMS), request.maxItems);
        assertEquals("Workload saved. Study uses the new adaptive focus.", request.message);
    }
}
