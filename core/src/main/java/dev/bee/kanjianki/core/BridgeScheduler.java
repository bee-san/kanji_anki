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

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis
    ) {
        return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, Records.StudyLadderSettings.defaults());
    }

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.StudyLadderSettings ladder
    ) {
        return queueSeeder.seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, ladder);
    }

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.AdaptiveLoadPlan plan
    ) {
        return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, plan, Records.StudyLadderSettings.defaults());
    }

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.AdaptiveLoadPlan plan,
            Records.StudyLadderSettings ladder
    ) {
        return queueSeeder.seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, plan, ladder);
    }

    public ExtraNewCardsResult seedExtraNewCards(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount
    ) {
        return seedExtraNewCards(rows, existing, settings, nowMillis, startOfDayMillis, requestedCount, Records.StudyLadderSettings.defaults());
    }

    public ExtraNewCardsResult seedExtraNewCards(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount,
            Records.StudyLadderSettings ladder
    ) {
        return queueSeeder.seedExtraNewCards(rows, existing, settings, nowMillis, startOfDayMillis, requestedCount, ladder);
    }

    public Records.StudySession nextSession(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis) {
        return nextSession(items, rows, nowMillis, null);
    }

    public Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        return nextSession(items, rows, nowMillis, 0L, allowedKanji);
    }

    public Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji
    ) {
        return nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, Records.Settings.kikuDefaults());
    }

    public Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            Records.Settings settings
    ) {
        return nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, settings, Records.StudyLadderSettings.defaults());
    }

    public Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            Records.Settings settings,
            Records.StudyLadderSettings ladder
    ) {
        return sessionSelector.nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, settings, ladder);
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, Records.SchedulerParameters.defaults(), Records.Settings.kikuDefaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, Records.Settings.kikuDefaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, Records.LearningStepSettings.defaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings,
            Records.StudyLadderSettings ladder
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, Records.LearningStepSettings.defaults(), ladder);
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings,
            Records.LearningStepSettings learningSettings
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, learningSettings, Records.StudyLadderSettings.defaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings,
            Records.LearningStepSettings learningSettings,
            Records.StudyLadderSettings ladder
    ) {
        return transitionEngine.applyReview(item, request, consumedTokens, nowMillis, parameters, settings, learningSettings, ladder);
    }

    public int dueCount(List<Records.StudyItem> items, long nowMillis) {
        return dueCount(items, nowMillis, 0L);
    }

    public int dueCount(List<Records.StudyItem> items, long nowMillis, long studyAheadMillis) {
        return sessionSelector.dueCount(items, nowMillis, studyAheadMillis);
    }

    public int dueCount(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis) {
        return dueCount(items, rows, nowMillis, 0L);
    }

    public int dueCount(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis, long studyAheadMillis) {
        return dueCount(items, rows, nowMillis, studyAheadMillis, Records.StudyLadderSettings.defaults());
    }

    public int dueCount(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Records.StudyLadderSettings ladder
    ) {
        return sessionSelector.dueCount(items, rows, nowMillis, studyAheadMillis, ladder);
    }

    public List<Records.StudyItem> activeQueueItems(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        return activeQueueItems(items, rows, nowMillis, 0L, allowedKanji);
    }

    public List<Records.StudyItem> activeQueueItems(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji
    ) {
        return activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, Records.StudyLadderSettings.defaults());
    }

    public List<Records.StudyItem> activeQueueItems(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            Records.StudyLadderSettings ladder
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

    public List<Records.StudyItem> applySuppression(List<Records.StudyItem> items) {
        return suppressionPolicy.apply(items);
    }

    static Records.LadderRung promoteRung(Records.LadderRung current, boolean hasSimilarKanji) {
        return StudyLadderRules.promoteRung(current, hasSimilarKanji);
    }

    static Records.LadderRung promoteRung(
            Records.LadderRung current,
            boolean hasSimilarKanji,
            Records.StudyLadderSettings ladder
    ) {
        return StudyLadderRules.promoteRung(current, hasSimilarKanji, ladder);
    }

    static Records.LadderRung demoteRung(Records.LadderRung current, boolean hasSimilarKanji) {
        return StudyLadderRules.demoteRung(current, hasSimilarKanji);
    }

    static Records.LadderRung demoteRung(
            Records.LadderRung current,
            boolean hasSimilarKanji,
            Records.StudyLadderSettings ladder
    ) {
        return StudyLadderRules.demoteRung(current, hasSimilarKanji, ladder);
    }

    static List<Records.LadderRung> rungsForItem(Records.StudyItem item) {
        return StudyLadderRules.rungsForItem(item);
    }

    static List<Records.LadderRung> rungsForItem(Records.StudyItem item, Records.StudyLadderSettings ladder) {
        return StudyLadderRules.rungsForItem(item, ladder);
    }

    public static final class ExtraNewCardsResult {
        public final List<Records.StudyItem> items;
        public final List<String> admittedKanji;
        public final int availableCount;
        public final int admittedCount;

        ExtraNewCardsResult(
                List<Records.StudyItem> items,
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
