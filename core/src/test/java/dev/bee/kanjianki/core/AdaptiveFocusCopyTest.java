package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class AdaptiveFocusCopyTest {
    @Test
    public void adaptiveFocusTextPreservesSummaryCopy() {
        RecordsSchedulerModels.AdaptiveLoadPlan waiting = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, Collections.emptyList(), 0, false, "");
        RecordsSchedulerModels.AdaptiveLoadPlan all = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 3, 3, Arrays.asList("裂", "提", "語"), 0, true, "all");
        RecordsSchedulerModels.AdaptiveLoadPlan focused = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 5, 2, Arrays.asList("裂", "提"), 0, false, "focus");

        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(null));
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(waiting));
        assertEquals("Adaptive focus is set to all current problem kanji", AdaptiveFocusCopy.adaptiveFocusText(all));
        assertEquals("Today's adaptive focus: 2 items left / 5", AdaptiveFocusCopy.adaptiveFocusText(focused));
    }
}
