package dev.bee.kanjianki

import dev.bee.kanjianki.application.StudyUseCases
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.ReviewCommitResult
import dev.bee.kanjianki.data.ReviewTokenStatus
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduler-facing half of Goal 195's parity, including the persist-restart-prove
 * requirement: a review committed by one runtime is seen by a fresh one against the
 * same store, and the same card is not re-served.
 *
 * The store is an in-memory [FakeStudyRepository] wired to behave like the real one on
 * the paths the runtime touches — commit advances the item and records the token, the
 * token query reads that record, undo restores the pre-review item. That is enough to
 * exercise the token-idempotence, revision advance, and only-APPLIED-advances rules the
 * runtime enforces without standing up SQLite.
 */
class StudyRuntimeTest {
    @Test
    fun loadingServesTheDueCardAndAGoodGradeThenContinueDrainsTheQueue() = runTest {
        val store = InMemoryStudyStore(items = listOf(dueItem("脱")))
        val runtime = StudyRuntime(StudyUseCases(store.repository))

        val first = runtime.load(NOW)
        assertNotNull(first.session)
        assertEquals("脱", first.session?.item?.kanji)
        assertEquals(StudySessionPhase.ACTIVE, first.routeSnapshot.phase)

        val graded = runtime.grade("good", NOW)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, graded.routeSnapshot.feedback?.phase)
        assertTrue(graded.undoable)
        // The commit advanced the item and recorded the token.
        assertEquals(1, store.reviewLog.size)
        assertTrue("a good grade schedules the card into the future", store.item("脱").dueAtMillis > NOW)

        val advanced = runtime.continueCard(NOW)
        // The only due card was answered and pushed out; the queue is drained.
        assertNull(advanced.session)
    }

    @Test
    fun aReviewSurvivesRestartingTheRuntimeAgainstTheSameStore() {
        // The Goal 195 "persist a review, restart, prove exact state" requirement.
        runTest {
            val store = InMemoryStudyStore(items = listOf(dueItem("脱"), dueItem("説")))
            val before = StudyRuntime(StudyUseCases(store.repository))
            before.load(NOW)
            val gradedKanji = before.render().session?.item?.kanji
            assertNotNull(gradedKanji)
            before.grade("good", NOW)

            // A brand-new runtime — the "restart" — reading the same committed store.
            val after = StudyRuntime(StudyUseCases(store.repository))
            val resumed = after.load(NOW + 1_000L)

            // The graded card was advanced past the horizon, so the restarted session
            // serves the *other* card, never the one already reviewed.
            assertEquals(1, store.reviewLog.size)
            assertTrue(store.item(gradedKanji!!).dueAtMillis > NOW)
            val served = resumed.session?.item?.kanji
            assertNotNull(served)
            assertFalse("the restarted session must not re-serve the reviewed card", served == gradedKanji)
        }
    }

    @Test
    fun aFailGradeReschedulesButKeepsTheCardInTheQueueSooner() = runTest {
        val store = InMemoryStudyStore(items = listOf(dueItem("脱")))
        val runtime = StudyRuntime(StudyUseCases(store.repository))
        runtime.load(NOW)

        val graded = runtime.grade("again", NOW)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, graded.routeSnapshot.feedback?.phase)
        assertEquals(
            dev.bee.kanjianki.StudyAnswerOutcome.INCORRECT,
            graded.routeSnapshot.feedback?.outcome,
        )
        // A lapse still commits a review and advances the stored item.
        assertEquals(1, store.reviewLog.size)
    }

    @Test
    fun aDuplicateTokenAdvancesNothingAndStillMovesThePresentationOn() = runTest {
        val store = InMemoryStudyStore(items = listOf(dueItem("脱")))
        val runtime = StudyRuntime(StudyUseCases(store.repository))
        val loaded = runtime.load(NOW)
        // Pre-consume the mounted card's exact token, as a competing commit would have.
        store.forceConsume(loaded.session!!.token)

        val graded = runtime.grade("good", NOW)

        // No new review row — the token was already consumed — but the gate still
        // reaches APPLIED so the card is not stranded.
        assertEquals(0, store.reviewLog.size)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, graded.routeSnapshot.feedback?.phase)
    }

    @Test
    fun undoRestoresTheItemAndOffersTheCardAgain() = runTest {
        val store = InMemoryStudyStore(items = listOf(dueItem("脱")))
        val runtime = StudyRuntime(StudyUseCases(store.repository))
        runtime.load(NOW)
        val beforeDue = store.item("脱").dueAtMillis
        runtime.grade("good", NOW)
        assertTrue(store.item("脱").dueAtMillis != beforeDue)

        val undone = runtime.undo(NOW)

        assertEquals("undo deletes the review row", 0, store.reviewLog.size)
        assertEquals("undo restores the pre-review item", beforeDue, store.item("脱").dueAtMillis)
        assertFalse("the one reversible snapshot is spent", undone.undoable)
        // The restored card is due again and is re-served.
        assertEquals("脱", undone.session?.item?.kanji)
    }

    @Test
    fun aSecondGradeOnAnAnsweredCardIsIgnored() = runTest {
        val store = InMemoryStudyStore(items = listOf(dueItem("脱")))
        val runtime = StudyRuntime(StudyUseCases(store.repository))
        runtime.load(NOW)
        runtime.grade("good", NOW)

        // The double-commit guard: a second grade before Continue does nothing.
        runtime.grade("again", NOW)

        assertEquals(1, store.reviewLog.size)
    }

    @Test
    fun anEmptyQueueLoadsToNoCard() = runTest {
        val store = InMemoryStudyStore(items = emptyList())
        val runtime = StudyRuntime(StudyUseCases(store.repository))

        val loaded = runtime.load(NOW)

        assertNull(loaded.session)
        assertEquals(0, loaded.routeSnapshot.progress.targetCount)
    }

    private fun dueItem(kanji: String) = RecordsStudyModels.StudyItem(
        kanji,
        "review",
        NOW - 1_000L,
        5.0,
        5.0,
        3,
        0,
        0,
        1,
        null,
        0L,
    ).withRung(RecordsBase.LadderRung.KANJI_MEANING)

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}

