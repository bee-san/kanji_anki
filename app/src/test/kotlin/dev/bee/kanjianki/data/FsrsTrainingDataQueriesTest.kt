package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FsrsTrainingDataQueriesTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun extractionDropsLegacyAndPracticeRowsKeepsSameDayAndGroupsWithoutKanaAssumptions() {
        insert("legacy", "good", 1L, "", "{\"phase\":\"review\"}")
        insert("practice", "good", 2L, memory(dueDay = 1, intervalDays = 1), "{\"phase\":\"new_learning\"}")
        insert("missing-phase", "good", 3L, memory(dueDay = 1, intervalDays = 1), "{}")
        insert("review-1", "good", 5 * DAY, memory(dueDay = 10, intervalDays = 5), "{\"phase\":\"review\"}")
        insert("review-2", "again", 25 * DAY, memory(dueDay = 20, intervalDays = 10), "{\"phase\":\"review\"}")

        val sequences = FsrsTrainingDataQueries(store.readableDatabase).sequences()

        assertEquals(1, sequences.size)
        val sequence = sequences.single()
        assertEquals(7.5, sequence.initialStability, 0.0)
        assertEquals(4.25, sequence.initialDifficulty, 0.0)
        assertEquals(listOf(0, 15), sequence.samples.map { it.elapsedDays })
        assertEquals(listOf(3, 1), sequence.samples.map { it.rating })
        assertEquals(listOf(true, false), sequence.samples.map { it.outcome })
    }

    @Test
    fun elapsedDerivationExactlyMirrorsReviewContextFormula() {
        val memory = RecordsStudyModels.TaskMemory(
            "review", 12 * DAY, 4.0, 5.0, 3, 0,
            0, "good", 7,
        )
        assertEquals(4, FsrsTrainingDataQueries.elapsedDays(9 * DAY, memory))
        assertEquals(0, FsrsTrainingDataQueries.elapsedDays(1 * DAY, memory))
    }

    @Test
    fun adaptivePresentationVariantsShareOneCoreTrainingSequence() {
        insert(
            "recognition-standard",
            "good",
            5 * DAY,
            memory(dueDay = 10, intervalDays = 5),
            "{\"phase\":\"review\"}",
            taskType = "kanji_meaning",
            coreSkill = "recognition",
        )
        insert(
            "recognition-font",
            "hard",
            6 * DAY,
            memory(dueDay = 11, intervalDays = 5),
            "{\"phase\":\"review\"}",
            taskType = "font_meaning",
            coreSkill = "recognition",
        )

        val sequences = FsrsTrainingDataQueries(store.readableDatabase).sequences()

        assertEquals(1, sequences.size)
        assertEquals(listOf(3, 2), sequences.single().samples.map { it.rating })
    }

    private fun insert(
        token: String,
        rating: String,
        reviewedAt: Long,
        memory: String,
        schedulerJson: String,
        taskType: String = "latin-task",
        coreSkill: String = "",
    ) {
        val values = ContentValues().apply {
            put("kanji", "A")
            put("token", token)
            put("rating", rating)
            put("writing_required", 0)
            put("writing_passed", 1)
            put("manual_override", 0)
            put("reviewed_at", reviewedAt)
            put("review_day_start", 0)
            put("task_type", taskType)
            put("answer_signature", "latin-signature")
            put("memory_before", memory)
            put("scheduler_state_before_json", schedulerJson)
            put("core_skill", coreSkill)
        }
        store.writableDatabase.insertOrThrow("review_log", null, values)
    }

    private fun memory(dueDay: Long, intervalDays: Int): String =
        RecordsStudyModels.TaskMemory(
            "review", dueDay * DAY, 7.5, 4.25, 3, 0,
            0, "good", intervalDays,
        ).encode()

    private companion object {
        const val DAY = 86_400_000L
    }
}
