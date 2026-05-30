package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudyTaskCopyTest {
    @Test
    public void labelForTaskPreservesKnownTaskCopy() {
        assertEquals("Study", StudyTaskCopy.labelForTask(null));
        assertEquals("Focused recall", StudyTaskCopy.labelForTask("targeted_flashcard"));
        assertEquals("Kanji -> meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.KANJI_MEANING));
        assertEquals("Meaning -> kanji", StudyTaskCopy.labelForTask(StudyTaskTypes.MEANING_KANJI));
        assertEquals("Type the meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.TYPING_MEANING));
        assertEquals("Type the meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.TYPE_MEANING));
        assertEquals("Font -> meaning", StudyTaskCopy.labelForTask(StudyTaskTypes.FONT_MEANING));
        assertEquals("Word -> reading", StudyTaskCopy.labelForTask(StudyTaskTypes.WORD_READING));
        assertEquals("Write kanji", StudyTaskCopy.labelForTask(StudyTaskTypes.WRITE_KANJI));
        assertEquals("Similar kanji", StudyTaskCopy.labelForTask(StudyTaskTypes.SIMILAR_KANJI));
        assertEquals("Quick recall", StudyTaskCopy.labelForTask("meaning_flashcard"));
        assertEquals("Font check", StudyTaskCopy.labelForTask("font_recognition"));
        assertEquals("Repair", StudyTaskCopy.labelForTask("repair_writing"));
        assertEquals("Focused practice", StudyTaskCopy.labelForTask("targeted_writing"));
        assertEquals("New problem kanji", StudyTaskCopy.labelForTask("context_writing"));
        assertEquals("Guided review", StudyTaskCopy.labelForTask("guided_writing"));
        assertEquals("Memory check", StudyTaskCopy.labelForTask("blind_writing"));
        assertEquals("Memory check", StudyTaskCopy.labelForTask("sampled_handwriting"));
        assertEquals("Learn the shape", StudyTaskCopy.labelForTask("confusable_recognition"));
        assertEquals("Study", StudyTaskCopy.labelForTask("unexpected"));
    }

    @Test
    public void flashcardTitlePreservesPromptHeadings() {
        assertEquals("Read this word", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.WORD_READING, false)));
        assertEquals("Type the meaning", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.TYPING_MEANING, false)));
        assertEquals("Type the meaning", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.TYPE_MEANING, false)));
        assertEquals("Choose the kanji", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.MEANING_KANJI, false)));
        assertEquals("Recognise this kanji", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.FONT_MEANING, false)));
        assertEquals("Recognise this kanji", StudyTaskCopy.flashcardTitle(session("font_recognition", false)));
        assertEquals("Name this kanji", StudyTaskCopy.flashcardTitle(session(StudyTaskTypes.KANJI_MEANING, false)));
        assertEquals("Name this kanji", StudyTaskCopy.flashcardTitle(null));
    }

    @Test
    public void studyModeLabelPreservesModePills() {
        assertEquals("Practice", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.WORD_READING, true)));
        assertEquals("Read", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.WORD_READING, false)));
        assertEquals("Type", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.TYPING_MEANING, false)));
        assertEquals("Type", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.TYPE_MEANING, false)));
        assertEquals("Recall", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.MEANING_KANJI, false)));
        assertEquals("Recognise", StudyTaskCopy.studyModeLabel(session(StudyTaskTypes.KANJI_MEANING, false)));
        assertEquals("Recognise", StudyTaskCopy.studyModeLabel(null));
    }

    @Test
    public void taskPredicatesPreserveStudyUiClassificationRules() {
        assertFalse(StudyTaskCopy.isTeachingTask(null));
        assertTrue(StudyTaskCopy.isTeachingTask(session("context_writing", false)));
        assertTrue(StudyTaskCopy.isTeachingTask(session("guided_writing", false)));
        assertTrue(StudyTaskCopy.isTeachingTask(nullItemSession("context_writing")));
        assertTrue(StudyTaskCopy.isTeachingTask(nullItemSession("guided_writing")));
        assertTrue(StudyTaskCopy.isTeachingTask(sessionWithLearningStep("targeted_writing", 1)));
        assertFalse(StudyTaskCopy.isTeachingTask(nullItemSession("targeted_writing")));
        assertFalse(StudyTaskCopy.isTeachingTask(sessionWithLearningStep("targeted_writing", 2)));
        assertFalse(StudyTaskCopy.isTeachingTask(session(StudyTaskTypes.KANJI_MEANING, false)));

        assertFalse(StudyTaskCopy.isRecallTask(null));
        assertTrue(StudyTaskCopy.isRecallTask(session("blind_writing", true)));
        assertTrue(StudyTaskCopy.isRecallTask(session("sampled_handwriting", true)));
        assertFalse(StudyTaskCopy.isRecallTask(session("guided_writing", true)));

        assertTrue(StudyTaskCopy.isRepairWritingTask(session("repair_writing", true)));
        assertFalse(StudyTaskCopy.isRepairWritingTask(session(StudyTaskTypes.WRITE_KANJI, true)));
        assertFalse(StudyTaskCopy.isRepairWritingTask(null));

        assertTrue(StudyTaskCopy.isFontRecognitionTask(session(StudyTaskTypes.FONT_MEANING, false)));
        assertTrue(StudyTaskCopy.isFontRecognitionTask(session("font_recognition", false)));
        assertFalse(StudyTaskCopy.isFontRecognitionTask(null));
        assertTrue(StudyTaskCopy.isTypingMeaningTask(session(StudyTaskTypes.TYPING_MEANING, false)));
        assertTrue(StudyTaskCopy.isTypingMeaningTask(session(StudyTaskTypes.TYPE_MEANING, false)));
        assertFalse(StudyTaskCopy.isTypingMeaningTask(null));
        assertTrue(StudyTaskCopy.isMeaningKanjiTask(session(StudyTaskTypes.MEANING_KANJI, false)));
        assertFalse(StudyTaskCopy.isMeaningKanjiTask(null));
        assertTrue(StudyTaskCopy.isWordReadingTask(session(StudyTaskTypes.WORD_READING, false)));
        assertFalse(StudyTaskCopy.isWordReadingTask(null));
    }

    private static RecordsSchedulerModels.StudySession session(String taskType, boolean writingRequired) {
        return sessionWithLearningStep(taskType, writingRequired, 1);
    }

    private static RecordsSchedulerModels.StudySession sessionWithLearningStep(String taskType, int learningStep) {
        return sessionWithLearningStep(taskType, true, learningStep);
    }

    private static RecordsSchedulerModels.StudySession sessionWithLearningStep(
            String taskType,
            boolean writingRequired,
            int learningStep
    ) {
        return new RecordsSchedulerModels.StudySession(
                new RecordsStudyModels.StudyItem(
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
        );
    }

    private static RecordsSchedulerModels.StudySession nullItemSession(String taskType) {
        return new RecordsSchedulerModels.StudySession(
                null,
                null,
                "token",
                taskType,
                true,
                "prompt"
        );
    }
}
