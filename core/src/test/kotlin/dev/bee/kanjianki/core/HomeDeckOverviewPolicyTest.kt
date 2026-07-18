package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDeckOverviewPolicyTest {
    @Test
    fun countsOnlyMatchingActiveFamiliesAndScopesSuspensionsToActiveKanji() {
        val now = 1_000L
        val activeReviewRow = row("裂", "expr-active", "read-a", "mean-a")
        val activeStudyRow = row("語", "expr-study", "read-b", "mean-b")
        val staleFamilyRow = row("裂", "expr-stale", "read-a", "mean-a")

        val overview = HomeDeckOverviewPolicy.from(
            studyItems = listOf(
                studyItem(
                    kanji = "裂",
                    state = StudyLadderRules.STATE_REVIEW,
                    dueAtMillis = 900L,
                    answerSignature = StudyQueueSeeder.answerSignature(activeReviewRow),
                ),
                studyItem(
                    kanji = "裂",
                    state = StudyLadderRules.STATE_REVIEW,
                    dueAtMillis = 800L,
                    answerSignature = StudyQueueSeeder.answerSignature(staleFamilyRow),
                ),
                studyItem(
                    kanji = "語",
                    state = StudyLadderRules.STATE_NEW,
                    dueAtMillis = 0L,
                    answerSignature = StudyQueueSeeder.answerSignature(activeStudyRow),
                ),
                studyItem(
                    kanji = "語",
                    state = StudyLadderRules.STATE_LEARNING,
                    dueAtMillis = 0L,
                    answerSignature = StudyQueueSeeder.answerSignature(activeStudyRow),
                    phase = RecordsBase.SchedulerPhase.NEW_LEARNING,
                ),
                studyItem(
                    kanji = "語",
                    state = StudyLadderRules.STATE_LEARNING,
                    dueAtMillis = 0L,
                    answerSignature = StudyQueueSeeder.answerSignature(activeStudyRow),
                    phase = RecordsBase.SchedulerPhase.RELEARNING,
                ),
                studyItem(
                    kanji = "語",
                    state = StudyLadderRules.STATE_REVIEW,
                    dueAtMillis = 900L,
                    answerSignature = StudyQueueSeeder.answerSignature(activeStudyRow),
                    suppressedByTaskType = "sync",
                ),
            ),
            dashboardRows = listOf(activeReviewRow, activeStudyRow),
            nowMillis = now,
            locallySuspendedKanji = setOf("裂", "語", "外"),
        )

        // Legacy suppression flags no longer hide items: the flagged due
        // review item counts as due like any other.
        assertEquals(2, overview.dueCount)
        assertEquals(1, overview.newCount)
        assertEquals(1, overview.learningCount)
        assertEquals(1, overview.relearningCount)
        assertEquals(2, overview.suspendedCount)
        assertEquals(
            listOf("Due 2", "New 1", "Learning 1", "Relearning 1", "Suspended 2"),
            overview.rows(),
        )
    }

    @Test
    fun legacyItemsWithoutAnswerSignatureCanStillMatchSingleActiveFamily() {
        val activeRow = row("語", "expr-active", "read-a", "mean-a")

        val overview = HomeDeckOverviewPolicy.from(
            studyItems = listOf(
                studyItem(
                    kanji = "語",
                    state = StudyLadderRules.STATE_NEW,
                    dueAtMillis = 0L,
                    answerSignature = "",
                ),
            ),
            dashboardRows = listOf(activeRow),
            nowMillis = 1_000L,
            locallySuspendedKanji = emptySet(),
        )

        assertEquals(1, overview.newCount)
        assertEquals(listOf("New 1"), overview.rows())
    }

    @Test
    fun retiredItemsAreExcludedAndReopenedItemsAreIncluded() {
        val row = row("語", "expr-active", "read-a", "mean-a")
        val signature = StudyQueueSeeder.answerSignature(row)

        val retired = HomeDeckOverviewPolicy.from(
            listOf(studyItem("語", StudyLadderRules.STATE_RETIRED, 900L, signature)),
            listOf(row),
            1_000L,
            emptySet(),
        )
        val reopened = HomeDeckOverviewPolicy.from(
            listOf(studyItem("語", StudyLadderRules.STATE_REVIEW, 900L, signature)),
            listOf(row),
            1_000L,
            emptySet(),
        )

        assertEquals(0, retired.dueCount)
        assertEquals(1, reopened.dueCount)
    }

    private fun studyItem(
        kanji: String,
        state: String,
        dueAtMillis: Long,
        answerSignature: String,
        phase: RecordsBase.SchedulerPhase = RecordsBase.SchedulerPhase.NEW_LEARNING,
        suppressedByTaskType: String = "",
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            state,
            dueAtMillis,
            1.0,
            5.0,
            1,
            0,
            0,
            0,
            "",
            0L,
        ).copyBuilder()
            .answerSignature(answerSignature)
            .suppressedByTaskType(suppressedByTaskType.takeIf { it.isNotEmpty() })
            .phase(phase)
            .build()
    }

    private fun row(
        kanji: String,
        expression: String,
        reading: String,
        meaning: String,
    ): RecordsImportModels.DashboardRow {
        val example = RecordsImportModels.Example(
            "active",
            1L,
            2L,
            expression,
            reading,
            meaning,
            "",
            false,
            0,
        )
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning",
            "reading",
            "browser:$kanji",
            0,
            "reason",
            "reason text",
            1,
            0,
            0,
            listOf(example),
        )
    }
}
