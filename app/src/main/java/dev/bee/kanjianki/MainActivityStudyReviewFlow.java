package dev.bee.kanjianki;

import android.widget.Toast;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudyReviewRequestPolicy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.data.StudyStatsStore;

import java.util.HashSet;
import java.util.Set;

final class MainActivityStudyReviewFlow {
    private final MainActivityStudy activity;

    MainActivityStudyReviewFlow(MainActivityStudy activity) {
        this.activity = activity;
    }

    void submitReview(String rating, boolean override) {
        if (activity.activeSession == null) {
            return;
        }
        if (activity.activeSimilarWritingRepair != null) {
            submitSimilarWritingRepair(rating);
            return;
        }
        StudyReviewRequestPolicy.MappedReview mappedReview = StudyReviewRequestPolicy.from(
                activity.activeSession,
                StudyReviewWritingOutcome.from(activity.activeAnalysis),
                activity.hintsUsed,
                rating,
                override
        );
        RecordsSchedulerModels.ReviewRequest request = mappedReview.request();
        submitNormalReview(request);
    }

    void submitSimilarWritingRepair(String rating) {
        RecordsImportModels.SimilarKanjiWritingRepair repair = activity.activeSimilarWritingRepair;
        if (repair == null) {
            return;
        }
        long now = System.currentTimeMillis();
        activity.completeActiveRepairStudyTask(activity.similarRepairStudyTaskKey(repair), rating, now);
        StudyRepairActions.RepairCompletion completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                rating,
                now,
                activity.store::finishSimilarWritingRepair,
                activity.studySessionTracker::recordRepairOutcome,
                activity::markStudyTaskCompleted
        );
        Toast.makeText(
                activity,
                StudyTextCopy.similarWritingRepairSavedToast(completion.passed()),
                Toast.LENGTH_SHORT
        ).show();
        activity.activeSimilarWritingRepair = null;
        activity.renderStudy();
    }

    void submitSimilarKanjiChoice(RecordsImportModels.SimilarKanjiChoiceCard card, String selectedKanji) {
        long now = System.currentTimeMillis();
        RecordsImportModels.SimilarKanjiChoiceResult result = activity.store.submitSimilarChoice(
                card,
                selectedKanji,
                now,
                activity.studyLadderSettings().isEnabled(RecordsBase.LadderRung.WRITE_KANJI)
        );
        submitReview(result.correct ? activity.RATING_GOOD : activity.RATING_AGAIN, false);
    }

    void submitNormalReview(RecordsSchedulerModels.ReviewRequest request) {
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> consumed = new HashSet<>(activity.store.consumedTokens());
        long now = System.currentTimeMillis();
        RecordsSchedulerModels.SchedulerParameters parameters = activity.store.schedulerParameters();
        RecordsSchedulerModels.SchedulerParameters effectiveParameters = parameters.withTargetRetention(
                parameters.targetRetentionForRank(activity.activeSession.row.jitenRank)
        );
        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(
                activity.activeSession.item,
                request,
                consumed,
                now,
                effectiveParameters,
                activity.settings(),
                activity.studyLadderSettings()
        );
        activity.completeActiveStudyTask(activity.sessionTaskKey(activity.activeSession), result.appliedRating, now);
        StudyStatsStore.StudyStreak streak = null;
        if (!result.duplicate) {
            saveAppliedReview(request, result, now);
            streak = activity.store.studyStreak(now);
            activity.tuneSchedulerIfNeeded(parameters, now);
        }
        int currentStreakDays = streak == null ? 0 : streak.currentDays;
        Toast.makeText(activity, HomeTextCopy.reviewToast(result.duplicate, result.appliedRating, currentStreakDays), Toast.LENGTH_SHORT).show();
        activity.renderStudy();
    }

    void saveAppliedReview(RecordsSchedulerModels.ReviewRequest request, RecordsSchedulerModels.ReviewResult result, long now) {
        StudyReviewActions.saveAppliedReview(
                request,
                result,
                activity.activeSession.item,
                now,
                reviewWriter(),
                activity.studySessionTracker::recordReviewOutcome,
                activity::markStudyRunPassed
        );
    }

    StudyReviewActions.ReviewWriter reviewWriter() {
        return new StudyReviewActions.ReviewWriter() {
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
        };
    }
}