/**
 * A minimal in-memory study store over [FakeStudyRepository].
 *
 * Real enough on the runtime's paths: `commitReview` advances the stored item and
 * records the token (returning APPLIED with the revision-bumped item), the token query
 * reflects that log, and `undoLastReview` restores the pre-review item and drops the
 * row — the token-first, revision-CAS behavior CLAUDE.md pins.
 */
private class InMemoryStudyStore(items: List<RecordsStudyModels.StudyItem>) {
    private val itemsByKanji = items.associateBy { it.kanji }.toMutableMap()
    val reviewLog = mutableListOf<String>()
    private val forcedTokens = mutableSetOf<String>()

    val repository = FakeStudyRepository().apply {
        loadQueueHandler = { StoreResult.ok(snapshot()) }
        tokenHandler = { query ->
            StoreResult.ok(
                ReviewTokenStatus(
                    consumed = query.token in reviewLog || query.token in forcedTokens,
                    matchesReview = query.token in reviewLog,
                ),
            )
        }
        commitReviewHandler = { command ->
            if (command.request.token in reviewLog) {
                StoreResult.ok(ReviewCommitResult.duplicate())
            } else {
                val persisted = command.persistedItem()
                itemsByKanji[persisted.kanji] = persisted
                reviewLog += command.request.token
                StoreResult.ok(ReviewCommitResult.applied(persisted))
            }
        }
        undoHandler = { snapshot ->
            if (snapshot.token in reviewLog) {
                itemsByKanji[snapshot.beforeReview.kanji] = snapshot.beforeReview
                reviewLog -= snapshot.token
                StoreResult.ok(true)
            } else {
                StoreResult.ok(false)
            }
        }
    }

    fun item(kanji: String): RecordsStudyModels.StudyItem = itemsByKanji.getValue(kanji)

    /** Marks a specific token consumed, as a competing commit would have. */
    fun forceConsume(token: String) {
        forcedTokens += token
    }

    private fun snapshot(): StudyQueueSnapshot {
        val items = itemsByKanji.values.toList()
        return StudyQueueSnapshot(
            activeRows = items.map(::row),
            availableRows = items.map(::row),
            studyItems = items,
            locallySuspendedKanji = emptySet(),
            latestSuccessfulSyncAtMillis = null,
            studyLadder = RecordsBase.StudyLadderSettings.defaults(),
            syncSettings = RecordsSyncModels.Settings.kikuDefaults(),
            schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            schedulerFsrsWeights = null,
            learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
            adaptiveWorkload = AdaptiveWorkloadSnapshot(workPercent = 100, maxItems = 40, mode = "balanced"),
            studyAheadMinutes = 20,
            studyStreak = StudyStreakSnapshot(0, 0, false, 0, 0L),
            recentReviewStats = RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
            studiedKanjiToday = emptySet(),
            dueLegacyWritingRepairs = emptyList(),
            consecutiveFailedSyncs = 0,
        )
    }

    private fun row(item: RecordsStudyModels.StudyItem) = RecordsImportModels.DashboardRow(
        item.kanji,
        900,
        "take off",
        "だつ",
        "deck:current",
        50,
        "reason",
        "reason text",
        1,
        1,
        0,
        listOf(RecordsImportModels.Example("active", 1L, 1L, "脱出", "だつ", "escape", "脱出する", false, 0)),
    )
}
