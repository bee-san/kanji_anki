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

    @Test
    fun recentMistakesProjectionExcludesMatureUnseededRow() {
        val matureRow = row("済", "current", matureSupportCount = 2)

        val eligible = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
            listOf(matureRow),
            emptyList(),
        )

        assertEquals(emptySet<String>(), eligible)
    }

    @Test
    fun recentMistakesProjectionExcludesMatureRowWithOnlyStaleActiveFamily() {
        val matureRow = row("済", "current", matureSupportCount = 2)
        val staleActive = item(row("済", "stale"), StudyLadderRules.STATE_REVIEW)

        val eligible = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
            listOf(matureRow),
            listOf(staleActive),
        )

        assertEquals(emptySet<String>(), eligible)
    }

    @Test
    fun recentMistakesProjectionExcludesMatureRetiredFamilyWithActiveLegacyDuplicate() {
        val matureRow = row("済", "current", matureSupportCount = 2)
        val retired = item(matureRow, StudyLadderRules.STATE_RETIRED)
        val legacyActive = item(matureRow, StudyLadderRules.STATE_REVIEW, signature = "")

        val eligible = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
            listOf(matureRow),
            listOf(retired, legacyActive),
        )

        assertEquals(emptySet<String>(), eligible)
    }

    @Test
    fun recentMistakesProjectionRestoresRetiredFamilyAfterSupportDrops() {
        val weakRow = row("済", "current", matureSupportCount = 1)
        val retired = item(weakRow, StudyLadderRules.STATE_RETIRED)

        val eligible = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
            listOf(weakRow),
            listOf(retired),
        )

        assertEquals(setOf("済"), eligible)
    }

    @Test
    fun recentMistakesProjectionKeepsMatureRegressingRowEligible() {
        val matureRow = row("済", "current", matureSupportCount = 2)

        val eligible = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
            listOf(matureRow),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            mapOf("済" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )

        assertEquals(setOf("済"), eligible)
    }

    @Test
    fun recentMistakesProjectionMatchesSeedQueueForAdversarialFamilies() {
        val matureRow = row("済", "current", matureSupportCount = 2)
        val weakRow = row("済", "current", matureSupportCount = 1)
        val staleActive = item(row("済", "stale"), StudyLadderRules.STATE_REVIEW)
        val currentRetired = item(matureRow, StudyLadderRules.STATE_RETIRED)
        val legacyActive = item(matureRow, StudyLadderRules.STATE_REVIEW, signature = "")
        val regressingEvidence = mapOf("済" to KanjiRepairEvidencePolicy.Status.REGRESSING)
        val probes = listOf(
            ProjectionProbe(listOf(matureRow), emptyList()),
            ProjectionProbe(listOf(matureRow), listOf(staleActive)),
            ProjectionProbe(listOf(matureRow), listOf(currentRetired, legacyActive)),
            ProjectionProbe(listOf(weakRow), listOf(currentRetired)),
            ProjectionProbe(listOf(matureRow), emptyList(), regressingEvidence),
        )

        for (probe in probes) {
            val projected = StudyProjectionEligibilityPolicy.eligibleDashboardKanji(
                probe.rows,
                probe.items,
                RecordsSyncModels.Settings.kikuDefaults(),
                probe.evidenceStatusByKanji,
            )
            val seeded = StudyQueueSeeder().seedQueue(
                probe.rows,
                probe.items,
                RecordsSyncModels.Settings.kikuDefaults(),
                1_000L,
                0L,
                ladder = null,
                evidenceStatusByKanji = probe.evidenceStatusByKanji,
            ).asSequence()
                .filter { it.state != StudyLadderRules.STATE_RETIRED }
                .map { it.kanji }
                .toSet()

            assertEquals(seeded, projected)
        }
    }

    private data class ProjectionProbe(
        val rows: List<RecordsImportModels.DashboardRow>,
        val items: List<RecordsStudyModels.StudyItem>,
        val evidenceStatusByKanji: Map<String, KanjiRepairEvidencePolicy.Status>? = null,
    )

    private fun item(
        row: RecordsImportModels.DashboardRow,
        state: String,
        signature: String = StudyQueueSeeder.answerSignature(row),
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
            .answerSignature(signature)
            .build()
    }

    private fun row(
        kanji: String,
        expression: String,
        matureSupportCount: Int = 0,
    ): RecordsImportModels.DashboardRow {
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
            matureSupportCount,
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
