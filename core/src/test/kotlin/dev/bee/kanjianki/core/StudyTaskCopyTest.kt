package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StudyTaskCopyTest {
    @Test
    fun labelForTaskPreservesKnownTaskCopy() {
        assertEquals("Study", StudyTaskCopy.labelForTask(null))
        assertEquals("Focused recall", StudyTaskCopy.labelForTask("targeted_flashcard"))
        assertEquals("Kanji -> meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.KANJI_MEANING))
        assertEquals("Meaning -> kanji", StudyTaskCopy.labelForTask(StudyTaskTypes.MEANING_KANJI))
        assertEquals("Type the meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.TYPING_MEANING))
        assertEquals("Type the meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.TYPE_MEANING))
        assertEquals("Font -> meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.FONT_MEANING))
        assertEquals("Word -> reading", StudyTaskCopy.labelForTask(StudyTaskTypes.WORD_READING))
        assertEquals("Kanji -> reading", StudyTaskCopy.labelForTask(StudyTaskTypes.KANJI_READING))
        assertEquals("Write kanji", StudyTaskCopy.labelForTask(StudyTaskTypes.WRITE_KANJI))
        assertEquals("Similar kanji", StudyTaskCopy.labelForTask(StudyTaskTypes.SIMILAR_KANJI))
        assertEquals("Quick recall", StudyTaskCopy.labelForTask("meaning_flashcard"))
        assertEquals("Font check", StudyTaskCopy.labelForTask("font_recognition"))
        assertEquals("Repair", StudyTaskCopy.labelForTask("repair_writing"))
        assertEquals("Focused practice", StudyTaskCopy.labelForTask("targeted_writing"))
        assertEquals("New problem kanji", StudyTaskCopy.labelForTask("context_writing"))
        assertEquals("Guided review", StudyTaskCopy.labelForTask("guided_writing"))
        assertEquals("Memory check", StudyTaskCopy.labelForTask("blind_writing"))
        assertEquals("Memory check", StudyTaskCopy.labelForTask("sampled_handwriting"))
        assertEquals("Learn the shape", StudyTaskCopy.labelForTask("confusable_recognition"))
        assertEquals("Study", StudyTaskCopy.labelForTask("unexpected"))
    }

    @Test
    fun flashcardTitlePreservesPromptHeadings() {
        assertEquals("Read this word", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.WORD_READING, false)))
        assertEquals("Type the meaning", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.TYPING_MEANING, false)))
        assertEquals("Type the meaning", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.TYPE_MEANING, false)))
        assertEquals("Choose the kanji", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.MEANING_KANJI, false)))
        assertEquals("Recognise this kanji", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.FONT_MEANING, false)))
        assertEquals("Recognise this kanji", StudyTaskCopy.flashcardTitle(session("font_recognition", false)))
        assertEquals("Name this kanji", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.KANJI_MEANING, false)))
        assertEquals("Name this kanji", StudyTaskCopy.flashcardTitle(null))
    }

    @Test
    fun studyModeLabelPreservesModePills() {
        assertEquals("Practice", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.WORD_READING, true)))
        assertEquals("Read", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.WORD_READING, false)))
        assertEquals("Type", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.TYPING_MEANING, false)))
        assertEquals("Type", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.TYPE_MEANING, false)))
        assertEquals("Recall", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.MEANING_KANJI, false)))
        assertEquals("Recognise", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.KANJI_MEANING, false)))
        assertEquals("Recognise", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.SIMILAR_KANJI, false)))
        assertEquals("Recognise", StudyTaskCopy.studyModeLabel(null))
    }

    @Test
    fun studyModeLabelNamesLearningAndRelearningRepeats() {
        assertEquals(
            "Learn",
            StudyTaskCopy.studyModeLabel(
                sessionWithPhase(StudyTaskTypes.KANJI_MEANING, false, RecordsBase.SchedulerPhase.NEW_LEARNING)
            )
        )
        assertEquals(
            "Recognise",
            StudyTaskCopy.studyModeLabel(
                sessionWithPhase(StudyTaskTypes.KANJI_MEANING, false, RecordsBase.SchedulerPhase.NEW_LEARNING, 0)
            )
        )
        assertEquals(
            "Learn",
            StudyTaskCopy.studyModeLabel(
                sessionWithPhase(StudyTaskTypes.WRITE_KANJI, true, RecordsBase.SchedulerPhase.NEW_LEARNING)
            )
        )
        assertEquals(
            "Relearning",
            StudyTaskCopy.studyModeLabel(
                sessionWithPhase(StudyTaskTypes.KANJI_MEANING, false, RecordsBase.SchedulerPhase.RELEARNING)
            )
        )
        assertEquals(
            "Relearning",
            StudyTaskCopy.studyModeLabel(
                sessionWithPhase(StudyTaskTypes.WRITE_KANJI, true, RecordsBase.SchedulerPhase.RELEARNING)
            )
        )
        assertEquals(
            "Practice",
            StudyTaskCopy.studyModeLabel(
                sessionWithPhase(StudyTaskTypes.WRITE_KANJI, true, RecordsBase.SchedulerPhase.REVIEW)
            )
        )
    }

    @Test
    fun studyCopyTranslatesToJapaneseLocale() {
        withJapaneseLocale {
            assertEquals("学習", StudyTaskCopy.labelForTask(null))
            assertEquals("集中復習", StudyTaskCopy.labelForTask("targeted_flashcard"))
            assertEquals("漢字→意味", StudyTaskCopy.labelForTask(StudyTaskTypes.KANJI_MEANING))
            assertEquals("意味→漢字", StudyTaskCopy.labelForTask(StudyTaskTypes.MEANING_KANJI))
            assertEquals("意味を入力", StudyTaskCopy.labelForTask(StudyTaskTypes.TYPING_MEANING))
            assertEquals("意味を入力", StudyTaskCopy.labelForTask(StudyTaskTypes.TYPE_MEANING))
            assertEquals("フォント→意味", StudyTaskCopy.labelForTask(StudyTaskTypes.FONT_MEANING))
            assertEquals("単語→読み", StudyTaskCopy.labelForTask(StudyTaskTypes.WORD_READING))
            assertEquals("漢字を書く", StudyTaskCopy.labelForTask(StudyTaskTypes.WRITE_KANJI))
            assertEquals("似た漢字", StudyTaskCopy.labelForTask(StudyTaskTypes.SIMILAR_KANJI))
            assertEquals("素早く復習", StudyTaskCopy.labelForTask("meaning_flashcard"))
            assertEquals("フォント確認", StudyTaskCopy.labelForTask("font_recognition"))
            assertEquals("修正", StudyTaskCopy.labelForTask("repair_writing"))
            assertEquals("集中練習", StudyTaskCopy.labelForTask("targeted_writing"))
            assertEquals("新しい問題漢字", StudyTaskCopy.labelForTask("context_writing"))
            assertEquals("ガイド付き復習", StudyTaskCopy.labelForTask("guided_writing"))
            assertEquals("記憶確認", StudyTaskCopy.labelForTask("blind_writing"))
            assertEquals("記憶確認", StudyTaskCopy.labelForTask("sampled_handwriting"))
            assertEquals("形を覚える", StudyTaskCopy.labelForTask("confusable_recognition"))

            assertEquals("この単語を読む", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.WORD_READING, false)))
            assertEquals("意味を入力", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.TYPING_MEANING, false)))
            assertEquals("漢字を選ぶ", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.MEANING_KANJI, false)))
            assertEquals("この漢字を見分ける", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.FONT_MEANING, false)))
            assertEquals("この漢字の意味は？", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.KANJI_MEANING, false)))

            assertEquals("学習", StudyTaskCopy.studyModeLabel(sessionWithPhase(StudyTaskTypes.KANJI_MEANING, false, RecordsBase.SchedulerPhase.NEW_LEARNING)))
            assertEquals("再学習", StudyTaskCopy.studyModeLabel(sessionWithPhase(StudyTaskTypes.KANJI_MEANING, false, RecordsBase.SchedulerPhase.RELEARNING)))
            assertEquals("練習", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.WRITE_KANJI, true)))
            assertEquals("読む", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.WORD_READING, false)))
            assertEquals("入力", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.TYPING_MEANING, false)))
            assertEquals("思い出す", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.MEANING_KANJI, false)))
            assertEquals("見分ける", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.SIMILAR_KANJI, false)))
            assertEquals("見分ける", StudyTaskCopy.studyModeLabel(null))
        }
    }

    @Test
    fun taskPredicatesPreserveStudyUiClassificationRules() {
        assertFalse(StudyTaskCopy.isTeachingTask(null))
        assertTrue(StudyTaskCopy.isTeachingTask(session("context_writing", false)))
        assertTrue(StudyTaskCopy.isTeachingTask(session("guided_writing", false)))
        assertTrue(StudyTaskCopy.isTeachingTask(nullItemSession("context_writing")))
        assertTrue(StudyTaskCopy.isTeachingTask(nullItemSession("guided_writing")))
        assertTrue(StudyTaskCopy.isTeachingTask(sessionWithLearningStep("targeted_writing", 1)))
        assertFalse(StudyTaskCopy.isTeachingTask(nullItemSession("targeted_writing")))
        assertFalse(StudyTaskCopy.isTeachingTask(sessionWithLearningStep("targeted_writing", 2)))
        assertFalse(StudyTaskCopy.isTeachingTask(session(StudyTaskTypes.KANJI_MEANING, false)))

        assertFalse(StudyTaskCopy.isRecallTask(null))
        assertTrue(StudyTaskCopy.isRecallTask(session("blind_writing", true)))
        assertTrue(StudyTaskCopy.isRecallTask(session("sampled_handwriting", true)))
        assertFalse(StudyTaskCopy.isRecallTask(session("guided_writing", true)))

        assertTrue(StudyTaskCopy.isRepairWritingTask(session("repair_writing", true)))
        assertFalse(StudyTaskCopy.isRepairWritingTask(session(StudyTaskTypes.WRITE_KANJI, true)))
        assertFalse(StudyTaskCopy.isRepairWritingTask(null))

        assertTrue(StudyTaskCopy.isFontRecognitionTask(session(StudyTaskTypes.FONT_MEANING, false)))
        assertTrue(StudyTaskCopy.isFontRecognitionTask(session("font_recognition", false)))
        assertFalse(StudyTaskCopy.isFontRecognitionTask(null))
        assertTrue(StudyTaskCopy.isTypingMeaningTask(session(StudyTaskTypes.TYPING_MEANING, false)))
        assertTrue(StudyTaskCopy.isTypingMeaningTask(session(StudyTaskTypes.TYPE_MEANING, false)))
        assertFalse(StudyTaskCopy.isTypingMeaningTask(null))
        assertTrue(StudyTaskCopy.isMeaningKanjiTask(session(StudyTaskTypes.MEANING_KANJI, false)))
        assertFalse(StudyTaskCopy.isMeaningKanjiTask(null))
        assertTrue(StudyTaskCopy.isWordReadingTask(session(StudyTaskTypes.WORD_READING, false)))
        assertFalse(StudyTaskCopy.isWordReadingTask(null))
    }

    private fun session(taskType: String?, writingRequired: Boolean): RecordsSchedulerModels.StudySession {
        return sessionWithLearningStep(taskType, writingRequired, 1)
    }

    private fun sessionWithLearningStep(taskType: String?, learningStep: Int): RecordsSchedulerModels.StudySession {
        return sessionWithLearningStep(taskType, true, learningStep)
    }

    private fun sessionWithLearningStep(
        taskType: String?,
        writingRequired: Boolean,
        learningStep: Int
    ): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            RecordsStudyModels.StudyItem(
                "x",
                "review",
                0L,
                1.0,
                5.0,
                1,
                0,
                learningStep,
                1,
                0,
                0,
                0L,
                false,
                null,
                0L
            ),
            null,
            "token",
            taskType,
            writingRequired,
            "prompt"
        )
    }

    private fun sessionWithPhase(
        taskType: String?,
        writingRequired: Boolean,
        phase: RecordsBase.SchedulerPhase
    ): RecordsSchedulerModels.StudySession = sessionWithPhase(taskType, writingRequired, phase, 1)

    private fun sessionWithPhase(
        taskType: String?,
        writingRequired: Boolean,
        phase: RecordsBase.SchedulerPhase,
        totalReviews: Int
    ): RecordsSchedulerModels.StudySession {
        val session = sessionWithLearningStep(taskType, writingRequired, 1)
        return RecordsSchedulerModels.StudySession(
            session.item!!.copyBuilder().phase(phase).totalReviews(totalReviews).build(),
            session.row,
            session.token,
            session.taskType,
            session.writingRequired,
            session.prompt
        )
    }

    private fun nullItemSession(taskType: String?): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            null,
            null,
            "token",
            taskType,
            true,
            "prompt"
        )
    }

    private fun withJapaneseLocale(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
