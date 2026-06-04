package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyTextCopyTest {
    @Test
    fun countAndCompactTextPreserveAppCopyHelpers() {
        assertEquals("1 item", StudyTextCopy.countText(1, "item", "items"))
        assertEquals("2 items", StudyTextCopy.countText(2, "item", "items"))
        assertEquals("1 null", StudyTextCopy.countText(1, null, "items"))
        assertEquals("2 null", StudyTextCopy.countText(2, "item", null))
        assertEquals("", StudyTextCopy.compact(null, 12))
        assertEquals("short", StudyTextCopy.compact("short", 12))
        assertEquals("a very long s...", StudyTextCopy.compact("a very long sentence that should be shortened", 16))
    }

    @Test
    fun rowMeaningAndCleanLearnerTextUseDictionaryMeaningCleanup() {
        assertEquals("Split", StudyTextCopy.rowMeaning(row("裂", "meaning: split", "fallback", emptyList())))
        assertEquals("Fallback", StudyTextCopy.rowMeaning(row("裂", "", "fallback", emptyList())))
        assertEquals("Collection clue", StudyTextCopy.rowMeaning(null))
        assertEquals("Quiet", StudyTextCopy.cleanLearnerText("(suru verb) quiet", "", 72))
    }

    @Test
    fun sessionCluePrefersDictionaryThenRowThenPrompt() {
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("裂", "split", "tear")))
        val item = studyItem("裂")
        val row = row("裂", "row meaning", "reason", emptyList())

        assertEquals("Split, tear", StudyTextCopy.sessionClue(lookup, session(item, row, "fallback prompt")))
        assertEquals("Row meaning", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(item, row, "fallback prompt")))
        assertEquals("Fallback prompt", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(studyItem("?"), null, "fallback prompt")))
        assertEquals("Collection clue", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(studyItem("?"), null, "")))
        assertEquals("Collection clue", StudyTextCopy.sessionClue(DictionaryLookup.empty(), null))
    }

    @Test
    fun canonicalKanjiMeaningFallsBackWhenDictionaryHasNoGloss() {
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("裂")))

        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(DictionaryLookup.empty(), "?", "fallback", 40))
        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(lookup, "裂", "fallback", 40))
        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(null, "裂", "fallback", 40))
        assertEquals(
            "Very long meaning...",
            StudyTextCopy.canonicalKanjiMeaning(
                DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("長", "very long meaning that compacts"))),
                "長",
                "fallback",
                21,
            ),
        )
    }

    @Test
    fun wordPromptPrefersWordReadingExampleExpression() {
        val active = example("active", "活動語")
        val suspended = example("suspended", "休止語")
        val item = studyItem("語")

        assertEquals("休止語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", listOf(active, suspended)), "prompt")))
        assertEquals("活動語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", listOf(active)), "prompt")))
        assertEquals("語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", emptyList()), "prompt")))
        assertEquals("", StudyTextCopy.wordPrompt(null))
    }

    @Test
    fun heroQuestionUsesWordReadingTaskOnly() {
        assertEquals("What is the reading?", StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.WORD_READING)))
        assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.KANJI_MEANING)))
        assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(null))
    }

    @Test
    fun collectionMeaningForSessionUsesSelectedExampleThenRowMeaning() {
        val active = example("active", "活動語", "active meaning")
        val suspended = example("suspended", "休止語", "suspended meaning")
        val item = studyItem("語")
        val row = row("語", "language", "reason", listOf(active, suspended))

        assertEquals("suspended meaning", StudyTextCopy.collectionMeaningForSession(session(item, row, "prompt", StudyTaskTypes.WORD_READING)))
        assertEquals("active meaning", StudyTextCopy.collectionMeaningForSession(session(item, row, "prompt", StudyTaskTypes.KANJI_MEANING)))
        assertEquals("language", StudyTextCopy.collectionMeaningForSession(session(item, row("語", "language", "reason", emptyList()), "prompt")))
        assertEquals("", StudyTextCopy.collectionMeaningForSession(null))
        assertEquals("", StudyTextCopy.collectionMeaningForSession(session(item, null, "prompt")))
    }

    @Test
    fun meaningKanjiChoiceCopyCleansLearnerMeaningAndPreservesResultBranches() {
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "静",
            "(suru verb) quiet",
            "しず",
            listOf("静", "青", "清", "晴"),
        )

        assertEquals("Which kanji means Quiet?", StudyTextCopy.meaningKanjiChoiceQuestion(card, "fallback"))
        assertEquals("Correct. 静 means Quiet.", StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", true))
        assertEquals("Answer: 静 · Quiet", StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", false))
        assertEquals("Which kanji means Fallback clue?", StudyTextCopy.meaningKanjiChoiceQuestion(null, "fallback clue"))
        assertEquals("Typing answer accepted.", StudyTextCopy.typingAnswerAcceptedToast())
    }

    @Test
    fun meaningKanjiChoiceCopyUsesTestedCompoundMeaningOverIndividualKanjiGloss() {
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("脱", "undress", "removing")))
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "脱",
            "Loss of strength exhaustion weakness",
            "だつりょく",
            listOf("脱", "弱", "欠", "疲"),
        )

        assertEquals("Which kanji means Loss of strength exhaustion weakness?", StudyTextCopy.meaningKanjiChoiceQuestion(lookup, card, "fallback"))
        assertEquals("Correct. 脱 means Loss of strength exhaustion weakness.", StudyTextCopy.meaningKanjiChoiceResult(lookup, card, "fallback", true))
        assertEquals("Answer: 脱 · Loss of strength exhaustion weakness", StudyTextCopy.meaningKanjiChoiceResult(lookup, card, "fallback", false))
    }

    @Test
    fun studyDoneCopyPreservesFocusAndRunSummaryText() {
        assertEquals("Today's focus done", StudyTextCopy.studyDoneTitle())
        assertEquals(
            "Kani finished today's adaptive focus. Keep going or stop here.",
            StudyTextCopy.adaptiveFocusDoneBody(),
        )
        assertEquals(
            "Kani finished this study session. Keep going or stop here.",
            StudyTextCopy.studyRunDoneBody(),
        )
        assertEquals("Today's focus: 0 of 7 left", StudyTextCopy.adaptiveFocusDoneSummary(7))
        assertEquals("1 kanji moved forward this session", StudyTextCopy.movedForwardSummary(1))
        assertEquals("3 kanji moved forward this session", StudyTextCopy.movedForwardSummary(3))
        assertEquals("1 kanji was missed and will come back soon", StudyTextCopy.missedSummary(1))
        assertEquals("2 kanji were missed and will come back soon", StudyTextCopy.missedSummary(2))
        assertEquals("1 task completed", StudyTextCopy.completedTaskSummary(1))
        assertEquals("4 tasks completed", StudyTextCopy.completedTaskSummary(4))
    }

    @Test
    fun similarRepairPromptPreservesRepairCopyBranches() {
        assertEquals("You picked 提 — write 拉.", StudyTextCopy.similarRepairPrompt(repair("拉", "提", "pull")))
        assertEquals("Write 拉.", StudyTextCopy.similarRepairPrompt(repair("拉", "", "")))
        assertEquals("Repair saved.", StudyTextCopy.similarWritingRepairSavedToast(true))
        assertEquals("Saved. Try that repair again.", StudyTextCopy.similarWritingRepairSavedToast(false))
    }

    @Test
    fun studyReasonLineIsHiddenFromStudyCards() {
        val item = studyItem("裂")
        val row = row("裂", "split", "reason", emptyList())
        val session = session(item, row, "prompt")

        assertEquals("", StudyTextCopy.studyReasonLine(true, session, 3, 1000L))
        assertEquals("", StudyTextCopy.studyReasonLine(false, session, 3, 1000L))
        assertEquals("", StudyTextCopy.studyReasonLine(false, null, 3, 1000L))
        assertEquals("", StudyTextCopy.studyReasonLine(false, session(item, null, "prompt"), 3, 1000L))
    }

    @Test
    fun copyHelpersTolerateLegacyNullItemSessionSentinel() {
        val row = row("裂", "split", "reason", emptyList())
        val session = session(null, row, "fallback prompt")

        assertEquals("Split", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session))
        assertEquals("", StudyTextCopy.wordPrompt(session))
        assertEquals("", StudyTextCopy.studyReasonLine(false, session, 3, 1000L))
    }

    private fun session(
        item: RecordsStudyModels.StudyItem?,
        row: RecordsImportModels.DashboardRow?,
        prompt: String,
    ): RecordsSchedulerModels.StudySession = session(item, row, prompt, StudyTaskTypes.KANJI_MEANING)

    private fun session(
        item: RecordsStudyModels.StudyItem?,
        row: RecordsImportModels.DashboardRow?,
        prompt: String,
        taskType: String,
    ): RecordsSchedulerModels.StudySession = RecordsSchedulerModels.StudySession(item, row, "token", taskType, false, prompt)

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L)

    private fun row(
        kanji: String,
        meaning: String,
        reason: String,
        examples: List<RecordsImportModels.Example>,
    ): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            900,
            meaning,
            "reading",
            "search",
            1,
            reason,
            "reason text",
            1,
            0,
            1,
            examples,
        )

    private fun example(sourceType: String, expression: String): RecordsImportModels.Example =
        example(sourceType, expression, "meaning")

    private fun example(sourceType: String, expression: String, meaning: String): RecordsImportModels.Example =
        RecordsImportModels.Example(sourceType, 1L, 2L, expression, "reading", meaning, "sentence", false, 0)

    private fun repair(repairKanji: String, wrongSelection: String, promptMeaning: String): RecordsImportModels.SimilarKanjiWritingRepair =
        RecordsImportModels.SimilarKanjiWritingRepair(
            1L,
            repairKanji,
            repairKanji,
            "$repairKanji|$wrongSelection",
            wrongSelection,
            promptMeaning,
            "pending",
            0L,
            "",
            0,
            0L,
            0L,
            0L,
        )

    private fun kanjiEntry(literal: String, vararg meanings: String): DictionaryLookup.KanjiEntry =
        DictionaryLookup.KanjiEntry(
            DictionaryLookup.KanjiEntryFields(
                literal,
                meanings.asList(),
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                0,
                0,
                0,
                null,
            ),
        )
}
