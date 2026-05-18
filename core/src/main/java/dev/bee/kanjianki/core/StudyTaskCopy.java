package dev.bee.kanjianki.core;

public final class StudyTaskCopy {
    private static final String LABEL_STUDY = "Study";
    private static final String LABEL_SIMILAR_KANJI = "Similar kanji";

    private static final String TASK_TARGETED_FLASHCARD = "targeted_flashcard";
    private static final String TASK_MEANING_FLASHCARD = "meaning_flashcard";
    private static final String TASK_FONT_RECOGNITION = "font_recognition";
    private static final String TASK_REPAIR_WRITING = "repair_writing";
    private static final String TASK_TARGETED_WRITING = "targeted_writing";
    private static final String TASK_CONTEXT_WRITING = "context_writing";
    private static final String TASK_GUIDED_WRITING = "guided_writing";
    private static final String TASK_BLIND_WRITING = "blind_writing";
    private static final String TASK_SAMPLED_HANDWRITING = "sampled_handwriting";
    private static final String TASK_CONFUSABLE_RECOGNITION = "confusable_recognition";

    private StudyTaskCopy() {
    }

    public static String labelForTask(String task) {
        if (task == null) {
            return LABEL_STUDY;
        }
        return switch (task) {
            case TASK_TARGETED_FLASHCARD -> "Focused recall";
            case StudyTaskTypes.KANJI_MEANING -> "Kanji -> meaning";
            case StudyTaskTypes.MEANING_KANJI -> "Meaning -> kanji";
            case StudyTaskTypes.TYPING_MEANING, StudyTaskTypes.TYPE_MEANING -> "Type the meaning";
            case StudyTaskTypes.FONT_MEANING -> "Font -> meaning";
            case StudyTaskTypes.WORD_READING -> "Word -> reading";
            case StudyTaskTypes.WRITE_KANJI -> "Write kanji";
            case StudyTaskTypes.SIMILAR_KANJI -> LABEL_SIMILAR_KANJI;
            case TASK_MEANING_FLASHCARD -> "Quick recall";
            case TASK_FONT_RECOGNITION -> "Font check";
            case TASK_REPAIR_WRITING -> "Write to repair";
            case TASK_TARGETED_WRITING -> "Focused practice";
            case TASK_CONTEXT_WRITING -> "New problem kanji";
            case TASK_GUIDED_WRITING -> "Guided review";
            case TASK_BLIND_WRITING, TASK_SAMPLED_HANDWRITING -> "Memory check";
            case TASK_CONFUSABLE_RECOGNITION -> "Learn the shape";
            default -> LABEL_STUDY;
        };
    }

    public static String flashcardTitle(RecordsSchedulerModels.StudySession session) {
        if (isWordReadingTask(session)) {
            return "Read this word";
        }
        if (isTypingMeaningTask(session)) {
            return "Type the meaning";
        }
        if (isMeaningKanjiTask(session)) {
            return "Choose the kanji";
        }
        return isFontRecognitionTask(session) ? "Recognise this kanji" : "Name this kanji";
    }

    public static String studyModeLabel(RecordsSchedulerModels.StudySession session) {
        if (session != null && session.writingRequired) {
            return "Practice";
        }
        if (isWordReadingTask(session)) {
            return "Read";
        }
        if (isTypingMeaningTask(session)) {
            return "Type";
        }
        if (isMeaningKanjiTask(session)) {
            return "Recall";
        }
        return "Recognise";
    }

    public static boolean isTeachingTask(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return false;
        }
        return TASK_CONTEXT_WRITING.equals(session.taskType)
                || TASK_GUIDED_WRITING.equals(session.taskType)
                || (TASK_TARGETED_WRITING.equals(session.taskType) && session.item.learningStep < 2);
    }

    public static boolean isRecallTask(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return false;
        }
        return TASK_BLIND_WRITING.equals(session.taskType) || TASK_SAMPLED_HANDWRITING.equals(session.taskType);
    }

    public static boolean isFontRecognitionTask(RecordsSchedulerModels.StudySession session) {
        return session != null
                && (StudyTaskTypes.FONT_MEANING.equals(session.taskType)
                || TASK_FONT_RECOGNITION.equals(session.taskType));
    }

    public static boolean isTypingMeaningTask(RecordsSchedulerModels.StudySession session) {
        return session != null
                && (StudyTaskTypes.TYPING_MEANING.equals(session.taskType)
                || StudyTaskTypes.TYPE_MEANING.equals(session.taskType));
    }

    public static boolean isMeaningKanjiTask(RecordsSchedulerModels.StudySession session) {
        return session != null && StudyTaskTypes.MEANING_KANJI.equals(session.taskType);
    }

    public static boolean isWordReadingTask(RecordsSchedulerModels.StudySession session) {
        return session != null && StudyTaskTypes.WORD_READING.equals(session.taskType);
    }
}
