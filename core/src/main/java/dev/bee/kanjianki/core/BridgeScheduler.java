package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Public compatibility facade for the ladder scheduler.
 * <p>
 * The scheduler implementation is split across package-local collaborators:
 * queue seeding, session selection, review transitions, and sibling
 * suppression. Public callers should continue using this facade.
 */
public final class BridgeScheduler {
    static final long DAY = StudyLadderRules.DAY;

    public static final String RATING_AGAIN = StudyRatings.AGAIN;
    public static final String RATING_HARD = StudyRatings.HARD;
    public static final String RATING_GOOD = StudyRatings.GOOD;
    public static final String RATING_EASY = StudyRatings.EASY;

    public static final String TASK_WRITE_KANJI = StudyTaskTypes.WRITE_KANJI;
    public static final String TASK_TYPE_MEANING = StudyTaskTypes.TYPE_MEANING;
    public static final String TASK_SIMILAR_KANJI = StudyTaskTypes.SIMILAR_KANJI;
    public static final String TASK_MEANING_KANJI = StudyTaskTypes.MEANING_KANJI;
    public static final String TASK_KANJI_MEANING = StudyTaskTypes.KANJI_MEANING;
    public static final String TASK_FONT_MEANING = StudyTaskTypes.FONT_MEANING;
    public static final String TASK_WORD_READING = StudyTaskTypes.WORD_READING;

    public static final String TASK_TYPING_MEANING = StudyTaskTypes.TYPING_MEANING;
    public static final String TASK_WRITING_REMEDIATION = StudyTaskTypes.WRITING_REMEDIATION;

    private final StudyQueueSeeder queueSeeder;
    private final StudySessionSelector sessionSelector;
    private final ReviewTransitionEngine transitionEngine;
    private final SiblingSuppressionPolicy suppressionPolicy;

    public BridgeScheduler() {
        this(new LatestFsrsAdapter());
    }

    BridgeScheduler(KaniFsrsAdapter fsrsAdapter) {
        Objects.requireNonNull(fsrsAdapter);
        this.queueSeeder = new StudyQueueSeeder();
        this.sessionSelector = new StudySessionSelector();
        this.transitionEngine = new ReviewTransitionEngine(fsrsAdapter);
        this.suppressionPolicy = new SiblingSuppressionPolicy();
    }

