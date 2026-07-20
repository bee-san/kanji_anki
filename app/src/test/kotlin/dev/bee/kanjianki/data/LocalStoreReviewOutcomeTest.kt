package dev.bee.kanjianki.data

import android.content.Context
import androidx.core.database.sqlite.transaction
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goal 45: the advanced study item and its review-log row must persist in one
 * transaction, so process death can never advance scheduling with no
 * `review_log` row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreReviewOutcomeTest {
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
    fun saveReviewOutcomePersistsAdvancedItemAndReviewLogTogether() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val after = before.copyBuilder().totalReviews(2).stability(4.0).build()

        store.saveReviewOutcome(after, reviewRequest("痛", "token-outcome"), "good", 2_000L, before)

        assertEquals(2, store.studyItemsForKanji(listOf("痛")).single().totalReviews)
        assertEquals(1L, store.studyItemsForKanji(listOf("痛")).single().schedulerRevision)
        assertEquals(1, reviewRowCount("token-outcome"))
    }

    @Test
    fun consumedReviewHandoffRequiresEveryIdentityDimension() {
        val request = RecordsSchedulerModels.ReviewRequest(
            "継",
            "token-handoff",
            "good",
            false,
            true,
            false,
            false,
            0,
            "kanji_meaning",
            "継|継続|けいぞく|continuation",
            "Why is this weak?",
        )
        store.saveReview(request, "good", 2_000L)

        assertTrue(
            store.hasMatchingConsumedReview(
                "token-handoff",
                "継",
                "kanji_meaning",
                "継|継続|けいぞく|continuation",
            ),
        )
        assertFalse(
            store.hasMatchingConsumedReview("other-token", "継", "kanji_meaning", request.answerSignature),
        )
        assertFalse(
            store.hasMatchingConsumedReview(request.token, "続", "kanji_meaning", request.answerSignature),
        )
        assertFalse(
            store.hasMatchingConsumedReview(request.token, "継", "word_reading", request.answerSignature),
        )
        assertFalse(
            store.hasMatchingConsumedReview(request.token, "継", "kanji_meaning", "other-signature"),
        )
    }

    @Test
    fun duplicateAndStaleCommitsCannotAdvanceSchedulerState() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val firstAfter = before.copyBuilder().totalReviews(2).build()
        val first = store.saveReviewOutcome(firstAfter, reviewRequest("痛", "token-one"), "good", 2_000L, before)
        assertEquals(ReviewCommitDisposition.APPLIED, first.disposition)

        val duplicate = store.saveReviewOutcome(
            before.copyBuilder().totalReviews(99).build(),
            reviewRequest("痛", "token-one"),
            "good",
            3_000L,
            before,
        )
        assertEquals(ReviewCommitDisposition.DUPLICATE, duplicate.disposition)

        val stale = store.saveReviewOutcome(
            before.copyBuilder().totalReviews(3).build(),
            reviewRequest("痛", "token-stale"),
            "good",
            4_000L,
            before,
        )
        assertEquals(ReviewCommitDisposition.STALE, stale.disposition)
        assertEquals(2, store.studyItemsForKanji(listOf("痛")).single().totalReviews)
        assertEquals(0, reviewRowCount("token-stale"))
    }

    @Test
    fun evidenceAndTaskTimingCommitWithReview() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val request = reviewRequest("痛", "token-evidence").withEvidence(
            RecordsSchedulerModels.ReviewRequest.ReviewEvidence(
                "recognition",
                "visual_confusion",
                "objective_choice",
                "衡",
                "衝",
                "{\"variant\":\"standard_glyph\"}",
            )
        )
        val result = store.commitReview(
            ReviewCommitCommand(
                before.copyBuilder().totalReviews(2).build(),
                request,
                "again",
                2_000L,
                before,
                ReviewTaskTiming("task-1", "痛", "kanji_meaning", 1_000L, 2_000L, 500L, "again"),
            )
        )

        assertEquals(ReviewCommitDisposition.APPLIED, result.disposition)
        store.readableDatabase.rawQuery(
            "SELECT core_skill, failure_cause, evidence_source, selected_answer, correct_answer, answer_evidence_json " +
                "FROM review_log WHERE token=?",
            arrayOf("token-evidence"),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("recognition", cursor.getString(0))
            assertEquals("visual_confusion", cursor.getString(1))
            assertEquals("objective_choice", cursor.getString(2))
            assertEquals("衡", cursor.getString(3))
            assertEquals("衝", cursor.getString(4))
            assertEquals("{\"variant\":\"standard_glyph\"}", cursor.getString(5))
        }
        assertEquals(1, scalarCount("study_task_log", "task_key=?", arrayOf("task-1")))
    }

    @Test
    fun deletedSimilarChoiceStateMakesTheWholeReviewStale() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val submittedBeforeDeletion = RecordsImportModels.SimilarKanjiChoiceCard(
            "痛",
            "pain",
            listOf("痛", "病", "疲"),
            "痛|病|疲",
        )
        store.writableDatabase.execSQL(
            "INSERT INTO ${LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE} " +
                "(target_kanji, choice_signature, primary_meaning, choices, due_at, passed_at, " +
                "last_reviewed_at, correct_count, wrong_count, active_token, first_seen_at, last_seen_at) " +
                "VALUES (?, ?, ?, ?, 0, 0, 0, 0, 0, '', 1, 1)",
            arrayOf<Any>(
                submittedBeforeDeletion.targetKanji,
                submittedBeforeDeletion.choiceSignature,
                submittedBeforeDeletion.primaryMeaning,
                LocalStoreHistory.serializeChoices(submittedBeforeDeletion.choices),
            ),
        )
        assertEquals(
            1,
            scalarCount(LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE, "1=1", emptyArray()),
        )

        // The UI still holds its rendered card, but a sync/settings rebuild
        // deleted the corresponding state row before the review transaction.
        store.writableDatabase.delete(
            LocalStoreBase.TABLE_SIMILAR_KANJI_CHOICE_STATE,
            LocalStoreBase.WHERE_SIMILAR_CHOICE,
            arrayOf(submittedBeforeDeletion.targetKanji, submittedBeforeDeletion.choiceSignature),
        )
        val result = store.commitReview(
            ReviewCommitCommand(
                before.copyBuilder().totalReviews(2).build(),
                reviewRequest("痛", "token-similar-race"),
                "good",
                2_000L,
                before,
                similarChoice = SimilarChoiceCommit(submittedBeforeDeletion, "痛", 2_000L),
            ),
        )

        assertEquals(ReviewCommitDisposition.STALE, result.disposition)
        val persisted = store.studyItemsForKanji(listOf("痛")).single()
        assertEquals(1, persisted.totalReviews)
        assertEquals(0L, persisted.schedulerRevision)
        assertEquals(0, reviewRowCount("token-similar-race"))
        assertEquals(
            0,
            scalarCount(LocalStoreBase.TABLE_SIMILAR_KANJI_REVIEW_LOG, "1=1", emptyArray()),
        )
    }

    @Test
    fun processWideEpochInvalidatesAnotherStoreInstance() {
        val reader = LocalStore(context)
        try {
            val before = studyItem("痛", totalReviews = 1)
            store.saveStudyItem(before)
            assertEquals(1, reader.studyItemsForKanji(listOf("痛")).single().totalReviews)

            store.saveReviewOutcome(
                before.copyBuilder().totalReviews(2).build(),
                reviewRequest("痛", "token-cross-cache"),
                "good",
                2_000L,
                before,
            )

            assertEquals(2, reader.studyItemsForKanji(listOf("痛")).single().totalReviews)
        } finally {
            reader.close()
        }
    }

    @Test
    fun undoRestoresStateAtANewRevisionAndKeepsObjectiveTiming() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val applied = store.commitReview(
            ReviewCommitCommand(
                before.copyBuilder().totalReviews(2).build(),
                reviewRequest("痛", "token-undo"),
                "good",
                2_000L,
                before,
                ReviewTaskTiming("task-undo", "痛", "kanji_meaning", 1_000L, 2_000L, 500L, "good"),
            )
        )
        val after = applied.item!!

        assertTrue(
            store.undoLastAppliedReview(
                dev.bee.kanjianki.core.AppliedReviewSnapshot("token-undo", before, after)
            )
        )

        val restored = store.studyItemsForKanji(listOf("痛")).single()
        assertEquals(1, restored.totalReviews)
        assertEquals(2L, restored.schedulerRevision)
        assertEquals(0, reviewRowCount("token-undo"))
        assertEquals(1, scalarCount("study_task_log", "task_key=?", arrayOf("task-undo")))
    }

    @Test
    fun failureInsideTheOutcomeTransactionRollsBackTheItemAdvance() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val after = before.copyBuilder().totalReviews(2).stability(4.0).build()

        // Drive the same combined write inside an enclosing transaction that then
        // aborts (Android SQLite nests on one connection: the inner outcome write
        // only commits when the outermost transaction commits). This models a crash
        // before the commit: neither the item advance nor the review row survives.
        runCatching {
            store.writableDatabase.transaction {
                store.saveReviewOutcome(after, reviewRequest("痛", "token-fail"), "good", 2_000L, before)
                throw IllegalStateException("simulated crash before commit")
            }
        }

        assertEquals("item advance must roll back", 1, store.studyItemsForKanji(listOf("痛")).single().totalReviews)
        assertEquals("no review row may survive the aborted transaction", 0, reviewRowCount("token-fail"))
    }

    private fun reviewRowCount(token: String): Int {
        return store.readableDatabase.query(
            LocalStoreBase.TABLE_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TOKEN),
            "${LocalStoreBase.COLUMN_TOKEN} = ?",
            arrayOf(token),
            null,
            null,
            null,
        ).use { it.count }
    }

    private fun scalarCount(table: String, where: String, args: Array<String>): Int {
        return store.readableDatabase.query(table, arrayOf("COUNT(*)"), where, args, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    private fun reviewRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, true, false, 0)
    }

    private fun studyItem(kanji: String, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, totalReviews, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }
}
