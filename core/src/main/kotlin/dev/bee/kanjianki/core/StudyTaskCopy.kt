package dev.bee.kanjianki.core

import java.util.Locale

object StudyTaskCopy {
    private const val JAPANESE_LANGUAGE = "ja"
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
        null -> localizedText(LABEL_STUDY, "学習")
        TASK_TARGETED_FLASHCARD -> localizedText("Focused recall", "集中復習")
        StudyTaskTypes.KANJI_MEANING -> localizedText("Kanji -> meaning", "漢字→意味")
        StudyTaskTypes.MEANING_KANJI -> localizedText("Meaning -> kanji", "意味→漢字")
        StudyTaskTypes.TYPING_MEANING,
        StudyTaskTypes.TYPE_MEANING -> localizedText("Type the meaning", "意味を入力")

        StudyTaskTypes.FONT_MEANING -> localizedText("Font -> meaning", "フォント→意味")
        StudyTaskTypes.WORD_READING -> localizedText("Word -> reading", "単語→読み")
        StudyTaskTypes.WRITE_KANJI -> localizedText("Write kanji", "漢字を書く")
        StudyTaskTypes.SIMILAR_KANJI -> localizedText(LABEL_SIMILAR_KANJI, "似た漢字")
        TASK_MEANING_FLASHCARD -> localizedText("Quick recall", "素早く復習")
        TASK_FONT_RECOGNITION -> localizedText("Font check", "フォント確認")
        TASK_REPAIR_WRITING -> localizedText("Repair", "修正")
        TASK_TARGETED_WRITING -> localizedText("Focused practice", "集中練習")
        TASK_CONTEXT_WRITING -> localizedText("New problem kanji", "新しい問題漢字")
        TASK_GUIDED_WRITING -> localizedText("Guided review", "ガイド付き復習")
        TASK_BLIND_WRITING,
        TASK_SAMPLED_HANDWRITING -> localizedText("Memory check", "記憶確認")

        TASK_CONFUSABLE_RECOGNITION -> localizedText("Learn the shape", "形を覚える")
        else -> localizedText(LABEL_STUDY, "学習")
    }

    @JvmStatic
    fun flashcardTitle(session: RecordsSchedulerModels.StudySession?): String = when {
        isWordReadingTask(session) -> localizedText("Read this word", "この単語を読む")
        isTypingMeaningTask(session) -> localizedText("Type the meaning", "意味を入力")
        isMeaningKanjiTask(session) -> localizedText("Choose the kanji", "漢字を選ぶ")
        isFontRecognitionTask(session) -> localizedText("Recognise this kanji", "この漢字を見分ける")
        else -> localizedText("Name this kanji", "この漢字の意味は？")
    }

    @JvmStatic
    fun studyModeLabel(session: RecordsSchedulerModels.StudySession?): String = when {
        isNewLearningRepeat(session) -> localizedText("Learn", "学習")
        isRelearning(session) -> localizedText("Relearning", "再学習")
        session != null && session.writingRequired -> localizedText("Practice", "練習")
        isWordReadingTask(session) -> localizedText("Read", "読む")
        isTypingMeaningTask(session) -> localizedText("Type", "入力")
        isMeaningKanjiTask(session) -> localizedText("Recall", "思い出す")
        else -> localizedText("Recognise", "見分ける")
    }

    private fun isNewLearningRepeat(session: RecordsSchedulerModels.StudySession?): Boolean {
        val item = session?.item ?: return false
        return item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING && item.totalReviews > 0
    }

    private fun isRelearning(session: RecordsSchedulerModels.StudySession?): Boolean =
        session?.item?.phase == RecordsBase.SchedulerPhase.RELEARNING

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

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
