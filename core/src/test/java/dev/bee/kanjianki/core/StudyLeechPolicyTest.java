package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudyLeechPolicyTest {
    @Test
    public void repeatedReviewLapsesRaiseLeechFlagAtDefaultThreshold() {
        RecordsStudyModels.StudyItem item = reviewItem(7, 3);
        assertFalse(StudyLeechPolicy.isLeech(item));

        RecordsStudyModels.StudyItem leech = reviewItem(8, 3);
        assertTrue(StudyLeechPolicy.isLeech(leech));
        assertEquals(8, StudyLeechPolicy.lapseCount(leech));
        assertEquals("leech", StudyLeechPolicy.tagFor(leech));
    }

    @Test
    public void taskMemoryLapsesCanRaiseLeechFlagWhenTotalIsLower() {
        RecordsStudyModels.TaskMemory memory = new RecordsStudyModels.TaskMemory(
                "review",
                0L,
                0.4,
                5.0,
                10,
                8,
                0,
                "again",
                1,
                0,
                0L
        );
        RecordsStudyModels.StudyItem item = reviewItem(2, 3)
                .withTaskMemory(BridgeScheduler.TASK_WORD_READING, memory);

        assertTrue(StudyLeechPolicy.isLeech(item, BridgeScheduler.TASK_WORD_READING));
        assertEquals(8, StudyLeechPolicy.lapseCount(item, BridgeScheduler.TASK_WORD_READING));
        assertEquals("leech", StudyLeechPolicy.tagFor(item, BridgeScheduler.TASK_WORD_READING));
    }

    @Test
    public void thresholdIsBoundedAndNullItemsAreSafe() {
        assertFalse(StudyLeechPolicy.isLeech(null));
        assertEquals(0, StudyLeechPolicy.lapseCount(null));
        assertEquals("", StudyLeechPolicy.tagFor(null));
        assertTrue(StudyLeechPolicy.isLeech(reviewItem(1, 3), 0));
    }

    private static RecordsStudyModels.StudyItem reviewItem(int lapses, int taskLapses) {
        RecordsStudyModels.TaskMemory memory = new RecordsStudyModels.TaskMemory(
                "review",
                0L,
                0.4,
                5.0,
                10,
                taskLapses,
                0,
                "again",
                1,
                0,
                0L
        );
        return new RecordsStudyModels.StudyItem(
                "裂",
                "review",
                0L,
                0.4,
                5.0,
                10,
                lapses,
                0,
                0,
                0,
                0,
                0L,
                false,
                "",
                0L,
                1,
                "",
                "",
                0L,
                memory,
                memory,
                memory,
                memory,
                memory,
                memory,
                RecordsBase.LadderRung.KANJI_MEANING,
                RecordsBase.SchedulerPhase.REVIEW,
                0,
                0,
                0L,
                false,
                memory
        );
    }
}
