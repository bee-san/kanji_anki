package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val annotated = ArrayList(result.items)
        val writer = RecordingWriter(annotated)
        val selected = arrayListOf("old")
        var reset = false
        var target = -1

        val admission = StudyMoreNewCardActions.applyAdmission(
            result,
            writer,
            selected,
            { reset = true },
            { target = it },
        )

        assertTrue(admission.admittedAny)
        assertEquals(2, admission.admittedCount)
        assertEquals(result.items, writer.annotatedInput)
        assertEquals(annotated, writer.replacedInput)
        assertEquals(listOf("謎", "示"), selected)
        assertTrue(reset)
        assertEquals(2, target)
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
        val selected = arrayListOf("old")
        var reset = false
        var target = -1

        val admission = StudyMoreNewCardActions.applyAdmission(
            result,
            writer,
            selected,
            { reset = true },
            { target = it },
        )

        assertFalse(admission.admittedAny)
        assertEquals(0, admission.admittedCount)
        assertEquals(null, writer.annotatedInput)
        assertEquals(null, writer.replacedInput)
        assertEquals(listOf("old"), selected)
        assertFalse(reset)
        assertEquals(-1, target)
    }

    @Test
    fun admissionResultKeepsJavaRecordSemantics() {
        assertTrue(StudyMoreNewCardActions.AdmissionResult::class.java.isRecord)
        assertEquals(
            StudyMoreNewCardActions.AdmissionResult(true, 2),
            StudyMoreNewCardActions.AdmissionResult(true, 2),
        )
    }

    private fun rows(vararg kanji: String): List<RecordsImportModels.DashboardRow> {
        return kanji.map { item: String ->
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
                emptyList<RecordsImportModels.DashboardRow>(),
            )
        }
    }

    private class RecordingWriter(
        private val annotatedResult: List<RecordsStudyModels.StudyItem>,
    ) : StudyMoreNewCardActions.StudyItemWriter {
        var annotatedInput: List<RecordsStudyModels.StudyItem>? = null
        var replacedInput: List<RecordsStudyModels.StudyItem>? = null

        override fun annotateSimilarKanjiAvailability(items: List<RecordsStudyModels.StudyItem>): List<RecordsStudyModels.StudyItem> {
            annotatedInput = items
            return annotatedResult
        }

        override fun replaceStudyItems(items: List<RecordsStudyModels.StudyItem>) {
            replacedInput = items
        }
    }
}
