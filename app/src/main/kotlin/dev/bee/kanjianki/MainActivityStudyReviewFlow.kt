package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

internal class MainActivityStudyReviewFlow(private val activity: MainActivityStudy) {
    fun submitReview(rating: String, override: Boolean) {
        val session = activity.activeSession ?: return
        if (activity.activeSimilarWritingRepair != null) {
            submitSimilarWritingRepair(rating)
            return
        }
        val mappedReview = StudyReviewRequestPolicy.from(
            session,
            StudyReviewWritingOutcome.from(activity.activeAnalysis),
            activity.hintsUsed,
            rating,
            override
        )
        submitNormalReview(mappedReview.request())
    }

    fun submitSimilarWritingRepair(rating: String) {
        val repair = activity.activeSimilarWritingRepair ?: return
        val now = System.currentTimeMillis()
        activity.completeActiveRepairStudyTask(activity.similarRepairStudyTaskKey(repair), rating, now)
        val completion = StudyRepairActions.completeSimilarWritingRepair(
            repair,
            rating,
            now,
            activity.store::finishSimilarWritingRepair,
            activity.studySessionTracker::recordRepairOutcome,
            activity::markStudyTaskCompleted
        )
        Toast.makeText(
            activity,
            StudyTextCopy.similarWritingRepairSavedToast(completion.passed),
            Toast.LENGTH_SHORT
        ).show()
        activity.activeSimilarWritingRepair = null
        activity.renderStudy()
    }

    fun submitSimilarKanjiChoice(card: RecordsImportModels.SimilarKanjiChoiceCard, selectedKanji: String) {
        val now = System.currentTimeMillis()
        val result = activity.store.submitSimilarChoice(
            card,
            selectedKanji,
            now,
            activity.studyLadderSettings().isEnabled(RecordsBase.LadderRung.WRITE_KANJI)
        )
        submitReview(if (result.correct) MainActivityBase.RATING_GOOD else MainActivityBase.RATING_AGAIN, false)
    }

    fun submitNormalReview(request: RecordsSchedulerModels.ReviewRequest) {
        val session = activity.activeSession!!
        val item = session.item ?: return
        val scheduler = BridgeScheduler()
        val consumed = HashSet(activity.store.consumedTokens())
        val now = System.currentTimeMillis()
        val parameters = activity.store.schedulerParameters()
        val sessionRank = session.row?.jitenRank
        val effectiveParameters = parameters.withTargetRetention(
            parameters.targetRetentionForRank(sessionRank)
        )
        val result = scheduler.applyReview(
            item,
            request,
            consumed,
            now,
            effectiveParameters,
            activity.settings(),
            activity.store.learningStepSettings(),
            activity.studyLadderSettings()
        )
        activity.completeActiveStudyTask(activity.sessionTaskKey(session), result.appliedRating, now)
        var streak: StudyStatsStore.StudyStreak? = null
        if (!result.duplicate) {
            saveAppliedReview(request, result, now)
            streak = activity.store.studyStreak(now)
            activity.tuneSchedulerIfNeeded(parameters, now)
            ReminderScheduler.schedule(activity)
        }
        val currentStreakDays = streak?.currentDays ?: 0
        Toast.makeText(activity, HomeTextCopy.reviewToast(result.duplicate, result.appliedRating, currentStreakDays), Toast.LENGTH_SHORT).show()
        activity.renderStudy()
    }

    fun saveAppliedReview(
        request: RecordsSchedulerModels.ReviewRequest,
        result: RecordsSchedulerModels.ReviewResult,
        now: Long,
    ) {
        val item = activity.activeSession?.item ?: return
        StudyReviewActions.saveAppliedReview(
            request,
            result,
            item,
            now,
            object : StudyReviewActions.ReviewWriter {
                override fun saveStudyItem(item: RecordsStudyModels.StudyItem) {
                    activity.store.saveStudyItem(item)
                }

                override fun saveReview(
                    request: RecordsSchedulerModels.ReviewRequest,
                    appliedRating: String?,
                    reviewedAt: Long,
                    beforeReview: RecordsStudyModels.StudyItem,
                    afterReview: RecordsStudyModels.StudyItem,
                ) {
                    activity.store.saveReview(request, appliedRating, reviewedAt, beforeReview, afterReview)
                }
            },
            activity.studySessionTracker::recordReviewOutcome,
            activity::markStudyRunPassed
        )
    }
}
