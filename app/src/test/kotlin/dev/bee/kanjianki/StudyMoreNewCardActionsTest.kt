package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StudyMoreNewCardActionsTest {
    @Test
    fun applyAdmissionPersistsAnnotatedItemsAndUpdatesFocusState() {
        val result = BridgeScheduler().seedExtraNewCards(
            rows("謎", "示"),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            2,
        )
        val annotated = result.items.toMutableList()
        val writer = RecordingWriter(annotated)
        val selected = mutableListOf("old")
        val reset = AtomicBoolean(false)
        val target = AtomicInteger(-1)

        val admission = StudyMoreNewCardActions.applyAdmission(
            result,
            writer,
            selected,
            { reset.set(true) },
            target::set,
        )

        assertTrue(admission.admittedAny)
        assertEquals(2, admission.admittedCount)
        assertSame(result.items, writer.annotatedInput)
        assertSame(annotated, writer.replacedInput)
        assertEquals(listOf("謎", "示"), selected)
        assertTrue(reset.get())
        assertEquals(2, target.get())
    }

    @Test
    fun applyAdmissionDoesNothingWhenNoCardsAdmitted() {
        val result = BridgeScheduler().seedExtraNewCards(
            rows("謎"),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            0,
        )
        val writer = RecordingWriter(emptyList())
        val selected = mutableListOf("old")
        val reset = AtomicBoolean(false)
        val target = AtomicInteger(-1)

        val admission = StudyMoreNewCardActions.applyAdmission(
            result,
            writer,
            selected,
            { reset.set(true) },
            target::set,
        )

        assertFalse(admission.admittedAny)
        assertEquals(0, admission.admittedCount)
        assertEquals(null, writer.annotatedInput)
        assertEquals(null, writer.replacedInput)
        assertEquals(listOf("old"), selected)
        assertFalse(reset.get())
        assertEquals(-1, target.get())
    }

    @Test
    fun admissionResultKeepsJavaRecordSemantics() {
        assertTrue(StudyMoreNewCardActions.AdmissionResult::class.java.isRecord)
        assertEquals(
            StudyMoreNewCardActions.AdmissionResult(true, 2),
            StudyMoreNewCardActions.AdmissionResult(true, 2),
        )
    }

    private fun rows(vararg kanji: String): List<RecordsImportModels.DashboardRow> =
        kanji.map { item: String ->
            RecordsImportModels.DashboardRow(
                item,
                null,
                "meaning",
                "",
                item,
                1,
                "reason",
                "Needs practice",
                1,
                0,
                0,
                listOf<String>(),
            )
        }

    private class RecordingWriter(
        val annotatedResult: List<RecordsStudyModels.StudyItem>,
    ) : StudyMoreNewCardActions.StudyItemWriter {
        var annotatedInput: List<RecordsStudyModels.StudyItem>? = null
        var replacedInput: List<RecordsStudyModels.StudyItem>? = null

        override fun annotateSimilarKanjiAvailability(
            items: List<RecordsStudyModels.StudyItem>,
        ): List<RecordsStudyModels.StudyItem> {
            annotatedInput = items
            return annotatedResult
        }

        override fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
            replacedInput = items
        }
    }
}
