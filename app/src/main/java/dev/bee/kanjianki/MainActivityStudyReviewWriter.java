package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

final class MainActivityStudyReviewWriter implements StudyReviewActions.ReviewWriter {
    private final MainActivityStudy activity;

    MainActivityStudyReviewWriter(MainActivityStudy activity) {
        this.activity = activity;
    }

    @Override
    public void saveStudyItem(RecordsStudyModels.StudyItem item) {
        activity.store.saveStudyItem(item);
    }

    @Override
    public void saveReview(
            RecordsSchedulerModels.ReviewRequest request,
            String appliedRating,
            long reviewedAt,
            RecordsStudyModels.StudyItem beforeReview,
            RecordsStudyModels.StudyItem afterReview
    ) {
        activity.store.saveReview(request, appliedRating, reviewedAt, beforeReview, afterReview);
    }
}
