package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

private const val TEST_ACTIVE = "active"
private const val TEST_EXPRESSION = "expr"
private const val TEST_READING = "read"
private const val TEST_MEANING = "meaning"
private const val TEST_SENTENCE = "sentence"

class RecordsValueModelsTest {
    @Test
    fun splitModelConstructorsStayHiddenWhileExposingRecordApi() {
        val probe = SplitProbe()
        assertTrue(probe is RecordsBase)
        assertTrue(probe.defaultSuspendedCards())
        assertEquals("Kiku", probe.defaultModelName())
    }

    @Test
    fun timelineAndRepairRecordsNormalizeInputs() {
        val repair = RecordsImportModels.SimilarKanjiWritingRepair(
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
        )
        val completed = RecordsImportModels.SimilarKanjiWritingRepair(
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
        )
        val nullStatus = RecordsImportModels.SimilarKanjiWritingRepair(
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
        )

        assertEquals(0L, repair.id)
        assertEquals("", repair.targetKanji)
        assertEquals("pending", repair.status)
        assertEquals(0L, repair.dueAtMillis)
        assertEquals(0, repair.attempts)
        assertEquals("token", repair.withToken("token", 12L).activeToken)
        assertEquals("done", completed.status)
        assertEquals(TEST_ACTIVE, completed.activeToken)
        assertEquals("pending", nullStatus.status)

        val event = RecordsImportModels.KanjiTimelineEvent(
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
        )
        assertEquals("", event.sourceExpression)
        assertEquals("", event.sourceReading)
        assertEquals("", event.rating)

        val timeline = RecordsStudyModels.KanjiRecoveryTimeline(
            RecordsImportModels.KanjiInventoryItem("拉", "pull", "ら", "拉", 1, 1, false, 5L),
            row("拉"),
            item("拉"),
            listOf(event)
        )
        assertEquals("拉", requireNotNull(timeline.inventoryItem).kanji)
        assertEquals(1, timeline.events.size)
        assertEquals(1, RecordsStudyModels.KanjiRecoveryTimeline(row("拉"), item("拉"), listOf(event)).events.size)
    }

