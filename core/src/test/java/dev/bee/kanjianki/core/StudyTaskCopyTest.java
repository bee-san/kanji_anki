package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
        assertEquals("Write to repair", StudyTaskCopy.labelForTask("repair_writing"));
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

    private static RecordsSchedulerModels.StudySession session(String taskType, boolean writingRequired) {
        return new RecordsSchedulerModels.StudySession(
                new RecordsStudyModels.StudyItem("x", "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L),
                null,
                "token",
                taskType,
                writingRequired,
                "prompt"
        );
    }
}
