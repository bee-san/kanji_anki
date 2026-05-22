package dev.bee.kanjianki.core

object StudySessionRoute {
    enum class Destination {
        WRITING,
        SIMILAR_KANJI,
        MEANING_KANJI,
        FLASHCARD,
    }

    @JvmStatic
    fun destination(session: RecordsSchedulerModels.StudySession): Destination = when {
        session.writingRequired -> Destination.WRITING
        StudyTaskTypes.SIMILAR_KANJI == session.taskType -> Destination.SIMILAR_KANJI
        StudyTaskTypes.MEANING_KANJI == session.taskType -> Destination.MEANING_KANJI
        else -> Destination.FLASHCARD
    }
}
