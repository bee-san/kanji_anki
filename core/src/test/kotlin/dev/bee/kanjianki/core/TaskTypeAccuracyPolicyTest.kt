package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskTypeAccuracyPolicyTest {
    @Test fun mapsEveryCurrentAndLegacyWireName() {
        assertEquals(TaskTypeAccuracyPolicy.Group.MEANING, TaskTypeAccuracyPolicy.groupFor("type_meaning"))
        assertEquals(TaskTypeAccuracyPolicy.Group.MEANING, TaskTypeAccuracyPolicy.groupFor("typing_meaning"))
        assertEquals(TaskTypeAccuracyPolicy.Group.MEANING, TaskTypeAccuracyPolicy.groupFor("meaning_kanji"))
        assertEquals(TaskTypeAccuracyPolicy.Group.MEANING, TaskTypeAccuracyPolicy.groupFor("kanji_meaning"))
        assertEquals(TaskTypeAccuracyPolicy.Group.MEANING, TaskTypeAccuracyPolicy.groupFor("font_meaning"))
        assertEquals(TaskTypeAccuracyPolicy.Group.READING, TaskTypeAccuracyPolicy.groupFor("kanji_reading"))
        assertEquals(TaskTypeAccuracyPolicy.Group.READING, TaskTypeAccuracyPolicy.groupFor("word_reading"))
        assertEquals(TaskTypeAccuracyPolicy.Group.READING, TaskTypeAccuracyPolicy.groupFor("sentence_reading"))
        assertEquals(TaskTypeAccuracyPolicy.Group.WRITING, TaskTypeAccuracyPolicy.groupFor("write_kanji"))
        assertEquals(TaskTypeAccuracyPolicy.Group.WRITING, TaskTypeAccuracyPolicy.groupFor("writing_remediation"))
        assertEquals(TaskTypeAccuracyPolicy.Group.DISCRIMINATION, TaskTypeAccuracyPolicy.groupFor("similar_kanji"))
        assertEquals(TaskTypeAccuracyPolicy.Group.DISCRIMINATION, TaskTypeAccuracyPolicy.groupFor("reading_kanji"))
        assertNull(TaskTypeAccuracyPolicy.groupFor("unknown"))
        assertNull(TaskTypeAccuracyPolicy.groupFor(null))
    }

    @Test fun combinesCorrectAndTotalHonestlyAndDropsEmptyGroups() {
        val result = TaskTypeAccuracyPolicy.summarize(listOf(
            TaskTypeAccuracyPolicy.Summary("kanji_meaning", 2, 3),
            TaskTypeAccuracyPolicy.Summary("typing_meaning", 4, 4),
            TaskTypeAccuracyPolicy.Summary("word_reading", 1, 2),
            TaskTypeAccuracyPolicy.Summary("unknown", 9, 9),
            TaskTypeAccuracyPolicy.Summary("write_kanji", 9, -1),
        ))
        assertEquals(
            listOf(
                TaskTypeAccuracyPolicy.Accuracy(TaskTypeAccuracyPolicy.Group.MEANING, 6, 7),
                TaskTypeAccuracyPolicy.Accuracy(TaskTypeAccuracyPolicy.Group.READING, 1, 2),
            ),
            result,
        )
        assertEquals(86, result.first().percent)
        assertEquals(emptyList<TaskTypeAccuracyPolicy.Accuracy>(), TaskTypeAccuracyPolicy.summarize(null))
    }
}
