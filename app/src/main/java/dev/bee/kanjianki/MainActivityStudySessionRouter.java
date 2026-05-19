package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudySessionRoute;

final class MainActivityStudySessionRouter {
    private final MainActivityStudy home;

    MainActivityStudySessionRouter(MainActivityStudy home) {
        this.home = home;
    }

    void renderSession(RecordsSchedulerModels.StudySession session) {
        switch (StudySessionRoute.destination(session)) {
            case WRITING:
                home.renderWritingSession(session);
                break;
            case SIMILAR_KANJI:
                home.renderSimilarKanjiSession(session);
                break;
            case MEANING_KANJI:
                home.renderMeaningKanjiSession(session);
                break;
            case FLASHCARD:
            default:
                home.renderFlashcardSession(session);
                break;
        }
    }
}
