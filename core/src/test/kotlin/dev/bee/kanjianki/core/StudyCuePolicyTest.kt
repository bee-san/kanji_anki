package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyCuePolicyTest {
    @Test
    fun dictionaryCueWinsOverCollectionFallback() {
        val fields = DictionaryLookup.KanjiEntryFields(
            "安",
            listOf("peace", "cheap", "duplicate ignored"),
            listOf("アン"),
            listOf("やす.い"),
            emptyList(),
            6,
            3,
            40,
            500,
            1200
        )
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(DictionaryLookup.KanjiEntry(fields))
        )
        val session = session("安", false, BridgeScheduler.TASK_KANJI_MEANING)
        val example = example("安心", "アンシン", "old collection meaning")

        assertEquals(
            listOf("Peace, cheap", "Reading: あんしん", "From: 安心"),
            StudyCuePolicy.answerLines(lookup, session, example, false)
        )
    }

    @Test
    fun collectionFallbackIsCleanedWhenDictionaryHasNoEntry() {
        val session = session("語", false, BridgeScheduler.TASK_KANJI_MEANING)
        val example = example("言語", "", "(noun) JMdict [x] 1. language\nspeech")

        assertEquals(
            listOf("Language speech", "Reading: ご", "From: 言語"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), session, example, false)
        )
        assertEquals("Small, tiny", StudyCuePolicy.displayGlosses(listOf(" small ", "small", "tiny"), 3))
        assertEquals("Collection clue", StudyCuePolicy.cleanFallbackMeaning("", "", 40))
        assertEquals(
            "A very long clue with enough words to...",
            StudyCuePolicy.cleanFallbackMeaning(
                "a very long clue with enough words to be compacted without chopping the first word",
                "",
                40
            )
        )
    }

    @Test
    fun meaningChoiceAnswerLinesHeadlineCompoundMeaningBeforeIndividualGlosses() {
        val fields = DictionaryLookup.KanjiEntryFields(
            "脱",
            listOf("undress", "removing"),
            listOf("ダツ"),
            listOf("ぬ.ぐ"),
            emptyList(),
            11,
            0,
            47,
            1500,
            2200
        )
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(DictionaryLookup.KanjiEntry(fields))
        )
        val session = session(
            "脱",
            false,
            BridgeScheduler.TASK_MEANING_KANJI,
            "Loss of strength exhaustion weakness",
            "だつりょく"
        )
        val example = example("脱力", "だつりょく", "Loss of strength exhaustion weakness")

        assertEquals(
            listOf(
                "Loss of strength exhaustion weakness",
                "Reading: だつりょく",
                "From: 脱力",
                "Individual kanji meanings: Undress, removing"
            ),
            StudyCuePolicy.meaningChoiceAnswerLines(lookup, session, example)
        )
    }

    @Test
    fun meaningChoiceAnswerLinesFallsBackForNonMeaningKanjiTask() {
        val fields = DictionaryLookup.KanjiEntryFields(
            "脱",
            listOf("undress", "removing"),
            listOf("ダツ"),
            listOf("ぬ.ぐ"),
            emptyList(),
            11,
            0,
            47,
            1500,
            2200
        )
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(DictionaryLookup.KanjiEntry(fields))
        )
        val session = session(
            "脱",
            false,
            BridgeScheduler.TASK_KANJI_MEANING,
            "Loss of strength exhaustion weakness",
            "だつりょく"
        )
        val example = example("脱力", "だつりょく", "Loss of strength exhaustion weakness")

        assertEquals(
            listOf(
                "Undress, removing",
                "Reading: だつりょく",
                "From: 脱力"
            ),
            StudyCuePolicy.meaningChoiceAnswerLines(lookup, session, example)
        )
    }

    @Test
    fun meaningChoiceAnswerLinesFallsBackWhenCompoundPromptIsUnavailable() {
        val regular = session("脱", false, BridgeScheduler.TASK_MEANING_KANJI)

        assertEquals(
            listOf("Collection meaning", "Reading: ご", "From: 脱"),
            StudyCuePolicy.meaningChoiceAnswerLines(DictionaryLookup.empty(), regular, example("脱", null, null))
        )
        assertEquals(
            listOf("Collection meaning", "Reading: ご", "From: 外"),
            StudyCuePolicy.meaningChoiceAnswerLines(DictionaryLookup.empty(), regular, example("外", null, null))
        )
        assertEquals(
            listOf("Collection meaning", "Reading: ご"),
            StudyCuePolicy.meaningChoiceAnswerLines(DictionaryLookup.empty(), regular, null)
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.meaningChoiceAnswerLines(DictionaryLookup.empty(), withRow(null, regular.item), example("脱力", "だつりょく", "loss"))
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.meaningChoiceAnswerLines(DictionaryLookup.empty(), withRow(regular.row, null), example("脱力", "だつりょく", "loss"))
        )
    }

    @Test
    fun meaningChoiceAnswerLinesUsesTestedMeaningWhenSourceCompoundIsMissing() {
        val fields = DictionaryLookup.KanjiEntryFields(
            "脱",
            listOf("undress", "removing"),
            listOf("ダツ"),
            listOf("ぬ.ぐ"),
            emptyList(),
            11,
            0,
            47,
            1500,
            2200
        )
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(DictionaryLookup.KanjiEntry(fields))
        )
        val session = session(
            "脱",
            false,
            BridgeScheduler.TASK_MEANING_KANJI,
            "Loss of strength exhaustion weakness",
            "だつりょく"
        )

        assertEquals(
            listOf(
                "Undress, removing",
                "Reading: だつりょく"
            ),
            StudyCuePolicy.meaningChoiceAnswerLines(lookup, session, null)
        )
    }

    @Test
    fun meaningChoiceAnswerLinesOmitsDuplicateOrMissingIndividualGlosses() {
        val duplicateFields = DictionaryLookup.KanjiEntryFields(
            "脱",
            listOf("Loss of strength exhaustion weakness"),
            emptyList(),
            emptyList(),
            emptyList(),
            11,
            0,
            47,
            1500,
            2200
        )
        val duplicateLookup = DictionaryLookup.fromKanjiEntries(
            listOf(DictionaryLookup.KanjiEntry(duplicateFields))
        )
        val session = session(
            "脱",
            false,
            BridgeScheduler.TASK_MEANING_KANJI,
            "Loss of strength exhaustion weakness",
            "だつりょく"
        )

        assertEquals(
            listOf(
                "Loss of strength exhaustion weakness",
                "Reading: だつりょく",
                "From: 脱力"
            ),
            StudyCuePolicy.meaningChoiceAnswerLines(duplicateLookup, session, example("脱力", null, null))
        )
        assertEquals(
            listOf(
                "Loss of strength exhaustion weakness",
                "Reading: だつりょく",
                "From: 脱力"
            ),
            StudyCuePolicy.meaningChoiceAnswerLines(null, session, example("脱力", null, null))
        )
    }

    @Test
    fun wordReadingCueUsesExampleReadingAndExpression() {
        val session = session("読", false, BridgeScheduler.TASK_WORD_READING)
        val example = example("読書", "ドクショ", "")

        assertEquals(
            listOf("Reading: どくしょ", "From: 読書"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), session, example, true)
        )
    }

    @Test
    fun wordReadingCueDoesNotRequireStudyItem() {
        val session = RecordsSchedulerModels.StudySession(
            null,
            row("読"),
            "token",
            BridgeScheduler.TASK_WORD_READING,
            false,
            "prompt"
        )

        assertEquals(
            listOf("Reading: どくしょ", "From: 読書"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), session, example("読書", "ドクショ", ""), true)
        )
    }

    @Test
    fun emptySessionsAndBlankWordReadingCuesUseCollectionClueFallback() {
        val wordReading = session("読", false, BridgeScheduler.TASK_WORD_READING, "", null)

        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.answerLines(null, null, null, false)
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.answerLines(null, wordReading, example(null, null, ""), true)
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), wordReading, example(null, null, ""), true)
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), wordReading, null, true)
        )
    }

    @Test
    fun nullRowsAndExamplesPreserveExistingFallbacks() {
        val noRow = RecordsSchedulerModels.StudySession(
            RecordsStudyModels.StudyItem(
                "空",
                "new",
                1234L,
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                "",
                0L,
                0,
                "answer-signature",
                "active-token",
                100L
            ),
            null,
            "token",
            BridgeScheduler.TASK_KANJI_MEANING,
            false,
            ""
        )
        val regular = session("語", false, BridgeScheduler.TASK_KANJI_MEANING)

        assertEquals(
            listOf("Collection clue"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), noRow, null, false)
        )
        assertEquals(
            listOf("Collection meaning", "Reading: ご", "From: 言語"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), regular, example("言語", "ゴ", ""), false)
        )
        assertEquals(
            listOf("Collection meaning", "Reading: ご"),
            StudyCuePolicy.answerLines(DictionaryLookup.empty(), regular, null, false)
        )
    }

    @Test
    fun exposesStudyCueForCoreCallers() {
        val session = session("読", false, BridgeScheduler.TASK_WORD_READING)
        val example = example(" 読書 ", " ドクショ ", "")

        val cue = StudyCuePolicy.studyCue(DictionaryLookup.empty(), session, example, true)

        assertEquals("", cue.meaning)
        assertEquals("ドクショ", cue.reading)
        assertEquals("読書", cue.fromExpression)
        assertEquals(DictionaryLookup.SOURCE_ANKI, cue.meaningSource)
    }

    private fun session(
        kanji: String,
        writingRequired: Boolean,
        taskType: String,
        primaryMeaning: String = "collection meaning",
        reading: String? = "ご"
    ): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(
            kanji,
            "new",
            1234L,
            0.0,
            0.0,
            0,
            0,
            0,
            0,
            0,
            0,
            0L,
            writingRequired,
            "",
            0L,
            0,
            "answer-signature",
            "active-token",
            100L
        )
        val row = RecordsImportModels.DashboardRow(
            kanji,
            null,
            primaryMeaning,
            reading,
            kanji,
            1,
            "reason",
            "Needs practice",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )
        return RecordsSchedulerModels.StudySession(item, row, "session-token", taskType, writingRequired, "prompt text")
    }

    private fun withRow(
        row: RecordsImportModels.DashboardRow?,
        item: RecordsStudyModels.StudyItem?
    ): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item,
            row,
            "session-token",
            BridgeScheduler.TASK_MEANING_KANJI,
            false,
            "prompt text"
        )
    }

    private fun example(expression: String?, reading: String?, meaning: String?): RecordsImportModels.Example {
        return RecordsImportModels.Example("anki", 1L, 2L, expression, reading, meaning, "", false, 0)
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            null,
            "collection meaning",
            "ご",
            kanji,
            1,
            "reason",
            "Needs practice",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )
    }
}
