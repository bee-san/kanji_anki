package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionEngine;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionInput;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionResult;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

final class LegacyStudyReviewBridge {
    private final StudyReviewTransitionEngine transitionEngine;

    LegacyStudyReviewBridge() {
        this(new StudyReviewTransitionEngine());
    }

    LegacyStudyReviewBridge(StudyReviewTransitionEngine transitionEngine) {
        this.transitionEngine = Objects.requireNonNull(transitionEngine);
    }

    RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        StudyReviewTransitionResult result = transitionEngine.apply(
                new StudyReviewTransitionInput(
                        LegacyStudyMappers.toDomain(item),
                        LegacyStudyMappers.toDomain(request),
                        nowMillis,
                        consumedTokens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(consumedTokens),
                        safeParameters(parameters).targetRetention,
                        LegacyStudyMappers.toDomain(safeLearningSettings(null)),
                        LegacyStudyMappers.toDomain(settings, ladder)
                )
        );
        if (!result.getDuplicate() && consumedTokens != null) {
            consumedTokens.add(request.token == null ? "" : request.token);
        }
        return new RecordsSchedulerModels.ReviewResult(
                result.getDuplicate() ? item : LegacyStudyMappers.toLegacy(item, result.getItem()),
                result.getAppliedRating() == null ? "duplicate" : result.getAppliedRating().getWireName(),
                result.getDuplicate(),
                result.getMessage()
        );
    }

    RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsSchedulerModels.LearningStepSettings learningSettings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        StudyReviewTransitionResult result = transitionEngine.apply(
                new StudyReviewTransitionInput(
                        LegacyStudyMappers.toDomain(item),
                        LegacyStudyMappers.toDomain(request),
                        nowMillis,
                        consumedTokens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(consumedTokens),
                        safeParameters(parameters).targetRetention,
                        LegacyStudyMappers.toDomain(safeLearningSettings(learningSettings)),
                        LegacyStudyMappers.toDomain(settings, ladder)
                )
        );
        if (!result.getDuplicate() && consumedTokens != null) {
            consumedTokens.add(request.token == null ? "" : request.token);
        }
        return new RecordsSchedulerModels.ReviewResult(
                result.getDuplicate() ? item : LegacyStudyMappers.toLegacy(item, result.getItem()),
                result.getAppliedRating() == null ? "duplicate" : result.getAppliedRating().getWireName(),
                result.getDuplicate(),
                result.getMessage()
        );
    }

    private static RecordsSchedulerModels.SchedulerParameters safeParameters(
            RecordsSchedulerModels.SchedulerParameters parameters
    ) {
        return parameters == null ? RecordsSchedulerModels.SchedulerParameters.defaults() : parameters;
    }

    private static RecordsSchedulerModels.LearningStepSettings safeLearningSettings(
            RecordsSchedulerModels.LearningStepSettings settings
    ) {
        return settings == null ? RecordsSchedulerModels.LearningStepSettings.defaults() : settings;
    }
}
