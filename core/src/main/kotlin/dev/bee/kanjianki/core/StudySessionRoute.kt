package dev.bee.kanjianki.core

object StudySessionRoute {
    enum class Destination {
        WRITING,
        SIMILAR_KANJI,
        MEANING_KANJI,
        KANJI_READING,
        READING_KANJI,
        FLASHCARD,
    }

    @JvmStatic
    fun destination(session: RecordsSchedulerModels.StudySession): Destination = when {
        session.writingRequired -> Destination.WRITING
        StudyTaskTypes.SIMILAR_KANJI == session.taskType -> Destination.SIMILAR_KANJI
        StudyTaskTypes.MEANING_KANJI == session.taskType -> Destination.MEANING_KANJI
        StudyTaskTypes.KANJI_READING == session.taskType -> Destination.KANJI_READING
        StudyTaskTypes.READING_KANJI == session.taskType -> Destination.READING_KANJI
        else -> Destination.FLASHCARD
    }
}
