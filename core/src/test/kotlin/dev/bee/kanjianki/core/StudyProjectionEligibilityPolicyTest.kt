package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyProjectionEligibilityPolicyTest {
    @Test
    fun planningProjectionExcludesRetiredCurrentFamilyButKeepsUnseededRows() {
        val retiredRow = row("済", "current")
        val unseededRow = row("新", "current")
        val staleRow = row("済", "stale")
        val retired = item(retiredRow, StudyLadderRules.STATE_RETIRED)
        val staleActive = item(staleRow, StudyLadderRules.STATE_REVIEW)

        val projection = StudyProjectionEligibilityPolicy.planningProjection(
            listOf(retiredRow, unseededRow),
            listOf(retired, staleActive),
        )

        assertEquals(listOf("新"), projection.rows.map { it.kanji })
        assertNull(projection.itemByKanji["済"])
        assertNull(projection.itemByKanji["新"])
    }

    @Test
    fun planningProjectionRestoresReopenedCurrentFamily() {
        val row = row("済", "current")
        val reopened = item(row, StudyLadderRules.STATE_LEARNING)

        val projection = StudyProjectionEligibilityPolicy.planningProjection(
            listOf(row),
            listOf(reopened),
        )

        assertEquals(listOf(row), projection.rows)
        assertEquals(reopened, projection.itemByKanji["済"])
    }

    private fun item(
        row: RecordsImportModels.DashboardRow,
        state: String,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            row.kanji,
            state,
            0L,
            1.0,
            5.0,
            1,
            0,
            0,
            0,
            "",
            0L,
        ).copyBuilder()
            .answerSignature(StudyQueueSeeder.answerSignature(row))
            .build()
    }

    private fun row(kanji: String, expression: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning-$kanji",
            "reading-$kanji",
            "search-$kanji",
            10,
            "weak_support",
            "reason-$kanji",
            1,
            0,
            0,
            listOf(
                RecordsImportModels.Example(
                    "active",
                    1L,
                    2L,
                    expression,
                    "reading-$kanji",
                    "meaning-$kanji",
                    "",
                    false,
                    0,
                ),
            ),
        )
    }
}
