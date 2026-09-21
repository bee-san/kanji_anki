package dev.bee.kanjianki.core

import org.junit.Test

import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashSet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

/**
 * Scheduler tests that do not depend on the ladder state machine directly:
 * queue seeding, admission caps, token handling, adaptive focus, and retired
 * reconciliation. Ladder / rung / phase transition tests live in
 * {@link LadderSchedulerTest}.
 */
public class BridgeSchedulerTest {
    @Test
    public fun seedsQueueWithDailyNewAndActiveCaps() {
        var settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                2,
                3000,
                2,
                1
        )
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("裂", 20), row("謎", 19), row("示", 18))
        var items: List<RecordsStudyModels.StudyItem> = BridgeScheduler().seedQueue(rows, emptyList(), settings, 1000L, 0L)
        assertEquals(1, items.size)
        assertEquals("裂", items.get(0).kanji)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, items.get(0).rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, items.get(0).phase)
    }

    @Test
    public fun writingFailureOnWriteKanjiRungMapsToAgain() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var item: RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
                .withRungAndPhase(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW)
        var request: RecordsSchedulerModels.ReviewRequest = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", true, false, false, 0)
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(item, request, HashSet(), 1000L)
        assertEquals("again", result.appliedRating)
    }

    @Test
    public fun manualOverrideAllowsWritingRatingOnWriteKanjiRung() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var item: RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
                .withRungAndPhase(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW)
        var request: RecordsSchedulerModels.ReviewRequest = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", true, false, true, 0)
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(item, request, HashSet(), 1000L)
        // Manual override on the write rung is mapped to Hard by the scheduler
        // so the learner keeps progress but does not auto-pass.
        assertEquals("hard", result.appliedRating)
        assertFalse(result.duplicate)
        assertEquals(2, result.item.writingLevel)
    }

    @Test
    public fun writingHelpOnlyChangesAfterWritingReviews() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var item: RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
        var request: RecordsSchedulerModels.ReviewRequest = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "easy", false, false, false, 0)
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(item, request, HashSet(), 1000L)
        assertEquals("easy", result.appliedRating)
        assertEquals(2, result.item.writingLevel)
    }

    @Test
    public fun cleanWritingAdvancesHintAssistedAndMessyWritingHold() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var template: RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 1, "clean", 0)
                .withRungAndPhase(RecordsBase.LadderRung.WRITE_KANJI, RecordsBase.SchedulerPhase.REVIEW)
        var clean: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                template,
                RecordsSchedulerModels.ReviewRequest("裂", "clean", "hard", true, true, true, false, 0),
                HashSet(),
                1000L
        )
        var hinted: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                template.withToken("hinted"),
                RecordsSchedulerModels.ReviewRequest("裂", "hinted", "good", true, true, true, false, 1),
                HashSet(),
                1000L
        )
        var messy: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                template.withToken("messy"),
                RecordsSchedulerModels.ReviewRequest("裂", "messy", "hard", true, true, false, false, 0),
                HashSet(),
                1000L
        )
        assertEquals(2, clean.item.writingLevel)
        assertEquals(1, hinted.item.writingLevel)
        assertEquals(1, messy.item.writingLevel)
    }

    @Test
    public fun duplicateTokenDoesNotAdvanceTwice() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var item: RecordsStudyModels.StudyItem = item("裂").withToken("token-1")
        var consumed: HashSet<String> = HashSet()
        var request: RecordsSchedulerModels.ReviewRequest = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "easy", false, false, false, 0)
        var first: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(item, request, consumed, 1000L)
        var second: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(first.item.withToken("token-1"), request, consumed, 2000L)
        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(first.item.totalReviews, second.item.totalReviews)
    }

    @Test
    public fun nextSessionRotatesTaskShapeForEachRung() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var typing: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(itemAtRung("拉", RecordsBase.LadderRung.TYPE_MEANING)),
                listOf(row("拉", 10)),
                1000L
        )!!
        var kanji: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(itemAtRung("裂", RecordsBase.LadderRung.KANJI_MEANING)),
                listOf(row("裂", 10)),
                1000L
        )!!
        var meaningKanji: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(itemAtRung("浅", RecordsBase.LadderRung.MEANING_KANJI)),
                listOf(row("浅", 10)),
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults().withRungEnabled(RecordsBase.LadderRung.MEANING_KANJI, true)
        )!!
        var font: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(itemAtRung("謎", RecordsBase.LadderRung.FONT_MEANING)),
                listOf(row("謎", 10)),
                1000L
        )!!
        var word: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(itemAtRung("示", RecordsBase.LadderRung.WORD_READING)),
                listOf(row("示", 10)),
                1000L
        )!!
        assertNotNull(typing)
        assertNotNull(meaningKanji)
        assertNotNull(kanji)
        assertNotNull(font)
        assertNotNull(word)
        assertEquals("type_meaning", typing.taskType)
        assertEquals("meaning_kanji", meaningKanji.taskType)
        assertEquals("kanji_meaning", kanji.taskType)
        assertEquals("font_meaning", font.taskType)
        assertEquals("word_reading", word.taskType)
        assertFalse(typing.writingRequired)
        assertFalse(meaningKanji.writingRequired)
        assertFalse(kanji.writingRequired)
        assertFalse(font.writingRequired)
        assertFalse(word.writingRequired)
    }

    @Test
    public fun writeKanjiRungRoutesToWritingTask() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var pending: RecordsStudyModels.StudyItem = itemAtRung("裂", RecordsBase.LadderRung.WRITE_KANJI)
                .copyBuilder()
                .writingRemediationPending(true)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(pending),
                listOf(row("裂", 10)),
                1000L
        )!!
        assertNotNull(session)
        assertTrue(session.writingRequired)
        assertEquals("write_kanji", session.taskType)
    }

    @Test
    public fun targetedSessionUsesExistingItemAndLearnerMeaningPrompt() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var existing: RecordsStudyModels.StudyItem = itemAtRung("裂", RecordsBase.LadderRung.WORD_READING)
                .copyBuilder()
                .state("review")
                .activeToken("keep-token")
                .hasSimilarKanji(true)
                .build()
        var session: RecordsSchedulerModels.StudySession = scheduler.targetedSession(
                listOf(existing),
                rowWithMeaning("裂", "split", "reason fallback"),
                1234L,
                RecordsBase.StudyLadderSettings.defaults()
        )!!
        assertNotNull(session)
        assertEquals("keep-token", session.token)
        assertEquals("keep-token", session.item!!.activeToken)
        assertSame(existing, scheduler.targetedStudyItem(listOf(existing), "裂", 1234L, RecordsBase.StudyLadderSettings.defaults()))
        assertEquals(RecordsBase.LadderRung.WORD_READING, session.item.rung)
        assertEquals("word_reading", session.taskType)
        assertEquals("split", session.prompt)
        assertFalse(session.writingRequired)
    }

    @Test
    public fun targetedSessionCreatesNewItemWithFallbackPromptAndEffectiveRung() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)
        var session: RecordsSchedulerModels.StudySession = scheduler.targetedSession(
                emptyList(),
                rowWithMeaning("謎", "", "local reason"),
                1234L,
                ladder
        )!!
        assertNotNull(session)
        assertEquals("謎", session.item!!.kanji)
        assertEquals("new", session.item.state)
        assertEquals(1234L, session.item.dueAtMillis)
        assertEquals(1234L, session.item.createdAtMillis)
        // New default order (Goal 65): with kanji_meaning disabled and no
        // similar-kanji content, the nearest enabled rung by distance is
        // font_meaning (the content-less similar_kanji directly below is
        // unavailable, so the mapping falls to the closer higher rung).
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, session.item.rung)
        assertEquals("font_meaning", session.taskType)
        assertEquals("local reason", session.prompt)
        assertFalse(session.writingRequired)
        assertTrue(session.token.startsWith("謎-"))
        assertEquals(session.token, session.item.activeToken)
    }

    @Test
    public fun nextSessionPrioritizesDueReviewsBeforeNewCards() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var newProblem: RecordsStudyModels.StudyItem = itemAtRung("裂", RecordsBase.LadderRung.KANJI_MEANING)
        var dueReview: RecordsStudyModels.StudyItem = itemAtRung("謎", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("review")
                .dueAtMillis(500L)
                .stability(1.8)
                .difficulty(4.8)
                .totalReviews(2)
                .learningStep(2)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(newProblem, dueReview),
                listOf(row("裂", 30), row("謎", 20)),
                1000L
        )!!
        assertNotNull(session)
        assertEquals("謎", session.item!!.kanji)
        assertEquals("kanji_meaning", session.taskType)
        assertFalse(session.writingRequired)
    }

    @Test
    public fun nextSessionTreatsRelearningAndWriteRungAsUrgentBeforeReviews() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var review: RecordsStudyModels.StudyItem = itemAtRung("謎", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("review")
                .stability(2.0)
                .difficulty(4.0)
                .totalReviews(4)
                .learningStep(2)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()
        var relearning: RecordsStudyModels.StudyItem = itemAtRung("習", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .dueAtMillis(500L)
                .stability(0.9)
                .difficulty(5.5)
                .totalReviews(2)
                .lapses(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()
        var writeRung: RecordsStudyModels.StudyItem = itemAtRung("裂", RecordsBase.LadderRung.WRITE_KANJI)
                .copyBuilder()
                .state("review")
                .stability(1.0)
                .difficulty(5.0)
                .totalReviews(5)
                .lapses(2)
                .learningStep(2)
                .writingLevel(1)
                .writingRemediationPending(true)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()
        var writeSession: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(review, relearning, writeRung),
                listOf(row("謎", 100), row("習", 10), row("裂", 1)),
                1000L
        )!!
        var relearningSession: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(review, relearning),
                listOf(row("謎", 100), row("習", 10)),
                1000L
        )!!
        assertNotNull(writeSession)
        assertEquals("裂", writeSession.item!!.kanji)
        assertEquals("write_kanji", writeSession.taskType)
        assertNotNull(relearningSession)
        assertEquals("習", relearningSession.item!!.kanji)
        assertEquals("kanji_meaning", relearningSession.taskType)
    }

    @Test
    public fun randomizedSessionTaskKeysUseDeterministicSeedWithinPriorityGroups() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var items: List<RecordsStudyModels.StudyItem> = listOf(
                reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L),
                reviewItem("裂", RecordsBase.LadderRung.WRITE_KANJI, 0L),
                reviewItem("示", RecordsBase.LadderRung.WORD_READING, 0L)
        )
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("謎", 10), row("裂", 80), row("示", 50))
        var first: List<String> = scheduler.randomizedSessionTaskKeys(items, rows, 1000L, 0L, null, RecordsSyncModels.Settings.kikuDefaults(), RecordsBase.StudyLadderSettings.defaults(), 42L)
        var second: List<String> = scheduler.randomizedSessionTaskKeys(items, rows, 1000L, 0L, null, RecordsSyncModels.Settings.kikuDefaults(), RecordsBase.StudyLadderSettings.defaults(), 42L)
        var dueSorted: List<String> = listOf(
                BridgeScheduler.sessionTaskKeyForItem(items.get(1)),
                BridgeScheduler.sessionTaskKeyForItem(items.get(0)),
                BridgeScheduler.sessionTaskKeyForItem(items.get(2))
        )
        assertEquals(first, second)
        assertEquals(3, first.size)
        assertTrue(first.containsAll(dueSorted))
        assertEquals(BridgeScheduler.sessionTaskKeyForItem(items.get(1)), first.get(0))
    }

    @Test
    public fun randomizedSessionTaskKeysKeepLearningRepeatsAndReviewsBeforeNewCards() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var learningRepeat: RecordsStudyModels.StudyItem = itemAtRung("学", RecordsBase.LadderRung.KANJI_MEANING)
                .copyBuilder()
                .state("learning")
                .totalReviews(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
        var dueReview: RecordsStudyModels.StudyItem = reviewItem("復", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var unseenNew: RecordsStudyModels.StudyItem = itemAtRung("新", RecordsBase.LadderRung.KANJI_MEANING)
        var plan: List<String> = scheduler.randomizedSessionTaskKeys(
                listOf(unseenNew, dueReview, learningRepeat),
                listOf(row("新", 30), row("復", 20), row("学", 10)),
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults(),
                7L
        )
        var repeatKey: String = BridgeScheduler.sessionTaskKeyForItem(learningRepeat)
        var reviewKey: String = BridgeScheduler.sessionTaskKeyForItem(dueReview)
        var newKey: String = BridgeScheduler.sessionTaskKeyForItem(unseenNew)

        assertTrue(plan.contains(repeatKey))
        assertTrue(plan.contains(reviewKey))
        assertTrue(plan.contains(newKey))
        assertTrue(plan.indexOf(repeatKey) < plan.indexOf(newKey))
        assertTrue(plan.indexOf(reviewKey) < plan.indexOf(newKey))
    }

    @Test
    public fun plannedSessionSkipsWrongAnswerRelearningRepeatUntilNextSession() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var failedReview: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("review-token")
        var nextReview: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.WORD_READING, 0L)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("裂", 80), row("謎", 10))
        var initialPlan: List<String> = listOf(
                BridgeScheduler.sessionTaskKeyForItem(failedReview),
                BridgeScheduler.sessionTaskKeyForItem(nextReview)
        )
        var wrong: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                failedReview,
                RecordsSchedulerModels.ReviewRequest("裂", "review-token", "again", false, false, false, 0),
                HashSet(),
                1000L
        )
        var currentSessionNext: RecordsSchedulerModels.StudySession = scheduler.nextSessionForTaskKeys(
                listOf(wrong.item, nextReview),
                rows,
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults(),
                initialPlan.subList(1, initialPlan.size)
        )!!
        var nextSessionPlan: List<String> = scheduler.randomizedSessionTaskKeys(
                listOf(wrong.item, nextReview),
                rows,
                wrong.item.dueAtMillis,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults(),
                7L
        )
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, wrong.item.phase)
        assertTrue(wrong.item.dueAtMillis > 1000L)
        assertNotNull(currentSessionNext)
        assertEquals("謎", currentSessionNext.item!!.kanji)
        assertTrue(nextSessionPlan.contains(BridgeScheduler.sessionTaskKeyForItem(wrong.item)))
    }

    @Test
    public fun applyReviewAcceptsCustomLearningStepsTogetherWithCustomLadder() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var failedReview: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 0L).withToken("review-token")
        var learningSteps: RecordsSchedulerModels.LearningStepSettings = RecordsSchedulerModels.LearningStepSettings(
                listOf(2, 9),
                listOf(3)
        )
        var ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings(
                listOf(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.LadderRung.WORD_READING),
                listOf(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.LadderRung.WORD_READING)
        )
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                failedReview,
                RecordsSchedulerModels.ReviewRequest("裂", "review-token", "again", false, false, false, 0),
                HashSet(),
                1_000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                learningSteps,
                ladder
        )
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, result.item.phase)
        assertEquals(1_000L + (3L * 60L * 1000L), result.item.dueAtMillis)
        assertEquals(RecordsBase.LadderRung.WORD_READING, result.item.rung)
    }

    @Test
    public fun dueRelearningRepeatWithEmptyReviewStepsReturnsToReviewWithoutCrashing() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var relearning: RecordsStudyModels.StudyItem = reviewItem("習", RecordsBase.LadderRung.KANJI_MEANING, 1_000L)
                .copyBuilder()
                .state("learning")
                .lapses(1)
                .learningStep(0)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .activeToken("relearn-token")
                .build()
        var learningSteps: RecordsSchedulerModels.LearningStepSettings = RecordsSchedulerModels.LearningStepSettings(
                listOf(1, 10),
                emptyList()
        )

        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                relearning,
                RecordsSchedulerModels.ReviewRequest("習", "relearn-token", "again", false, false, false, 0),
                HashSet(),
                2_000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                learningSteps,
                RecordsBase.StudyLadderSettings.defaults()
        )

        assertEquals("again", result.appliedRating)
        assertEquals("review", result.item.state)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase)
        assertEquals(0, result.item.learningStep)
        assertEquals(2_000L + BridgeScheduler.DAY, result.item.dueAtMillis)
        assertEquals(1, result.item.matureIntervalDays)
    }

    @Test
    public fun dueRelearningRepeatWithEmptyReviewStepsCanGraduateOnPass() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var relearning: RecordsStudyModels.StudyItem = reviewItem("習", RecordsBase.LadderRung.KANJI_MEANING, 1_000L)
                .copyBuilder()
                .state("learning")
                .learningStep(0)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .activeToken("relearn-token")
                .build()
        var learningSteps: RecordsSchedulerModels.LearningStepSettings = RecordsSchedulerModels.LearningStepSettings(
                listOf(1, 10),
                emptyList()
        )

        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                relearning,
                RecordsSchedulerModels.ReviewRequest("習", "relearn-token", "good", false, false, false, 0),
                HashSet(),
                2_000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                learningSteps,
                RecordsBase.StudyLadderSettings.defaults()
        )

        assertEquals("good", result.appliedRating)
        assertEquals("review", result.item.state)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase)
        assertEquals(0, result.item.learningStep)
        assertTrue(result.item.dueAtMillis > 2_000L)
    }

    @Test
    public fun nextSessionUsesWeaknessAndKanjiTieBreakersForSamePriorityDueItems() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var lowerWeakness: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var higherWeakness: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var firstKanji: RecordsStudyModels.StudyItem = reviewItem("亜", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var laterKanji: RecordsStudyModels.StudyItem = reviewItem("唖", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var weaknessSession: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(lowerWeakness, higherWeakness),
                listOf(row("謎", 10), row("裂", 80)),
                1000L
        )!!
        var kanjiSession: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(laterKanji, firstKanji),
                listOf(row("唖", 20), row("亜", 20)),
                1000L
        )!!
        assertNotNull(weaknessSession)
        assertEquals("裂", weaknessSession.item!!.kanji)
        assertNotNull(kanjiSession)
        assertEquals("亜", kanjiSession.item!!.kanji)
    }

    @Test
    public fun seedQueueRetiresItemsMissingFromDashboardRows() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                1,
                3000,
                1,
                2
        )
        var stale: RecordsStudyModels.StudyItem = item("古")
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("裂", 30)),
                listOf(stale),
                settings,
                1000L,
                0L
        )
        assertEquals("new", findItem(items, "裂").state)
        assertEquals("retired", findItem(items, "古").state)
    }

    @Test
    public fun seedQueueRetiresReviewedItemsWithEnoughMatureSupport() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var reviewed: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 0L)
                .copyBuilder()
                .totalReviews(3)
                .stability(1.5)
                .build()
        var covered: RecordsImportModels.DashboardRow = RecordsImportModels.DashboardRow("裂", 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2, ArrayList<RecordsImportModels.Example>())
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(covered),
                listOf(reviewed),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L
        )
        assertEquals("retired", findItem(items, "裂").state)
    }

    @Test
    public fun seedQueueRetiresUnreviewedItemsWithEnoughMatureSupport() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var unreviewed: RecordsStudyModels.StudyItem = item("裂")
        var covered: RecordsImportModels.DashboardRow = RecordsImportModels.DashboardRow("裂", 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2, ArrayList<RecordsImportModels.Example>())
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(covered),
                listOf(unreviewed),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L
        )
        assertEquals("retired", findItem(items, "裂").state)
    }

    @Test
    public fun seedQueueReopensRetiredItemsWhenWeakEvidenceReturns() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var retired: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .state("retired")
                .stability(1.5)
                .difficulty(4.0)
                .totalReviews(3)
                .learningStep(2)
                .writingLevel(2)
                .rung(RecordsBase.LadderRung.WORD_READING)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("裂", 30)),
                listOf(retired),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L
        )
        var reopened: RecordsStudyModels.StudyItem = findItem(items, "裂")
        assertEquals("review", reopened.state)
        assertEquals(3, reopened.totalReviews)
        assertEquals(retired.createdAtMillis, reopened.createdAtMillis)
        assertEquals(RecordsBase.LadderRung.WORD_READING, reopened.rung)
    }

    @Test
    public fun retiredItemsReopenWithoutConsumingAdmissionRoom() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var active: RecordsStudyModels.StudyItem = item("謎").copyBuilder()
                .createdAtMillis(0L)
                .build()
        var retired: RecordsStudyModels.StudyItem = reviewItem(
                "裂",
                RecordsBase.LadderRung.KANJI_MEANING,
                0L
        ).copyBuilder()
                .state("retired")
                .totalReviews(3)
                .build()
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("謎", 20), row("裂", 30)),
                listOf(active, retired),
                settingsWithQueue(1, 3),
                1000L,
                500L
        )
        var reopened: RecordsStudyModels.StudyItem = findItem(items, "裂")
        assertEquals("review", reopened.state)
        assertEquals(3, reopened.totalReviews)
    }

    @Test
    public fun seedExtraNewCardsAddsRequestedCardsBeyondDailyNewCap() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var active: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .createdAtMillis(1000L)
                .build()
        var result: BridgeScheduler.ExtraNewCardsResult = scheduler.seedExtraNewCards(
                listOf(row("裂", 50), row("謎", 40), row("示", 30), row("浸", 20)),
                listOf(active),
                settingsWithQueue(4, 1),
                2000L,
                0L,
                2
        )
        assertEquals(2, result.admittedCount)
        assertTrue(result.admittedAny())
        assertEquals(3, result.availableCount)
        assertEquals(listOf("謎", "示"), result.admittedKanji)
        assertEquals("new", findItem(result.items, "謎").state)
        assertEquals("new", findItem(result.items, "示").state)
        assertEquals("new", findItem(result.items, "裂").state)
        assertFalse(result.admittedKanji.contains("裂"))
    }

    @Test
    public fun seedExtraNewCardsClampsToRemainingCandidatesAndReopensRetiredItems() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var active: RecordsStudyModels.StudyItem = item("謎")
        var retired: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .state("retired")
                .totalReviews(3)
                .build()
        var result: BridgeScheduler.ExtraNewCardsResult = scheduler.seedExtraNewCards(
                listOf(row("裂", 50), row("謎", 40), row("示", 30)),
                listOf(active, retired),
                settingsWithQueue(2, 1),
                2000L,
                0L,
                5
        )
        assertEquals(2, result.availableCount)
        assertEquals(2, result.admittedCount)
        assertEquals(listOf("裂", "示"), result.admittedKanji)
        assertEquals("new", findItem(result.items, "裂").state)
        assertNull(findItem(result.items, "裂").activeToken)
        assertEquals("new", findItem(result.items, "示").state)
        assertEquals("new", findItem(result.items, "謎").state)
    }

    @Test
    public fun seedExtraNewCardsReportsNoAdmissionWhenRequestIsZero() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var result: BridgeScheduler.ExtraNewCardsResult = scheduler.seedExtraNewCards(
                listOf(row("裂", 50), row("謎", 40)),
                emptyList(),
                settingsWithQueue(4, 1),
                2000L,
                0L,
                0
        )
        assertEquals(0, result.admittedCount)
        assertFalse(result.admittedAny())
        assertEquals(2, result.availableCount)
        assertTrue(result.admittedKanji.isEmpty())
    }

    @Test
    public fun countExtraNewCardsAvailableMatchesSeededAvailability() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var active: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .createdAtMillis(1000L)
                .build()
        var count: Int = scheduler.countExtraNewCardsAvailable(
                listOf(row("裂", 50), row("謎", 40), row("示", 30), row("浸", 20)),
                listOf(active),
                settingsWithQueue(4, 1),
                2000L,
                0L
        )
        assertEquals(3, count)
    }

    @Test
    public fun seedExtraNewCardsHonorsConfiguredNewCardSortMode() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var rows: List<RecordsImportModels.DashboardRow> = listOf(
                rankedRow("低", 300, 40, example("低", 3.0, 0.60)),
                rankedRow("難", 100, 20, example("難", 8.0, 0.90)),
                rankedRow("弱", 200, 80, example("弱", null, 45.0))
        )
        assertEquals(listOf("難", "弱", "低"), scheduler.seedExtraNewCards(
                rows,
                emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                2000L,
                0L,
                3
        ).admittedKanji)
        assertEquals(listOf("難", "低", "弱"), scheduler.seedExtraNewCards(
                rows,
                emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
                2000L,
                0L,
                3
        ).admittedKanji)
        assertEquals(listOf("弱", "低", "難"), scheduler.seedExtraNewCards(
                rows,
                emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                2000L,
                0L,
                3
        ).admittedKanji)
        assertEquals(listOf("弱", "低", "難"), scheduler.seedExtraNewCards(
                rows,
                emptyList(),
                settingsWithSortMode(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
                2000L,
                0L,
                3
        ).admittedKanji)
    }

    @Test
    public fun nextSessionUsesNewCardSortOnlyForUnseenNewCards() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var difficultySort: RecordsSyncModels.Settings = settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(
                rankedRow("低", 300, 90, example("低", 3.0, 0.60)),
                rankedRow("難", 100, 10, example("難", 8.0, 0.90))
        )
        var result: BridgeScheduler.ExtraNewCardsResult = scheduler.seedExtraNewCards(
                rows,
                emptyList(),
                difficultySort,
                2000L,
                0L,
                2
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(result.items, rows, 2000L, 0L, null, difficultySort)!!
        assertNotNull(session)
        assertEquals("難", session.item!!.kanji)
    }

    @Test
    public fun coreSessionSelectionHidesSameFamilyWithoutPermanentSuppression() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var word: RecordsStudyModels.StudyItem = matureReview("裂", RecordsBase.LadderRung.WORD_READING)
                .withAnswerSignature("裂|裂ける|さける|split")
        var kanji: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|裂ける|さける|split")
        var items: List<RecordsStudyModels.StudyItem> = listOf(kanji, word)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "裂ける", "さける", "split", "", false, 0)
        ))
        var active: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(items, rows, 1000L, null)
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(items, rows, 1000L)!!
        assertEquals(1, active.size)
        assertEquals(RecordsBase.LadderRung.WORD_READING, active.get(0).rung)
        assertEquals(1, scheduler.dueCount(items, rows, 1000L))
        assertNotNull(session)
        assertEquals(RecordsBase.LadderRung.WORD_READING, session.item!!.rung)
    }

    @Test
    public fun immaturePromotedSiblingHidesLowerFamilyWithoutPermanentSuppression() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var immatureWord: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 0L)
                .copyBuilder()
                .matureIntervalDays(7)
                .totalReviews(2)
                .build()
                .withAnswerSignature("裂|裂ける|さける|split")
        var kanji: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|裂ける|さける|split")
        var rows: List<RecordsImportModels.DashboardRow> = listOf(rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "裂ける", "さける", "split", "", false, 0)
        ))
        var active: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(listOf(kanji, immatureWord), rows, 1000L, null)
        assertEquals(1, active.size)
        assertEquals(RecordsBase.LadderRung.WORD_READING, active.get(0).rung)
        assertTrue(active.get(0).suppressedByTaskType.isEmpty())
    }

    @Test
    public fun adaptivePlanLimitsNewAdmissionsToFocusSet() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var plan: RecordsSchedulerModels.AdaptiveLoadPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
                20,
                1,
                1,
                listOf("謎"),
                1,
                false,
                "focus"
        )
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("裂", 50), row("謎", 10)),
                emptyList(),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L,
                plan
        )
        assertEquals(1, items.size)
        assertEquals("謎", items.get(0).kanji)
    }

    @Test
    public fun adaptiveFocusCanonicalizesDuplicateRowsToOneKanjiItem() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var plan: RecordsSchedulerModels.AdaptiveLoadPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
                20,
                2,
                2,
                listOf("古", "裂"),
                2,
                false,
                "focus"
        )
        var firstFamily: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "古い", "ふるい", "old", "", false, 0)
        )
        var secondFamily: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                25,
                RecordsImportModels.Example("active", 2L, 2L, "新しい", "あたらしい", "new", "", false, 0)
        )
        var ambiguousLegacy: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|stale|stale|stale")
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(firstFamily, secondFamily),
                listOf(ambiguousLegacy),
                settingsWithQueue(10, 2),
                1000L,
                0L,
                plan
        )
        assertEquals(1, items.size)
        assertEquals("裂", items[0].kanji)
        assertEquals(StudyQueueSeeder.answerSignature(secondFamily), items[0].answerSignature)
    }

    @Test
    public fun allKanjiAdaptivePlanBypassesDeckNewCardLimit() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var plan: RecordsSchedulerModels.AdaptiveLoadPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
                100,
                3,
                3,
                listOf("裂", "謎", "示"),
                3,
                true,
                "all"
        )
        var settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                21,
                2,
                3000,
                1,
                1
        )

        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("裂", 50), row("謎", 10), row("示", 5)),
                emptyList(),
                settings,
                1000L,
                0L,
                plan
        )

        assertEquals(1, settings.newPerDay)
        assertEquals(3, items.size)
    }

    @Test
    public fun nextSessionSkipsItemsWithoutCurrentDashboardRows() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var stale: RecordsStudyModels.StudyItem = item("古")
        var current: RecordsStudyModels.StudyItem = item("裂").copyBuilder().dueAtMillis(500L).build()
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(stale, current),
                listOf(row("裂", 30)),
                1000L
        )!!
        assertNotNull(session)
        assertEquals("裂", session.item!!.kanji)
    }

    @Test
    public fun reseedPreservesExistingProgressForSameKanji() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var learned: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .state("learning")
                .dueAtMillis(1234L)
                .stability(1.2)
                .difficulty(4.4)
                .totalReviews(2)
                .lapses(1)
                .learningStep(1)
                .writingLevel(2)
                .activeToken("active")
                .createdAtMillis(55L)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("裂", 30)),
                listOf(learned),
                RecordsSyncModels.Settings.kikuDefaults(),
                2000L,
                0L
        )
        var item: RecordsStudyModels.StudyItem = findItem(items, "裂")
        assertEquals("learning", item.state)
        assertEquals(1234L, item.dueAtMillis)
        assertEquals(2, item.totalReviews)
        assertEquals(1, item.lapses)
        assertEquals(2, item.writingLevel)
        assertEquals("active", item.activeToken)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, item.phase)
    }

    @Test
    public fun latestFsrsUsesLastReviewElapsedDaysForOnTimeReviews() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var dueAt: Long = 30L * BridgeScheduler.DAY
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .build()
                .withToken("latest")
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item,
                RecordsSchedulerModels.ReviewRequest("裂", "latest", "good", false, false, false, 0),
                HashSet(),
                dueAt
        )
        // matureIntervalDays ceils the real interval, which under FSRS-7 is fractional:
        // ~17.76 days here, so 18. The due time keeps the fraction rather than snapping
        // to the whole-day multiple it used to equal exactly.
        assertEquals(18, result.item.matureIntervalDays)
        assertTrue(
            "due time should keep FSRS-7's fractional interval",
            result.item.dueAtMillis > dueAt + 17L * BridgeScheduler.DAY &&
                result.item.dueAtMillis < dueAt + 18L * BridgeScheduler.DAY,
        )
        // Persistence keeps full FSRS precision (it was once rounded to 2 dp).
        assertEquals(12.3806, result.item.stability, 0.01)
        assertEquals(5.968, result.item.difficulty, 0.01)
    }

    @Test
    public fun reviewTransitionPassesOverdueElapsedDaysToFsrs() {
        var adapter: RecordingFsrsAdapter = RecordingFsrsAdapter(3L * BridgeScheduler.DAY)
        var scheduler: BridgeScheduler = BridgeScheduler(adapter)
        var now: Long = 40L * BridgeScheduler.DAY
        var dueAt: Long = now - 2L * BridgeScheduler.DAY
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .activeToken("overdue")
                .build()
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item,
                RecordsSchedulerModels.ReviewRequest("裂", "overdue", "good", false, false, false, 0),
                HashSet(),
                now
        )
        assertEquals(9.0, adapter.elapsedDays, 0.0)
        assertEquals(now + 3L * BridgeScheduler.DAY, result.item.dueAtMillis)
        assertEquals(3, result.item.matureIntervalDays)
    }

    @Test
    public fun reviewTransitionPassesFractionalElapsedDaysToFsrs() {
        var adapter: RecordingFsrsAdapter = RecordingFsrsAdapter(4L * BridgeScheduler.DAY)
        var scheduler: BridgeScheduler = BridgeScheduler(adapter)
        var halfDay: Long = BridgeScheduler.DAY / 2L
        var now: Long = 40L * BridgeScheduler.DAY + halfDay
        var dueAt: Long = now - 2L * BridgeScheduler.DAY - halfDay
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .kanjiMeaningMemory(taskMemory)
                .activeToken("fractional")
                .build()
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item,
                RecordsSchedulerModels.ReviewRequest("裂", "fractional", "good", false, false, false, 0),
                HashSet(),
                now
        )
        // 9.5, not 9. This test used to assert the floor; FSRS-7 takes fractional
        // days, and dropping the half day here would discard the sub-day resolution
        // at the boundary rather than in the engine.
        assertEquals(9.5, adapter.elapsedDays, 0.0)
        assertEquals(now + 4L * BridgeScheduler.DAY, result.item.dueAtMillis)
        assertEquals(4, result.item.matureIntervalDays)
    }

    @Test
    public fun reviewTransitionPrefersExactLastReviewTimeOverRoundedIntervalInference() {
        var adapter: RecordingFsrsAdapter = RecordingFsrsAdapter(4L * BridgeScheduler.DAY)
        var scheduler: BridgeScheduler = BridgeScheduler(adapter)
        var now: Long = 40L * BridgeScheduler.DAY
        var exactLastReview: Long = now - 7L * BridgeScheduler.DAY - BridgeScheduler.DAY / 2L
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                now,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                9,
                0,
                0L,
                exactLastReview
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, now)
                .copyBuilder()
                .kanjiMeaningMemory(taskMemory)
                .activeToken("exact-last-review")
                .build()

        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item,
                RecordsSchedulerModels.ReviewRequest("裂", "exact-last-review", "good", false, false, false, 0),
                HashSet(),
                now
        )

        assertEquals(7.5, adapter.elapsedDays, 0.0)
        assertEquals(now, result.item.kanjiMeaningMemory.lastReviewedAtMillis)
    }

    @Test
    public fun relearningGraduationPreservesPostLapseStability() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var dueAt: Long = 30L * BridgeScheduler.DAY
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                .build()
        var consumed: HashSet<String> = HashSet()
        var lapsed: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item.withToken("lapse"),
                RecordsSchedulerModels.ReviewRequest("裂", "lapse", "again", false, false, false, 0),
                consumed,
                dueAt
        )
        var postLapseStability: Double = lapsed.item.stability
        var graduated: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                lapsed.item.withToken("graduate"),
                RecordsSchedulerModels.ReviewRequest("裂", "graduate", "good", false, false, false, 0),
                consumed,
                lapsed.item.dueAtMillis
        )
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, graduated.item.phase)
        assertEquals(postLapseStability, graduated.item.stability, 0.0)
        assertEquals(postLapseStability, graduated.item.kanjiMeaningMemory.stability, 0.0)
    }

    @Test
    public fun activeRungInitialMemoryFallsBackToItemFsrsState() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var dueAt: Long = 30L * BridgeScheduler.DAY
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .build()
                .withToken("fallback")
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item,
                RecordsSchedulerModels.ReviewRequest("裂", "fallback", "good", false, false, false, 0),
                HashSet(),
                dueAt
        )
        assertEquals(18, result.item.matureIntervalDays)
        assertTrue(
            "due time should keep FSRS-7's fractional interval",
            result.item.dueAtMillis > dueAt + 17L * BridgeScheduler.DAY &&
                result.item.dueAtMillis < dueAt + 18L * BridgeScheduler.DAY,
        )
        assertEquals(18, result.item.fontMeaningMemory.matureIntervalDays)
    }

    @Test
    public fun promotionUpdatesNewActiveRungMemory() {
        var scheduler: BridgeScheduler = schedulerWithReviewIntervalDays(22)
        var consumed: HashSet<String> = HashSet()
        var dueAt: Long = 30L * BridgeScheduler.DAY
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .stability(5.0)
                .difficulty(6.0)
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                // Prime one prior real-due pass so this pass clears the default
                // two-pass promotion gate (Goal 63).
                .realPassStreak(1)
                .build()
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item.withToken("promote"),
                RecordsSchedulerModels.ReviewRequest("裂", "promote", "good", false, false, false, 0),
                consumed,
                item.dueAtMillis
        )
        item = result.item
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, item.rung)
        assertEquals(AdaptiveStudyItemPolicy.ROUTING_VERSION, item.routingVersion)
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(item)!!.activeCore)
        assertEquals(0, item.realPassStreak)
        // The legacy promotion still landed on font_meaning before lazy
        // conversion. Its updated memory remains in that compatibility slot
        // and is copied into the canonical recognition owner.
        assertEquals(item.dueAtMillis, item.fontMeaningMemory.dueAtMillis)
        assertEquals(item.matureIntervalDays, item.fontMeaningMemory.matureIntervalDays)
        assertEquals(item.totalReviews, item.fontMeaningMemory.totalReviews)
        assertEquals(item.fontMeaningMemory.encode(), item.kanjiMeaningMemory.encode())
    }

    @Test
    public fun fsrsPromotionBoundaryRequiresMoreThanConfiguredDays() {
        var dueAt: Long = 30L * BridgeScheduler.DAY
        var taskMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory(
                "review",
                dueAt,
                5.0,
                6.0,
                4,
                0,
                0,
                "good",
                7
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
                .copyBuilder()
                .totalReviews(4)
                .matureIntervalDays(7)
                .kanjiMeaningMemory(taskMemory)
                // Prime one prior real-due pass so the beyond-boundary pass
                // clears the default two-pass promotion gate (Goal 63).
                .realPassStreak(1)
                .build()
        var exactBoundary: RecordsSchedulerModels.ReviewResult = schedulerWithReviewIntervalDays(21).applyReview(
                item.withToken("exact-boundary"),
                RecordsSchedulerModels.ReviewRequest("裂", "exact-boundary", "good", false, false, false, 0),
                HashSet(),
                dueAt
        )
        var beyondBoundary: RecordsSchedulerModels.ReviewResult = schedulerWithReviewIntervalDays(22).applyReview(
                item.withToken("beyond-boundary"),
                RecordsSchedulerModels.ReviewRequest("裂", "beyond-boundary", "good", false, false, false, 0),
                HashSet(),
                dueAt
        )
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, exactBoundary.item.rung)
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(exactBoundary.item)!!.activeCore)
        assertEquals(21, exactBoundary.item.matureIntervalDays)
        assertEquals(dueAt + 21L * BridgeScheduler.DAY, exactBoundary.item.dueAtMillis)
        assertEquals(0, exactBoundary.item.fontMeaningMemory.totalReviews)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, beyondBoundary.item.rung)
        assertEquals(CoreSkill.RECOGNITION, AdaptiveStudyItemPolicy.routeState(beyondBoundary.item)!!.activeCore)
        // The promoted rung's first review is capped at promotionDays / 3 (7 days).
        assertEquals(7, beyondBoundary.item.matureIntervalDays)
        assertEquals(dueAt + 7L * BridgeScheduler.DAY, beyondBoundary.item.dueAtMillis)
        assertEquals(7, beyondBoundary.item.fontMeaningMemory.matureIntervalDays)
        assertEquals(
                beyondBoundary.item.fontMeaningMemory.encode(),
                beyondBoundary.item.kanjiMeaningMemory.encode()
        )
    }

    @Test
    public fun customParametersAffectReviewInterval() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var matureMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory("review", 0L, 10.0, 5.0, 5, 0, 0, "good", 10)
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .stability(10.0)
                .difficulty(5.0)
                .totalReviews(5)
                .learningStep(0)
                .kanjiMeaningMemory(matureMemory)
                .build()
                .withToken("token-1")
        var request: RecordsSchedulerModels.ReviewRequest = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", false, false, false, 0)
        var highRetention: RecordsSchedulerModels.SchedulerParameters = RecordsSchedulerModels.SchedulerParameters(0.95)
        var lowRetention: RecordsSchedulerModels.SchedulerParameters = RecordsSchedulerModels.SchedulerParameters(0.80)
        var highResult: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(item, request, HashSet(), 1000L, highRetention)
        var lowResult: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(item, request, HashSet(), 1000L, lowRetention)
        assertTrue(lowResult.item.dueAtMillis > highResult.item.dueAtMillis)
    }

    @Test
    public fun allowedKanjiFilterExcludesSuspendedKanjiFromActiveReview() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var suspended: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var active: RecordsStudyModels.StudyItem = reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var allowed: Set<String> = HashSet(listOf("提"))
        var activeItems: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(suspended, active),
                listOf(row("裂", 30), row("提", 20)),
                1000L,
                allowed
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(suspended, active),
                listOf(row("裂", 30), row("提", 20)),
                1000L,
                allowed
        )!!
        assertEquals(1, activeItems.size)
        assertEquals("提", activeItems.get(0).kanji)
        assertNotNull(session)
        assertEquals("提", session.item!!.kanji)
    }

    @Test
    public fun nullAdaptivePlanUsesDefaultSeedingPath() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row("裂", 30)),
                emptyList<RecordsStudyModels.StudyItem>(),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L,
                plan = null
        )
        assertEquals(1, items.size)
        assertEquals("裂", items.get(0).kanji)
    }

    @Test
    public fun seedQueueAlignsLegacyEmptySignatureToSuspendedExample() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var legacy: RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem("裂", "review", 1234L, 2.0, 4.0, 2, 0, 2, 1, null, 55L)
                .withAnswerSignature("")
        var row: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "破裂", "はれつ", "burst", "", false, 0),
                RecordsImportModels.Example("suspended", 2L, 2L, "裂ける", "さける", "split", "", true, 0)
        )
        var items: List<RecordsStudyModels.StudyItem> = scheduler.seedQueue(
                listOf(row),
                listOf(legacy),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        )
        var aligned: RecordsStudyModels.StudyItem = findItem(items, "裂")
        assertEquals("review", aligned.state)
        assertEquals(1234L, aligned.dueAtMillis)
        assertEquals("裂|裂ける|さける|split", aligned.answerSignature)
    }

    @Test
    public fun seedQueueKeepsMatchingAnswerSignatureProgress() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var current: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("裂|裂ける|さける|split")
        var row: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("suspended", 2L, 2L, "裂ける", "さける", "split", "", true, 0)
        )
        var aligned: RecordsStudyModels.StudyItem = findItem(scheduler.seedQueue(
                listOf(row),
                listOf(current),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂")
        assertEquals("review", aligned.state)
        assertEquals(1234L, aligned.dueAtMillis)
    }

    @Test
    public fun seedQueueUsesFirstExampleFallbackAndNormalizesSignatureParts() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var legacy: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("")
        var row: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("other", 1L, 1L, "  split   apart  ", null, " main   meaning ", "", false, 0)
        )
        var aligned: RecordsStudyModels.StudyItem = findItem(scheduler.seedQueue(
                listOf(row),
                listOf(legacy),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂")
        assertEquals("裂|split apart||main meaning", aligned.answerSignature)
    }

    @Test
    public fun seedQueueKeepsFirstActiveExampleWhenNoSuspendedExampleExists() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var legacy: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1234L)
                .withAnswerSignature("")
        var row: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "first", "one", "meaning one", "", false, 0),
                RecordsImportModels.Example("active", 2L, 2L, "second", "two", "meaning two", "", false, 0)
        )
        var aligned: RecordsStudyModels.StudyItem = findItem(scheduler.seedQueue(
                listOf(row),
                listOf(legacy),
                RecordsSyncModels.Settings.kikuDefaults(),
                5000L,
                0L
        ), "裂")
        assertEquals("裂|first|one|meaning one", aligned.answerSignature)
    }

    @Test
    public fun reseedResetsNonRetiredItemWhenAnswerSignatureChanges() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var oldMeaningMemory: RecordsStudyModels.TaskMemory = RecordsStudyModels.TaskMemory("review", 5000L, 2.0, 4.5, 7, 1, 2, "good", 12)
        var learned: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING, 5000L)
                .copyBuilder()
                .answerSignature("裂|old|old|old")
                .activeToken("active")
                .meaningKanjiMemory(oldMeaningMemory)
                .realPassStreak(2)
                .realAgainStreak(1)
                .lastRealReviewDueAtMillis(123L)
                .build()
        var changed: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "新しい", "あたらしい", "new", "", false, 0)
        )
        var reset: RecordsStudyModels.StudyItem = findItem(scheduler.seedQueue(
                listOf(changed),
                listOf(learned),
                RecordsSyncModels.Settings.kikuDefaults(),
                2000L,
                0L
        ), "裂")
        assertEquals("learning", reset.state)
        assertEquals(2000L, reset.dueAtMillis)
        assertEquals(0, reset.totalReviews)
        assertNull(reset.activeToken)
        assertEquals("裂|新しい|あたらしい|new", reset.answerSignature)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, reset.rung)
        assertEquals(0, reset.realPassStreak)
        assertEquals(0, reset.lastRealReviewDueAtMillis)
        assertEquals("new", reset.meaningKanjiMemory.state)
        assertEquals(0, reset.meaningKanjiMemory.totalReviews)
    }

    @Test
    public fun reseedRetiredItemPreservesProgressWhenExampleChangesWithoutMeaningChange() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var retired: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 5000L)
                .copyBuilder()
                .state("retired")
                .answerSignature("裂|old|old|new")
                .build()
        var changed: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "新しい", "あたらしい", "new", "", false, 0)
        )
        var updated: RecordsStudyModels.StudyItem = findItem(scheduler.seedQueue(
                listOf(changed),
                listOf(retired),
                RecordsSyncModels.Settings.kikuDefaults(),
                2000L,
                0L
        ), "裂")
        assertEquals("review", updated.state)
        assertEquals("裂|新しい|あたらしい|new", updated.answerSignature)
        assertEquals(retired.totalReviews, updated.totalReviews)
    }

    @Test
    public fun activeQueueGroupsByFamilyAndPrefersHigherRung() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var kanji: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withAnswerSignature("裂|expr|read|meaning")
        var word: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.WORD_READING, 5000L).withAnswerSignature("裂|expr|read|meaning")
        var legacyEmptySignature: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L).withAnswerSignature("")
        var active: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(kanji, word, legacyEmptySignature),
                listOf(rowWithExamples("裂", 30, RecordsImportModels.Example("active", 1L, 1L, "expr", "read", "meaning", "", false, 0)), row("謎", 20)),
                1000L,
                null
        )
        assertEquals(2, active.size)
        assertEquals("謎", findItem(active, "謎").kanji)
        assertEquals(RecordsBase.LadderRung.WORD_READING, findItem(active, "裂").rung)
    }

    @Test
    public fun activeQueueRejectsChangedNonEmptyAnswerSignature() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var staleFamily: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withAnswerSignature("裂|old|read|meaning")
        var currentRow: RecordsImportModels.DashboardRow = rowWithExamples(
                "裂",
                30,
                RecordsImportModels.Example("active", 1L, 1L, "new", "read", "meaning", "", false, 0)
        )
        var active: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(staleFamily),
                listOf(currentRow),
                1000L,
                null
        )
        assertTrue(active.isEmpty())
    }

    @Test
    public fun nextSessionReusesActiveTokenAndGeneratesForEmptyToken() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var reused: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("kept")),
                listOf(row("裂", 30)),
                1000L
        )!!
        var generated: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 0L).withToken("")),
                listOf(row("謎", 30)),
                1000L
        )!!
        assertEquals("kept", reused.token)
        assertTrue(generated.token.startsWith("謎-"))
    }

    @Test
    public fun nextSessionReturnsNullWhenNothingDueOrAllowed() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var future: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 2000L)
        assertNull(scheduler.nextSession(listOf(future), listOf(row("裂", 30)), 1000L))
        assertNull(scheduler.nextSession(
                listOf(item("裂")),
                listOf(row("裂", 30)),
                1000L,
                setOf("提")
        ))
    }

    @Test
    public fun tokenMismatchAndNullReviewInputsStaySafe() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var active: RecordsStudyModels.StudyItem = item("裂").withToken("expected")
        var duplicate: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                active,
                RecordsSchedulerModels.ReviewRequest("裂", "actual", "easy", false, false, false, 0),
                HashSet(),
                1000L,
                null,
                null
        )
        var normalized: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item("提"),
                RecordsSchedulerModels.ReviewRequest("提", "token", null, false, false, false, 0),
                HashSet(),
                1000L,
                null,
                null
        )
        assertTrue(duplicate.duplicate)
        assertEquals("again", normalized.appliedRating)
        assertEquals("learning", normalized.item.state)
        var emptyToken: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item("空").withToken(""),
                RecordsSchedulerModels.ReviewRequest("空", "token", "easy", false, false, false, 0),
                HashSet(),
                1000L
        )
        assertFalse(emptyToken.duplicate)
    }

    @Test
    public fun invalidRatingDefaultsToAgainAndResetsLearningStep() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var item: RecordsStudyModels.StudyItem = item("裂").copyBuilder().learningStep(1).build()
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item.withToken("bad-rating"),
                RecordsSchedulerModels.ReviewRequest("裂", "bad-rating", "perfect", false, false, false, 0),
                HashSet(),
                1000L
        )
        assertEquals("again", result.appliedRating)
        assertEquals("learning", result.item.state)
        assertEquals(0, result.item.learningStep)
    }

    @Test
    public fun relearningGoodCanAdvanceWithoutGraduating() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var relearning: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder()
                .state("learning")
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .learningStep(0)
                .activeToken("relearn")
                .build()
        var steps: RecordsSchedulerModels.LearningStepSettings = RecordsSchedulerModels.LearningStepSettings(
                RecordsSchedulerModels.LearningStepSettings.defaultNewSteps(),
                listOf(5, 20)
        )
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                relearning,
                RecordsSchedulerModels.ReviewRequest("裂", "relearn", "good", false, false, false, 0),
                HashSet(),
                1000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                steps
        )
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, result.item.phase)
        assertEquals(1, result.item.learningStep)
    }

    @Test
    public fun learningHardRepeatsLaterStep() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var learning: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .state("learning")
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .learningStep(1)
                .activeToken("hard")
                .build()
                .withTaskMemory(
                        BridgeScheduler.TASK_KANJI_MEANING,
                        RecordsStudyModels.TaskMemory("learning", 0L, 0.4, 5.0, 1, 0, 1, "", 0)
                )
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                learning,
                RecordsSchedulerModels.ReviewRequest("裂", "hard", "hard", false, false, false, 0),
                HashSet(),
                1000L
        )
        assertEquals(1, result.item.learningStep)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, result.item.phase)
    }

    @Test
    public fun learningHardOnSingleStepWaitsOneAndAHalfSteps() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var learning: RecordsStudyModels.StudyItem = item("裂").copyBuilder()
                .state("learning")
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .activeToken("hard")
                .build()
        var steps: RecordsSchedulerModels.LearningStepSettings = RecordsSchedulerModels.LearningStepSettings(
                listOf(5),
                RecordsSchedulerModels.LearningStepSettings.defaultReviewSteps()
        )
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                learning,
                RecordsSchedulerModels.ReviewRequest("裂", "hard", "hard", false, false, false, 0),
                HashSet(),
                1000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                steps
        )
        assertEquals(0, result.item.learningStep)
        // Anki semantics: Hard on a single learning step waits 1.5x the step.
        assertEquals(1000L + 5L * 60_000L * 3L / 2L, result.item.dueAtMillis)
    }

    @Test
    public fun futureReviewAgainDoesNotCountAsRealDueFailure() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var future: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 5000L)
                .withToken("future")
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                future,
                RecordsSchedulerModels.ReviewRequest("裂", "future", "again", false, false, false, 0),
                HashSet(),
                1000L
        )
        assertEquals(0, result.item.realAgainStreak)
        assertEquals(0L, result.item.lastRealReviewDueAtMillis)
    }

    @Test
    public fun practicedLearningCardsSortBeforeDueReviews() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var practicedLearning: RecordsStudyModels.StudyItem = item("学").copyBuilder()
                .state("learning")
                .dueAtMillis(0L)
                .totalReviews(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
        var review: RecordsStudyModels.StudyItem = reviewItem("復", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(review, practicedLearning),
                listOf(row("復", 100), row("学", 1)),
                1000L
        )!!
        assertNotNull(session)
        assertEquals("学", session.item!!.kanji)
    }

    @Test
    public fun dueLearningRepeatIsNotHiddenByFutureHigherRungInSameFamily() {
        // Regression guard for the learning-repeat session pitfall: a future higher-rung task
        // for the same kanji must not hide the repeat that is due now.
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var practicedLearning: RecordsStudyModels.StudyItem = itemAtRung("学", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now)
                .totalReviews(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
        var futureHigherRung: RecordsStudyModels.StudyItem = reviewItem(
                "学",
                RecordsBase.LadderRung.FONT_MEANING,
                now + 60_000L
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(futureHigherRung, practicedLearning),
                listOf(row("学", 1)),
                now
        )!!
        assertNotNull(session)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, session.item!!.rung)
    }

    @Test
    public fun dueRelearningRepeatIsNotHiddenByFutureHigherRungInSameFamily() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var relearning: RecordsStudyModels.StudyItem = itemAtRung("復", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now)
                .totalReviews(5)
                .lapses(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()
        var futureHigherRung: RecordsStudyModels.StudyItem = reviewItem(
                "復",
                RecordsBase.LadderRung.FONT_MEANING,
                now + 60_000L
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(futureHigherRung, relearning),
                listOf(row("復", 1)),
                now
        )!!
        assertNotNull(session)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, session.item!!.rung)
    }

    @Test
    public fun ankiBuryOrderKeepsDueLearningRepeatAheadOfDueReviewSibling() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var learning: RecordsStudyModels.StudyItem = itemAtRung("学", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now)
                .totalReviews(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
        var dueReviewHigherRung: RecordsStudyModels.StudyItem = reviewItem(
                "学",
                RecordsBase.LadderRung.FONT_MEANING,
                now
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(dueReviewHigherRung, learning),
                listOf(row("学", 1)),
                now
        )!!
        assertNotNull(session)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, session.item!!.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, session.item.rung)
    }

    @Test
    public fun ankiBuryOrderKeepsDueRelearningRepeatAheadOfDueReviewSibling() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var relearning: RecordsStudyModels.StudyItem = itemAtRung("復", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now)
                .totalReviews(5)
                .lapses(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()
        var dueReviewHigherRung: RecordsStudyModels.StudyItem = reviewItem(
                "復",
                RecordsBase.LadderRung.FONT_MEANING,
                now
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(dueReviewHigherRung, relearning),
                listOf(row("復", 1)),
                now
        )!!
        assertNotNull(session)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, session.item!!.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, session.item.rung)
    }

    @Test
    public fun focusQueueItemsKeepDueRelearningRepeatAheadOfHigherReviewSibling() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var relearning: RecordsStudyModels.StudyItem = itemAtRung("復", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now)
                .totalReviews(5)
                .lapses(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()
                .withToken("relearning")
        var dueReviewHigherRung: RecordsStudyModels.StudyItem = reviewItem(
                "復",
                RecordsBase.LadderRung.FONT_MEANING,
                now
        ).withToken("review")
        var active: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(dueReviewHigherRung, relearning),
                listOf(row("復", 1)),
                now,
                0L,
                null,
                RecordsBase.StudyLadderSettings.defaults()
        )
        var focus: List<RecordsStudyModels.StudyItem> = scheduler.focusQueueItems(
                listOf(dueReviewHigherRung, relearning),
                listOf(row("復", 1)),
                now,
                0L,
                RecordsBase.StudyLadderSettings.defaults()
        )
        assertEquals(1, active.size)
        assertEquals("review", active.get(0).activeToken)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, active.get(0).rung)
        assertEquals(1, focus.size)
        assertEquals("relearning", focus.get(0).activeToken)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, focus.get(0).phase)
    }

    @Test
    public fun studyAheadLearningRepeatIsSelectedBeforeDueReviewSiblingInSameFamily() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var studyAhead: Long = 24 * 60 * 60_000L
        var interdayLearning: RecordsStudyModels.StudyItem = itemAtRung("間", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now + 12 * 60 * 60_000L)
                .totalReviews(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
        var dueReviewHigherRung: RecordsStudyModels.StudyItem = reviewItem(
                "間",
                RecordsBase.LadderRung.FONT_MEANING,
                now
        )
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(dueReviewHigherRung, interdayLearning),
                listOf(row("間", 1)),
                now,
                studyAhead,
                null
        )!!
        assertNotNull(session)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, session.item!!.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, session.item.rung)
    }

    @Test
    public fun ankiBuryOrderPrefersIntradayThenInterdayLearningThenReviewThenNewSiblings() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var studyAhead: Long = 24 * 60 * 60_000L
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("順", 1))
        var intradayLearning: RecordsStudyModels.StudyItem = itemAtRung("順", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now)
                .totalReviews(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
                .withToken("intraday-learning")
        var interdayLearning: RecordsStudyModels.StudyItem = itemAtRung("順", RecordsBase.LadderRung.KANJI_MEANING).copyBuilder()
                .state("learning")
                .dueAtMillis(now + 12 * 60 * 60_000L)
                .totalReviews(1)
                .learningStep(1)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
                .withToken("interday-learning")
        var dueReview: RecordsStudyModels.StudyItem = reviewItem(
                "順",
                RecordsBase.LadderRung.FONT_MEANING,
                now
        ).withToken("review")
        var newCard: RecordsStudyModels.StudyItem = itemAtRung("順", RecordsBase.LadderRung.WORD_READING)
                .withToken("new")

        assertEquals("intraday-learning", scheduler.nextSession(
                listOf(newCard, dueReview, interdayLearning, intradayLearning),
                rows,
                now,
                studyAhead,
                null
        )!!.item!!.activeToken)
        assertEquals("interday-learning", scheduler.nextSession(
                listOf(newCard, dueReview, interdayLearning),
                rows,
                now,
                studyAhead,
                null
        )!!.item!!.activeToken)
        assertEquals("review", scheduler.nextSession(
                listOf(newCard, dueReview),
                rows,
                now,
                studyAhead,
                null
        )!!.item!!.activeToken)
        assertEquals("new", scheduler.nextSession(
                listOf(newCard),
                rows,
                now,
                studyAhead,
                null
        )!!.item!!.activeToken)
    }

    @Test
    public fun activeQueueFiltersRetiredAndMissingRows() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var retired: RecordsStudyModels.StudyItem = item("古").copyBuilder().state("retired").build()
        var missing: RecordsStudyModels.StudyItem = item("消")
        var fontRung: RecordsStudyModels.StudyItem = itemAtRung("裂", RecordsBase.LadderRung.FONT_MEANING)
        var active: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(retired, missing, fontRung),
                listOf(row("裂", 30)),
                1000L,
                null
        )
        assertEquals(1, active.size)
        assertEquals("裂", active.get(0).kanji)
    }

    @Test
    public fun activeFamilyItemPrefersDueStatusThenEarlierDueTimeWithinRank() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var due: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1000L).withToken("due")
        var future: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 5000L).withToken("future")
        var earlierFuture: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 3000L).withToken("early")
        var laterFuture: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 5000L).withToken("late")
        var activeDue: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(future, due),
                listOf(row("裂", 30)),
                2000L,
                null
        )
        var activeFuture: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(laterFuture, earlierFuture),
                listOf(row("謎", 30)),
                1000L,
                null
        )
        assertEquals(1, activeDue.size)
        assertEquals("due", activeDue.get(0).activeToken)
        assertEquals(1, activeFuture.size)
        assertEquals("early", activeFuture.get(0).activeToken)
        var alreadyBest: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(
                listOf(due, future),
                listOf(row("裂", 30)),
                2000L,
                null
        )
        assertEquals("due", alreadyBest.get(0).activeToken)
    }

    @Test
    public fun rungsForItemSkipsSimilarOnlyWhenUnavailable() {
        var withoutSimilar: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
        var withSimilar: RecordsStudyModels.StudyItem = withoutSimilar.withHasSimilarKanji(true)
        var without: List<RecordsBase.LadderRung> = BridgeScheduler.rungsForItem(withoutSimilar)
        var with: List<RecordsBase.LadderRung> = BridgeScheduler.rungsForItem(withSimilar)
        assertFalse(without.contains(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertTrue(with.contains(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertTrue(without.contains(RecordsBase.LadderRung.MEANING_KANJI))
        assertTrue(with.contains(RecordsBase.LadderRung.MEANING_KANJI))
        assertTrue(with.indexOf(RecordsBase.LadderRung.TYPE_MEANING) < with.indexOf(RecordsBase.LadderRung.MEANING_KANJI))
        assertTrue(with.indexOf(RecordsBase.LadderRung.MEANING_KANJI) < with.indexOf(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, with.get(0))
        assertEquals(RecordsBase.LadderRung.WORD_READING, with.get(with.size - 1))
    }

    @Test
    public fun rungsForItemHonorsDisabledConfiguredRungs() {
        var withSimilar: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withHasSimilarKanji(true)
        var ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)
        var rungs: List<RecordsBase.LadderRung> = BridgeScheduler.rungsForItem(withSimilar, ladder)
        assertFalse(rungs.contains(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertFalse(rungs.contains(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, rungs.get(0))
    }

    @Test
    public fun nextSessionMapsDisabledCurrentRungToNearestEnabledRung() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)
        // New default order (Goal 65): with kanji_meaning disabled and
        // similar-kanji content present, the nearest enabled rung is the
        // similar_kanji rung directly below it.
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .withHasSimilarKanji(true)
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(item),
                listOf(row("裂", 20)),
                0L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        )!!
        assertNotNull(session)
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, session.item!!.rung)
        assertEquals(BridgeScheduler.TASK_SIMILAR_KANJI, session.taskType)
    }

    @Test
    public fun mappedMeaningKanjiReviewInheritsExistingSchedulerMemory() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        // New default order (Goal 65): kanji_meaning (index 4) sits above
        // similar_kanji; with kanji_meaning and font_meaning both disabled and
        // no similar-kanji content, the nearest enabled rung is meaning_kanji
        // (ties prefer the lower/more-scaffolded rung), so the review maps into
        // meaning_kanji memory.
        var ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)
                .withRungEnabled(RecordsBase.LadderRung.FONT_MEANING, false)
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 500L)
                .copyBuilder()
                .stability(2.5)
                .difficulty(4.2)
                .totalReviews(5)
                .matureIntervalDays(12)
                .build()
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(item),
                listOf(row("裂", 20)),
                1000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        )!!
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                session.item!!.withToken("meaning-pass"),
                RecordsSchedulerModels.ReviewRequest("裂", "meaning-pass", "good", false, false, false, 0),
                HashSet(),
                1000L,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        )
        assertEquals(6, result.item.meaningKanjiMemory.totalReviews)
    }

    @Test
    public fun customLadderOrderControlsPromotion() {
        var scheduler: BridgeScheduler = schedulerWithReviewIntervalDays(22)
        var consumed: HashSet<String> = HashSet()
        var ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings(
                listOf(
                        RecordsBase.LadderRung.WRITE_KANJI,
                        RecordsBase.LadderRung.KANJI_MEANING,
                        RecordsBase.LadderRung.WORD_READING,
                        RecordsBase.LadderRung.FONT_MEANING,
                        RecordsBase.LadderRung.TYPE_MEANING,
                        RecordsBase.LadderRung.SIMILAR_KANJI
                ),
                listOf(
                        RecordsBase.LadderRung.WRITE_KANJI,
                        RecordsBase.LadderRung.KANJI_MEANING,
                        RecordsBase.LadderRung.WORD_READING,
                        RecordsBase.LadderRung.FONT_MEANING,
                        RecordsBase.LadderRung.TYPE_MEANING,
                        RecordsBase.LadderRung.SIMILAR_KANJI
                )
        )
        var item: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L)
                .copyBuilder().realPassStreak(1).build()
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(
                item.withToken("custom-order"),
                RecordsSchedulerModels.ReviewRequest("裂", "custom-order", "good", false, false, false, 0),
                consumed,
                item.dueAtMillis,
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSyncModels.Settings.kikuDefaults(),
                ladder
        )
        item = result.item
        assertEquals(RecordsBase.LadderRung.WORD_READING, item.rung)
    }

    @Test
    public fun dueCountAndTokenSetCoverCollectionHelpers() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var tokens: Set<String> = scheduler.tokenSet(listOf("a", "b", "a"))
        assertEquals(2, tokens.size)
        assertEquals(1, scheduler.dueCount(
                listOf(
                        reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 0L),
                        reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, 0L).copyBuilder().state("retired").build(),
                        reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 2000L)
                ),
                listOf(row("裂", 30), row("提", 20), row("謎", 10)),
                1000L
        ))
    }

    @Test
    public fun studyAheadZeroMinutesMatchesBaselineActiveQueue() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var dueNow: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, 1000L)
        var inFiveMin: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, 1000L + 5L * 60_000L)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("裂", 30), row("謎", 20))
        var baseline: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(listOf(dueNow, inFiveMin), rows, 1000L, null)
        var zeroAhead: List<RecordsStudyModels.StudyItem> = scheduler.activeQueueItems(listOf(dueNow, inFiveMin), rows, 1000L, 0L, null)
        assertEquals(baseline.size, zeroAhead.size)
        assertNotNull(scheduler.nextSession(listOf(dueNow, inFiveMin), rows, 1000L, 0L, null))
    }

    @Test
    public fun studyAheadFifteenMinutesPullsItemDueWithinHorizon() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var dueIn10Min: Long = now + 10L * 60_000L
        var ahead: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, dueIn10Min)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("謎", 20))
        var none: RecordsSchedulerModels.StudySession? = scheduler.nextSession(listOf(ahead), rows, now)
        assertNull(none)
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(ahead), rows, now, 15L * 60_000L, null
        )!!
        assertNotNull(session)
        assertEquals("謎", session.item!!.kanji)
    }

    @Test
    public fun studyAheadFifteenMinutesExcludesItemBeyondHorizon() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var dueIn30Min: Long = now + 30L * 60_000L
        var beyond: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, dueIn30Min)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("謎", 20))
        var session: RecordsSchedulerModels.StudySession? = scheduler.nextSession(
                listOf(beyond), rows, now, 15L * 60_000L, null
        )
        assertNull(session)
    }

    @Test
    public fun studyAheadDueCountIncludesHorizonEligibleItems() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var dueNow: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, now)
        var dueIn5: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, now + 5L * 60_000L)
        var dueIn30: RecordsStudyModels.StudyItem = reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, now + 30L * 60_000L)
        var items: List<RecordsStudyModels.StudyItem> = listOf(dueNow, dueIn5, dueIn30)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("裂", 30), row("謎", 20), row("提", 10))
        assertEquals(1, scheduler.dueCount(items, rows, now))
        assertEquals(2, scheduler.dueCount(items, rows, now, 15L * 60_000L))
        assertEquals(3, scheduler.dueCount(items, rows, now, 60L * 60_000L))
    }

    @Test
    public fun studyAheadClampsNegativeToZeroAndAboveDayToDay() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        assertEquals(
                SettingsInputRules.MAX_STUDY_AHEAD_MINUTES.toLong() * 60_000L,
                StudyLadderRules.clampStudyAheadMillis(Long.MAX_VALUE)
        )
        var dueIn1Hour: Long = now + 60L * 60_000L
        var dueIn25Hours: Long = now + 25L * 60L * 60_000L
        var nearItem: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, dueIn1Hour)
        var farItem: RecordsStudyModels.StudyItem = reviewItem("提", RecordsBase.LadderRung.KANJI_MEANING, dueIn25Hours)
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("謎", 20), row("提", 10))
        var negative: RecordsSchedulerModels.StudySession? = scheduler.nextSession(
                listOf(nearItem), rows, now, -5L * 60_000L, null
        )
        assertNull(negative)
        var farBeyondDay: RecordsSchedulerModels.StudySession? = scheduler.nextSession(
                listOf(farItem), rows, now, 48L * 60L * 60_000L, null
        )
        assertNull(farBeyondDay)
        var withinDay: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(nearItem), rows, now, 48L * 60L * 60_000L, null
        )!!
        assertNotNull(withinDay)
        assertEquals("謎", withinDay.item!!.kanji)
    }

    @Test
    public fun studyAheadPrefersTrulyDueOverHorizonEligibleAtSameRung() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var dueNow: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, now).withToken("now")
        var dueIn5: RecordsStudyModels.StudyItem = reviewItem("謎", RecordsBase.LadderRung.KANJI_MEANING, now + 5L * 60_000L).withToken("ahead")
        var rows: List<RecordsImportModels.DashboardRow> = listOf(row("裂", 30), row("謎", 20))
        var session: RecordsSchedulerModels.StudySession = scheduler.nextSession(
                listOf(dueIn5, dueNow), rows, now, 15L * 60_000L, null
        )!!
        assertNotNull(session)
        assertEquals("裂", session.item!!.kanji)
    }

    @Test
    public fun studyAheadDoesNotShiftLearningStepDelaysOnAgain() {
        var scheduler: BridgeScheduler = BridgeScheduler()
        var now: Long = 1_000_000L
        var dueIn5Min: Long = now + 5L * 60_000L
        val reviewDueItem: RecordsStudyModels.StudyItem = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING, dueIn5Min)
                .copyBuilder()
                .activeToken("token-ahead")
                .build()
        var request: RecordsSchedulerModels.ReviewRequest = RecordsSchedulerModels.ReviewRequest("裂", "token-ahead", "again", false, false, false, 0)
        var result: RecordsSchedulerModels.ReviewResult = scheduler.applyReview(reviewDueItem, request, HashSet(), now)
        var expectedNextDue: Long = now + RecordsSchedulerModels.LearningStepSettings.defaults().reviewStepsMinutes.get(0) * 60_000L
        assertEquals(expectedNextDue, result.item.dueAtMillis)
    }

    // --- Test factories ---

    private fun item(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
    }

    private fun itemAtRung(kanji: String, rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem {
        return item(kanji).withRungAndPhase(rung, RecordsBase.SchedulerPhase.NEW_LEARNING)
    }

    private fun reviewItem(kanji: String, rung: RecordsBase.LadderRung, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return item(kanji).copyBuilder()
                .state("review")
                .dueAtMillis(dueAtMillis)
                .stability(1.0)
                .difficulty(5.0)
                .totalReviews(1)
                .lapses(0)
                .learningStep(2)
                .writingLevel(1)
                .rung(rung)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()
    }

    private fun matureReview(kanji: String, rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem {
        return reviewItem(kanji, rung, 0L).copyBuilder()
                .matureIntervalDays(21)
                .totalReviews(12)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()
    }

    private fun findItem(items: List<RecordsStudyModels.StudyItem>, kanji: String): RecordsStudyModels.StudyItem {
        for (item in items) {
            if (item.kanji.equals(kanji)) {
                return item
            }
        }
        throw AssertionError("Missing study item for " + kanji)
    }

    private fun row(kanji: String, score: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, if (score > 15) 1 else 0, 0, ArrayList<RecordsImportModels.Example>())
    }

    private fun rowWithMeaning(kanji: String, meaning: String, reasonText: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 900, meaning, "reading", "search", 10, "reason", reasonText, 1, 0, 0, ArrayList<RecordsImportModels.Example>())
    }

    private fun rowWithExamples(kanji: String, score: Int, vararg examples: RecordsImportModels.Example): RecordsImportModels.DashboardRow {
        var list: ArrayList<RecordsImportModels.Example> = ArrayList()
        list.addAll(examples.asList())
        return RecordsImportModels.DashboardRow(kanji, 900, "meaning", "reading", "search", score, "reason", "reason text", 1, if (score > 15) 1 else 0, 0, list)
    }

    private fun rankedRow(kanji: String, rank: Int?, score: Int, vararg examples: RecordsImportModels.Example): RecordsImportModels.DashboardRow {
        var list: ArrayList<RecordsImportModels.Example> = ArrayList()
        list.addAll(examples.asList())
        return RecordsImportModels.DashboardRow(kanji, rank, "meaning", "reading", "search", score, "reason", "reason text", 1, if (score > 15) 1 else 0, 0, list)
    }

    private fun example(kanji: String, difficulty: Double?, retrievability: Double?): RecordsImportModels.Example {
        var id: Long = kanji.codePointAt(0).toLong()
        return RecordsImportModels.Example(
                "active",
                id,
                id + 1L,
                kanji,
                "reading",
                "meaning",
                "",
                false,
                0,
                10,
                3,
                20.0,
                difficulty,
                retrievability
        )
    }

    private fun schedulerWithReviewIntervalDays(intervalDays: Long): BridgeScheduler {
        return BridgeScheduler(FixedIntervalFsrsAdapter(intervalDays * BridgeScheduler.DAY))
    }

    private class FixedIntervalFsrsAdapter : KaniFsrsAdapter {
        private val reviewIntervalMillis: Long
        constructor(reviewIntervalMillis: Long) {
            this.reviewIntervalMillis = reviewIntervalMillis
        }

        override fun initialReview(rating: String?, currentStability: Double, currentDifficulty: Double, targetRetention: Double, isNewLearning: Boolean): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(currentStability, currentDifficulty, BridgeScheduler.DAY)
        }

        override fun review(stability: Double, difficulty: Double, rating: String?, elapsedDays: Double, targetRetention: Double): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis)
        }
    }

    private class RecordingFsrsAdapter : KaniFsrsAdapter {
        private val reviewIntervalMillis: Long
        var elapsedDays: Double = -1.0
        constructor(reviewIntervalMillis: Long) {
            this.reviewIntervalMillis = reviewIntervalMillis
        }

        override fun initialReview(rating: String?, currentStability: Double, currentDifficulty: Double, targetRetention: Double, isNewLearning: Boolean): KaniFsrsReviewResult {
            return KaniFsrsReviewResult(currentStability, currentDifficulty, BridgeScheduler.DAY)
        }

        override fun review(stability: Double, difficulty: Double, rating: String?, elapsedDays: Double, targetRetention: Double): KaniFsrsReviewResult {
            this.elapsedDays = elapsedDays
            return KaniFsrsReviewResult(stability, difficulty, reviewIntervalMillis)
        }
    }

    private fun settingsWithQueue(activeQueueCap: Int, newPerDay: Int): RecordsSyncModels.Settings {
        var defaults: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                activeQueueCap,
                newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove
        )
    }

    private fun settingsWithSortMode(mode: String): RecordsSyncModels.Settings {
        var defaults: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                defaults.importActiveCards,
                defaults.importSuspendedCards,
                defaults.importTaggedCards,
                defaults.importTags,
                defaults.importWeakCards,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                defaults.importMinMatchingCardsPerKanji,
                defaults.importBrowserQueryCards,
                defaults.importBrowserQuery,
                mode
        )
    }
}