    public List<RecordsStudyModels.StudyItem> seedQueue(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis
    ) {
        return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, RecordsBase.StudyLadderSettings.defaults());
    }

    public List<RecordsStudyModels.StudyItem> seedQueue(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return queueSeeder.seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, ladder);
    }

    public List<RecordsStudyModels.StudyItem> seedQueue(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            RecordsSchedulerModels.AdaptiveLoadPlan plan
    ) {
        return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, plan, RecordsBase.StudyLadderSettings.defaults());
    }

    public List<RecordsStudyModels.StudyItem> seedQueue(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return queueSeeder.seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, plan, ladder);
    }

    public ExtraNewCardsResult seedExtraNewCards(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount
    ) {
        return seedExtraNewCards(rows, existing, settings, nowMillis, startOfDayMillis, requestedCount, RecordsBase.StudyLadderSettings.defaults());
    }

    public ExtraNewCardsResult seedExtraNewCards(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> existing,
            RecordsSyncModels.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return queueSeeder.seedExtraNewCards(rows, existing, settings, nowMillis, startOfDayMillis, requestedCount, ladder);
    }

    public RecordsSchedulerModels.StudySession nextSession(List<RecordsStudyModels.StudyItem> items, List<RecordsImportModels.DashboardRow> rows, long nowMillis) {
        return nextSession(items, rows, nowMillis, null);
    }

    public RecordsSchedulerModels.StudySession nextSession(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        return nextSession(items, rows, nowMillis, 0L, allowedKanji);
    }

    public RecordsSchedulerModels.StudySession nextSession(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji
    ) {
        return nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, RecordsSyncModels.Settings.kikuDefaults());
    }

    public RecordsSchedulerModels.StudySession nextSession(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            RecordsSyncModels.Settings settings
    ) {
        return nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, settings, RecordsBase.StudyLadderSettings.defaults());
    }

    public RecordsSchedulerModels.StudySession nextSession(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return sessionSelector.nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, settings, ladder);
    }

    public RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, RecordsSchedulerModels.SchedulerParameters.defaults(), RecordsSyncModels.Settings.kikuDefaults());
    }

    public RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, RecordsSyncModels.Settings.kikuDefaults());
    }

    public RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, RecordsSchedulerModels.LearningStepSettings.defaults());
    }

    public RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, RecordsSchedulerModels.LearningStepSettings.defaults(), ladder);
    }

    public RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsSchedulerModels.LearningStepSettings learningSettings
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, learningSettings, RecordsBase.StudyLadderSettings.defaults());
    }

    public RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsSchedulerModels.LearningStepSettings learningSettings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return transitionEngine.applyReview(item, request, consumedTokens, nowMillis, parameters, settings, learningSettings, ladder);
    }

    public int dueCount(List<RecordsStudyModels.StudyItem> items, long nowMillis) {
        return dueCount(items, nowMillis, 0L);
    }

    public int dueCount(List<RecordsStudyModels.StudyItem> items, long nowMillis, long studyAheadMillis) {
        return sessionSelector.dueCount(items, nowMillis, studyAheadMillis);
    }

    public int dueCount(List<RecordsStudyModels.StudyItem> items, List<RecordsImportModels.DashboardRow> rows, long nowMillis) {
        return dueCount(items, rows, nowMillis, 0L);
    }

    public int dueCount(List<RecordsStudyModels.StudyItem> items, List<RecordsImportModels.DashboardRow> rows, long nowMillis, long studyAheadMillis) {
        return dueCount(items, rows, nowMillis, studyAheadMillis, RecordsBase.StudyLadderSettings.defaults());
    }

    public int dueCount(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return sessionSelector.dueCount(items, rows, nowMillis, studyAheadMillis, ladder);
    }

    public List<RecordsStudyModels.StudyItem> activeQueueItems(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        return activeQueueItems(items, rows, nowMillis, 0L, allowedKanji);
    }

    public List<RecordsStudyModels.StudyItem> activeQueueItems(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji
    ) {
        return activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, RecordsBase.StudyLadderSettings.defaults());
    }

    public List<RecordsStudyModels.StudyItem> activeQueueItems(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return sessionSelector.activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, ladder);
    }

    /**
     * Creates a mutable token set from the given list of previously consumed
     * tokens. The returned set is not thread-safe; callers must synchronize
     * externally if the set will be shared across threads.
     */
    public Set<String> tokenSet(List<String> tokens) {
        return new HashSet<>(tokens);
    }

    public List<RecordsStudyModels.StudyItem> applySuppression(List<RecordsStudyModels.StudyItem> items) {
        return suppressionPolicy.apply(items);
    }

    static RecordsBase.LadderRung promoteRung(RecordsBase.LadderRung current, boolean hasSimilarKanji) {
        return StudyLadderRules.promoteRung(current, hasSimilarKanji);
    }

    static RecordsBase.LadderRung promoteRung(
            RecordsBase.LadderRung current,
            boolean hasSimilarKanji,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return StudyLadderRules.promoteRung(current, hasSimilarKanji, ladder);
    }

    static RecordsBase.LadderRung demoteRung(RecordsBase.LadderRung current, boolean hasSimilarKanji) {
        return StudyLadderRules.demoteRung(current, hasSimilarKanji);
    }

    static RecordsBase.LadderRung demoteRung(
            RecordsBase.LadderRung current,
            boolean hasSimilarKanji,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return StudyLadderRules.demoteRung(current, hasSimilarKanji, ladder);
    }

    static List<RecordsBase.LadderRung> rungsForItem(RecordsStudyModels.StudyItem item) {
        return StudyLadderRules.rungsForItem(item);
    }

    static List<RecordsBase.LadderRung> rungsForItem(RecordsStudyModels.StudyItem item, RecordsBase.StudyLadderSettings ladder) {
        return StudyLadderRules.rungsForItem(item, ladder);
    }

    public static final class ExtraNewCardsResult {
        public final List<RecordsStudyModels.StudyItem> items;
        public final List<String> admittedKanji;
        public final int availableCount;
        public final int admittedCount;

        ExtraNewCardsResult(
                List<RecordsStudyModels.StudyItem> items,
                List<String> admittedKanji,
                int availableCount
        ) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.admittedKanji = Collections.unmodifiableList(new ArrayList<>(admittedKanji));
            this.availableCount = availableCount;
            this.admittedCount = admittedKanji.size();
        }

        public boolean admittedAny() {
            return admittedCount > 0;
        }
    }
}
