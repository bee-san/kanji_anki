package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
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
        val current = mutableListOf<RecordsStudyModels.StudyItem>()
        val seeded = AtomicBoolean(false)
        val writer = RecordingWriter(current)

        val result = HomeStudyQueueActions.studyQueue(request(
            persist = false,
            current = current,
            providedPlan = null,
            seeder = { _, _, _, _, _, _, _ ->
                seeded.set(true)
                emptyList()
            },
            writer = writer,
        ))

        assertSame(current, result)
        assertFalse(seeded.get())
        assertFalse(writer.annotated)
        assertFalse(writer.replaced)
    }

    @Test
    fun persistentQueueSeedsAnnotatesPersistsAndReturnsAnnotatedItems() {
        val current = mutableListOf<RecordsStudyModels.StudyItem>()
        val seeded = mutableListOf<RecordsStudyModels.StudyItem>()
        val annotated = mutableListOf<RecordsStudyModels.StudyItem>()
        val writer = RecordingWriter(annotated)
        val startOfDay = AtomicLong()
        val seenPlan = AtomicReference<RecordsSchedulerModels.AdaptiveLoadPlan?>(null)

        val result = HomeStudyQueueActions.studyQueue(baseRequest(
            persist = true,
            current = current,
            providedPlan = null,
            seeder = { rows, currentItems, settings, nowMillis, startOfDayMillis, plan, ladder ->
                assertSame(current, currentItems)
                assertEquals(123L, nowMillis)
                startOfDay.set(startOfDayMillis)
                seenPlan.set(plan)
                seeded
            },
            writer = writer,
        ))

        assertSame(annotated, result)
        assertSame(seeded, writer.annotatedInput)
        assertSame(annotated, writer.replacedInput)
        assertEquals(100L, startOfDay.get())
        assertTrue(seenPlan.get()!!.autoMode)
    }

    @Test
    fun persistentQueueUsesProvidedPlanWithoutRecomputing() {
        val current = mutableListOf<RecordsStudyModels.StudyItem>()
        val provided = plan(false)
        val recomputed = AtomicBoolean(false)
        val writer = RecordingWriter(current)

        HomeStudyQueueActions.studyQueue(HomeStudyQueueActions.StudyQueueRequest(
            emptyList(),
            123L,
            true,
            provided,
            { current },
            RecordsSyncModels.Settings::kikuDefaults,
            { 100L },
            RecordsBase.StudyLadderSettings::defaults,
            { _, _, _ ->
                recomputed.set(true)
                plan(true)
            },
            { rows, currentItems, settings, nowMillis, startOfDayMillis, plan, ladder ->
                assertSame(provided, plan)
                current
            },
            writer,
        ))

        assertFalse(recomputed.get())
    }

    @Test
    fun persistentQueueUsesProvidedCurrentItemsWithoutReadingStoreAgain() {
        val current = mutableListOf<RecordsStudyModels.StudyItem>()
        val writer = RecordingWriter(current)

        val result = HomeStudyQueueActions.studyQueue(
            HomeStudyQueueActions.StudyQueueRequest(
                emptyList(),
                123L,
                true,
                null,
                { throw AssertionError("reader should not be called when current items are supplied") },
                RecordsSyncModels.Settings::kikuDefaults,
                { 100L },
                RecordsBase.StudyLadderSettings::defaults,
                { _, _, _ -> plan(true) },
                { _, currentItems, _, _, _, _, _ -> currentItems },
                writer,
            ),
            currentItems = current,
        )

        assertSame(current, result)
        assertTrue(writer.annotated)
        assertTrue(writer.replaced)
    }

    @Test
    fun persistentQueueSkipsReplaceWhenAnnotatedQueueMatchesCurrentItems() {
        val currentItem = studyItem()
        val current = listOf(currentItem)
        val annotated = listOf(studyItem())
        val writer = RecordingWriter(annotated)

        val result = HomeStudyQueueActions.studyQueue(
            baseRequest(
                persist = true,
                current = current,
                providedPlan = null,
                seeder = { _, _, _, _, _, _, _ -> listOf(studyItem()) },
                writer = writer,
            ),
            currentItems = current,
        )

        assertSame(annotated, result)
        assertTrue(writer.annotated)
        assertFalse(writer.replaced)
    }

    @Test
    fun persistentQueuePersistsWhenSchedulerStateChanges() {
        val variants = listOf(
            "state" to { item: RecordsStudyModels.StudyItem -> item.copyBuilder().state("learning").build() },
            "due" to { item: RecordsStudyModels.StudyItem -> item.copyBuilder().dueAtMillis(456L).build() },
            "answer signature" to { item: RecordsStudyModels.StudyItem -> item.copyBuilder().answerSignature("changed-signature").build() },
            "token" to { item: RecordsStudyModels.StudyItem -> item.copyBuilder().activeToken("changed-token").build() },
            "rung" to { item: RecordsStudyModels.StudyItem -> item.copyBuilder().rung(RecordsBase.LadderRung.TYPE_MEANING).build() },
            "phase" to { item: RecordsStudyModels.StudyItem -> item.copyBuilder().phase(RecordsBase.SchedulerPhase.RELEARNING).build() },
        )

        for ((name, mutate) in variants) {
            val currentItem = studyItem()
            val changed = mutate(currentItem)
            val writer = RecordingWriter(listOf(changed))

            HomeStudyQueueActions.studyQueue(
                baseRequest(
                    persist = true,
                    current = listOf(currentItem),
                    providedPlan = null,
                    seeder = { _, _, _, _, _, _, _ -> listOf(changed) },
                    writer = writer,
                ),
                currentItems = listOf(currentItem),
            )

            assertTrue("$name changes must be persisted", writer.replaced)
        }
    }

    @Test
    fun studyQueueRequestKeepsJavaRecordSemantics() {
        val seeder = HomeStudyQueueActions.StudyQueueSeeder {
            _: List<RecordsImportModels.DashboardRow>,
            currentItems: List<RecordsStudyModels.StudyItem>,
            _: RecordsSyncModels.Settings,
            _: Long,
            _: Long,
            _: RecordsSchedulerModels.AdaptiveLoadPlan,
            _: RecordsBase.StudyLadderSettings,
        -> currentItems }
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
    ): HomeStudyQueueActions.StudyQueueRequest {
        return baseRequest(persist, current, providedPlan, seeder, writer)
    }

    private fun baseRequest(
        persist: Boolean,
        current: List<RecordsStudyModels.StudyItem>,
        providedPlan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        seeder: HomeStudyQueueActions.StudyQueueSeeder,
        writer: HomeStudyQueueActions.StudyItemsWriter,
    ): HomeStudyQueueActions.StudyQueueRequest {
        return HomeStudyQueueActions.StudyQueueRequest(
            emptyList<RecordsImportModels.DashboardRow>(),
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
    }

    private fun plan(enabled: Boolean): RecordsSchedulerModels.AdaptiveLoadPlan {
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            enabled,
            20,
            1,
            1,
            emptyList(),
            1,
            false,
            "status",
        )
    }

    private fun studyItem(
        kanji: String = "裂",
        state: String = "review",
        dueAtMillis: Long = 123L,
        answerSignature: String = "signature",
        activeToken: String? = "token",
        rung: RecordsBase.LadderRung = RecordsBase.LadderRung.KANJI_MEANING,
        phase: RecordsBase.SchedulerPhase = RecordsBase.SchedulerPhase.REVIEW,
        hasSimilarKanji: Boolean = true,
    ): RecordsStudyModels.StudyItem {
        val memory = RecordsStudyModels.TaskMemory.initial()
        return RecordsStudyModels.StudyItem(
            kanji,
            state,
            dueAtMillis,
            1.0,
            2.0,
            3,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            "",
            0L,
            0,
            answerSignature,
            activeToken,
            111L,
            memory,
            memory,
            memory,
            memory,
            memory,
            memory,
            rung,
            phase,
            1,
            0,
            dueAtMillis,
            hasSimilarKanji,
            memory,
        )
    }

    private class RecordingWriter(private val annotatedResult: List<RecordsStudyModels.StudyItem>) : HomeStudyQueueActions.StudyItemsWriter {
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
