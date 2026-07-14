package dev.bee.kanjianki

import dev.bee.kanjianki.core.AdaptiveRouteState
import dev.bee.kanjianki.core.AdaptiveRouteStateCodec
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.AnswerEvidence
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StudySessionRestorationPolicyTest {
    @Test
    fun recoveredRunTargetIncludesCompletedCardAndCurrentRemainingWork() {
        assertEquals(3, recoveredStudyRunTarget(currentTarget = 1, completed = 1, selectableRemaining = 2))
        assertEquals(4, recoveredStudyRunTarget(currentTarget = 4, completed = 1, selectableRemaining = 2))
    }

    @Test
    fun exactActiveFamilyRestoresCurrentDatabaseBackedSession() {
        val row = row("裂", expression = "分裂", meaning = "split")
        val item = item(row)
        val snapshot = activeSnapshot(item)

        val session = StudySessionRestorationPolicy.restoreActive(
            snapshot,
            listOf(item),
            row,
            ladder(),
            latestSuccessfulSyncAtMillis = 9_000L,
            tokenConsumed = false,
        )

        assertSame(item, session?.item)
        assertSame(row, session?.row)
        assertEquals("Needs support", session?.prompt)
        assertEquals("active-token", session?.token)
    }

    @Test
    fun activeRestoreRejectsEveryStaleIdentityBoundary() {
        val row = row("裂", expression = "分裂", meaning = "split")
        val item = item(row)
        val snapshot = activeSnapshot(item)
        fun restore(
            candidateSnapshot: StudyActiveSessionSnapshot = snapshot,
            candidateItems: List<RecordsStudyModels.StudyItem> = listOf(item),
            candidateRow: RecordsImportModels.DashboardRow? = row,
            syncAt: Long = 9_000L,
            consumed: Boolean = false,
        ) = StudySessionRestorationPolicy.restoreActive(
            candidateSnapshot,
            candidateItems,
            candidateRow,
            ladder(),
            syncAt,
            consumed,
        )

        assertNull(restore(candidateSnapshot = snapshot.copy(sessionToken = "other")))
        assertNull(restore(candidateSnapshot = snapshot.copy(schedulerRevision = 8L)))
        assertNull(restore(candidateSnapshot = snapshot.copy(routingVersion = 2)))
        assertNull(restore(candidateSnapshot = snapshot.copy(taskType = StudyTaskTypes.KANJI_MEANING)))
        assertNull(restore(candidateRow = row("裂", expression = "亀裂", meaning = "crack")))
        assertNull(restore(syncAt = 9_001L))
        assertNull(restore(consumed = true))
        assertNull(restore(candidateItems = listOf(item.copyBuilder().state(MainActivityBase.STATE_RETIRED).build())))
        assertNull(restore(candidateItems = listOf(item.copyBuilder().suppressedByTaskType("type_meaning").build())))
        assertNull(restore(candidateItems = listOf(item, item.copyBuilder().build())))
    }

    @Test
    fun exactSimilarChoiceRouteRestoresThroughAdaptiveRepairState() {
        val row = row("裂", expression = "分裂", meaning = "split")
        val item = item(row).copyBuilder()
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(
                AdaptiveRouteStateCodec.encode(
                    AdaptiveRouteState(
                        activeCore = CoreSkill.RECOGNITION,
                        activeRepairTasks = listOf(StudyTaskTypes.SIMILAR_KANJI),
                        answerEvidence = AnswerEvidence(
                            coreSkill = CoreSkill.RECOGNITION,
                            failureKind = FailureKind.VISUAL_CONFUSION,
                            confusedWith = "烈",
                        ),
                    ),
                ),
            )
            .build()
        val snapshot = activeSnapshot(item).copy(
            taskType = StudyTaskTypes.SIMILAR_KANJI,
            typedDraft = "",
            similarChoiceSignatureDigest = similarKanjiChoiceRecoveryDigest(listOf("裂", "烈")),
        )

        val session = StudySessionRestorationPolicy.restoreActive(
            snapshot,
            listOf(item),
            row,
            ladder(),
            latestSuccessfulSyncAtMillis = 9_000L,
            tokenConsumed = false,
        )

        assertSame(item, session?.item)
        assertEquals(StudyTaskTypes.SIMILAR_KANJI, session?.taskType)
    }

    @Test
    fun activeRestoreAllowlistRejectsOtherwiseMatchingChoiceDestination() {
        val row = row("裂", expression = "分裂", meaning = "split")
        val item = item(row).copyBuilder()
            .rung(RecordsBase.LadderRung.MEANING_KANJI)
            .build()
        val snapshot = activeSnapshot(item).copy(
            taskType = StudyTaskTypes.MEANING_KANJI,
            typedDraft = "",
        )

        assertNull(
            StudySessionRestorationPolicy.restoreActive(
                snapshot,
                listOf(item),
                row,
                ladder(),
                latestSuccessfulSyncAtMillis = 9_000L,
                tokenConsumed = false,
            ),
        )
    }

    @Test
    fun pendingAnswerUsesExactFamilyAndPostReviewRevision() {
        val row = row("裂", expression = "分裂", meaning = "split")
        val signature = StudyQueueSeeder.answerSignature(row)
        val expected = item(row).copyBuilder().schedulerRevision(8L).activeToken(null).build()
        val other = expected.copyBuilder().answerSignature("裂|亀裂|きれつ|crack").build()
        val pending = pendingSnapshot(signature, revision = 7L)

        assertSame(
            expected,
            StudySessionRestorationPolicy.restorePendingItem(
                pending,
                listOf(other, expected),
                row,
                tokenConsumed = true,
            ),
        )
        assertNull(
            StudySessionRestorationPolicy.restorePendingItem(
                pending.copy(schedulerRevision = 6L),
                listOf(expected),
                row,
                tokenConsumed = true,
            ),
        )
        assertNull(
            StudySessionRestorationPolicy.restorePendingItem(
                pending,
                listOf(expected),
                row,
                tokenConsumed = false,
            ),
        )
        assertNull(
            StudySessionRestorationPolicy.restorePendingItem(
                pending.copy(schedulerRevision = Long.MAX_VALUE),
                listOf(expected),
                row,
                tokenConsumed = true,
            ),
        )
    }

    @Test
    fun legacyPendingAnswerRequiresOneUniqueSameKanjiFamily() {
        val row = row("旧", expression = "旧式", meaning = "old style")
        val only = item(row).copyBuilder().schedulerRevision(20L).build()
        val legacy = pendingSnapshot(signature = null, revision = null, kanji = "旧")

        assertSame(
            only,
            StudySessionRestorationPolicy.restorePendingItem(
                legacy,
                listOf(only),
                row = null,
                tokenConsumed = true,
            ),
        )
        assertNull(
            StudySessionRestorationPolicy.restorePendingItem(
                legacy,
                listOf(only, only.copyBuilder().answerSignature("other").build()),
                row = null,
                tokenConsumed = true,
            ),
        )
    }

    private fun activeSnapshot(item: RecordsStudyModels.StudyItem): StudyActiveSessionSnapshot =
        StudyActiveSessionSnapshot(
            sessionToken = "active-token",
            kanji = item.kanji,
            answerSignatureDigest = studyAnswerSignatureDigest(item.answerSignature),
            schedulerRevision = item.schedulerRevision,
            routingVersion = item.routingVersion,
            taskType = StudyTaskTypes.TYPE_MEANING,
            promptSource = StudyPromptSource.REASON_TEXT,
            sourceSyncFinishedAtMillis = 9_000L,
            typedDraft = "weak",
        )

    private fun pendingSnapshot(
        signature: String?,
        revision: Long?,
        kanji: String = "裂",
    ): StudyPendingAnswerSnapshot = StudyPendingAnswerSnapshot(
        feedback = StudyAnswerFeedbackSnapshot(
            sessionToken = "answered-token",
            phase = StudyAnswerFeedbackPhase.APPLIED,
            outcome = StudyAnswerOutcome.CORRECT,
            selectedAnswer = "split",
        ),
        kanji = kanji,
        taskType = StudyTaskTypes.TYPE_MEANING,
        writingRequired = false,
        prompt = "Needs support",
        answerSignature = signature,
        schedulerRevision = revision,
    )

    private fun item(row: RecordsImportModels.DashboardRow): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(row.kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.TYPE_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .answerSignature(StudyQueueSeeder.answerSignature(row))
            .activeToken("active-token")
            .schedulerRevision(7L)
            .routingVersion(1)
            .build()

    private fun row(
        kanji: String,
        expression: String,
        meaning: String,
    ): RecordsImportModels.DashboardRow {
        val example = RecordsImportModels.Example(
            "active",
            1L,
            2L,
            expression,
            "ぶんれつ",
            meaning,
            "Example sentence",
            false,
            0,
        )
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            meaning,
            "ぶんれつ",
            "deck:Kiku",
            10,
            "weak",
            "Needs support",
            1,
            0,
            0,
            listOf(example),
        )
    }

    private fun ladder(): RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults()
}
