package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

final class StudyReviewActions {
    private StudyReviewActions() {
    }

    static void saveAppliedReview(
            RecordsSchedulerModels.ReviewRequest request,
            RecordsSchedulerModels.ReviewResult result,
            RecordsStudyModels.StudyItem beforeReview,
            long reviewedAt,
            ReviewWriter writer,
            ReviewOutcomeRecorder recorder,
            StudyRunMarker marker
    ) {
        writer.saveStudyItem(result.item);
        writer.saveReview(request, result.appliedRating, reviewedAt, beforeReview, result.item);
        recorder.recordReviewOutcome(request.kanji, result.appliedRating, beforeReview, result.item);
        if (!MainActivityBase.RATING_AGAIN.equals(result.appliedRating)) {
            marker.markStudyRunPassed(request.kanji);
        }
    }

    static void saveTunedSchedulerIfChanged(
            RecordsSchedulerModels.SchedulerParameters original,
            RecordsSchedulerModels.SchedulerParameters tuned,
            SchedulerParametersWriter writer
    ) {
        if (tuned.lastAdjustedAtMillis != original.lastAdjustedAtMillis
                || tuned.lastAdjustmentReviewCount != original.lastAdjustmentReviewCount) {
            writer.saveSchedulerParameters(tuned);
        }
    }

    interface ReviewWriter {
        void saveStudyItem(RecordsStudyModels.StudyItem item);

        void saveReview(
                RecordsSchedulerModels.ReviewRequest request,
                String appliedRating,
                long reviewedAt,
                RecordsStudyModels.StudyItem beforeReview,
                RecordsStudyModels.StudyItem afterReview
        );
    }

    interface ReviewOutcomeRecorder {
        void recordReviewOutcome(
                String kanji,
                String appliedRating,
                RecordsStudyModels.StudyItem beforeReview,
                RecordsStudyModels.StudyItem afterReview
        );
    }

    interface StudyRunMarker {
        void markStudyRunPassed(String kanji);
    }

    interface SchedulerParametersWriter {
        void saveSchedulerParameters(RecordsSchedulerModels.SchedulerParameters parameters);
    }
}
