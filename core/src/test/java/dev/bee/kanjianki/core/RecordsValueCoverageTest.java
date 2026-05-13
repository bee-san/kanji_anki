package dev.bee.kanjianki.core;

import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeOrderEvaluation;
import dev.bee.kanjianki.core.study.WritingAnalysis;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RecordsValueCoverageTest {
    private static final String TEST_ACTIVE = "active";
    private static final String TEST_EXPRESSION = "expr";
    private static final String TEST_READING = "read";
    private static final String TEST_MEANING = "meaning";
    private static final String TEST_SENTENCE = "sentence";

    @Test
    public void timelineAndRepairRecordsNormalizeInputs() {
        Records.SimilarKanjiWritingRepair repair = new Records.SimilarKanjiWritingRepair(
                -1L,
                null,
                null,
                null,
                null,
                null,
                "",
                -2L,
                null,
                -3,
                -4L,
                -5L,
                -6L
        );
        Records.SimilarKanjiWritingRepair completed = new Records.SimilarKanjiWritingRepair(
                2L,
                "裂",
                "列",
                "sig",
                "列",
                "tear",
                "done",
                3L,
                TEST_ACTIVE,
                1,
                4L,
                5L,
                6L
        );
        Records.SimilarKanjiWritingRepair nullStatus = new Records.SimilarKanjiWritingRepair(
                3L,
                "裂",
                "列",
                "sig",
                "列",
                "tear",
                null,
                3L,
                TEST_ACTIVE,
                1,
                4L,
                5L,
                6L
        );

        assertEquals(0L, repair.id);
        assertEquals("", repair.targetKanji);
        assertEquals("pending", repair.status);
        assertEquals(0L, repair.dueAtMillis);
        assertEquals(0, repair.attempts);
        assertEquals("token", repair.withToken("token", 12L).activeToken);
        assertEquals("done", completed.status);
        assertEquals(TEST_ACTIVE, completed.activeToken);
        assertEquals("pending", nullStatus.status);

        Records.KanjiTimelineEvent event = new Records.KanjiTimelineEvent(
                1L,
                "拉",
                2L,
                "review",
                "title",
                "detail",
                null,
                null,
                null,
                true,
                false,
                true,
                10,
                1,
                99L,
                "dedupe"
        );
        assertEquals("", event.sourceExpression);
        assertEquals("", event.sourceReading);
        assertEquals("", event.rating);

        Records.KanjiRecoveryTimeline timeline = new Records.KanjiRecoveryTimeline(
                new Records.KanjiInventoryItem("拉", "pull", "ら", "拉", 1, 1, false, 5L),
                row("拉"),
                item("拉"),
                Collections.singletonList(event)
        );
        assertEquals("拉", timeline.inventoryItem.kanji);
        assertEquals(1, timeline.events.size());
        assertEquals(1, new Records.KanjiRecoveryTimeline(row("拉"), item("拉"), Collections.singletonList(event)).events.size());
    }

    @Test
    public void taskMemoryLearningRepeatAndReviewStatsCoverFallbacks() {
        Records.TaskMemory fallback = new Records.TaskMemory("fallback", 1L, 2.0, 3.0, 4, 5, 1, "good", 6);
        Records.TaskMemory emptyState = new Records.TaskMemory("", 1L, 2.0, 3.0, 4, 5, 1, "good", 6);
        assertSame(fallback, Records.TaskMemory.decode(null, fallback));
        assertSame(Records.TaskMemory.initial().state, Records.TaskMemory.decode("", null).state);
        assertSame(fallback, Records.TaskMemory.decode("too\tshort", fallback));
        assertSame(fallback, Records.TaskMemory.decode("new\tbad\t0.4\t5.0\t0\t0\t0\t\t0", fallback));
        assertEquals("new", emptyState.state);

        Records.TaskMemory decoded = Records.TaskMemory.decode(
                new Records.TaskMemory(null, -1L, 0.4, 5.0, -2, -3, -4, null, -5).encode(),
                null
        );
        assertEquals("new", decoded.state);
        assertEquals(0L, decoded.dueAtMillis);
        assertEquals("", decoded.lastRating);
        assertEquals(0, decoded.consecutivePasses);
        assertEquals(0L, decoded.lastPassedDueAtMillis);
        Records.TaskMemory promoted = fallback.withDueAtMillis(10L);
        assertEquals(10L, promoted.dueAtMillis);
        assertEquals(fallback.consecutivePasses, promoted.consecutivePasses);
        Records.TaskMemory legacyNinePart = Records.TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7", null);
        Records.TaskMemory legacyTenPart = Records.TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7\t3", null);
        assertEquals(0, legacyNinePart.consecutivePasses);
        assertEquals(3, legacyTenPart.consecutivePasses);
        assertEquals("review", new Records.TaskMemory("review", 1L, 2.0, 3.0, 4, 5, 1, "good", 6).state);

        Records.LearningRepeat repeat = new Records.LearningRepeat(null, null, null, "bad", -1, -2L, null, -3L, -4L);
        assertEquals("", repeat.kanji);
        assertEquals(Records.LEARNING_REPEAT_NEW, repeat.repeatType);
        assertEquals(0, repeat.stepIndex);
        assertEquals("tok", repeat.withToken("tok", 10L).activeToken);
        assertEquals(3, repeat.withStep(3, 20L, 30L).stepIndex);
        assertEquals(Records.LEARNING_REPEAT_REVIEW, new Records.LearningRepeat("裂", "sig", "task", Records.LEARNING_REPEAT_REVIEW, 1, 2L, TEST_ACTIVE, 3L, 4L).repeatType);

        assertEquals(1.0, new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0).retentionProxy(), 0.001);
        assertEquals(0.25, new Records.ReviewStats(4, 1, 1, 1, 1, 4, 1).writingFailureRate(), 0.001);
    }

    @Test
    public void settingsLegacyShapesStayCompatible() {
        Records.Settings eightArg = settingsWithRest(3000, 24, 3, 4);
        Records.Settings oldNineArg = settingsWithRest(100, 3000, 24, 3, 4);
        Records.Settings tenArg = settingsWithRest(100, 3000, 24, 3, 4, 6);
        Records.Settings elevenArg = settingsWithRest(100, 3000, 24, 3, 4, 6, 7);
        Records.Settings full = fullSettingsWithNoisyImportValues();

        assertEquals(4, eightArg.writingTriggerMissDays);
        assertEquals(3, oldNineArg.recognitionPromotionPasses);
        assertEquals(6, tenArg.recognitionPromotionPasses);
        assertEquals(7, elevenArg.realDueReviewsToMove);
        assertEquals(100, full.suspendedRankMin);
        assertEquals(5000, full.suspendedRankMax);
    }

    @Test
    public void settingsImportDefaultsNormalizeAndParseTags() {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        Records.Settings full = fullSettingsWithNoisyImportValues();

        assertFalse(defaults.importActiveCards);
        assertTrue(defaults.importSuspendedCards);
        assertTrue(full.importTaggedCardsEnabled());
        assertTrue(full.hasImportSourceEnabled());
        assertEquals("mine archive", full.importTagsText());
        assertEquals(Records.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, full.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(Arrays.asList("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort"), full.requiredFields());
        assertEquals(Arrays.asList("mine", "archive"), Records.parseImportTags(" mine, archive mine "));
        assertTrue(Records.parseImportTags(" ").isEmpty());
        assertTrue(Records.parseImportTags(null).isEmpty());
    }

    @Test
    public void settingsImportFiltersCoverDisabledAndStringTagInputs() {
        Records.Settings disabled = new Records.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "",
                "Expression",
                "",
                "",
                "",
                21,
                2,
                100,
                3000,
                24,
                3,
                4,
                6,
                7,
                false,
                false,
                true,
                Collections.emptyList(),
                false,
                Double.POSITIVE_INFINITY,
                2,
                1
        );
        assertFalse(disabled.importTaggedCardsEnabled());
        assertFalse(disabled.hasImportSourceEnabled());
        assertEquals(Collections.singletonList("Expression"), disabled.requiredFields());

        Records.Settings stringTags = new Records.Settings(
                "Kiku", "Mining", null, " ", "Meaning", "Meaning", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, true, "mine archive", false, 1.2, 2, 1
        );
        Records.Settings nullTags = new Records.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, true, null, false, 0.1, 2, 1
        );
        Records.Settings negativeWeakThreshold = new Records.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, false, "", true, -1.0, 2, 1
        );
        assertEquals(Arrays.asList("mine", "archive"), stringTags.importTags);
        assertTrue(nullTags.importTags.isEmpty());
        assertEquals(Records.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, negativeWeakThreshold.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(Collections.singletonList("Meaning"), stringTags.requiredFields());
        assertEquals(Arrays.asList("mine", "archive"), Records.parseImportTags("mine,, archive"));

        Records.Settings blankTags = new Records.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, true, Arrays.asList(" ", null, ""), false, 1.2, 2, 1
        );
        assertTrue(blankTags.importTags.isEmpty());
    }

    @Test
    public void cardRecordsCoverFallbacks() {
        Records.Card card = new Records.Card(1L, 2L, 0, null, "Deck", 1, 2, 3, 4, 5, 6, true, 1.2, 3.4, 5.6);
        Records.Card activeMature = new Records.Card(2L, 3L, 0, "Deck", 1, 2, 3, 20, 5, 0, false);
        assertEquals("", card.deckId);
        assertEquals("Deck", card.deckName);
        assertTrue(card.suspended);
        assertFalse(card.active());
        assertFalse(card.mature(1));
        assertTrue(activeMature.active());
        assertTrue(activeMature.mature(10));
        assertEquals(Double.valueOf(3.4), card.fsrsDifficulty);
    }

    @Test
    public void collectionRowsAndSimilarChoiceRecordsCoverFallbacks() {
        Records.Note note = new Records.Note(1L, 2L, "model", Collections.singletonMap("Front", "value"), Collections.singletonList("tag"));
        Records.Card card = new Records.Card(1L, 2L, 0, "Deck", 1, 2, 3, 4, 5, 6, false);
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card));
        assertEquals(note, snapshot.notesById().get(1L));
        assertEquals("", note.field("Missing"));
        assertEquals("value", note.expression(new Records.Settings("m", "t", "Front", "", "", "", "", "", 21, 2, 100, 3000, 24, 3, 3)));

        Records.Example example = new Records.Example(TEST_ACTIVE, 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING, TEST_SENTENCE, true, 2, 30, 4, 1.0, 2.0, 3.0);
        Records.DashboardRow dashboard = new Records.DashboardRow("裂", 10, "tear", "レツ", "search", 9, "weak", "reason", 1, 2, 3, Collections.singletonList(example));
        assertEquals(example, dashboard.examples.get(0));

        Records.KanjiInventoryItem inventory = new Records.KanjiInventoryItem(null, null, null, null, -1, -2, true, -3L);
        assertEquals("", inventory.kanji);
        assertEquals(0, inventory.sourceCount);
        assertEquals(0L, inventory.lastSeenAtMillis);

        Records.SimilarKanjiPair pair = new Records.SimilarKanjiPair(null, null, null, -1L, -2L);
        assertEquals("", pair.kanjiA);
        assertEquals(0L, pair.firstSeenAtMillis);

        Records.SimilarKanjiChoiceCard emptyChoice = new Records.SimilarKanjiChoiceCard(null, null, null, null);
        Records.SimilarKanjiChoiceCard reviewedChoice = new Records.SimilarKanjiChoiceCard("裂", "tear", Arrays.asList("裂", "列"), "sig", -1L, 2L, 3L, -4, 5);
        assertFalse(emptyChoice.passed());
        assertTrue(reviewedChoice.passed());
        assertEquals(0L, reviewedChoice.dueAtMillis);
        assertEquals(0, reviewedChoice.correctCount);

        Records.SimilarKanjiChoiceResult result = new Records.SimilarKanjiChoiceResult(reviewedChoice, null, false, null);
        assertEquals("", result.selectedKanji);
        assertTrue(result.repairKanji.isEmpty());

        Records.SuspendedSource activeFallback = new Records.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
                Records.SuspendedSourceDetails.builder(TEST_SENTENCE)
                        .sourceType(" ")
                        .suspended(false)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(-1, -2, -3)
                        .build()
        );
        Records.SuspendedSource suspendedFallback = new Records.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
                Records.SuspendedSourceDetails.builder(TEST_SENTENCE)
                        .sourceType(null)
                        .suspended(true)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(1, 2, 3)
                        .build()
        );
        Records.SuspendedSource explicit = new Records.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
                Records.SuspendedSourceDetails.builder(TEST_SENTENCE)
                        .sourceType(" custom ")
                        .suspended(true)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(1, 2, 3)
                        .fsrs(1.0, 2.0, 3.0)
                        .build()
        );
        Records.SuspendedSource nullDetails = new Records.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING, (Records.SuspendedSourceDetails) null
        );
        assertEquals(Records.SOURCE_ACTIVE, activeFallback.sourceType);
        assertEquals(Records.SOURCE_SUSPENDED, suspendedFallback.sourceType);
        assertEquals("custom", explicit.sourceType);
        assertEquals("", nullDetails.sentence);
    }

    @Test
    public void studyItemLegacyConstructorsCoverFallbacks() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals("tok", fixture.compact.activeToken);
        assertEquals("sig", fixture.thirteenArg.answerSignature);
        assertEquals(Records.LadderRung.FONT_MEANING, fixture.legacyMemories.rung);
        assertEquals(fixture.kanji, fixture.legacyMemories.kanjiMeaningMemory);
        assertEquals(Records.LadderRung.TYPE_MEANING, fixture.full.rung);
        assertEquals(Records.SchedulerPhase.REVIEW, fixture.full.phase);
        assertEquals("", fixture.full.suppressedByTaskType);
        assertEquals(0, fixture.full.realAgainStreak);
        assertTrue(fixture.full.hasSimilarKanji);
        assertEquals(Records.TaskMemory.initial().state, fixture.full.similarKanjiMemory.state);
    }

    @Test
    public void studyItemTaskTypeMemoryRoutingStaysCompatible() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals(fixture.writing, fixture.legacyMemories.memoryForTaskType(BridgeScheduler.TASK_WRITE_KANJI));
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_TYPE_MEANING, fixture.typed).typingMeaningMemory);
        assertEquals(fixture.font, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_FONT_MEANING, fixture.font).fontMeaningMemory);
        assertEquals(fixture.word, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_WORD_READING, fixture.word).wordReadingMemory);
        assertEquals(fixture.kanji, fixture.legacyMemories.withTaskMemory(null, fixture.kanji).kanjiMeaningMemory);
    }

    @Test
    public void studyItemRungMemoryRoutingStaysCompatible() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals(fixture.kanji, fixture.legacyMemories.memoryForRung(null));
        assertEquals(fixture.writing, fixture.legacyMemories.memoryForRung(Records.LadderRung.WRITE_KANJI));
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_TYPE_MEANING, fixture.typed).memoryForRung(Records.LadderRung.TYPE_MEANING));
        assertEquals(fixture.kanji, fixture.legacyMemories.memoryForRung(Records.LadderRung.KANJI_MEANING));
        assertEquals(fixture.font, fixture.legacyMemories.memoryForRung(Records.LadderRung.FONT_MEANING));
        assertEquals(fixture.word, fixture.legacyMemories.memoryForRung(Records.LadderRung.WORD_READING));
        assertEquals(Records.TaskMemory.initial().state, fixture.legacyMemories.memoryForRung(Records.LadderRung.SIMILAR_KANJI).state);
    }

    @Test
    public void studyItemTaskMemoryCopiesStayCompatible() {
        StudyItemFixture fixture = studyItemFixture();
        Records.StudyItem fourMemories = fixture.compact.withTaskMemories(fixture.kanji, fixture.font, fixture.word, fixture.writing);
        Records.StudyItem fiveMemories = fixture.compact.withTaskMemories(fixture.typed, fixture.kanji, fixture.font, fixture.word, fixture.writing);

        assertEquals(fixture.typed, fixture.legacyMemories.withSimilarKanjiMemory(fixture.typed).similarKanjiMemory);
        assertEquals(fixture.kanji, fourMemories.kanjiMeaningMemory);
        assertEquals(fixture.typed, fiveMemories.typingMeaningMemory);
    }

    @Test
    public void studyItemCopyBuilderTransitionsStayCompatible() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals(Records.LadderRung.WRITE_KANJI, fixture.compact.copyBuilder().writingRemediationPending(true).build().rung);
        assertEquals(Records.LadderRung.WORD_READING, fixture.compact.copyBuilder().recognitionStage(2).build().rung);
        assertEquals(Records.SchedulerPhase.NEW_LEARNING, fixture.compact.copyBuilder().state(Records.LEARNING_REPEAT_REVIEW).build().phase);
        assertEquals(Records.SchedulerPhase.REVIEW, new Records.StudyItem("裂", Records.LEARNING_REPEAT_REVIEW, 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L).phase);
        assertEquals(Records.SchedulerPhase.REVIEW, new Records.StudyItem("裂", "retired", 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L).phase);
        assertEquals(Records.SchedulerPhase.RELEARNING, new Records.StudyItem("裂", "learning", 1L, 0.4, 5.0, 1, 0, 0, 0, "tok", 2L).phase);
        assertEquals(Records.SchedulerPhase.RELEARNING, fixture.compact.copyBuilder().writingRemediationPending(true).phase(null).build().phase);
        assertEquals("typing", fixture.compact.withSuppression("typing", 11L, 12).suppressedByTaskType);
        assertEquals(Records.LadderRung.FONT_MEANING, fixture.compact.withRung(Records.LadderRung.FONT_MEANING).rung);
        assertEquals(Records.SchedulerPhase.RELEARNING, fixture.compact.withPhase(Records.SchedulerPhase.RELEARNING).phase);
        assertEquals(Records.LadderRung.WORD_READING, fixture.compact.withLadderProgress(Records.LadderRung.WORD_READING, Records.SchedulerPhase.REVIEW, 2, 3, 4, 5L).rung);
    }

    @Test
    public void reviewRequestPlansAndReleaseRecordsCoverBranches() {
        Records.ReviewRequest legacyClean = new Records.ReviewRequest("裂", "tok", "good", true, true, false, 0);
        Records.ReviewRequest legacyEasy = new Records.ReviewRequest("裂", "tok", "easy", true, true, false, 0);
        Records.ReviewRequest legacyMessy = new Records.ReviewRequest("裂", "tok", "hard", true, true, false, 0);
        Records.ReviewRequest legacyFailedEasy = new Records.ReviewRequest("裂", "tok", "easy", true, false, false, 0);
        Records.ReviewRequest full = new Records.ReviewRequest("裂", "tok", "again", false, false, true, false, 2, null, null, null);
        assertTrue(legacyClean.writingClean);
        assertTrue(legacyEasy.writingClean);
        assertFalse(legacyMessy.writingClean);
        assertFalse(legacyFailedEasy.writingClean);
        assertEquals("", full.taskType);
        assertEquals("", full.answerSignature);
        assertEquals("", full.prompt);

        assertTrue(Records.LearningStepSettings.tryParseSteps(",,,").isEmpty());
        assertEquals(Arrays.asList(1, 10), Records.LearningStepSettings.tryParseSteps("1m,,10m"));
        assertEquals("1h, 30m", Records.LearningStepSettings.formatSteps(Arrays.asList(60, 30)));
        assertEquals("90m", Records.LearningStepSettings.formatSteps(Collections.singletonList(90)));
        assertEquals("1m, 10m", Records.LearningStepSettings.formatSteps(null));

        Records.AdaptiveLoadPlan incomplete = new Records.AdaptiveLoadPlan(100, 2, 1, Collections.singletonList("裂"), 1, false, "pending");
        Records.AdaptiveLoadPlan complete = new Records.AdaptiveLoadPlan(100, 2, 0, Collections.singletonList("裂"), 1, false, "done");
        Records.AdaptiveLoadPlan all = new Records.AdaptiveLoadPlan(100, 2, 0, Collections.singletonList("裂"), 1, true, "all");
        Records.AdaptiveLoadPlan noTarget = new Records.AdaptiveLoadPlan(0, 0, 0, Collections.singletonList("裂"), 1, false, "none");
        assertFalse(incomplete.focusComplete());
        assertTrue(complete.focusComplete());
        assertFalse(all.focusComplete());
        assertFalse(noTarget.focusComplete());

        Records.ReleaseInfo release = new Records.ReleaseInfo(
                "v1",
                "url",
                Arrays.asList(new Records.ReleaseAsset("notes.txt", "notes"), new Records.ReleaseAsset("kani.apk", "apk"), new Records.ReleaseAsset("kani.apk.sha256", "sha"))
        );
        assertEquals("apk", release.apkAsset().downloadUrl);
        assertEquals("sha", release.checksumAssetFor("kani.apk").downloadUrl);
        assertNull(release.checksumAssetFor("other.apk"));
    }

    @Test
    public void invalidVarargsKeepExplicitCompatibilityErrors() throws Exception {
        try {
            new Records.Settings("Kiku", "Mining", "Expression", "Reading", "Meaning", "Sentence", "Frequency");
            throw new AssertionError("Expected invalid Settings varargs to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Settings received"));
        }

        Method arg = Records.class.getDeclaredMethod("arg", Object[].class, int.class, String.class);
        arg.setAccessible(true);
        try {
            arg.invoke(null, new Object[]{new Object[]{"only"}}, 2, "test");
            throw new AssertionError("Expected private arg guard to fail");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause().getMessage().contains("test expected more arguments"));
        }
    }

    @Test
    public void strokeEvaluationAndPointValuesCoverAccessorsAndFallbacks() {
        StrokeOrderEvaluation empty = new StrokeOrderEvaluation(-1, -1, -1, null, null, null, null, 2.0);
        assertEquals(0, empty.expectedCount());
        assertEquals(0, empty.attemptedCount());
        assertEquals(0, empty.orderedMatchCount());
        assertEquals(1.0, empty.score(), 0.001);
        assertFalse(empty.complete());
        assertFalse(empty.exactOrder());
        assertFalse(empty.passed());

        StrokeOrderEvaluation exact = new StrokeOrderEvaluation(
                2,
                2,
                2,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                -1.0
        );
        assertTrue(exact.complete());
        assertTrue(exact.exactOrder());
        assertTrue(exact.passed());
        assertEquals(0.0, exact.score(), 0.001);

        StrokeOrderEvaluation imperfect = new StrokeOrderEvaluation(
                2,
                2,
                1,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("2"),
                0.5
        );
        assertTrue(imperfect.complete());
        assertFalse(imperfect.exactOrder());
        assertEquals(Collections.singletonList("2"), imperfect.outOfPositionStrokeIds());
        assertEquals(Collections.emptyList(), imperfect.missingStrokeIds());
        assertEquals(Collections.emptyList(), imperfect.extraStrokeIds());
        assertEquals(Collections.emptyList(), imperfect.duplicateStrokeIds());

        InkPoint point = new InkPoint(0.25f, 0.5f, 7L);
        assertEquals(new InkPoint(25f, 100f, 7L), point.scaled(100f, 200f));
        Object nonPoint = "not a point";
        boolean equalsNonPoint = point.equals(nonPoint);
        assertFalse(equalsNonPoint);
        assertNotEquals(point, new InkPoint(0.25f, 0.6f, 7L));
        assertEquals(point.hashCode(), new InkPoint(0.25f, 0.5f, 7L).hashCode());
    }

    @Test
    public void writingAnalysisAndDiagnosisCoverFallbacks() {
        WritingAnalysis fallback = new WritingAnalysis(
                WritingAnalysis.Status.PASS,
                "good",
                true,
                null,
                Collections.singletonList(new RecognitionCandidate("拉", null)),
                null,
                null,
                -1
        );

        assertEquals("", fallback.message);
        assertEquals(HintLevel.BLIND, fallback.hintLevel());
        assertEquals(0, fallback.hintsUsed());
        assertTrue(fallback.passed());
        assertFalse(fallback.failed());
        assertEquals((0.78 * 0.55) + (0.7 * 0.45), fallback.confidenceScore(), 0.001);

        WritingAnalysis failed = new WritingAnalysis(
                WritingAnalysis.Status.WRONG,
                "again",
                false,
                "wrong",
                Collections.emptyList(),
                null
        );
        assertTrue(failed.failed());
        assertEquals(0.0, failed.confidenceScore(), 0.001);

        StrokeDiagnosis diagnosis = StrokeDiagnosis.builder()
                .add(null, 1)
                .add(StrokeDiagnosis.Label.WRONG_ORDER, -1)
                .add(StrokeDiagnosis.Label.WRONG_ORDER, -1)
                .build();
        assertFalse(diagnosis.isEmpty());
        assertTrue(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER));
        assertTrue(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 0));
        assertFalse(diagnosis.hasLabel(StrokeDiagnosis.Label.MISSING_STROKE));
        assertFalse(diagnosis.hasLabel(StrokeDiagnosis.Label.WRONG_ORDER, 1));
        assertEquals(2, diagnosis.plus(StrokeDiagnosis.Label.MISSING_STROKE, 2).entries.size());
        assertTrue(StrokeDiagnosis.builder().build().isEmpty());
    }

    @Test
    public void releaseInfoAndAdaptiveLoadPlanCoverNullBranches() {
        Records.ReleaseInfo emptyRelease = new Records.ReleaseInfo("v0", "https://example", Collections.emptyList());
        assertNull(emptyRelease.apkAsset());
        assertNull(emptyRelease.checksumAssetFor("kani.apk"));

        Records.AdaptiveLoadPlan complete = new Records.AdaptiveLoadPlan(
                true,
                100,
                1,
                0,
                Arrays.asList("拉"),
                0,
                false,
                null
        );
        assertTrue(complete.autoMode);
        assertEquals("", complete.status);
        assertTrue(complete.focusComplete());

        Records.AdaptiveLoadPlan all = new Records.AdaptiveLoadPlan(100, 1, 0, Arrays.asList("拉"), 0, true, "all");
        assertFalse(all.focusComplete());
    }

    private static Records.DashboardRow row(String kanji) {
        return new Records.DashboardRow(kanji, 1, "meaning", "reading", kanji, 10, "reason", "reason", 1, 0, 0, Collections.emptyList());
    }

    private static Records.StudyItem item(String kanji) {
        return new Records.StudyItem(kanji, "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);
    }

    private static Records.Settings fullSettingsWithNoisyImportValues() {
        return new Records.Settings(
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
                5000,
                100,
                24,
                3,
                4,
                6,
                7,
                false,
                false,
                true,
                Arrays.asList(" mine ", null, "", "archive", "mine"),
                true,
                Double.NaN,
                0,
                0
        );
    }

    private static Records.Settings settingsWithRest(Object... rest) {
        List<Object> values = new java.util.ArrayList<>();
        Collections.addAll(values, "Frequency", "FreqSort", 21, 2);
        Collections.addAll(values, rest);
        return new Records.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                values.toArray()
        );
    }

    private static StudyItemFixture studyItemFixture() {
        return new StudyItemFixture();
    }

    private static final class StudyItemFixture {
        final Records.TaskMemory typed = new Records.TaskMemory("review", 10L, 1.0, 2.0, 3, 0, 1, "good", 2, 4, 5L);
        final Records.TaskMemory kanji = new Records.TaskMemory("review", 20L, 1.0, 2.0, 3, 0, 1, "hard", 2, 0, 0L);
        final Records.TaskMemory font = new Records.TaskMemory("review", 30L, 1.0, 2.0, 3, 0, 1, "easy", 2, 0, 0L);
        final Records.TaskMemory word = new Records.TaskMemory("review", 40L, 1.0, 2.0, 3, 0, 1, "again", 2, 0, 0L);
        final Records.TaskMemory writing = new Records.TaskMemory("review", 50L, 1.0, 2.0, 3, 0, 1, "good", 2, 0, 0L);
        final Records.StudyItem compact = new Records.StudyItem("裂", "new", 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L);
        final Records.StudyItem thirteenArg = new Records.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, 1, 2, 3L, false, "task", 4L, 5, "sig", "tok", 6L);
        final Records.StudyItem legacyMemories = new Records.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, 1, 2, 3L, false, "task", 4L, 5, "sig", "tok", 6L, kanji, font, word, writing);
        final Records.StudyItem full = new Records.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, -9, -2, -3L, false, null, -4L, -5, null, "tok", 6L, null, null, null, null, null, null, null, -1, -2, -3L, true, null);
    }
}
