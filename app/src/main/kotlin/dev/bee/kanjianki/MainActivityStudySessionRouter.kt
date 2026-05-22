package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudySessionRoute

internal class MainActivityStudySessionRouter(private val home: MainActivityStudy) {
    fun renderSession(session: RecordsSchedulerModels.StudySession) {
        when (StudySessionRoute.destination(session)) {
            StudySessionRoute.Destination.WRITING -> {
                home.renderLegacyStudyRoute()
                home.renderWritingSession(session)
            }
            StudySessionRoute.Destination.SIMILAR_KANJI -> home.renderSimilarKanjiSession(session)
            StudySessionRoute.Destination.MEANING_KANJI -> home.renderMeaningKanjiSession(session)
            StudySessionRoute.Destination.FLASHCARD -> {
                home.renderLegacyStudyRoute()
                home.renderFlashcardSession(session)
            }
        }
    }
}
