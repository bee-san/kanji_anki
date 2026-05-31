package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class HomeStudyQueueActionsTest {
    @Test
    fun nonPersistentQueueReturnsCurrentItemsWithoutSeedingOrWriting() {
        val current = ArrayList<RecordsStudyModels.StudyItem>()
        val seeded = AtomicBoolean(false)
        val writer = RecordingWriter(current)

        val result = HomeStudyQueueActions.studyQueue(
            request(
                false,
                current,
                null,
                { _: List<RecordsImportModels.DashboardRow>, _: List<RecordsStudyModels.StudyItem>, _: RecordsSyncModels.Settings, _: Long, _: Long, _: RecordsSchedulerModels.AdaptiveLoadPlan, _: RecordsBase.StudyLadderSettings ->
                    seeded.set(true)
                    emptyList()
                },
                writer,
            ),
        )

        assertSame(current, result)
        assertFalse(seeded.get())
        assertFalse(writer.annotated)
        assertFalse(writer.replaced)
    }

    @Test
    fun persistentQueueSeedsAnnotatesPersistsAndReturnsAnnotatedItems() {
        val current = ArrayList<RecordsStudyModels.StudyItem>()
        val seeded = ArrayList<RecordsStudyModels.StudyItem>()
        val annotated = ArrayList<RecordsStudyModels.StudyItem>()
        val writer = RecordingWriter(annotated)
        val startOfDay = AtomicLong()
        val seenPlan = AtomicReference<RecordsSchedulerModels.AdaptiveLoadPlan>()

        val result = HomeStudyQueueActions.studyQueue(
            baseRequest(
                true,
                current,
                null,
                { _: List<RecordsImportModels.DashboardRow>, currentItems: List<RecordsStudyModels.StudyItem>, _: RecordsSyncModels.Settings, nowMillis: Long, startOfDayMillis: Long, plan: RecordsSchedulerModels.AdaptiveLoadPlan, _: RecordsBase.StudyLadderSettings ->
                    assertSame(current, currentItems)
                    assertEquals(123L, nowMillis)
                    startOfDay.set(startOfDayMillis)
                    seenPlan.set(plan)
                    seeded
                },
                writer,
            ),
        )

        assertSame(annotated, result)
        assertSame(seeded, writer.annotatedInput)
        assertSame(annotated, writer.replacedInput)
        assertEquals(100L, startOfDay.get())
        assertTrue(seenPlan.get().autoMode)
    }

    @Test
    fun persistentQueueUsesProvidedPlanWithoutRecomputing() {
        val current = ArrayList<RecordsStudyModels.StudyItem>()
        val provided = plan(false)
        val recomputed = AtomicBoolean(false)
        val writer = RecordingWriter(current)

        HomeStudyQueueActions.studyQueue(
            HomeStudyQueueActions.StudyQueueRequest(
                emptyList(),
                123L,
                true,
                provided,
                { current },
                RecordsSyncModels.Settings::kikuDefaults,
                { 100L },
                RecordsBase.StudyLadderSettings::defaults,
                { _: List<RecordsImportModels.DashboardRow>, _: List<RecordsStudyModels.StudyItem>, _: Long ->
                    recomputed.set(true)
                    plan(true)
                },
                { _: List<RecordsImportModels.DashboardRow>, _: List<RecordsStudyModels.StudyItem>, _: RecordsSyncModels.Settings, _: Long, _: Long, plan: RecordsSchedulerModels.AdaptiveLoadPlan, _: RecordsBase.StudyLadderSettings ->
                    assertSame(provided, plan)
                    current
                },
                writer,
            ),
        )

        assertFalse(recomputed.get())
    }

    @Test
    fun studyQueueRequestKeepsJavaRecordSemantics() {
        val seeder = object : HomeStudyQueueActions.StudyQueueSeeder {
            override fun seedQueue(
                rows: List<RecordsImportModels.DashboardRow>,
                currentItems: List<RecordsStudyModels.StudyItem>,
                settings: RecordsSyncModels.Settings,
                nowMillis: Long,
                startOfDayMillis: Long,
                plan: RecordsSchedulerModels.AdaptiveLoadPlan,
                ladder: RecordsBase.StudyLadderSettings,
            ): List<RecordsStudyModels.StudyItem> = currentItems
        }
        val writer = RecordingWriter(emptyList())
        val request = baseRequest(false, emptyList(), null, seeder, writer)

        assertTrue(HomeStudyQueueActions.StudyQueueRequest::class.java.isRecord)
        assertFalse(request.persist)
        assertSame(seeder, request.seeder)
        assertSame(writer, request.writer)
    }

    private fun request(
        persist: Boolean,
        current: List<RecordsStudyModels.StudyItem>,
        providedPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        seeder: HomeStudyQueueActions.StudyQueueSeeder,
        writer: HomeStudyQueueActions.StudyItemsWriter,
    ) = baseRequest(persist, current, providedPlan, seeder, writer)

    private fun baseRequest(
        persist: Boolean,
        current: List<RecordsStudyModels.StudyItem>,
        providedPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        seeder: HomeStudyQueueActions.StudyQueueSeeder,
        writer: HomeStudyQueueActions.StudyItemsWriter,
    ) = HomeStudyQueueActions.StudyQueueRequest(
        emptyList(),
        123L,
        persist,
        providedPlan,
        { current },
        RecordsSyncModels.Settings::kikuDefaults,
        { 100L },
        RecordsBase.StudyLadderSettings::defaults,
        { _, _, _ -> plan(true) },
        seeder,
        writer,
    )

    private fun plan(enabled: Boolean) = RecordsSchedulerModels.AdaptiveLoadPlan(
        enabled,
        20,
        1,
        1,
        emptyList(),
        1,
        false,
        "status",
    )

    private class RecordingWriter(
        private val annotatedResult: List<RecordsStudyModels.StudyItem>,
    ) : HomeStudyQueueActions.StudyItemsWriter {
        var annotated = false
        var replaced = false
        var annotatedInput: List<RecordsStudyModels.StudyItem>? = null
        var replacedInput: List<RecordsStudyModels.StudyItem>? = null

        override fun annotateSimilarKanjiAvailability(items: List<RecordsStudyModels.StudyItem>): List<RecordsStudyModels.StudyItem> {
            annotated = true
            annotatedInput = items
            return annotatedResult
        }

        override fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
            replaced = true
            replacedInput = items
        }
    }
}