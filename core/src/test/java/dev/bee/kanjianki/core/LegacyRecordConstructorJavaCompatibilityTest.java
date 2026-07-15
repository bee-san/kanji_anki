package dev.bee.kanjianki.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LegacyRecordConstructorJavaCompatibilityTest {
    @Test
    public void legacyConstructorsRemainCallableFromJava() {
        RecordsStudyModels.TaskMemory memory = new RecordsStudyModels.TaskMemory(
                "review", 1_000L, 2.5, 4.5, 3, 1, 2, "good", 7);
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest(
                "裂", "token", "good", true, true, false, 0);

        assertEquals("review\t1000\t2.5\t4.5\t3\t1\t2\tgood\t7\t0\t0\t0", memory.encode());
        assertTrue(request.writingClean);
        assertEquals("", request.taskType);
    }
}
