package dev.bee.kanjianki;

import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats;
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlan;
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanRequest;
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner;
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudySettings;
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadMode;
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LegacyAdaptiveStudyPlannerBridge {
    private static final RecordsSchedulerModels.ReviewStats EMPTY_STATS =
            new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0);

    private final AdaptiveStudyPlanner planner;

    public LegacyAdaptiveStudyPlannerBridge() {
        this(new AdaptiveStudyPlanner());
    }

    LegacyAdaptiveStudyPlannerBridge(AdaptiveStudyPlanner planner) {
        this.planner = Objects.requireNonNull(planner);
    }

    public RecordsSchedulerModels.AdaptiveLoadPlan plan(
            AdaptiveLoadPlanner.PlanRequest request
    ) {
        if (request == null) {
            return plan(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    EMPTY_STATS,
                    0,
                    Collections.emptySet(),
                    AdaptiveLoadPlanner.WorkloadPolicy.manual(AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT),
                    0L,
                    RecordsSyncModels.Settings.kikuDefaults()
            );
        }
        return plan(
                request.rows(),
                request.items(),
                request.recentStats(),
                request.currentStreakDays(),
                request.studiedToday(),
                request.workloadPolicy(),
                request.nowMillis(),
                request.settings()
        );
    }

    public RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            int workloadPercent,
            String workloadMode,
            int maxItems,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        return plan(
                rows,
                items,
                recentStats,
                currentStreakDays,
                studiedToday,
                AdaptiveWorkloadPolicy.Companion.fromSettings(workloadPercent, workloadMode, maxItems),
                nowMillis,
                settings
        );
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            AdaptiveLoadPlanner.WorkloadPolicy workloadPolicy,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        AdaptiveLoadPlanner.WorkloadPolicy safePolicy = workloadPolicy == null
                ? AdaptiveLoadPlanner.WorkloadPolicy.manual(AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT)
                : workloadPolicy;
        AdaptiveWorkloadMode mode = safePolicy.mode().isAuto()
                ? AdaptiveWorkloadMode.AUTO
                : AdaptiveWorkloadMode.MANUAL;
        return plan(
                rows,
                items,
                recentStats,
                currentStreakDays,
                studiedToday,
                AdaptiveWorkloadPolicy.Companion.of(mode, safePolicy.workloadPercent(), safePolicy.maxItems()),
                nowMillis,
                settings
        );
    }

    private RecordsSchedulerModels.AdaptiveLoadPlan plan(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            RecordsSchedulerModels.ReviewStats recentStats,
            int currentStreakDays,
            Set<String> studiedToday,
            AdaptiveWorkloadPolicy workloadPolicy,
            long nowMillis,
            RecordsSyncModels.Settings settings
    ) {
        RecordsSchedulerModels.ReviewStats safeStats = recentStats == null ? EMPTY_STATS : recentStats;
        RecordsSyncModels.Settings safeSettings = settings == null ? RecordsSyncModels.Settings.kikuDefaults() : settings;
        AdaptiveStudyPlan plan = planner.plan(
                new AdaptiveStudyPlanRequest(
                        LegacyStudyMappers.toDomainRows(rows == null ? Collections.emptyList() : rows),
                        LegacyStudyMappers.toDomainItems(items == null ? Collections.emptyList() : items),
                        new AdaptiveReviewStats(
                                safeStats.total,
                                safeStats.again,
                                safeStats.hard,
                                safeStats.good,
                                safeStats.easy,
                                safeStats.writingRequired,
                                safeStats.writingFailed
                        ),
                        currentStreakDays,
                        studiedToday == null ? Collections.emptySet() : studiedToday,
                        workloadPolicy,
                        nowMillis,
                        new AdaptiveStudySettings(safeSettings.matureDays, safeSettings.matureSupportThreshold)
                )
        );
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                plan.getAutoMode(),
                plan.getWorkloadPercent(),
                plan.getTargetCount(),
                plan.getRemainingCount(),
                plan.getFocusKanji(),
                plan.getNewAdmissionLimit(),
                plan.getAllKanjiMode(),
                plan.getStatus()
        );
    }
}
