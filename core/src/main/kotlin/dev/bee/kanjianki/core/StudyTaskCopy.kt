package dev.bee.kanjianki.core

import java.util.Locale

object StudyTaskCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val LABEL_STUDY = "Study"
    private const val LABEL_SIMILAR_KANJI = "Similar kanji"

    private const val TASK_TARGETED_FLASHCARD = "targeted_flashcard"
    private const val TASK_MEANING_FLASHCARD = "meaning_flashcard"
    private const val TASK_FONT_RECOGNITION = "font_recognition"
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
        StudyTaskTypes.TYPE_READING -> localizedText("Type the reading", "読みを入力")
        StudyTaskTypes.KANJI_READING -> localizedText("Kanji -> reading", "漢字→読み")
        StudyTaskTypes.READING_KANJI -> localizedText("Reading -> kanji", "読み→漢字")
        StudyTaskTypes.SENTENCE_READING -> localizedText("Sentence reading", "文で読む")
        StudyTaskTypes.WRITE_KANJI -> localizedText("Write kanji", "漢字を書く")
        StudyTaskTypes.SIMILAR_KANJI -> localizedText(LABEL_SIMILAR_KANJI, "似た漢字")
        TASK_MEANING_FLASHCARD -> localizedText("Quick recall", "素早く復習")
        TASK_FONT_RECOGNITION -> localizedText("Font check", "フォント確認")
        StudyTaskTypes.REPAIR_WRITING -> localizedText("Repair", "修正")
        StudyTaskTypes.TARGETED_WRITING -> localizedText("Focused practice", "集中練習")
        StudyTaskTypes.CONTEXT_WRITING -> localizedText("New problem kanji", "新しい問題漢字")
        StudyTaskTypes.GUIDED_WRITING -> localizedText("Guided review", "ガイド付き復習")
        StudyTaskTypes.BLIND_WRITING,
        StudyTaskTypes.SAMPLED_HANDWRITING -> localizedText("Memory check", "記憶確認")

        TASK_CONFUSABLE_RECOGNITION -> localizedText("Learn the shape", "形を覚える")
        else -> localizedText(LABEL_STUDY, "学習")
    }

    @JvmStatic
    fun studyModeLabel(session: RecordsSchedulerModels.StudySession?): String = when {
        isNewLearningRepeat(session) -> localizedText("Learn", "学習")
        isRelearning(session) -> localizedText("Relearning", "再学習")
        session != null && session.writingRequired -> localizedText("Practice", "練習")
        isTypingReadingTask(session) -> localizedText("Type", "入力")
        isSentenceReadingTask(session) -> localizedText("Read", "読む")
        isWordReadingTask(session) -> localizedText("Read", "読む")
        isTypingMeaningTask(session) -> localizedText("Type", "入力")
        isKanjiReadingTask(session) -> localizedText("Choose", "選ぶ")
        isReadingKanjiTask(session) -> localizedText("Choose", "選ぶ")
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
        return StudyTaskTypes.CONTEXT_WRITING == session.taskType ||
            StudyTaskTypes.GUIDED_WRITING == session.taskType ||
            (
                StudyTaskTypes.TARGETED_WRITING == session.taskType &&
                    (session.item?.learningStep ?: Int.MAX_VALUE) < 2
                )
    }

    @JvmStatic
    fun isRecallTask(session: RecordsSchedulerModels.StudySession?): Boolean {
        if (session == null) {
            return false
        }
        return StudyTaskTypes.BLIND_WRITING == session.taskType ||
            StudyTaskTypes.SAMPLED_HANDWRITING == session.taskType
    }

    @JvmStatic
    fun isRepairWritingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.REPAIR_WRITING == session.taskType

    @JvmStatic
    fun isFontRecognitionTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null &&
            (StudyTaskTypes.FONT_MEANING == session.taskType || TASK_FONT_RECOGNITION == session.taskType)

    @JvmStatic
    fun isTypingMeaningTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null &&
            (StudyTaskTypes.TYPING_MEANING == session.taskType || StudyTaskTypes.TYPE_MEANING == session.taskType)

    @JvmStatic
    fun isTypingReadingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.TYPE_READING == session.taskType

    @JvmStatic
    fun isMeaningKanjiTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.MEANING_KANJI == session.taskType

    @JvmStatic
    fun isWordReadingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null &&
            (StudyTaskTypes.WORD_READING == session.taskType || StudyTaskTypes.TYPE_READING == session.taskType)

    @JvmStatic
    fun isKanjiReadingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.KANJI_READING == session.taskType

    @JvmStatic
    fun isReadingKanjiTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.READING_KANJI == session.taskType

    @JvmStatic
    fun isSentenceReadingTask(session: RecordsSchedulerModels.StudySession?): Boolean =
        session != null && StudyTaskTypes.SENTENCE_READING == session.taskType

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
