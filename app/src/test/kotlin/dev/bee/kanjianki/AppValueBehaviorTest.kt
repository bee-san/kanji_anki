package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.study.RecognitionCandidate
import dev.bee.kanjianki.core.study.WritingAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AppValueBehaviorTest {
    @Test
    fun studyCueTextsUseDictionaryCueBeforeCollectionFallback() {
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
            1200,
        )
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(DictionaryLookup.KanjiEntry(fields))
        )
        val session = session("安", false, BridgeScheduler.TASK_KANJI_MEANING)
        val example = example("安心", "アンシン", "old collection meaning")

        val lines = StudyCueTexts.answerLines(lookup, session, example, false)

        assertEquals(listOf("Peace, cheap", "Reading: あんしん", "From: 安心"), lines)
    }

    @Test
    fun meaningKanjiChoiceCopyUsesTestedCompoundMeaningOverDictionaryGloss() {
        val lookup = DictionaryLookup.fromKanjiEntries(
            listOf(
                DictionaryLookup.KanjiEntry(
                    DictionaryLookup.KanjiEntryFields(
                        "脱",
                        listOf("undress", "remove"),
                        listOf("ダツ"),
                        listOf("ぬ.ぐ"),
                        emptyList(),
                        11,
                        3,
                        40,
                        500,
                        1200,
                    )
                )
            )
        )
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "脱",
            "Loss of strength exhaustion weakness",
            "だつりょく",
            listOf("脱", "説", "税", "悦")
        )

        assertEquals(
            "Which kanji means Loss of strength exhaustion weakness?",
            StudyTextCopy.meaningKanjiChoiceQuestion(lookup, card, "undress, removing")
        )
        assertEquals(
            "Correct. 脱 means Loss of strength exhaustion weakness.",
            StudyTextCopy.meaningKanjiChoiceResult(lookup, card, "undress, removing", true)
        )
        assertEquals(
            "Answer: 脱 · Loss of strength exhaustion weakness",
            StudyTextCopy.meaningKanjiChoiceResult(lookup, card, "undress, removing", false)
        )
    }

    @Test
    fun studyCueTextsFallbacksCleanCollectionClues() {
        val session = session("語", false, BridgeScheduler.TASK_KANJI_MEANING)
        val example = example("言語", "", "(noun) JMdict [x] 1. language\nspeech")

        val lines = StudyCueTexts.answerLines(DictionaryLookup.empty(), session, example, false)

        assertEquals(listOf("Language speech", "Reading: ご", "From: 言語"), lines)
        assertEquals("Small, tiny", StudyCueTexts.displayGlosses(listOf(" small ", "small", "tiny"), 3))
        assertEquals("Collection clue", StudyCueTexts.cleanFallbackMeaning("", "", 40))
        assertEquals(
            "A very long clue with enough words to...",
            StudyCueTexts.cleanFallbackMeaning(
                "a very long clue with enough words to be compacted without chopping the first word",
                "",
                40,
            )
        )
    }

    @Test
    fun studyCueTextsHandleEmptyAndWordReadingSessions() {
        val emptyLines = StudyCueTexts.answerLines(DictionaryLookup.empty(), null, null, false)
        val session = session("読", false, BridgeScheduler.TASK_WORD_READING)
        val example = example("読書", "ドクショ", "")

        val wordReadingLines = StudyCueTexts.answerLines(DictionaryLookup.empty(), session, example, true)

        assertEquals(listOf("Collection clue"), emptyLines)
        assertEquals(listOf("Reading: どくしょ", "From: 読書"), wordReadingLines)
    }

    @Test
    fun wordReadingCueDoesNotInventReadingWhenExampleAndRowAreBlank() {
        val session = session("読", false, BridgeScheduler.TASK_WORD_READING, "", "")
        val blankExample = example("", "", "")

        val lines = StudyCueTexts.answerLines(DictionaryLookup.empty(), session, blankExample, true)

        assertEquals(listOf("Collection clue"), lines)
    }

    @Test
    fun studyCueTextsHandleNullRowsAndExamples() {
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
                100L,
            ),
            null,
            "token",
            BridgeScheduler.TASK_KANJI_MEANING,
            false,
            ""
        )
        val regular = session("語", false, BridgeScheduler.TASK_KANJI_MEANING)
        val wordReading = session("読", false, BridgeScheduler.TASK_WORD_READING, "", null)
        val emptyMeaning = example("言語", "ゴ", "")
        val nullExpression = example(null, null, "")

        assertEquals(
            listOf("Collection clue"),
            StudyCueTexts.answerLines(DictionaryLookup.empty(), noRow, null, false)
        )
        assertEquals(
            listOf("Collection meaning", "Reading: ご", "From: 言語"),
            StudyCueTexts.answerLines(DictionaryLookup.empty(), regular, emptyMeaning, false)
        )
        assertEquals(
            listOf("Collection meaning", "Reading: ご"),
            StudyCueTexts.answerLines(DictionaryLookup.empty(), regular, null, false)
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCueTexts.answerLines(DictionaryLookup.empty(), wordReading, nullExpression, true)
        )
        assertEquals(
            listOf("Collection clue"),
            StudyCueTexts.answerLines(DictionaryLookup.empty(), wordReading, null, true)
        )
    }

    @Test
    fun studyReviewRequestsMapWritingAnalysisIntoReviewPayload() {
        val session = session("書", true, BridgeScheduler.TASK_WRITE_KANJI)
        val analysis = WritingAnalysis(
            WritingAnalysis.Status.CLOSE,
            "hard",
            true,
            "Close enough to pass, but not clean.",
            emptyList(),
            null,
        )

        val mapped = StudyReviewRequestPolicy.from(session, StudyReviewWritingOutcome.from(analysis), 2, "easy", false)
        val request = mapped.request()

        assertEquals("hard", mapped.ratingCode())
        assertEquals("hard", request.rating)
        assertEquals("書", request.kanji)
        assertEquals("session-token", request.token)
        assertTrue(request.writingRequired)
        assertTrue(request.writingPassed)
        assertFalse(request.writingClean)
        assertFalse(request.manualOverride)
        assertEquals(2, request.hintsUsed)
        assertEquals(BridgeScheduler.TASK_WRITE_KANJI, request.taskType)
        assertEquals("answer-signature", request.answerSignature)
        assertEquals("prompt text", request.prompt)
    }

    @Test
    fun studyReviewRequestsRespectManualOverrideAndNonWritingTasks() {
        val writingSession = session("筆", true, BridgeScheduler.TASK_WRITE_KANJI)
        val readingSession = session("読", false, BridgeScheduler.TASK_WORD_READING)

        val override = StudyReviewRequestPolicy.from(writingSession, null, 0, "easy", true)
        val nonWriting = StudyReviewRequestPolicy.from(readingSession, null, 0, "good", false)

        assertEquals("easy", override.ratingCode())
        assertFalse(override.request().writingPassed)
        assertTrue(override.request().manualOverride)
        assertEquals("good", nonWriting.ratingCode())
        assertTrue(nonWriting.request().writingPassed)
        assertFalse(nonWriting.request().writingClean)
    }

    @Test
    fun studyReviewRequestsDistinguishCleanPassAndFailedWritingAnalysis() {
        val writingSession = session("清", true, BridgeScheduler.TASK_WRITE_KANJI)
        val cleanPass = WritingAnalysis(
            WritingAnalysis.Status.PASS,
            "good",
            true,
            "Clean pass.",
            listOf(RecognitionCandidate("清", 0.95f)),
            null,
        )
        val failed = WritingAnalysis(
            WritingAnalysis.Status.WRONG,
            "again",
            false,
            "Wrong shape.",
            emptyList(),
            null,
        )

        val clean = StudyReviewRequestPolicy.from(writingSession, StudyReviewWritingOutcome.from(cleanPass), 1, "good", false)
        val fail = StudyReviewRequestPolicy.from(writingSession, StudyReviewWritingOutcome.from(failed), 3, "good", false)

        assertTrue(clean.request().writingPassed)
        assertTrue(clean.request().writingClean)
        assertEquals("good", clean.ratingCode())
        assertFalse(fail.request().writingPassed)
        assertFalse(fail.request().writingClean)
        assertEquals("again", fail.ratingCode())
        assertEquals(3, fail.request().hintsUsed)
    }

    @Test
    fun studyTokenFactoryKeepsActiveTokensAndCreatesKanjiPrefixedTokens() {
        val existing = StudyTokenFactory.studyItem("学", "already-active")
        val generated = StudyTokenFactory.studyItem("学", "")
        val generatedFromNull = StudyTokenFactory.studyItem("習", null)

        assertEquals("already-active", existing)
        assertTrue(generated.startsWith("学-"))
        assertNotEquals("学-", generated)
        UUID.fromString(generated.substring("学-".length))
        assertTrue(generatedFromNull.startsWith("習-"))
        UUID.fromString(generatedFromNull.substring("習-".length))
    }

    companion object {
        private fun session(
            kanji: String,
            writingRequired: Boolean,
            taskType: String,
            primaryMeaning: String = "collection meaning",
            reading: String? = "ゴ",
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
                100L,
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
                emptyList<String>(),
            )
            return RecordsSchedulerModels.StudySession(item, row, "session-token", taskType, writingRequired, "prompt text")
        }

        private fun example(expression: String?, reading: String?, meaning: String): RecordsImportModels.Example {
            return RecordsImportModels.Example("anki", 1L, 2L, expression, reading, meaning, "", false, 0)
        }
    }
}
