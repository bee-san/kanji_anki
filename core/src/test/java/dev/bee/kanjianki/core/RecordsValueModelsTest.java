package dev.bee.kanjianki.core;

import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeOrderEvaluation;
import dev.bee.kanjianki.core.study.WritingAnalysis;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RecordsValueModelsTest {
    private static final String TEST_ACTIVE = "active";
    private static final String TEST_EXPRESSION = "expr";
    private static final String TEST_READING = "read";
    private static final String TEST_MEANING = "meaning";
    private static final String TEST_SENTENCE = "sentence";

    @Test
    public void splitModelConstructorsStayHiddenWhileExposingRecordApi() throws Exception {
        Constructor<Records> recordsConstructor = Records.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(recordsConstructor.getModifiers()));
        assertEquals(0, Records.class.getConstructors().length);

        recordsConstructor.setAccessible(true);
        Records reflectedRecords = recordsConstructor.newInstance();
        assertTrue(reflectedRecords instanceof RecordsSchedulerModels);

        SplitProbe probe = new SplitProbe();
        assertTrue(probe instanceof RecordsBase);
        assertTrue(probe.defaultSuspendedCards());
        assertEquals("Kiku", probe.defaultModelName());
    }

    @Test
    public void timelineAndRepairRecordsNormalizeInputs() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = new RecordsImportModels.SimilarKanjiWritingRepair(
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
        RecordsImportModels.SimilarKanjiWritingRepair completed = new RecordsImportModels.SimilarKanjiWritingRepair(
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
        RecordsImportModels.SimilarKanjiWritingRepair nullStatus = new RecordsImportModels.SimilarKanjiWritingRepair(
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

        RecordsImportModels.KanjiTimelineEvent event = new RecordsImportModels.KanjiTimelineEvent(
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

        RecordsStudyModels.KanjiRecoveryTimeline timeline = new RecordsStudyModels.KanjiRecoveryTimeline(
                new RecordsImportModels.KanjiInventoryItem("拉", "pull", "ら", "拉", 1, 1, false, 5L),
                row("拉"),
                item("拉"),
                Collections.singletonList(event)
        );
        assertEquals("拉", timeline.inventoryItem.kanji);
        assertEquals(1, timeline.events.size());
        assertEquals(1, new RecordsStudyModels.KanjiRecoveryTimeline(row("拉"), item("拉"), Collections.singletonList(event)).events.size());
    }

    @Test
    public void taskMemoryLearningRepeatAndReviewStatsCoverFallbacks() {
        RecordsStudyModels.TaskMemory fallback = new RecordsStudyModels.TaskMemory("fallback", 1L, 2.0, 3.0, 4, 5, 1, "good", 6);
        RecordsStudyModels.TaskMemory emptyState = new RecordsStudyModels.TaskMemory("", 1L, 2.0, 3.0, 4, 5, 1, "good", 6);
        assertSame(fallback, RecordsStudyModels.TaskMemory.decode(null, fallback));
        assertSame(RecordsStudyModels.TaskMemory.initial().state, RecordsStudyModels.TaskMemory.decode("", null).state);
        assertSame(fallback, RecordsStudyModels.TaskMemory.decode("too\tshort", fallback));
        assertSame(fallback, RecordsStudyModels.TaskMemory.decode("new\tbad\t0.4\t5.0\t0\t0\t0\t\t0", fallback));
        assertEquals("new", emptyState.state);

        RecordsStudyModels.TaskMemory decoded = RecordsStudyModels.TaskMemory.decode(
                new RecordsStudyModels.TaskMemory(null, -1L, 0.4, 5.0, -2, -3, -4, null, -5).encode(),
                null
        );
        assertEquals("new", decoded.state);
        assertEquals(0L, decoded.dueAtMillis);
        assertEquals("", decoded.lastRating);
        assertEquals(0, decoded.consecutivePasses);
        assertEquals(0L, decoded.lastPassedDueAtMillis);
        RecordsStudyModels.TaskMemory promoted = fallback.withDueAtMillis(10L);
        assertEquals(10L, promoted.dueAtMillis);
        assertEquals(fallback.consecutivePasses, promoted.consecutivePasses);
        RecordsStudyModels.TaskMemory legacyNinePart = RecordsStudyModels.TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7", null);
        RecordsStudyModels.TaskMemory legacyTenPart = RecordsStudyModels.TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7\t3", null);
        assertEquals(0, legacyNinePart.consecutivePasses);
        assertEquals(3, legacyTenPart.consecutivePasses);
        assertEquals("review", new RecordsStudyModels.TaskMemory("review", 1L, 2.0, 3.0, 4, 5, 1, "good", 6).state);

        RecordsSchedulerModels.LearningRepeat repeat = new RecordsSchedulerModels.LearningRepeat(null, null, null, "bad", -1, -2L, null, -3L, -4L);
        assertEquals("", repeat.kanji);
        assertEquals(RecordsBase.LEARNING_REPEAT_NEW, repeat.repeatType);
        assertEquals(0, repeat.stepIndex);
        assertEquals("tok", repeat.withToken("tok", 10L).activeToken);
        assertEquals(3, repeat.withStep(3, 20L, 30L).stepIndex);
        assertEquals(RecordsBase.LEARNING_REPEAT_REVIEW, new RecordsSchedulerModels.LearningRepeat("裂", "sig", "task", RecordsBase.LEARNING_REPEAT_REVIEW, 1, 2L, TEST_ACTIVE, 3L, 4L).repeatType);

        assertEquals(1.0, new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0).retentionProxy(), 0.001);
        assertEquals(0.25, new RecordsSchedulerModels.ReviewStats(4, 1, 1, 1, 1, 4, 1).writingFailureRate(), 0.001);
    }

    @Test
    public void settingsLegacyShapesStayCompatible() {
        RecordsSyncModels.Settings eightArg = settingsWithRest(3000, 24, 3, 4);
        RecordsSyncModels.Settings oldNineArg = settingsWithRest(100, 3000, 24, 3, 4);
        RecordsSyncModels.Settings tenArg = settingsWithRest(100, 3000, 24, 3, 4, 6);
        RecordsSyncModels.Settings elevenArg = settingsWithRest(100, 3000, 24, 3, 4, 6, 7);
        RecordsSyncModels.Settings full = fullSettingsWithNoisyImportValues();

        assertEquals(4, eightArg.writingTriggerMissDays);
        assertEquals(3, oldNineArg.recognitionPromotionPasses);
        assertEquals(6, tenArg.recognitionPromotionPasses);
        assertEquals(7, elevenArg.realDueReviewsToMove);
        assertEquals(100, full.suspendedRankMin);
        assertEquals(5000, full.suspendedRankMax);
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, full.newCardSortMode);
    }

    @Test
    public void settingsImportDefaultsNormalizeAndParseTags() {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        RecordsSyncModels.Settings full = fullSettingsWithNoisyImportValues();

        assertFalse(defaults.importActiveCards);
        assertTrue(defaults.importSuspendedCards);
        assertTrue(full.importTaggedCardsEnabled());
        assertTrue(full.hasImportSourceEnabled());
        assertEquals("mine archive", full.importTagsText());
        assertEquals(RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, full.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY).newCardSortMode);
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, settingsWithSortMode("not-real").newCardSortMode);
        assertEquals(Arrays.asList("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort"), full.requiredFields());
        assertEquals(Arrays.asList("mine", "archive"), RecordsBase.parseImportTags(" mine, archive mine "));
        assertTrue(RecordsBase.parseImportTags(" ").isEmpty());
        assertTrue(RecordsBase.parseImportTags(null).isEmpty());
    }

    @Test
    public void schedulerParametersResolveFrequencyRetentionByRank() {
        RecordsSchedulerModels.SchedulerParameters parameters = new RecordsSchedulerModels.SchedulerParameters(
                0.90,
                0.45,
                1.20,
                2.00,
                3.10,
                0L,
                0,
                true,
                "1-500=95%\n501-2000=85%"
        );

        assertEquals(0.95, parameters.targetRetentionForRank(200), 0.001);
        assertEquals(0.85, parameters.targetRetentionForRank(1000), 0.001);
        assertEquals(0.90, parameters.targetRetentionForRank(3000), 0.001);
        assertEquals(0.90, parameters.targetRetentionForRank(null), 0.001);
        assertEquals(0.88, parameters.withTargetRetention(0.88).targetRetention, 0.001);
        assertEquals(parameters.frequencyRetentionRanges, parameters.withAdjustment(0.5, 1.3, 2.3, 3.5, 10L, 40).frequencyRetentionRanges);
    }

    @Test
    public void settingsImportFiltersCoverDisabledAndStringTagInputs() {
        RecordsSyncModels.Settings disabled = new RecordsSyncModels.Settings(
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

        RecordsSyncModels.Settings stringTags = new RecordsSyncModels.Settings(
                "Kiku", "Mining", null, " ", "Meaning", "Meaning", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, true, "mine archive", false, 1.2, 2, 1
        );
        RecordsSyncModels.Settings nullTags = new RecordsSyncModels.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, true, null, false, 0.1, 2, 1
        );
        RecordsSyncModels.Settings negativeWeakThreshold = new RecordsSyncModels.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, false, "", true, -1.0, 2, 1
        );
        assertEquals(Arrays.asList("mine", "archive"), stringTags.importTags);
        assertTrue(nullTags.importTags.isEmpty());
        assertEquals(RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, negativeWeakThreshold.importWeakFsrsDifficultyThreshold, 0.001);
        assertEquals(Collections.singletonList("Meaning"), stringTags.requiredFields());
        assertEquals(Arrays.asList("mine", "archive"), RecordsBase.parseImportTags("mine,, archive"));

        RecordsSyncModels.Settings blankTags = new RecordsSyncModels.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
                false, false, true, Arrays.asList(" ", null, ""), false, 1.2, 2, 1
        );
        assertTrue(blankTags.importTags.isEmpty());
    }

    @Test
    public void settingsBrowserQueryDefaultsAndHelpers() {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        assertFalse(defaults.importBrowserQueryCards);
        assertEquals("", defaults.importBrowserQuery);
        assertFalse(defaults.browserQueryImportEnabled());
        assertEquals("", defaults.normalizedBrowserQuery());

        RecordsSyncModels.Settings enabledNonBlank = new RecordsSyncModels.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "",
                "", "", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
                false, false, false, Collections.emptyList(), false, 7.0, 2, 1,
                true, " tag:kani "
        );
        assertTrue(enabledNonBlank.browserQueryImportEnabled());
        assertEquals("tag:kani", enabledNonBlank.normalizedBrowserQuery());
        assertTrue(enabledNonBlank.hasImportSourceEnabled());

        RecordsSyncModels.Settings enabledBlank = new RecordsSyncModels.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "",
                "", "", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
                false, false, false, Collections.emptyList(), false, 7.0, 2, 1,
                true, "   "
        );
        assertFalse(enabledBlank.browserQueryImportEnabled());
        assertFalse(enabledBlank.hasImportSourceEnabled());

        RecordsSyncModels.Settings disabledNonBlank = new RecordsSyncModels.Settings(
                "Kiku", "Mining", "Expression", "", "Meaning", "",
                "", "", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
                false, false, false, Collections.emptyList(), false, 7.0, 2, 1,
                false, "tag:kani"
        );
        assertFalse(disabledNonBlank.browserQueryImportEnabled());
        assertFalse(disabledNonBlank.hasImportSourceEnabled());
    }

    @Test
    public void cardRecordsCoverFallbacks() {
        RecordsSyncModels.Card card = new RecordsSyncModels.Card(1L, 2L, 0, null, "Deck", 1, 2, 3, 4, 5, 6, true, 1.2, 3.4, 5.6);
        RecordsSyncModels.Card activeMature = new RecordsSyncModels.Card(2L, 3L, 0, "Deck", 1, 2, 3, 20, 5, 0, false);
        assertEquals("", card.deckId);
        assertEquals("Deck", card.deckName);
        assertTrue(card.suspended);
        assertFalse(card.active());
        assertFalse(card.mature(1));
        assertTrue(activeMature.active());
        assertTrue(activeMature.mature(10));
        assertEquals(Double.valueOf(3.4), card.fsrsDifficulty);
        assertFalse(card.browserQueryMatched);

        RecordsSyncModels.Card marked = card.withBrowserQueryMatched(true);
        assertTrue(marked.browserQueryMatched);
        assertEquals(card.cardId, marked.cardId);
        assertEquals(card.noteId, marked.noteId);
        assertEquals(card.suspended, marked.suspended);
        assertEquals(card.deckName, marked.deckName);
        assertSame(card, card.withBrowserQueryMatched(false));
    }

    @Test
    public void collectionRowsAndSimilarChoiceRecordsCoverFallbacks() {
        RecordsSyncModels.Note note = new RecordsSyncModels.Note(1L, 2L, "model", Collections.singletonMap("Front", "value"), Collections.singletonList("tag"));
        RecordsSyncModels.Card card = new RecordsSyncModels.Card(1L, 2L, 0, "Deck", 1, 2, 3, 4, 5, 6, false);
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card));
        assertEquals(note, snapshot.notesById().get(1L));
        assertEquals("", note.field("Missing"));
        assertEquals("value", note.expression(new RecordsSyncModels.Settings("m", "t", "Front", "", "", "", "", "", 21, 2, 100, 3000, 24, 3, 3)));

        RecordsImportModels.Example example = new RecordsImportModels.Example(TEST_ACTIVE, 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING, TEST_SENTENCE, true, 2, 30, 4, 1.0, 2.0, 3.0);
        RecordsImportModels.DashboardRow dashboard = new RecordsImportModels.DashboardRow("裂", 10, "tear", "レツ", "search", 9, "weak", "reason", 1, 2, 3, Collections.singletonList(example));
        assertEquals(example, dashboard.examples.get(0));

        RecordsImportModels.KanjiInventoryItem inventory = new RecordsImportModels.KanjiInventoryItem(null, null, null, null, -1, -2, true, -3L);
        assertEquals("", inventory.kanji);
        assertEquals(0, inventory.sourceCount);
        assertEquals(0L, inventory.lastSeenAtMillis);

        RecordsImportModels.SimilarKanjiPair pair = new RecordsImportModels.SimilarKanjiPair(null, null, null, -1L, -2L);
        assertEquals("", pair.kanjiA);
        assertEquals(0L, pair.firstSeenAtMillis);

        RecordsImportModels.SimilarKanjiChoiceCard emptyChoice = new RecordsImportModels.SimilarKanjiChoiceCard(null, null, null, null);
        RecordsImportModels.SimilarKanjiChoiceCard reviewedChoice = new RecordsImportModels.SimilarKanjiChoiceCard("裂", "tear", Arrays.asList("裂", "列"), "sig", -1L, 2L, 3L, -4, 5);
        assertFalse(emptyChoice.passed());
        assertTrue(reviewedChoice.passed());
        assertEquals(0L, reviewedChoice.dueAtMillis);
        assertEquals(0, reviewedChoice.correctCount);

        RecordsImportModels.SimilarKanjiChoiceResult result = new RecordsImportModels.SimilarKanjiChoiceResult(reviewedChoice, null, false, null);
        assertEquals("", result.selectedKanji);
        assertTrue(result.repairKanji.isEmpty());

        RecordsImportModels.SuspendedSource activeFallback = new RecordsImportModels.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
                RecordsImportModels.SuspendedSourceDetails.builder(TEST_SENTENCE)
                        .sourceType(" ")
                        .suspended(false)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(-1, -2, -3)
                        .build()
        );
        RecordsImportModels.SuspendedSource suspendedFallback = new RecordsImportModels.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
                RecordsImportModels.SuspendedSourceDetails.builder(TEST_SENTENCE)
                        .sourceType(null)
                        .suspended(true)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(1, 2, 3)
                        .build()
        );
        RecordsImportModels.SuspendedSource explicit = new RecordsImportModels.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
                RecordsImportModels.SuspendedSourceDetails.builder(TEST_SENTENCE)
                        .sourceType(" custom ")
                        .suspended(true)
                        .forcePractice(false)
                        .mature(true)
                        .reviewStats(1, 2, 3)
                        .fsrs(1.0, 2.0, 3.0)
                        .build()
        );
        RecordsImportModels.SuspendedSource nullDetails = new RecordsImportModels.SuspendedSource(
                "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING, (RecordsImportModels.SuspendedSourceDetails) null
        );
        assertEquals(RecordsBase.SOURCE_ACTIVE, activeFallback.sourceType);
        assertEquals(RecordsBase.SOURCE_SUSPENDED, suspendedFallback.sourceType);
        assertEquals("custom", explicit.sourceType);
        assertEquals("", nullDetails.sentence);
    }

    @Test
    public void studyItemLegacyConstructorsCoverFallbacks() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals("tok", fixture.compact.activeToken);
        assertEquals("sig", fixture.thirteenArg.answerSignature);
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, fixture.legacyMemories.rung);
        assertEquals(fixture.kanji, fixture.legacyMemories.kanjiMeaningMemory);
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, fixture.full.rung);
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, fixture.full.phase);
        assertEquals("", fixture.full.suppressedByTaskType);
        assertEquals(0, fixture.full.realAgainStreak);
        assertTrue(fixture.full.hasSimilarKanji);
        assertEquals(RecordsStudyModels.TaskMemory.initial().state, fixture.full.similarKanjiMemory.state);
    }

    @Test
    public void studyItemTaskTypeMemoryRoutingStaysCompatible() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals(fixture.writing, fixture.legacyMemories.memoryForTaskType(BridgeScheduler.TASK_WRITE_KANJI));
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_TYPE_MEANING, fixture.typed).typingMeaningMemory);
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_MEANING_KANJI, fixture.typed).meaningKanjiMemory);
        assertEquals(fixture.font, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_FONT_MEANING, fixture.font).fontMeaningMemory);
        assertEquals(fixture.word, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_WORD_READING, fixture.word).wordReadingMemory);
        assertEquals(fixture.kanji, fixture.legacyMemories.withTaskMemory(null, fixture.kanji).kanjiMeaningMemory);
    }

    @Test
    public void studyItemRungMemoryRoutingStaysCompatible() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals(fixture.kanji, fixture.legacyMemories.memoryForRung(null));
        assertEquals(fixture.writing, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.WRITE_KANJI));
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_TYPE_MEANING, fixture.typed).memoryForRung(RecordsBase.LadderRung.TYPE_MEANING));
        assertEquals(RecordsStudyModels.TaskMemory.initial().state, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.MEANING_KANJI).state);
        assertEquals(fixture.kanji, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.KANJI_MEANING));
        assertEquals(fixture.font, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.FONT_MEANING));
        assertEquals(fixture.word, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.WORD_READING));
        assertEquals(RecordsStudyModels.TaskMemory.initial().state, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.SIMILAR_KANJI).state);
    }

    @Test
    public void studyItemTaskMemoryCopiesStayCompatible() {
        StudyItemFixture fixture = studyItemFixture();
        RecordsStudyModels.StudyItem fourMemories = fixture.compact.withTaskMemories(fixture.kanji, fixture.font, fixture.word, fixture.writing);
        RecordsStudyModels.StudyItem fiveMemories = fixture.compact.withTaskMemories(fixture.typed, fixture.kanji, fixture.font, fixture.word, fixture.writing);

        assertEquals(fixture.typed, fixture.legacyMemories.withSimilarKanjiMemory(fixture.typed).similarKanjiMemory);
        assertEquals(fixture.kanji, fourMemories.kanjiMeaningMemory);
        assertEquals(fixture.typed, fiveMemories.typingMeaningMemory);
    }

    @Test
    public void studyItemCopyBuilderTransitionsStayCompatible() {
        StudyItemFixture fixture = studyItemFixture();

        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, fixture.compact.copyBuilder().writingRemediationPending(true).build().rung);
        assertEquals(RecordsBase.LadderRung.WORD_READING, fixture.compact.copyBuilder().recognitionStage(2).build().rung);
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, fixture.compact.copyBuilder().state(RecordsBase.LEARNING_REPEAT_REVIEW).build().phase);
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, new RecordsStudyModels.StudyItem("裂", RecordsBase.LEARNING_REPEAT_REVIEW, 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L).phase);
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, new RecordsStudyModels.StudyItem("裂", "retired", 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L).phase);
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, new RecordsStudyModels.StudyItem("裂", "learning", 1L, 0.4, 5.0, 1, 0, 0, 0, "tok", 2L).phase);
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, fixture.compact.copyBuilder().writingRemediationPending(true).phase(null).build().phase);
        assertEquals("typing", fixture.compact.withSuppression("typing", 11L, 12).suppressedByTaskType);
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, fixture.compact.withRung(RecordsBase.LadderRung.FONT_MEANING).rung);
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, fixture.compact.withPhase(RecordsBase.SchedulerPhase.RELEARNING).phase);
        assertEquals(RecordsBase.LadderRung.WORD_READING, fixture.compact.withLadderProgress(RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 2, 3, 4, 5L).rung);
    }

    @Test
    public void reviewRequestPlansAndReleaseRecordsCoverBranches() {
        RecordsSchedulerModels.ReviewRequest legacyClean = new RecordsSchedulerModels.ReviewRequest("裂", "tok", "good", true, true, false, 0);
        RecordsSchedulerModels.ReviewRequest legacyEasy = new RecordsSchedulerModels.ReviewRequest("裂", "tok", "easy", true, true, false, 0);
        RecordsSchedulerModels.ReviewRequest legacyMessy = new RecordsSchedulerModels.ReviewRequest("裂", "tok", "hard", true, true, false, 0);
        RecordsSchedulerModels.ReviewRequest legacyFailedEasy = new RecordsSchedulerModels.ReviewRequest("裂", "tok", "easy", true, false, false, 0);
        RecordsSchedulerModels.ReviewRequest full = new RecordsSchedulerModels.ReviewRequest("裂", "tok", "again", false, false, true, false, 2, null, null, null);
        assertTrue(legacyClean.writingClean);
        assertTrue(legacyEasy.writingClean);
        assertFalse(legacyMessy.writingClean);
        assertFalse(legacyFailedEasy.writingClean);
        assertEquals("", full.taskType);
        assertEquals("", full.answerSignature);
        assertEquals("", full.prompt);

        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps(",,,").isEmpty());
        assertEquals(Arrays.asList(1, 10), RecordsSchedulerModels.LearningStepSettings.tryParseSteps("1m,,10m"));
        assertEquals("1h, 30m", RecordsSchedulerModels.LearningStepSettings.formatSteps(Arrays.asList(60, 30)));
        assertEquals("90m", RecordsSchedulerModels.LearningStepSettings.formatSteps(Collections.singletonList(90)));
        assertEquals("1m, 10m", RecordsSchedulerModels.LearningStepSettings.formatSteps(null));

        RecordsSchedulerModels.AdaptiveLoadPlan incomplete = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 1, Collections.singletonList("裂"), 1, false, "pending");
        RecordsSchedulerModels.AdaptiveLoadPlan complete = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 0, Collections.singletonList("裂"), 1, false, "done");
        RecordsSchedulerModels.AdaptiveLoadPlan all = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 0, Collections.singletonList("裂"), 1, true, "all");
        RecordsSchedulerModels.AdaptiveLoadPlan noTarget = new RecordsSchedulerModels.AdaptiveLoadPlan(0, 0, 0, Collections.singletonList("裂"), 1, false, "none");
        assertFalse(incomplete.focusComplete());
        assertTrue(complete.focusComplete());
        assertFalse(all.focusComplete());
        assertFalse(noTarget.focusComplete());

        RecordsSchedulerModels.ReleaseInfo release = new RecordsSchedulerModels.ReleaseInfo(
                "v1",
                "url",
                Arrays.asList(new RecordsSchedulerModels.ReleaseAsset("notes.txt", "notes"), new RecordsSchedulerModels.ReleaseAsset("kani.apk", "apk"), new RecordsSchedulerModels.ReleaseAsset("kani.apk.sha256", "sha"))
        );
        assertEquals("apk", release.apkAsset().downloadUrl);
        assertEquals("sha", release.checksumAssetFor("kani.apk").downloadUrl);
        assertNull(release.checksumAssetFor("other.apk"));
    }

    @Test
    public void invalidVarargsKeepExplicitCompatibilityErrors() throws Exception {
        try {
            new RecordsSyncModels.Settings("Kiku", "Mining", "Expression", "Reading", "Meaning", "Sentence", "Frequency");
            throw new AssertionError("Expected invalid Settings varargs to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Settings received"));
        }

        assertEquals("only", Records.arg(new Object[]{"only"}, 0, "test"));

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
        RecordsSchedulerModels.ReleaseInfo emptyRelease = new RecordsSchedulerModels.ReleaseInfo("v0", "https://example", Collections.emptyList());
        assertNull(emptyRelease.apkAsset());
        assertNull(emptyRelease.checksumAssetFor("kani.apk"));

        RecordsSchedulerModels.AdaptiveLoadPlan complete = new RecordsSchedulerModels.AdaptiveLoadPlan(
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

        RecordsSchedulerModels.AdaptiveLoadPlan all = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 1, 0, Arrays.asList("拉"), 0, true, "all");
        assertFalse(all.focusComplete());
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(kanji, 1, "meaning", "reading", kanji, 10, "reason", "reason", 1, 0, 0, Collections.emptyList());
    }

    private static RecordsStudyModels.StudyItem item(String kanji) {
        return new RecordsStudyModels.StudyItem(kanji, "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L);
    }

    private static RecordsSyncModels.Settings fullSettingsWithNoisyImportValues() {
        return new RecordsSyncModels.Settings(
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

    private static RecordsSyncModels.Settings settingsWithRest(Object... rest) {
        List<Object> values = new java.util.ArrayList<>();
        Collections.addAll(values, "Frequency", "FreqSort", 21, 2);
        Collections.addAll(values, rest);
        return new RecordsSyncModels.Settings(
                "Kiku",
                "Mining",
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                values.toArray()
        );
    }

    private static RecordsSyncModels.Settings settingsWithSortMode(String mode) {
        return new RecordsSyncModels.Settings(
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
                100,
                3000,
                24,
                3,
                3,
                3,
                3,
                false,
                true,
                false,
                Collections.emptyList(),
                false,
                7.0,
                2,
                1,
                false,
                "",
                mode
        );
    }

    private static StudyItemFixture studyItemFixture() {
        return new StudyItemFixture();
    }

    private static final class SplitProbe extends RecordsSchedulerModels {
        boolean defaultSuspendedCards() {
            return DEFAULT_IMPORT_SUSPENDED_CARDS;
        }

        String defaultModelName() {
            return Settings.kikuDefaults().modelName;
        }
    }

    private static final class StudyItemFixture {
        final RecordsStudyModels.TaskMemory typed = new RecordsStudyModels.TaskMemory("review", 10L, 1.0, 2.0, 3, 0, 1, "good", 2, 4, 5L);
        final RecordsStudyModels.TaskMemory kanji = new RecordsStudyModels.TaskMemory("review", 20L, 1.0, 2.0, 3, 0, 1, "hard", 2, 0, 0L);
        final RecordsStudyModels.TaskMemory font = new RecordsStudyModels.TaskMemory("review", 30L, 1.0, 2.0, 3, 0, 1, "easy", 2, 0, 0L);
        final RecordsStudyModels.TaskMemory word = new RecordsStudyModels.TaskMemory("review", 40L, 1.0, 2.0, 3, 0, 1, "again", 2, 0, 0L);
        final RecordsStudyModels.TaskMemory writing = new RecordsStudyModels.TaskMemory("review", 50L, 1.0, 2.0, 3, 0, 1, "good", 2, 0, 0L);
        final RecordsStudyModels.StudyItem compact = new RecordsStudyModels.StudyItem("裂", "new", 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L);
        final RecordsStudyModels.StudyItem thirteenArg = new RecordsStudyModels.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, 1, 2, 3L, false, "task", 4L, 5, "sig", "tok", 6L);
        final RecordsStudyModels.StudyItem legacyMemories = new RecordsStudyModels.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, 1, 2, 3L, false, "task", 4L, 5, "sig", "tok", 6L, kanji, font, word, writing);
        final RecordsStudyModels.StudyItem full = new RecordsStudyModels.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, -9, -2, -3L, false, null, -4L, -5, null, "tok", 6L, null, null, null, null, null, null, null, -1, -2, -3L, true, null);
    }
}
