package dev.bee.kanjianki.core

object StudyTaskCopy {
    private const val LABEL_STUDY = "Study"
    private const val LABEL_SIMILAR_KANJI = "Similar kanji"

    private const val TASK_TARGETED_FLASHCARD = "targeted_flashcard"
    private const val TASK_MEANING_FLASHCARD = "meaning_flashcard"
    private const val TASK_FONT_RECOGNITION = "font_recognition"
    private const val TASK_REPAIR_WRITING = "repair_writing"
    private const val TASK_TARGETED_WRITING = "targeted_writing"
    private const val TASK_CONTEXT_WRITING = "context_writing"
    private const val TASK_GUIDED_WRITING = "guided_writing"
    private const val TASK_BLIND_WRITING = "blind_writing"
    private const val TASK_SAMPLED_HANDWRITING = "sampled_handwriting"
    private const val TASK_CONFUSABLE_RECOGNITION = "confusable_recognition"

    @JvmStatic
    fun labelForTask(task: String?): String = when (task) {
        null -> LABEL_STUDY
        TASK_TARGETED_FLASHCARD -> "Focused recall"
        StudyTaskTypes.KANJI_MEANING -> "Kanji -> meaning"
        StudyTaskTypes.MEANING_KANJI -> "Meaning -> kanji"
        StudyTaskTypes.TYPING_MEANING,
        StudyTaskTypes.TYPE_MEANING -> "Type the meaning"

        StudyTaskTypes.FONT_MEANING -> "Font -> meaning"
        StudyTaskTypes.WORD_READING -> "Word -> reading"
        StudyTaskTypes.WRITE_KANJI -> "Write kanji"
        StudyTaskTypes.SIMILAR_KANJI -> LABEL_SIMILAR_KANJI
        TASK_MEANING_FLASHCARD -> "Quick recall"
        TASK_FONT_RECOGNITION -> "Font check"
        TASK_REPAIR_WRITING -> "Repair"
        TASK_TARGETED_WRITING -> "Focused practice"
        TASK_CONTEXT_WRITING -> "New problem kanji"
        TASK_GUIDED_WRITING -> "Guided review"
        TASK_BLIND_WRITING,
        TASK_SAMPLED_HANDWRITING -> "Memory check"

        TASK_CONFUSABLE_RECOGNITION -> "Learn the shape"
        else -> LABEL_STUDY
    }

    @JvmStatic
    fun flashcardTitle(session: RecordsSchedulerModels.StudySession?): String = when {
        isWordReadingTask(session) -> "Read this word"
        isTypingMeaningTask(session) -> "Type the meaning"
        isMeaningKanjiTask(session) -> "Choose the kanji"
        isFontRecognitionTask(session) -> "Recognise this kanji"
        else -> "Name this kanji"
    }

    @JvmStatic
    fun studyModeLabel(session: RecordsSchedulerModels.StudySession?): String = when {
        session != null && session.writingRequired -> "Practice"
        isWordReadingTask(session) -> "Read"
        isTypingMeaningTask(session) -> "Type"
        isMeaningKanjiTask(session) -> "Recall"
        else -> "Recognise"
    }

    @JvmStatic
    fun isTeachingTask(session: RecordsSchedulerModels.StudySession?): Boolean {
        if (session == null) {
            return false
        }
        return TASK_CONTEXT_WRITING == session.taskType ||
            TASK_GUIDED_WRITING == session.taskType ||
            (TASK_TARGETED_WRITING == session.taskType && (session.item?.learningStep ?: Int.MAX_VALUE) < 2)
    }

    @JvmStatic
    fun isRecallTask(session: RecordsSchedulerModels.StudySession?): Boolean {
        if (session == null) {
            return false
        }
        return TASK_BLIND_WRITING == session.taskType || TASK_SAMPLED_HANDWRITING == session.taskType
    }

    @JvmStatic
    fun isRepairWritingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && TASK_REPAIR_WRITING == session.taskType

    @JvmStatic
    fun isFontRecognitionTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null &&
            (StudyTaskTypes.FONT_MEANING == session.taskType || TASK_FONT_RECOGNITION == session.taskType)

    @JvmStatic
    fun isTypingMeaningTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null &&
            (StudyTaskTypes.TYPING_MEANING == session.taskType || StudyTaskTypes.TYPE_MEANING == session.taskType)

    @JvmStatic
    fun isMeaningKanjiTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.MEANING_KANJI == session.taskType

    @JvmStatic
    fun isWordReadingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.WORD_READING == session.taskType
}