    @Test
    fun taskMemoryLearningRepeatAndReviewStatsCoverFallbacks() {
        val fallback = RecordsStudyModels.TaskMemory("fallback", 1L, 2.0, 3.0, 4, 5, 1, "good", 6)
        val emptyState = RecordsStudyModels.TaskMemory("", 1L, 2.0, 3.0, 4, 5, 1, "good", 6)
        assertSame(fallback, RecordsStudyModels.TaskMemory.decode(null, fallback))
        assertSame(RecordsStudyModels.TaskMemory.initial().state, RecordsStudyModels.TaskMemory.decode("", null).state)
        assertSame(fallback, RecordsStudyModels.TaskMemory.decode("too\tshort", fallback))
        assertSame(fallback, RecordsStudyModels.TaskMemory.decode("new\tbad\t0.4\t5.0\t0\t0\t0\t\t0", fallback))
        assertEquals("new", emptyState.state)

        val decoded = RecordsStudyModels.TaskMemory.decode(
            RecordsStudyModels.TaskMemory(null, -1L, 0.4, 5.0, -2, -3, -4, null, -5).encode(),
            null
        )
        assertEquals("new", decoded.state)
        assertEquals(0L, decoded.dueAtMillis)
        assertEquals("", decoded.lastRating)
        assertEquals(0, decoded.consecutivePasses)
        assertEquals(0L, decoded.lastPassedDueAtMillis)
        val promoted = fallback.withDueAtMillis(10L)
        assertEquals(10L, promoted.dueAtMillis)
        assertEquals(fallback.consecutivePasses, promoted.consecutivePasses)
        val legacyNinePart = RecordsStudyModels.TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7", null)
        val legacyTenPart = RecordsStudyModels.TaskMemory.decode("review\t9\t1.2\t4.3\t5\t1\t2\thard\t7\t3", null)
        assertEquals(0, legacyNinePart.consecutivePasses)
        assertEquals(3, legacyTenPart.consecutivePasses)
        assertEquals("review", RecordsStudyModels.TaskMemory("review", 1L, 2.0, 3.0, 4, 5, 1, "good", 6).state)

        val repeat = RecordsSchedulerModels.LearningRepeat(null, null, null, "bad", -1, -2L, null, -3L, -4L)
        assertEquals("", repeat.kanji)
        assertEquals(RecordsBase.LEARNING_REPEAT_NEW, repeat.repeatType)
        assertEquals(0, repeat.stepIndex)
        assertEquals("tok", repeat.withToken("tok", 10L).activeToken)
        assertEquals(3, repeat.withStep(3, 20L, 30L).stepIndex)
        assertEquals(RecordsBase.LEARNING_REPEAT_REVIEW, RecordsSchedulerModels.LearningRepeat("裂", "sig", "task", RecordsBase.LEARNING_REPEAT_REVIEW, 1, 2L, TEST_ACTIVE, 3L, 4L).repeatType)

        assertEquals(1.0, RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0).retentionProxy(), 0.001)
        assertEquals(0.25, RecordsSchedulerModels.ReviewStats(4, 1, 1, 1, 1, 4, 1).writingFailureRate(), 0.001)
    }

    @Test
    fun settingsLegacyShapesStayCompatible() {
        val eightArg = settingsWithRest(3000, 24, 3, 4)
        val oldNineArg = settingsWithRest(100, 3000, 24, 3, 4)
        val tenArg = settingsWithRest(100, 3000, 24, 3, 4, 6)
        val elevenArg = settingsWithRest(100, 3000, 24, 3, 4, 6, 7)
        val full = fullSettingsWithNoisyImportValues()

        assertEquals(4, eightArg.writingTriggerMissDays)
        assertEquals(3, oldNineArg.recognitionPromotionPasses)
        assertEquals(6, tenArg.recognitionPromotionPasses)
        assertEquals(7, elevenArg.realDueReviewsToMove)
        assertEquals(100, full.suspendedRankMin)
        assertEquals(5000, full.suspendedRankMax)
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, full.newCardSortMode)
    }

    @Test
    fun settingsImportDefaultsNormalizeAndParseTags() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val full = fullSettingsWithNoisyImportValues()

        assertFalse(defaults.importActiveCards)
        assertTrue(defaults.importSuspendedCards)
        assertTrue(full.importTaggedCardsEnabled())
        assertTrue(full.hasImportSourceEnabled())
        assertEquals("mine archive", full.importTagsText())
        assertEquals(RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, full.importWeakFsrsDifficultyThreshold, 0.001)
        assertEquals(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY, settingsWithSortMode(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY).newCardSortMode)
        assertEquals(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY, settingsWithSortMode(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY).newCardSortMode)
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, settingsWithSortMode("not-real").newCardSortMode)
        assertEquals(listOf("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort"), full.requiredFields())
        assertEquals(listOf("mine", "archive"), RecordsBase.parseImportTags(" mine, archive mine "))
        assertTrue(RecordsBase.parseImportTags(" ").isEmpty())
        assertTrue(RecordsBase.parseImportTags(null).isEmpty())
    }

    @Test
    fun schedulerParametersResolveFrequencyRetentionByRank() {
        val parameters = RecordsSchedulerModels.SchedulerParameters(0.90)
            .withFrequencyRetention(true, "1-500=95%\n501-2000=85%")

        assertEquals(0.95, parameters.targetRetentionForRank(200), 0.001)
        assertEquals(0.85, parameters.targetRetentionForRank(1000), 0.001)
        assertEquals(0.90, parameters.targetRetentionForRank(3000), 0.001)
        assertEquals(0.90, parameters.targetRetentionForRank(null), 0.001)
        assertEquals(0.88, parameters.withTargetRetention(0.88).targetRetention, 0.001)
        assertEquals(parameters.frequencyRetentionRanges, parameters.withTargetRetention(0.88).frequencyRetentionRanges)
    }

    @Test
    fun settingsImportFiltersCoverDisabledAndStringTagInputs() {
        val disabled = RecordsSyncModels.Settings(
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
            emptyList<String>(),
            false,
            Double.POSITIVE_INFINITY,
            2,
            1
        )
        assertFalse(disabled.importTaggedCardsEnabled())
        assertFalse(disabled.hasImportSourceEnabled())
        assertEquals(listOf("Expression"), disabled.requiredFields())

        val stringTags = RecordsSyncModels.Settings(
            "Kiku", "Mining", null, " ", "Meaning", "Meaning", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
            false, false, true, listOf<String?>("mine", "archive"), false, 1.2, 2, 1
        )
        val nullTags = RecordsSyncModels.Settings(
            "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
            false, false, true, null, false, 0.1, 2, 1
        )
        val negativeWeakThreshold = RecordsSyncModels.Settings(
            "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
            false, false, false, emptyList<String>(), true, -1.0, 2, 1
        )
        assertEquals(listOf("mine", "archive"), stringTags.importTags)
        assertTrue(nullTags.importTags.isEmpty())
        assertEquals(RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY, negativeWeakThreshold.importWeakFsrsDifficultyThreshold, 0.001)
        assertEquals(listOf("Meaning"), stringTags.requiredFields())
        assertEquals(listOf("mine", "archive"), RecordsBase.parseImportTags("mine,, archive"))

        val blankTags = RecordsSyncModels.Settings(
            "Kiku", "Mining", "Expression", "", "Meaning", "", "", "", 21, 2, 100, 3000, 24, 3, 4, 6, 7,
            false, false, true, listOf<String?>(" ", null, ""), false, 1.2, 2, 1
        )
        assertTrue(blankTags.importTags.isEmpty())
    }

    @Test
    fun settingsBrowserQueryDefaultsAndHelpers() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        assertFalse(defaults.importBrowserQueryCards)
        assertEquals("", defaults.importBrowserQuery)
        assertFalse(defaults.browserQueryImportEnabled())
        assertEquals("", defaults.normalizedBrowserQuery())

        val enabledNonBlank = RecordsSyncModels.Settings(
            "Kiku", "Mining", "Expression", "", "Meaning", "",
            "", "", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
            false, false, false, emptyList<String>(), false, 7.0, 2, 1,
            true, " tag:kani "
        )
        assertTrue(enabledNonBlank.browserQueryImportEnabled())
        assertEquals("tag:kani", enabledNonBlank.normalizedBrowserQuery())
        assertTrue(enabledNonBlank.hasImportSourceEnabled())

        val enabledBlank = RecordsSyncModels.Settings(
            "Kiku", "Mining", "Expression", "", "Meaning", "",
            "", "", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
            false, false, false, emptyList<String>(), false, 7.0, 2, 1,
            true, "   "
        )
        assertFalse(enabledBlank.browserQueryImportEnabled())
        assertFalse(enabledBlank.hasImportSourceEnabled())

        val disabledNonBlank = RecordsSyncModels.Settings(
            "Kiku", "Mining", "Expression", "", "Meaning", "",
            "", "", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
            false, false, false, emptyList<String>(), false, 7.0, 2, 1,
            false, "tag:kani"
        )
        assertFalse(disabledNonBlank.browserQueryImportEnabled())
        assertFalse(disabledNonBlank.hasImportSourceEnabled())
    }

    @Test
    fun cardRecordsCoverFallbacks() {
        val card = RecordsSyncModels.Card(1L, 2L, 0, null, "Deck", 1, 2, 3, 4, 5, 6, true, 1.2, 3.4, 5.6)
        val activeMature = RecordsSyncModels.Card(2L, 3L, 0, "Deck", 1, 2, 3, 20, 5, 0, false)
        assertEquals("", card.deckId)
        assertEquals("Deck", card.deckName)
        assertTrue(card.suspended)
        assertFalse(card.active())
        assertFalse(card.mature(1))
        assertTrue(activeMature.active())
        assertTrue(activeMature.mature(10))
        assertEquals(3.4, requireNotNull(card.fsrsDifficulty), 0.0)
        assertFalse(card.browserQueryMatched)

        val marked = card.withBrowserQueryMatched(true)
        assertTrue(marked.browserQueryMatched)
        assertEquals(card.cardId, marked.cardId)
        assertEquals(card.noteId, marked.noteId)
        assertEquals(card.suspended, marked.suspended)
        assertEquals(card.deckName, marked.deckName)
        assertSame(card, card.withBrowserQueryMatched(false))
    }

    @Test
    fun collectionRowsAndSimilarChoiceRecordsCoverFallbacks() {
        val note = RecordsSyncModels.Note(1L, 2L, "model", mapOf("Front" to "value"), listOf("tag"))
        val card = RecordsSyncModels.Card(1L, 2L, 0, "Deck", 1, 2, 3, 4, 5, 6, false)
        val snapshot = RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card))
        assertEquals(note, snapshot.notesById()[1L])
        assertEquals("", note.field("Missing"))
        assertEquals("value", note.expression(RecordsSyncModels.Settings("m", "t", "Front", "", "", "", "", "", 21, 2, 100, 3000, 24, 3, 3)))

        val example = RecordsImportModels.Example(TEST_ACTIVE, 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING, TEST_SENTENCE, true, 2, 30, 4, 1.0, 2.0, 3.0)
        val dashboard = RecordsImportModels.DashboardRow("裂", 10, "tear", "レツ", "search", 9, "weak", "reason", 1, 2, 3, listOf(example))
        assertEquals(example, dashboard.examples[0])

        val inventory = RecordsImportModels.KanjiInventoryItem(null, null, null, null, -1, -2, true, -3L)
        assertEquals("", inventory.kanji)
        assertEquals(0, inventory.sourceCount)
        assertEquals(0L, inventory.lastSeenAtMillis)

        val pair = RecordsImportModels.SimilarKanjiPair(null, null, null, -1L, -2L)
        assertEquals("", pair.kanjiA)
        assertEquals(0L, pair.firstSeenAtMillis)

        val emptyChoice = RecordsImportModels.SimilarKanjiChoiceCard(null, null, null, null)
        val reviewedChoice = RecordsImportModels.SimilarKanjiChoiceCard("裂", "tear", listOf("裂", "列"), "sig", -1L, 2L, 3L, -4, 5)
        assertFalse(emptyChoice.passed())
        assertTrue(reviewedChoice.passed())
        assertEquals(0L, reviewedChoice.dueAtMillis)
        assertEquals(0, reviewedChoice.correctCount)

        val result = RecordsImportModels.SimilarKanjiChoiceResult(reviewedChoice, null, false, null)
        assertEquals("", result.selectedKanji)
        assertTrue(result.repairKanji.isEmpty())

        val activeFallback = RecordsImportModels.SuspendedSource(
            "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
            RecordsImportModels.SuspendedSourceDetails.builder(TEST_SENTENCE)
                .sourceType(" ")
                .suspended(false)
                .forcePractice(false)
                .mature(true)
                .reviewStats(-1, -2, -3)
                .build()
        )
        val suspendedFallback = RecordsImportModels.SuspendedSource(
            "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
            RecordsImportModels.SuspendedSourceDetails.builder(TEST_SENTENCE)
                .sourceType(null)
                .suspended(true)
                .forcePractice(false)
                .mature(true)
                .reviewStats(1, 2, 3)
                .build()
        )
        val explicit = RecordsImportModels.SuspendedSource(
            "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING,
            RecordsImportModels.SuspendedSourceDetails.builder(TEST_SENTENCE)
                .sourceType(" custom ")
                .suspended(true)
                .forcePractice(false)
                .mature(true)
                .reviewStats(1, 2, 3)
                .fsrs(1.0, 2.0, 3.0)
                .build()
        )
        val nullDetails = RecordsImportModels.SuspendedSource(
            "裂", 1L, 2L, TEST_EXPRESSION, TEST_READING, TEST_MEANING, null as RecordsImportModels.SuspendedSourceDetails?
        )
        assertEquals(RecordsBase.SOURCE_ACTIVE, activeFallback.sourceType)
        assertEquals(RecordsBase.SOURCE_SUSPENDED, suspendedFallback.sourceType)
        assertEquals("custom", explicit.sourceType)
        assertEquals("", nullDetails.sentence)
    }

    @Test
    fun studyItemLegacyConstructorsCoverFallbacks() {
        val fixture = studyItemFixture()

        assertEquals("tok", fixture.compact.activeToken)
        assertEquals("sig", fixture.thirteenArg.answerSignature)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, fixture.legacyMemories.rung)
        assertEquals(fixture.kanji, fixture.legacyMemories.kanjiMeaningMemory)
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, fixture.full.rung)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, fixture.full.phase)
        assertEquals("", fixture.full.suppressedByTaskType)
        assertEquals(0, fixture.full.realAgainStreak)
        assertTrue(fixture.full.hasSimilarKanji)
        assertEquals(RecordsStudyModels.TaskMemory.initial().state, fixture.full.similarKanjiMemory.state)
    }

    @Test
    fun studyItemTaskTypeMemoryRoutingStaysCompatible() {
        val fixture = studyItemFixture()

        assertEquals(fixture.writing, fixture.legacyMemories.memoryForTaskType(BridgeScheduler.TASK_WRITE_KANJI))
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_TYPE_MEANING, fixture.typed).typingMeaningMemory)
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_MEANING_KANJI, fixture.typed).meaningKanjiMemory)
        assertEquals(fixture.font, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_FONT_MEANING, fixture.font).fontMeaningMemory)
        assertEquals(fixture.word, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_WORD_READING, fixture.word).wordReadingMemory)
        assertEquals(fixture.kanji, fixture.legacyMemories.withTaskMemory(null, fixture.kanji).kanjiMeaningMemory)
    }

    @Test
    fun studyItemRungMemoryRoutingStaysCompatible() {
        val fixture = studyItemFixture()

        assertEquals(fixture.kanji, fixture.legacyMemories.memoryForRung(null))
        assertEquals(fixture.writing, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.WRITE_KANJI))
        assertEquals(fixture.typed, fixture.legacyMemories.withTaskMemory(BridgeScheduler.TASK_TYPE_MEANING, fixture.typed).memoryForRung(RecordsBase.LadderRung.TYPE_MEANING))
        assertEquals(RecordsStudyModels.TaskMemory.initial().state, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.MEANING_KANJI).state)
        assertEquals(fixture.kanji, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(fixture.font, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.FONT_MEANING))
        assertEquals(fixture.word, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.WORD_READING))
        assertEquals(RecordsStudyModels.TaskMemory.initial().state, fixture.legacyMemories.memoryForRung(RecordsBase.LadderRung.SIMILAR_KANJI).state)
    }

    @Test
    fun studyItemTaskMemoryCopiesStayCompatible() {
        val fixture = studyItemFixture()
        val fourMemories = fixture.compact.withTaskMemories(fixture.kanji, fixture.font, fixture.word, fixture.writing)
        val fiveMemories = fixture.compact.withTaskMemories(fixture.typed, fixture.kanji, fixture.font, fixture.word, fixture.writing)

        assertEquals(fixture.typed, fixture.legacyMemories.withSimilarKanjiMemory(fixture.typed).similarKanjiMemory)
        assertEquals(fixture.kanji, fourMemories.kanjiMeaningMemory)
        assertEquals(fixture.typed, fiveMemories.typingMeaningMemory)
    }

    @Test
    fun kanjiReadingMemorySlotRoundTrips() {
        val fixture = studyItemFixture()
        // Round-trip via the dedicated setter and via the task-type router.
        val viaSetter = fixture.compact.withKanjiReadingMemory(fixture.word)
        assertEquals(fixture.word, viaSetter.kanjiReadingMemory)
        assertEquals(fixture.word, viaSetter.memoryForRung(RecordsBase.LadderRung.KANJI_READING))
        assertEquals(fixture.word, viaSetter.memoryForTaskType(StudyTaskTypes.KANJI_READING))
        val viaTaskMemory = fixture.compact.withTaskMemory(BridgeScheduler.TASK_KANJI_READING, fixture.font)
        assertEquals(fixture.font, viaTaskMemory.kanjiReadingMemory)
        // Availability round-trips through the flag.
        assertTrue(fixture.compact.withHasKanjiReading(true).rungAvailability().hasKanjiReading)
        assertFalse(fixture.compact.rungAvailability().hasKanjiReading)
    }

    @Test
    fun readingKanjiMemorySlotRoundTrips() {
        val fixture = studyItemFixture()
        val viaSetter = fixture.compact.withReadingKanjiMemory(fixture.word)
        assertEquals(fixture.word, viaSetter.readingKanjiMemory)
        assertEquals(fixture.word, viaSetter.memoryForRung(RecordsBase.LadderRung.READING_KANJI))
        assertEquals(fixture.word, viaSetter.memoryForTaskType(StudyTaskTypes.READING_KANJI))
        val viaTaskMemory = fixture.compact.withTaskMemory(BridgeScheduler.TASK_READING_KANJI, fixture.font)
        assertEquals(fixture.font, viaTaskMemory.readingKanjiMemory)
        assertTrue(fixture.compact.withHasReadingKanji(true).rungAvailability().hasReadingKanji)
        assertFalse(fixture.compact.rungAvailability().hasReadingKanji)
    }

    @Test
    fun sentenceReadingMemorySlotRoundTrips() {
        val fixture = studyItemFixture()
        val viaSetter = fixture.compact.withSentenceReadingMemory(fixture.word)
        assertEquals(fixture.word, viaSetter.sentenceReadingMemory)
        assertEquals(fixture.word, viaSetter.memoryForRung(RecordsBase.LadderRung.SENTENCE_READING))
        assertEquals(fixture.word, viaSetter.memoryForTaskType(StudyTaskTypes.SENTENCE_READING))
        val viaTaskMemory = fixture.compact.withTaskMemory(BridgeScheduler.TASK_SENTENCE_READING, fixture.font)
        assertEquals(fixture.font, viaTaskMemory.sentenceReadingMemory)
        assertTrue(fixture.compact.withHasSentenceReading(true).rungAvailability().hasSentenceReading)
        assertFalse(fixture.compact.rungAvailability().hasSentenceReading)
    }

    @Test
    fun studyItemCopyBuilderTransitionsStayCompatible() {
        val fixture = studyItemFixture()

        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, fixture.compact.copyBuilder().writingRemediationPending(true).build().rung)
        assertEquals(RecordsBase.LadderRung.WORD_READING, fixture.compact.copyBuilder().recognitionStage(2).build().rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, fixture.compact.copyBuilder().state(RecordsBase.LEARNING_REPEAT_REVIEW).build().phase)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, RecordsStudyModels.StudyItem("裂", RecordsBase.LEARNING_REPEAT_REVIEW, 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L).phase)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, RecordsStudyModels.StudyItem("裂", "retired", 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L).phase)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, RecordsStudyModels.StudyItem("裂", "learning", 1L, 0.4, 5.0, 1, 0, 0, 0, "tok", 2L).phase)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, fixture.compact.copyBuilder().writingRemediationPending(true).phase(null).build().phase)
        assertEquals("typing", fixture.compact.withSuppression("typing", 11L, 12).suppressedByTaskType)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, fixture.compact.withRung(RecordsBase.LadderRung.FONT_MEANING).rung)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, fixture.compact.withPhase(RecordsBase.SchedulerPhase.RELEARNING).phase)
        assertEquals(RecordsBase.LadderRung.WORD_READING, fixture.compact.withLadderProgress(RecordsBase.LadderRung.WORD_READING, RecordsBase.SchedulerPhase.REVIEW, 2, 3, 4, 5L).rung)
    }

    @Test
    fun reviewRequestPlansAndReleaseRecordsCoverBranches() {
        val legacyClean = RecordsSchedulerModels.ReviewRequest("裂", "tok", "good", true, true, false, 0)
        val legacyEasy = RecordsSchedulerModels.ReviewRequest("裂", "tok", "easy", true, true, false, 0)
        val legacyMessy = RecordsSchedulerModels.ReviewRequest("裂", "tok", "hard", true, true, false, 0)
        val legacyFailedEasy = RecordsSchedulerModels.ReviewRequest("裂", "tok", "easy", true, false, false, 0)
        val full = RecordsSchedulerModels.ReviewRequest("裂", "tok", "again", false, false, true, false, 2, null, null, null)
        assertTrue(legacyClean.writingClean)
        assertTrue(legacyEasy.writingClean)
        assertFalse(legacyMessy.writingClean)
        assertFalse(legacyFailedEasy.writingClean)
        assertEquals("", full.taskType)
        assertEquals("", full.answerSignature)
        assertEquals("", full.prompt)
        val emptyEvidence = RecordsSchedulerModels.ReviewRequest.ReviewEvidence.empty()
        assertEquals("", emptyEvidence.coreSkill)
        assertEquals("", emptyEvidence.failureCause)
        assertEquals("", emptyEvidence.evidenceSource)
        assertEquals("", emptyEvidence.selectedAnswer)
        assertEquals("", emptyEvidence.correctAnswer)
        assertEquals("", emptyEvidence.answerEvidenceJson)
        assertEquals("", full.withEvidence(emptyEvidence).coreSkill)

        assertTrue(RecordsSchedulerModels.LearningStepSettings.tryParseSteps(",,,").isEmpty())
        assertEquals(listOf(1, 10), RecordsSchedulerModels.LearningStepSettings.tryParseSteps("1m,,10m"))
        assertEquals("1h, 30m", RecordsSchedulerModels.LearningStepSettings.formatSteps(listOf(60, 30)))
        assertEquals("90m", RecordsSchedulerModels.LearningStepSettings.formatSteps(listOf(90)))
        assertEquals("1m, 10m", RecordsSchedulerModels.LearningStepSettings.formatSteps(null))

        val incomplete = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 1, listOf("裂"), 1, false, "pending")
        val complete = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 0, listOf("裂"), 1, false, "done")
        val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 0, listOf("裂"), 1, true, "all")
        val noTarget = RecordsSchedulerModels.AdaptiveLoadPlan(0, 0, 0, listOf("裂"), 1, false, "none")
        assertFalse(incomplete.focusComplete())
        assertTrue(complete.focusComplete())
        assertFalse(all.focusComplete())
        assertFalse(noTarget.focusComplete())

        val release = RecordsSchedulerModels.ReleaseInfo(
            "v1",
            "url",
            listOf(
                RecordsSchedulerModels.ReleaseAsset("notes.txt", "notes"),
                RecordsSchedulerModels.ReleaseAsset("kani.apk", "apk"),
                RecordsSchedulerModels.ReleaseAsset("kani.apk.sha256", "sha")
            )
        )
        assertEquals("apk", requireNotNull(release.apkAsset()).downloadUrl)
        assertEquals("sha", requireNotNull(release.checksumAssetFor("kani.apk")).downloadUrl)
        assertNull(release.checksumAssetFor("other.apk"))
    }

    @Test
    fun invalidVarargsKeepExplicitCompatibilityErrors() {
        try {
            RecordsSyncModels.Settings("Kiku", "Mining", "Expression", "Reading", "Meaning", "Sentence", "Frequency")
            throw AssertionError("Expected invalid Settings varargs to fail")
        } catch (expected: IllegalArgumentException) {
            assertTrue(requireNotNull(expected.message).contains("Settings received"))
        }

        assertEquals("only", RecordsBase.arg(arrayOf<Any?>("only"), 0, "test"))

        val arg: Method = RecordsBase::class.java.getDeclaredMethod("arg", Array<Any?>::class.java, Int::class.javaPrimitiveType, String::class.java)
        arg.isAccessible = true
        try {
            arg.invoke(null, arrayOf<Any?>(arrayOf<Any?>("only")), 2, "test")
            throw AssertionError("Expected private arg guard to fail")
        } catch (expected: InvocationTargetException) {
            assertTrue(requireNotNull(expected.cause).message!!.contains("test expected more arguments"))
        }
    }

    @Test
    fun releaseInfoAndAdaptiveLoadPlanCoverNullBranches() {
        val emptyRelease = RecordsSchedulerModels.ReleaseInfo("v0", "https://example", emptyList<RecordsSchedulerModels.ReleaseAsset>())
        assertNull(emptyRelease.apkAsset())
        assertNull(emptyRelease.checksumAssetFor("kani.apk"))

        val complete = RecordsSchedulerModels.AdaptiveLoadPlan(
            true,
            100,
            1,
            0,
            listOf("拉"),
            0,
            false,
            null
        )
        assertTrue(complete.autoMode)
        assertEquals("", complete.status)
        assertTrue(complete.focusComplete())

        val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 1, 0, listOf("拉"), 0, true, "all")
        assertFalse(all.focusComplete())
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 1, "meaning", "reading", kanji, 10, "reason", "reason", 1, 0, 0, emptyList<RecordsImportModels.Example>())
    }

    private fun item(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L)
    }

    private fun fullSettingsWithNoisyImportValues(): RecordsSyncModels.Settings {
        return RecordsSyncModels.Settings(
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
            listOf<String?>(" mine ", null, "", "archive", "mine"),
            true,
            Double.NaN,
            0,
            0
        )
    }

    private fun settingsWithRest(vararg rest: Any?): RecordsSyncModels.Settings {
        val values = mutableListOf<Any?>("Frequency", "FreqSort", 21, 2)
        values.addAll(rest)
        return RecordsSyncModels.Settings(
            "Kiku",
            "Mining",
            "Expression",
            "ExpressionReading",
            "MainDefinition",
            "Sentence",
            *values.toTypedArray()
        )
    }

    private fun settingsWithSortMode(mode: String): RecordsSyncModels.Settings {
        return RecordsSyncModels.Settings(
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
            emptyList<String>(),
            false,
            7.0,
            2,
            1,
            false,
            "",
            mode
        )
    }

    private fun studyItemFixture(): StudyItemFixture = StudyItemFixture()

    private class SplitProbe : RecordsSchedulerModels() {
        fun defaultSuspendedCards(): Boolean {
            return DEFAULT_IMPORT_SUSPENDED_CARDS
        }

        fun defaultModelName(): String {
            return RecordsSyncModels.Settings.kikuDefaults().modelName
        }
    }

    private class StudyItemFixture {
        val typed = RecordsStudyModels.TaskMemory("review", 10L, 1.0, 2.0, 3, 0, 1, "good", 2, 4, 5L)
        val kanji = RecordsStudyModels.TaskMemory("review", 20L, 1.0, 2.0, 3, 0, 1, "hard", 2, 0, 0L)
        val font = RecordsStudyModels.TaskMemory("review", 30L, 1.0, 2.0, 3, 0, 1, "easy", 2, 0, 0L)
        val word = RecordsStudyModels.TaskMemory("review", 40L, 1.0, 2.0, 3, 0, 1, "again", 2, 0, 0L)
        val writing = RecordsStudyModels.TaskMemory("review", 50L, 1.0, 2.0, 3, 0, 1, "good", 2, 0, 0L)
        val compact = RecordsStudyModels.StudyItem("裂", "new", 1L, 0.4, 5.0, 0, 0, 0, 0, "tok", 2L)
        val thirteenArg = RecordsStudyModels.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, 1, 2, 3L, false, "task", 4L, 5, "sig", "tok", 6L)
        val legacyMemories = RecordsStudyModels.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, 1, 2, 3L, false, "task", 4L, 5, "sig", "tok", 6L, kanji, font, word, writing)
        val full = RecordsStudyModels.StudyItem("裂", "review", 9L, 1.0, 2.0, 3, 1, 2, 3, -9, -2, -3L, false, null, -4L, -5, null, "tok", 6L, null, null, null, null, null, null, null, -1, -2, -3L, true, null)
    }
}
