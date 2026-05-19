package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsStudyModels;

import java.util.List;

final class MainActivityStudyMoreNewCardWriter implements StudyMoreNewCardActions.StudyItemWriter {
    private final MainActivityStudy activity;

    MainActivityStudyMoreNewCardWriter(MainActivityStudy activity) {
        this.activity = activity;
    }

    @Override
    public List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items) {
        return activity.store.annotateSimilarKanjiAvailability(items);
    }

    @Override
    public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items) {
        activity.store.replaceStudyItems(items);
    }
}
