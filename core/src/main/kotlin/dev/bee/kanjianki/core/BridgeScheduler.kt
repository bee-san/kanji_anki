package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * Public compatibility facade for the ladder scheduler.
 *
 * The scheduler implementation is split across package-local collaborators:
 * queue seeding, session selection, review transitions, and sibling
 * suppression. Public callers should continue using this facade.
 */
class BridgeScheduler {
    private val queueSeeder: StudyQueueSeeder
    private val sessionSelector: StudySessionSelector
    private val targetedSessionPolicy: TargetedStudySessionPolicy
    private val transitionEngine: ReviewTransitionEngine
    private val suppressionPolicy: SiblingSuppressionPolicy

    constructor() : this(LatestFsrsAdapter())

    internal constructor(fsrsAdapter: KaniFsrsAdapter) {
        queueSeeder = StudyQueueSeeder()
        sessionSelector = StudySessionSelector()
        targetedSessionPolicy = TargetedStudySessionPolicy()
        transitionEngine = ReviewTransitionEngine(fsrsAdapter)
        suppressionPolicy = SiblingSuppressionPolicy()
    }

    fun seedQueue(
        rows: List<RecordsImportModels.DashboardRow>?,
        existing: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long
    ): List<RecordsStudyModels.StudyItem> {
        return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, RecordsBase.StudyLadderSettings.defaults())
    }

    fun seedQueue(
        rows: List<RecordsImportModels.DashboardRow>?,
        existing: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?
    ): List<RecordsStudyModels.StudyItem> {
        return queueSeeder.seedQueue(safeRows(rows), safeItems(existing), safeSettings(settings), nowMillis, startOfDayMillis, ladder)
    }

    fun seedQueue(
        rows: List<RecordsImportModels.DashboardRow>?,
        existing: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?
    ): List<RecordsStudyModels.StudyItem> {
        return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis, plan, RecordsBase.StudyLadderSettings.defaults())
    }

    fun seedQueue(
        rows: List<RecordsImportModels.DashboardRow>?,
        existing: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        ladder: RecordsBase.StudyLadderSettings?
    ): List<RecordsStudyModels.StudyItem> {
        return queueSeeder.seedQueue(safeRows(rows), safeItems(existing), safeSettings(settings), nowMillis, startOfDayMillis, plan, ladder)
    }

    fun seedExtraNewCards(
        rows: List<RecordsImportModels.DashboardRow>?,
        existing: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long,
        requestedCount: Int
    ): ExtraNewCardsResult {
        return seedExtraNewCards(rows, existing, settings, nowMillis, startOfDayMillis, requestedCount, RecordsBase.StudyLadderSettings.defaults())
    }

    fun seedExtraNewCards(
        rows: List<RecordsImportModels.DashboardRow>?,
        existing: List<RecordsStudyModels.StudyItem>?,
        settings: RecordsSyncModels.Settings?,
        nowMillis: Long,
        startOfDayMillis: Long,
        requestedCount: Int,
        ladder: RecordsBase.StudyLadderSettings?
    ): ExtraNewCardsResult {
        return queueSeeder.seedExtraNewCards(safeRows(rows), safeItems(existing), safeSettings(settings), nowMillis, startOfDayMillis, requestedCount, ladder)
    }

    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long
    ): RecordsSchedulerModels.StudySession? {
        return nextSession(items, rows, nowMillis, null)
    }

    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        allowedKanji: Set<String>?
    ): RecordsSchedulerModels.StudySession? {
        return nextSession(items, rows, nowMillis, 0L, allowedKanji)
    }

    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?
    ): RecordsSchedulerModels.StudySession? {
        return nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, RecordsSyncModels.Settings.kikuDefaults())
    }

    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings?
    ): RecordsSchedulerModels.StudySession? {
        return nextSession(items, rows, nowMillis, studyAheadMillis, allowedKanji, settings, RecordsBase.StudyLadderSettings.defaults())
    }

    fun nextSession(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        settings: RecordsSyncModels.Settings?,
        ladder: RecordsBase.StudyLadderSettings?
    ): RecordsSchedulerModels.StudySession? {
        return sessionSelector.nextSession(safeItems(items), safeRows(rows), nowMillis, studyAheadMillis, allowedKanji, safeSettings(settings), ladder)
    }

    fun targetedSession(
        seededItems: List<RecordsStudyModels.StudyItem>?,
        row: RecordsImportModels.DashboardRow?,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?
    ): RecordsSchedulerModels.StudySession? {
        return targetedSessionPolicy.targetedSession(seededItems, row, nowMillis, ladder)
    }

    fun targetedStudyItem(
        seededItems: List<RecordsStudyModels.StudyItem>?,
        kanji: String?,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?
    ): RecordsStudyModels.StudyItem {
        return targetedSessionPolicy.targetedStudyItem(seededItems, kanji, nowMillis, ladder)
    }

    fun newTargetedStudyItem(
        kanji: String?,
        nowMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?
    ): RecordsStudyModels.StudyItem {
        return targetedSessionPolicy.newTargetedStudyItem(kanji, nowMillis, ladder)
    }

    fun applyReview(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>,
        nowMillis: Long
    ): RecordsSchedulerModels.ReviewResult {
        return applyReview(ReviewApplication.builder(item, request, consumedTokens, nowMillis).build())
    }

    fun applyReview(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters?
    ): RecordsSchedulerModels.ReviewResult {
        return applyReview(
            ReviewApplication.builder(item, request, consumedTokens, nowMillis)
                .parameters(parameters)
                .build()
        )
    }

    fun applyReview(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters?,
        settings: RecordsSyncModels.Settings?
    ): RecordsSchedulerModels.ReviewResult {
        return applyReview(
            ReviewApplication.builder(item, request, consumedTokens, nowMillis)
                .parameters(parameters)
                .settings(settings)
                .build()
        )
    }

    fun applyReview(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters?,
        settings: RecordsSyncModels.Settings?,
        ladder: RecordsBase.StudyLadderSettings?
    ): RecordsSchedulerModels.ReviewResult {
        return applyReview(
            ReviewApplication.builder(item, request, consumedTokens, nowMillis)
                .parameters(parameters)
                .settings(settings)
                .ladder(ladder)
                .build()
        )
    }

    fun applyReview(
        item: RecordsStudyModels.StudyItem,
        request: RecordsSchedulerModels.ReviewRequest,
        consumedTokens: MutableSet<String>,
        nowMillis: Long,
        parameters: RecordsSchedulerModels.SchedulerParameters?,
        settings: RecordsSyncModels.Settings?,
        learningSettings: RecordsSchedulerModels.LearningStepSettings?
    ): RecordsSchedulerModels.ReviewResult {
        return applyReview(
            ReviewApplication.builder(item, request, consumedTokens, nowMillis)
                .parameters(parameters)
                .settings(settings)
                .learningSettings(learningSettings)
                .build()
        )
    }

    fun applyReview(application: ReviewApplication): RecordsSchedulerModels.ReviewResult {
        return transitionEngine.applyReview(application)
    }

    fun dueCount(items: List<RecordsStudyModels.StudyItem>?, nowMillis: Long): Int {
        return dueCount(items, nowMillis, 0L)
    }

    fun dueCount(items: List<RecordsStudyModels.StudyItem>?, nowMillis: Long, studyAheadMillis: Long): Int {
        return sessionSelector.dueCount(safeItems(items), nowMillis, studyAheadMillis)
    }

    fun dueCount(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long
    ): Int {
        return dueCount(items, rows, nowMillis, 0L)
    }

    fun dueCount(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long
    ): Int {
        return dueCount(items, rows, nowMillis, studyAheadMillis, RecordsBase.StudyLadderSettings.defaults())
    }

    fun dueCount(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        ladder: RecordsBase.StudyLadderSettings?
    ): Int {
        return sessionSelector.dueCount(safeItems(items), safeRows(rows), nowMillis, studyAheadMillis, ladder)
    }

    fun activeQueueItems(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        allowedKanji: Set<String>?
    ): List<RecordsStudyModels.StudyItem> {
        return activeQueueItems(items, rows, nowMillis, 0L, allowedKanji)
    }

    fun activeQueueItems(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?
    ): List<RecordsStudyModels.StudyItem> {
        return activeQueueItems(items, rows, nowMillis, studyAheadMillis, allowedKanji, RecordsBase.StudyLadderSettings.defaults())
    }

    fun activeQueueItems(
        items: List<RecordsStudyModels.StudyItem>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        nowMillis: Long,
        studyAheadMillis: Long,
        allowedKanji: Set<String>?,
        ladder: RecordsBase.StudyLadderSettings?
    ): List<RecordsStudyModels.StudyItem> {
        return sessionSelector.activeQueueItems(safeItems(items), safeRows(rows), nowMillis, studyAheadMillis, allowedKanji, ladder)
    }

    /**
     * Creates a mutable token set from the given list of previously consumed
     * tokens. The returned set is not thread-safe; callers must synchronize
     * externally if the set will be shared across threads.
     */
    fun tokenSet(tokens: List<String>): MutableSet<String> = HashSet(tokens)

    fun applySuppression(items: List<RecordsStudyModels.StudyItem>?): List<RecordsStudyModels.StudyItem> {
        return suppressionPolicy.apply(safeItems(items))
    }

    class ExtraNewCardsResult internal constructor(
        items: List<RecordsStudyModels.StudyItem>,
        admittedKanji: List<String>,
        @JvmField val availableCount: Int
    ) {
        @JvmField
        val items: List<RecordsStudyModels.StudyItem> = Collections.unmodifiableList(ArrayList(items))

        @JvmField
        val admittedKanji: List<String> = Collections.unmodifiableList(ArrayList(admittedKanji))

        @JvmField
        val admittedCount: Int = admittedKanji.size

        fun admittedAny(): Boolean = admittedCount > 0
    }

    class ReviewApplication private constructor(builder: Builder) {
        internal val item: RecordsStudyModels.StudyItem = builder.item
        internal val request: RecordsSchedulerModels.ReviewRequest = builder.request
        internal val consumedTokens: MutableSet<String> = builder.consumedTokens
        internal val nowMillis: Long = builder.nowMillis
        internal val parameters: RecordsSchedulerModels.SchedulerParameters? = builder.parameters
        internal val settings: RecordsSyncModels.Settings? = builder.settings
        internal val learningSettings: RecordsSchedulerModels.LearningStepSettings? = builder.learningSettings
        internal val ladder: RecordsBase.StudyLadderSettings? = builder.ladder

        class Builder private constructor(
            val item: RecordsStudyModels.StudyItem,
            val request: RecordsSchedulerModels.ReviewRequest,
            val consumedTokens: MutableSet<String>,
            val nowMillis: Long
        ) {
            var parameters: RecordsSchedulerModels.SchedulerParameters? = RecordsSchedulerModels.SchedulerParameters.defaults()
                private set
            var settings: RecordsSyncModels.Settings? = RecordsSyncModels.Settings.kikuDefaults()
                private set
            var learningSettings: RecordsSchedulerModels.LearningStepSettings? = RecordsSchedulerModels.LearningStepSettings.defaults()
                private set
            var ladder: RecordsBase.StudyLadderSettings? = RecordsBase.StudyLadderSettings.defaults()
                private set

            fun parameters(parameters: RecordsSchedulerModels.SchedulerParameters?): Builder {
                this.parameters = parameters
                return this
            }

            fun settings(settings: RecordsSyncModels.Settings?): Builder {
                this.settings = settings
                return this
            }

            fun learningSettings(learningSettings: RecordsSchedulerModels.LearningStepSettings?): Builder {
                this.learningSettings = learningSettings
                return this
            }

            fun ladder(ladder: RecordsBase.StudyLadderSettings?): Builder {
                this.ladder = ladder
                return this
            }

            fun build(): ReviewApplication = ReviewApplication(this)

            companion object {
                fun create(
                    item: RecordsStudyModels.StudyItem,
                    request: RecordsSchedulerModels.ReviewRequest,
                    consumedTokens: MutableSet<String>,
                    nowMillis: Long
                ): Builder = Builder(item, request, consumedTokens, nowMillis)
            }
        }

        companion object {
            @JvmStatic
            fun builder(
                item: RecordsStudyModels.StudyItem,
                request: RecordsSchedulerModels.ReviewRequest,
                consumedTokens: MutableSet<String>,
                nowMillis: Long
            ): Builder = Builder.create(item, request, consumedTokens, nowMillis)
        }
    }

    companion object {
        @JvmField
        val DAY: Long = StudyLadderRules.DAY

        const val RATING_AGAIN: String = StudyRatings.AGAIN
        const val RATING_HARD: String = StudyRatings.HARD
        const val RATING_GOOD: String = StudyRatings.GOOD
        const val RATING_EASY: String = StudyRatings.EASY

        const val TASK_WRITE_KANJI: String = StudyTaskTypes.WRITE_KANJI
        const val TASK_TYPE_MEANING: String = StudyTaskTypes.TYPE_MEANING
        const val TASK_SIMILAR_KANJI: String = StudyTaskTypes.SIMILAR_KANJI
        const val TASK_MEANING_KANJI: String = StudyTaskTypes.MEANING_KANJI
        const val TASK_KANJI_MEANING: String = StudyTaskTypes.KANJI_MEANING
        const val TASK_FONT_MEANING: String = StudyTaskTypes.FONT_MEANING
        const val TASK_WORD_READING: String = StudyTaskTypes.WORD_READING

        const val TASK_TYPING_MEANING: String = StudyTaskTypes.TYPING_MEANING
        const val TASK_WRITING_REMEDIATION: String = StudyTaskTypes.WRITING_REMEDIATION

        @JvmStatic
        fun promoteRung(
            current: RecordsBase.LadderRung,
            hasSimilarKanji: Boolean
        ): RecordsBase.LadderRung = StudyLadderRules.promoteRung(current, hasSimilarKanji)

        @JvmStatic
        fun promoteRung(
            current: RecordsBase.LadderRung,
            hasSimilarKanji: Boolean,
            ladder: RecordsBase.StudyLadderSettings?
        ): RecordsBase.LadderRung = StudyLadderRules.promoteRung(current, hasSimilarKanji, ladder)

        @JvmStatic
        fun demoteRung(
            current: RecordsBase.LadderRung,
            hasSimilarKanji: Boolean
        ): RecordsBase.LadderRung = StudyLadderRules.demoteRung(current, hasSimilarKanji)

        @JvmStatic
        fun demoteRung(
            current: RecordsBase.LadderRung,
            hasSimilarKanji: Boolean,
            ladder: RecordsBase.StudyLadderSettings?
        ): RecordsBase.LadderRung = StudyLadderRules.demoteRung(current, hasSimilarKanji, ladder)

        @JvmStatic
        fun rungsForItem(item: RecordsStudyModels.StudyItem): List<RecordsBase.LadderRung> {
            return StudyLadderRules.rungsForItem(item)
        }

        @JvmStatic
        fun rungsForItem(
            item: RecordsStudyModels.StudyItem,
            ladder: RecordsBase.StudyLadderSettings?
        ): List<RecordsBase.LadderRung> {
            return StudyLadderRules.rungsForItem(item, ladder)
        }

        private fun safeRows(rows: List<RecordsImportModels.DashboardRow>?): List<RecordsImportModels.DashboardRow> {
            return rows ?: emptyList()
        }

        private fun safeItems(items: List<RecordsStudyModels.StudyItem>?): List<RecordsStudyModels.StudyItem> {
            return items ?: emptyList()
        }

        private fun safeSettings(settings: RecordsSyncModels.Settings?): RecordsSyncModels.Settings {
            return settings ?: RecordsSyncModels.Settings.kikuDefaults()
        }
    }
}
