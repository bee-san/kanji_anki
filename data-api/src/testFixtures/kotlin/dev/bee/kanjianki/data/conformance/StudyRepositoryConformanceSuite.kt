package dev.bee.kanjianki.data.conformance

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.FinishLegacyRepairCommand
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.ReviewCommitDisposition
import dev.bee.kanjianki.data.ReviewTaskTiming
import dev.bee.kanjianki.data.ReviewTokenQuery
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SkipLegacyRepairCommand
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import dev.bee.kanjianki.data.StudyRecoveryQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * The Goal 181 cross-implementation contract for Study persistence: the legacy
 * Android `LocalStore` StudyRepository and the shared `:data-sql`
 * StudyRepository must be indistinguishable through their typed surface,
 * including the token-first, revision-CAS review commit and undo recovery.
 *
 * Pins concrete expected values (APPLIED/DUPLICATE/STALE dispositions, streak
 * counters, token status) rather than comparing the implementations to each
 * other, so a drift in either one fails on its own.
 */
class StudyRepositoryConformanceSuite(
    private val host: RepositoryConformanceHost,
) {
    suspend fun runAll() {
        emptyQueueReadsAreStable()
        replaceQueuePersistsAndReloadsItems()
        commitReviewIsTokenFirstRevisionCasAndIdempotent()
        commitReviewRejectsAStaleRevision()
        undoRestoresTheBeforeReviewState()
        taskTimingIsInsertOnceAndTokenStatusReflectsCommits()
        auxiliaryReadsAndMnemonicRoundTrip()
        legacyWritingRepairIsNoOpWithoutRows()
    }

    private suspend fun emptyQueueReadsAreStable() {
        host.reset()
        val queue = host.study.loadQueue(NOW).expect("loadQueue on empty store")
        assertTrue("empty store has no study items", queue.studyItems.isEmpty())
        assertTrue(queue.activeRows.isEmpty())
        assertEquals(0, queue.studyStreak.currentDays)
        assertEquals(0, queue.recentReviewStats.total)
        assertTrue(queue.studiedKanjiToday.isEmpty())
        assertTrue(host.study.loadAllItems().expect("loadAllItems on empty store").isEmpty())
        assertEquals("", host.study.loadMnemonic("裂").expect("loadMnemonic on empty store"))
        assertFalse(
            "no token has been consumed",
            host.study.reviewTokenStatus(tokenQuery("裂", "missing"))
                .expect("token status on empty store").consumed,
        )
    }

    private suspend fun replaceQueuePersistsAndReloadsItems() {
        host.reset()
        val split = studyItem("裂", revision = 0)
        val escape = studyItem("脱", revision = 0)
        assertTrue(
            host.study.replaceQueue(StudyQueueWriteCommand(listOf(split, escape)))
                .isOk(),
        )
        val loaded = host.study.loadAllItems().expect("loadAllItems after replaceQueue")
        assertEquals(setOf("裂", "脱"), loaded.map { it.kanji }.toSet())

        val forKanji = host.study.loadItems(listOf("裂")).expect("loadItems")
        assertEquals(listOf("裂"), forKanji.map { it.kanji })

        // A saveItem with an equal-or-newer revision persists; an older one does not.
        val advanced = split.copyBuilder().schedulerRevision(5).build()
        assertTrue(host.study.saveItem(advanced).isOk())
        assertEquals(
            5L,
            host.study.loadItems(listOf("裂")).expect("loadItems after saveItem")
                .first().schedulerRevision,
        )
    }

    private suspend fun commitReviewIsTokenFirstRevisionCasAndIdempotent() {
        host.reset()
        val before = studyItem("裂", revision = 0)
        assertTrue(host.study.replaceQueue(StudyQueueWriteCommand(listOf(before))).isOk())

        val command = reviewCommit(before, token = "tok-1")
        val applied = host.study.commitReview(command).expect("first commit")
        assertEquals(ReviewCommitDisposition.APPLIED, applied.disposition)
        assertEquals(
            "an applied review advances the scheduler revision by one",
            before.schedulerRevision + 1,
            applied.item?.schedulerRevision,
        )
        assertTrue(
            "the token is consumed after a commit",
            host.study.reviewTokenStatus(tokenQuery("裂", "tok-1"))
                .expect("token status after commit").consumed,
        )
        assertTrue(
            host.study.reviewTokenStatus(tokenQuery("裂", "tok-1"))
                .expect("matching token status").matchesReview,
        )

        // Re-submitting the same token is an idempotent duplicate, not a second review.
        val duplicate = host.study.commitReview(command).expect("duplicate commit")
        assertEquals(ReviewCommitDisposition.DUPLICATE, duplicate.disposition)
        assertNull("a duplicate returns no item", duplicate.item)

        // The stats window and studied-today set now reflect exactly one review.
        val queue = host.study.loadQueue(NOW).expect("loadQueue after commit")
        assertEquals(1, queue.recentReviewStats.total)
        assertTrue(queue.studiedKanjiToday.contains("裂"))
    }

    private suspend fun commitReviewRejectsAStaleRevision() {
        host.reset()
        val before = studyItem("裂", revision = 0)
        assertTrue(host.study.replaceQueue(StudyQueueWriteCommand(listOf(before))).isOk())

        // First commit advances the row to revision 1.
        assertEquals(
            ReviewCommitDisposition.APPLIED,
            host.study.commitReview(reviewCommit(before, token = "tok-a")).expect("first").disposition,
        )
        // A second commit that still expects revision 0 must be rejected as stale
        // (the CAS predicate no longer matches), with a fresh token so it is not
        // classified as a duplicate.
        val stale = host.study.commitReview(reviewCommit(before, token = "tok-b")).expect("stale")
        assertEquals(ReviewCommitDisposition.STALE, stale.disposition)
        assertFalse(
            "a stale commit never inserts its review row",
            host.study.reviewTokenStatus(tokenQuery("裂", "tok-b")).expect("stale token").consumed,
        )
    }

    private suspend fun undoRestoresTheBeforeReviewState() {
        host.reset()
        val before = studyItem("裂", revision = 0)
        assertTrue(host.study.replaceQueue(StudyQueueWriteCommand(listOf(before))).isOk())
        val applied = host.study.commitReview(reviewCommit(before, token = "tok-undo")).expect("commit")
        val after = requireNotNull(applied.item)

        assertTrue(
            "undo removes the review and restores the item",
            host.study.undoLastReview(AppliedReviewSnapshot("tok-undo", before, after))
                .expect("undo"),
        )
        assertFalse(
            host.study.reviewTokenStatus(tokenQuery("裂", "tok-undo")).expect("token after undo").consumed,
        )
        assertEquals(
            "undo bumps the revision above the undone state",
            after.schedulerRevision + 1,
            host.study.loadItems(listOf("裂")).expect("item after undo").first().schedulerRevision,
        )
        // A second undo of the same token is a no-op.
        assertFalse(
            host.study.undoLastReview(AppliedReviewSnapshot("tok-undo", before, after))
                .expect("second undo"),
        )
    }

    private suspend fun taskTimingIsInsertOnceAndTokenStatusReflectsCommits() {
        host.reset()
        val timing = ReviewTaskTiming(
            taskKey = "task-1",
            kanji = "裂",
            taskType = "kanji_meaning",
            startedAtMillis = NOW,
            answeredAtMillis = NOW + 1_000,
            activeElapsedMillis = 1_000,
            outcome = "good",
        )
        assertTrue(
            "the first timing insert succeeds",
            host.study.recordTaskTiming(timing).expect("first timing"),
        )
        assertFalse(
            "a duplicate task key is an idempotent no-op",
            host.study.recordTaskTiming(timing).expect("duplicate timing"),
        )

        val recovery = host.study.recoveryStatus(
            StudyRecoveryQuery(review = tokenQuery("裂", "never-committed")),
        ).expect("recovery status")
        assertFalse(recovery.token.consumed)
        assertFalse(recovery.legacyRepairFinished)
    }

    private suspend fun auxiliaryReadsAndMnemonicRoundTrip() {
        host.reset()
        val split = studyItem("裂", revision = 0)
        assertTrue(host.study.replaceQueue(StudyQueueWriteCommand(listOf(split))).isOk())

        assertNull(
            "loadQueueVersion is null until a successful sync exists",
            host.study.loadQueueVersion().expect("loadQueueVersion"),
        )

        val choiceData = host.study.loadChoiceData("裂", NOW).expect("loadChoiceData")
        assertTrue("no reading usages seeded", choiceData.kanjiReadingUsages.isEmpty())
        assertTrue(choiceData.readingKanjiCandidates.isEmpty())

        assertNull(
            "no similar-choice card is due without one seeded",
            host.study.loadDueSimilarChoice("裂", NOW).expect("loadDueSimilarChoice"),
        )
        assertTrue(
            host.study.loadDueLegacyWritingRepairs(NOW)
                .expect("loadDueLegacyWritingRepairs").isEmpty(),
        )

        val annotated = host.study.annotateCapabilities(listOf(split)).expect("annotateCapabilities")
        assertEquals(listOf("裂"), annotated.map { it.kanji })

        assertTrue(host.study.saveMnemonic(SaveMnemonicCommand("裂", "  torn cloth  ", NOW)).isOk())
        assertEquals(
            "torn cloth",
            host.study.loadMnemonic("裂").expect("loadMnemonic after save"),
        )
        assertTrue(host.study.saveMnemonic(SaveMnemonicCommand("裂", "   ", NOW + 1)).isOk())
        assertEquals("", host.study.loadMnemonic("裂").expect("loadMnemonic after clear"))
    }

    private suspend fun legacyWritingRepairIsNoOpWithoutRows() {
        host.reset()
        // With no repair row present, save/finish/skip must be safe no-ops that
        // report "not finished" rather than throwing.
        assertTrue(
            host.study.saveLegacyWritingRepair(writingRepair()).isOk(),
        )
        assertFalse(
            host.study.finishLegacyWritingRepair(
                FinishLegacyRepairCommand(REPAIR_ID, "repair-token", passed = true, finishedAtMillis = NOW),
            ).expect("finish without a row"),
        )
        assertFalse(
            host.study.skipLegacyWritingRepair(
                SkipLegacyRepairCommand(REPAIR_ID, "repair-token", skippedAtMillis = NOW),
            ).expect("skip without a row"),
        )
    }

    private fun writingRepair(): RecordsImportModels.SimilarKanjiWritingRepair =
        RecordsImportModels.SimilarKanjiWritingRepair(
            REPAIR_ID,
            "痛",
            "痒",
            "痛|痒",
            "痒",
            "pain",
            "pending",
            NOW,
            "repair-token",
            0,
            NOW,
            NOW,
            0L,
        )

    private fun reviewCommit(
        before: RecordsStudyModels.StudyItem,
        token: String,
    ): ReviewCommitCommand {
        val after = before.copyBuilder()
            .schedulerRevision(before.schedulerRevision)
            .build()
        val request = RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = before.kanji,
                token = token,
                rating = "good",
                writingRequired = false,
                writingPassed = false,
                writingClean = false,
                manualOverride = false,
                hintsUsed = 0,
                taskType = "kanji_meaning",
                answerSignature = before.answerSignature,
                prompt = "",
            ),
        )
        return ReviewCommitCommand(
            afterReview = after,
            request = request,
            appliedRating = "good",
            reviewedAtMillis = NOW,
            beforeReview = before,
            taskTiming = null,
            choiceLog = null,
            similarChoice = null,
        )
    }

    private fun studyItem(kanji: String, revision: Long): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            NOW,
            1.0,
            2.0,
            3,
            0,
            0,
            0,
            "",
            NOW,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("active-$kanji")
            .schedulerRevision(revision)
            .build()

    private fun tokenQuery(kanji: String, token: String): ReviewTokenQuery =
        ReviewTokenQuery(token = token, kanji = kanji, taskType = "kanji_meaning", answerSignature = "")

    private fun <T> StoreResult<T>.expect(label: String): T {
        assertTrue("$label must succeed, got $this", isOk())
        if (this is StoreResult.Ok) {
            return value
        }
        throw AssertionError("$label was not Ok: $this")
    }

    private companion object {
        const val NOW = 1_770_100_000_000L
        const val REPAIR_ID = 4_242L
    }
}
