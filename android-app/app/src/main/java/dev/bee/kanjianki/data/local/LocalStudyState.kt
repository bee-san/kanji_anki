package dev.bee.kanjianki.data.local

import dev.bee.kanjianki.domain.HandwritingPolicySnapshot
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.SessionMode
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyQueuePreviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewRequest
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.json.JSONObject

private const val RETENTION_TARGET = 0.9
private const val SECONDS_PER_DAY = 86_400L
private const val FIRST_SCHEDULED_LEARNING_SECONDS = 10 * 60L
private const val MAX_ACTIVE_QUEUE_ITEMS = 25
private const val MAX_NEW_ITEMS_PER_DAY = 3
private const val RETIREMENT_INTERVAL_DAYS = 30L

private val ACTIVE_STATUSES = setOf("new", "learning", "review")
private val PASS_RATINGS = setOf("hard", "good", "easy", "pass")
private val VALID_RATINGS = PASS_RATINGS + setOf("again", "fail")
private val YOUNG_REVIEW_CYCLE = listOf(
    "context-production",
    "confusable-recognition",
    "handwriting",
)
private val MATURE_REVIEW_CYCLE = listOf(
    "context-production-a",
    "context-production-b",
    "confusable-recognition",
    "handwriting",
)

internal data class LocalStudyTask(
    val taskKind: String,
    val schedulerPhase: String,
    val promptType: String,
    val promptLabel: String,
    val requiresWriting: Boolean,
)

internal data class LocalStudyReviewResult(
    val updatedItem: StudyItemEntity,
    val task: LocalStudyTask,
    val binaryOutcome: String,
    val reviewedAt: String,
    val guideLevelBefore: Int,
    val guideLevelAfter: Int,
    val normalizedRating: String,
)

internal data class LocalSeedRefreshResult(
    val refresh: SeedRefreshSnapshot,
    val overview: StudyOverviewSnapshot,
    val items: List<StudyItemEntity>,
)

internal object LocalStudyState {
    fun buildOverview(
        items: List<StudyItemEntity>,
        nowTs: Long,
    ): StudyOverviewSnapshot {
        val dueCount = items.count { it.itemStatus in setOf("learning", "review") && it.dueTs <= nowTs }
        val newCount = items.count { it.itemStatus == "new" && it.isProblemSeed }
        val activeQueueCount = items.count { it.itemStatus in ACTIVE_STATUSES }
        val inactiveCount = items.count { it.itemStatus == "inactive" }
        val currentProblemSeedCount = items.count {
            it.isProblemSeed && it.itemStatus in ACTIVE_STATUSES
        }
        val nextDueAt = items
            .filter { it.itemStatus in setOf("learning", "review") }
            .minOfOrNull(StudyItemEntity::dueTs)
            ?.let(::toIso)
        val queuePreview = items
            .filter { it.itemStatus in setOf("new", "learning", "review") }
            .sortedWith(previewComparator(nowTs))
            .take(8)
            .map { toQueuePreview(it, nowTs) }

        return StudyOverviewSnapshot(
            dueCount = dueCount,
            newCount = newCount,
            activeQueueCount = activeQueueCount,
            inactiveCount = inactiveCount,
            currentProblemSeedCount = currentProblemSeedCount,
            nextDueAt = nextDueAt,
            queuePreview = queuePreview,
        )
    }

    fun syncProblemSeeds(
        existingItems: List<StudyItemEntity>,
        dashboardRows: List<ProblemKanjiSnapshotEntity>,
        profile: String,
        nowTs: Long,
    ): LocalSeedRefreshResult {
        val existingByKanji = existingItems.associateBy(StudyItemEntity::kanji)
        val remainingExisting = existingItems.associateBy(StudyItemEntity::kanji).toMutableMap()
        val mergedItems = mutableListOf<StudyItemEntity>()
        var activeCount = existingItems.count { it.itemStatus in ACTIVE_STATUSES }
        var introducedCount = 0
        var updatedCount = 0
        var reactivatedCount = 0
        var inactivatedCount = 0

        val orderedRows = dashboardRows.sortedWith(compareBy(ProblemKanjiSnapshotEntity::sortIndex, ProblemKanjiSnapshotEntity::kanji))
        orderedRows.forEach { row ->
            val existing = existingByKanji[row.kanji]
            remainingExisting.remove(row.kanji)
            if (existing == null) {
                val itemStatus = if (activeCount < MAX_ACTIVE_QUEUE_ITEMS) "new" else "inactive"
                mergedItems += buildNewSeedItem(
                    row = row,
                    profile = profile,
                    nowTs = nowTs,
                    itemStatus = itemStatus,
                )
                if (itemStatus == "new") {
                    activeCount += 1
                }
                introducedCount += 1
                return@forEach
            }

            val wasActive = existing.itemStatus in ACTIVE_STATUSES
            var nextStatus = existing.itemStatus
            var dueTs = existing.dueTs
            var learningStep = existing.learningStep
            var reviewCycleIndex = existing.reviewCycleIndex
            var inactiveReason = existing.inactiveReason
            var retiredTs = existing.retiredTs
            var retirementContextJson = existing.retirementContextJson

            if (nextStatus == "inactive" && existing.inactiveReason != "retired") {
                if (activeCount < MAX_ACTIVE_QUEUE_ITEMS) {
                    nextStatus = "new"
                    dueTs = nowTs
                    learningStep = 0
                    reviewCycleIndex = existing.reviewCycleIndex
                    inactiveReason = null
                    retiredTs = null
                    retirementContextJson = "{}"
                } else {
                    inactiveReason = "queue-cap"
                }
            } else {
                updatedCount += 1
            }

            if (wasActive && nextStatus == "inactive") {
                activeCount -= 1
                inactivatedCount += 1
            } else if (!wasActive && nextStatus in ACTIVE_STATUSES) {
                activeCount += 1
                reactivatedCount += 1
            }

            mergedItems += existing.copy(
                dueTs = dueTs,
                itemStatus = nextStatus,
                isProblemSeed = true,
                latestProblemSnapshotJson = row.toProblemSnapshotJson(),
                prioritySuspendedCount = row.suspendedExpressionCount,
                prioritySupportDeficit = row.supportDeficit,
                priorityActiveRecurringCount = row.activeRecurringExpressionCount,
                priorityRank = row.jitenRank,
                updatedTs = nowTs,
                learningStep = learningStep,
                reviewCycleIndex = reviewCycleIndex,
                inactiveReason = inactiveReason,
                retiredTs = retiredTs,
                retirementContextJson = retirementContextJson,
                activeReviewToken = if (nextStatus == "inactive") null else existing.activeReviewToken,
                activePromptType = if (nextStatus == "inactive") null else existing.activePromptType,
                activeSessionIssuedTs = if (nextStatus == "inactive") null else existing.activeSessionIssuedTs,
            )
        }

        remainingExisting.values.sortedBy(StudyItemEntity::kanji).forEach { existing ->
            if (!existing.isProblemSeed) {
                mergedItems += existing
                return@forEach
            }

            val wasActive = existing.itemStatus in ACTIVE_STATUSES
            var nextStatus = existing.itemStatus
            var inactiveReason = existing.inactiveReason
            if (nextStatus == "new") {
                nextStatus = "inactive"
                inactiveReason = "seed-dropped"
            }

            if (wasActive && nextStatus == "inactive") {
                activeCount -= 1
                inactivatedCount += 1
            } else if (!wasActive && nextStatus in ACTIVE_STATUSES) {
                activeCount += 1
                reactivatedCount += 1
            }

            mergedItems += existing.copy(
                itemStatus = nextStatus,
                isProblemSeed = false,
                inactiveReason = inactiveReason,
                updatedTs = nowTs,
                activeReviewToken = if (nextStatus == "inactive") null else existing.activeReviewToken,
                activePromptType = if (nextStatus == "inactive") null else existing.activePromptType,
                activeSessionIssuedTs = if (nextStatus == "inactive") null else existing.activeSessionIssuedTs,
            )
        }

        val overview = buildOverview(mergedItems, nowTs)
        return LocalSeedRefreshResult(
            refresh = SeedRefreshSnapshot(
                introducedCount = introducedCount,
                updatedCount = updatedCount,
                reactivatedCount = reactivatedCount,
                inactivatedCount = inactivatedCount,
                currentProblemSeedCount = orderedRows.size,
            ),
            overview = overview,
            items = mergedItems,
        )
    }

    fun selectSessionItem(
        items: List<StudyItemEntity>,
        mode: SessionMode,
        nowTs: Long,
    ): StudyItemEntity? {
        val active = items
            .filter { !it.activeReviewToken.isNullOrBlank() }
            .sortedWith(
                compareBy<StudyItemEntity>(
                    { if (it.activeSessionIssuedTs == null) 1 else 0 },
                    { it.activeSessionIssuedTs ?: Long.MAX_VALUE },
                    { it.updatedTs },
                    { it.kanji },
                ),
            )
            .firstOrNull()
        if (active != null) {
            return active
        }

        items
            .filter { it.itemStatus == "learning" && it.dueTs <= nowTs }
            .sortedWith(dueItemComparator())
            .firstOrNull()
            ?.let { return it }

        if (mode != SessionMode.NEW) {
            items
                .filter { it.itemStatus == "review" && it.dueTs <= nowTs }
                .sortedWith(dueItemComparator())
                .firstOrNull()
                ?.let { return it }
        }

        if (mode == SessionMode.REVIEW) {
            return null
        }
        if (introducedTodayCount(items, nowTs) >= MAX_NEW_ITEMS_PER_DAY) {
            return null
        }

        return items
            .filter { it.itemStatus == "new" && it.isProblemSeed }
            .sortedWith(priorityComparator())
            .firstOrNull()
    }

    fun describeTask(item: StudyItemEntity): LocalStudyTask {
        val taskKind = taskKindForItem(item)
        if (taskKind == "confusable-recognition") {
            return LocalStudyTask(
                taskKind = taskKind,
                schedulerPhase = if (item.itemStatus == "review" && isBridgeMature(item)) {
                    "mature-review"
                } else if (item.itemStatus == "review") {
                    "young-review"
                } else {
                    "acquisition"
                },
                promptType = "recognition",
                promptLabel = "Confusable recognition",
                requiresWriting = false,
            )
        }
        if (taskKind == "handwriting") {
            return LocalStudyTask(
                taskKind = taskKind,
                schedulerPhase = if (isBridgeMature(item)) "mature-review" else "young-review",
                promptType = "production",
                promptLabel = "Handwriting review",
                requiresWriting = true,
            )
        }
        if (item.itemStatus == "new") {
            return LocalStudyTask(
                taskKind = taskKind,
                schedulerPhase = "acquisition",
                promptType = "production",
                promptLabel = "Acquisition cue",
                requiresWriting = true,
            )
        }
        if (item.itemStatus == "learning") {
            return LocalStudyTask(
                taskKind = taskKind,
                schedulerPhase = "scheduled-learning",
                promptType = "production",
                promptLabel = "Bridge review",
                requiresWriting = true,
            )
        }
        return LocalStudyTask(
            taskKind = taskKind,
            schedulerPhase = if (isBridgeMature(item)) "mature-review" else "young-review",
            promptType = "production",
            promptLabel = "Context production",
            requiresWriting = false,
        )
    }

    fun buildSession(
        item: StudyItemEntity,
        detail: KanjiDetailSnapshot,
        reviewToken: String,
    ): StudySessionSnapshot {
        val task = describeTask(item)
        return StudySessionSnapshot(
            kanji = item.kanji,
            reviewToken = reviewToken,
            promptType = task.promptType,
            promptLabel = task.promptLabel,
            taskKind = task.taskKind,
            schedulerPhase = task.schedulerPhase,
            requiresWriting = task.requiresWriting,
            itemStatus = item.itemStatus,
            reviewCount = item.totalReviews,
            guideLevelLabel = guideLevelLabel(item.guideLevel),
            handwritingPolicy = HandwritingPolicySnapshot(
                required = task.requiresWriting,
                guideMode = guideMode(item.guideLevel),
                guideLevelLabel = guideLevelLabel(item.guideLevel),
                guidedEvaluationAvailable = detail.strokeCount > 0,
                manualOnlyWithoutGeometry = detail.strokeCount <= 0,
                allowedRatingsOnFailure = if (task.requiresWriting) {
                    listOf("again")
                } else {
                    listOf("again", "hard", "good", "easy")
                },
            ),
            keyword = detail.keyword,
            productionContext = productionContext(detail),
            recognitionContext = recognitionContext(detail),
            supportWords = supportWords(detail),
            painExample = detail.suspendedExamples.firstOrNull(),
            bridgeExample = detail.activeRecurringExamples.firstOrNull()
                ?: detail.collectionExamples.firstOrNull(),
            matureExample = detail.matureExamples.firstOrNull(),
        )
    }

    fun buildReviewSnapshot(
        item: StudyItemEntity,
        binaryOutcome: String,
        reviewedAt: String,
        overviewDueCount: Int,
    ): StudyReviewSnapshot =
        StudyReviewSnapshot(
            binaryOutcome = binaryOutcome,
            reviewedAt = reviewedAt,
            itemStatus = item.itemStatus,
            reviewCount = item.totalReviews,
            guideLevelLabel = guideLevelLabel(item.guideLevel),
            dueAt = dueAt(item),
            overviewDueCount = overviewDueCount,
        )

    fun applyReview(
        item: StudyItemEntity,
        request: StudyReviewRequest,
        nowTs: Long,
    ): LocalStudyReviewResult {
        val normalizedRating = normalizeRating(request.rating)
        require(normalizedRating in VALID_RATINGS) {
            "rating must be one of again, fail, hard, good, easy, or pass."
        }

        val task = describeTask(item)
        validateHandwriting(task, normalizedRating, request)
        val passedReview = isPassRating(normalizedRating)
        val guideBefore = item.guideLevel
        val (guideAfter, successStreak, failureStreak) = updateGuideProgression(
            item = item,
            attempted = request.handwritingResult.attempted,
            passed = request.handwritingResult.passed,
        )

        var dueTs = item.dueTs
        var nextStatus = item.itemStatus
        var learningStep = item.learningStep
        var reviewCycleIndex = item.reviewCycleIndex
        var stability = item.stability
        var difficulty = item.difficulty
        var totalLapses = item.totalLapses

        when {
            item.itemStatus == "new" -> {
                dueTs = nowTs
                nextStatus = "learning"
                learningStep = 1
                stability = adjustPacketStability(item.stability, passedReview)
                difficulty = adjustDifficulty(item.difficulty, passedReview)
            }

            item.itemStatus == "learning" && item.learningStep == 1 -> {
                stability = adjustPacketStability(item.stability, passedReview)
                difficulty = adjustDifficulty(item.difficulty, passedReview)
                if (passedReview) {
                    dueTs = nowTs + FIRST_SCHEDULED_LEARNING_SECONDS
                    nextStatus = "learning"
                    learningStep = 2
                } else {
                    dueTs = nowTs
                    nextStatus = "learning"
                    learningStep = 0
                }
            }

            item.itemStatus == "learning" && item.learningStep == 2 -> {
                val scheduled = scheduleAfterReview(item, passedReview, nowTs)
                dueTs = scheduled.dueTs
                nextStatus = scheduled.nextStatus
                stability = scheduled.stability
                difficulty = scheduled.difficulty
                totalLapses = scheduled.totalLapses
                if (passedReview) {
                    learningStep = 0
                    reviewCycleIndex = 1
                } else {
                    dueTs = nowTs
                    nextStatus = "learning"
                    learningStep = 0
                    reviewCycleIndex = 0
                }
            }

            else -> {
                val scheduled = scheduleAfterReview(item, passedReview, nowTs)
                dueTs = scheduled.dueTs
                nextStatus = scheduled.nextStatus
                stability = scheduled.stability
                difficulty = scheduled.difficulty
                totalLapses = scheduled.totalLapses
                if (passedReview) {
                    learningStep = 0
                    reviewCycleIndex = (item.reviewCycleIndex + 1) % reviewCycle(item).size
                } else {
                    dueTs = nowTs
                    nextStatus = "learning"
                    learningStep = 0
                    reviewCycleIndex = 0
                }
            }
        }

        val updatedItem = item.copy(
            dueTs = dueTs,
            itemStatus = nextStatus,
            guideLevel = guideAfter,
            consecutiveWritingSuccesses = successStreak,
            consecutiveWritingFailures = failureStreak,
            stability = stability,
            difficulty = difficulty,
            totalReviews = item.totalReviews + 1,
            totalLapses = totalLapses,
            lastPromptType = task.taskKind,
            updatedTs = nowTs,
            lastReviewedTs = nowTs,
            activeReviewToken = null,
            activePromptType = null,
            activeSessionIssuedTs = null,
            learningStep = learningStep,
            reviewCycleIndex = reviewCycleIndex,
            firstIntroducedTs = item.firstIntroducedTs ?: if (item.itemStatus == "new") nowTs else null,
            inactiveReason = null,
            retiredTs = null,
            retirementContextJson = "{}",
        )

        return LocalStudyReviewResult(
            updatedItem = updatedItem,
            task = task,
            binaryOutcome = if (passedReview) "pass" else "fail",
            reviewedAt = toIso(nowTs),
            guideLevelBefore = guideBefore,
            guideLevelAfter = guideAfter,
            normalizedRating = normalizedRating,
        )
    }

    fun guideLevelLabel(level: Int): String =
        when (level.coerceIn(0, 3)) {
            0 -> "Trace"
            1 -> "Outline"
            2 -> "Minimal hints"
            else -> "Blind recall"
        }

    fun dueAt(item: StudyItemEntity): String? =
        if (item.itemStatus == "new") null else toIso(item.dueTs)

    fun toIso(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds).toString()

    fun isPassRating(rating: String): Boolean = normalizeRating(rating) in PASS_RATINGS

    private fun validateHandwriting(
        task: LocalStudyTask,
        rating: String,
        request: StudyReviewRequest,
    ) {
        if (!task.requiresWriting) {
            return
        }
        require(request.handwritingResult.attempted) {
            "A handwriting result is required before submitting this writing review."
        }
        require(request.handwritingResult.passed || !isPassRating(rating)) {
            "A failed handwriting check only allows again unless you override it to pass."
        }
    }

    private fun updateGuideProgression(
        item: StudyItemEntity,
        attempted: Boolean,
        passed: Boolean,
    ): Triple<Int, Int, Int> {
        if (!attempted) {
            return Triple(
                item.guideLevel,
                item.consecutiveWritingSuccesses,
                item.consecutiveWritingFailures,
            )
        }
        if (passed) {
            var guideLevel = item.guideLevel
            var successStreak = item.consecutiveWritingSuccesses + 1
            if (successStreak >= 2 && guideLevel < 3) {
                guideLevel += 1
                successStreak = 0
            }
            return Triple(guideLevel, successStreak, 0)
        }

        var guideLevel = item.guideLevel
        var failureStreak = item.consecutiveWritingFailures + 1
        if (failureStreak >= 2 && guideLevel > 0) {
            guideLevel -= 1
            failureStreak = 0
        }
        return Triple(guideLevel, 0, failureStreak)
    }

    private fun scheduleAfterReview(
        item: StudyItemEntity,
        passed: Boolean,
        nowTs: Long,
    ): ScheduledReview {
        var difficulty = item.difficulty
        var stability = item.stability
        var totalLapses = item.totalLapses
        val retrievability = fsrsRetrievability(stability, elapsedDays(item, nowTs))

        if (!passed) {
            if (item.itemStatus == "review") {
                totalLapses += 1
            }
            difficulty = adjustDifficulty(difficulty, false)
            stability = fsrsFailedStability(stability, difficulty, retrievability)
            return ScheduledReview(
                dueTs = nowTs,
                nextStatus = "learning",
                stability = stability,
                difficulty = difficulty,
                totalLapses = totalLapses,
            )
        }

        difficulty = adjustDifficulty(difficulty, true)
        stability = if (item.itemStatus == "review") {
            fsrsNextStability(stability, difficulty, retrievability)
        } else {
            fsrsInitialStability(difficulty)
        }
        val intervalDays = max(1.0, stability)
        return ScheduledReview(
            dueTs = nowTs + (intervalDays * SECONDS_PER_DAY).toLong(),
            nextStatus = "review",
            stability = stability,
            difficulty = difficulty,
            totalLapses = totalLapses,
        )
    }

    private fun taskKindForItem(item: StudyItemEntity): String =
        when (item.itemStatus) {
            "review" -> {
                val cycle = reviewCycle(item)
                cycle[Math.floorMod(item.reviewCycleIndex, cycle.size)]
            }

            else -> "context-production"
        }

    private fun reviewCycle(item: StudyItemEntity): List<String> =
        if (isBridgeMature(item)) MATURE_REVIEW_CYCLE else YOUNG_REVIEW_CYCLE

    private fun isBridgeMature(item: StudyItemEntity): Boolean {
        if (item.itemStatus != "review" || item.lastReviewedTs == null) {
            return false
        }
        val scheduledInterval = item.dueTs - item.lastReviewedTs
        return scheduledInterval >= RETIREMENT_INTERVAL_DAYS * SECONDS_PER_DAY
    }

    private fun elapsedDays(item: StudyItemEntity, nowTs: Long): Double {
        val lastReviewedTs = item.lastReviewedTs ?: return 0.0
        return max(0.0, (nowTs - lastReviewedTs).toDouble() / SECONDS_PER_DAY.toDouble())
    }

    private fun adjustDifficulty(current: Double, passed: Boolean): Double {
        val meanReversion = 5.5
        val delta = if (passed) -0.35 else 0.55
        return max(1.0, min(10.0, (current * 0.9) + (meanReversion * 0.1) + delta))
    }

    private fun adjustPacketStability(current: Double, passed: Boolean): Double =
        if (passed) {
            max(0.9, min(4.0, current + 0.25))
        } else {
            max(0.6, current * 0.82)
        }

    private fun fsrsInitialStability(difficulty: Double): Double =
        max(1.0, 3.0 - (difficulty * 0.22))

    private fun fsrsRetrievability(stability: Double, elapsedDays: Double): Double {
        val stableDays = max(0.1, stability)
        val retention = RETENTION_TARGET.pow(elapsedDays / stableDays)
        return max(0.01, min(0.999, retention))
    }

    private fun fsrsNextStability(
        stability: Double,
        difficulty: Double,
        retrievability: Double,
    ): Double {
        val difficultyTerm = 1.0 + ((10.0 - difficulty) / 9.0) * 0.75
        val retrievabilityTerm = 1.0 + max(0.0, 1.0 - retrievability) * 2.4
        val scaleTerm = 1.05 + min(0.8, stability / 15.0)
        return max(stability + 0.25, stability * difficultyTerm * retrievabilityTerm * scaleTerm)
    }

    private fun fsrsFailedStability(
        stability: Double,
        difficulty: Double,
        retrievability: Double,
    ): Double =
        max(
            0.45,
            min(
                1.6,
                stability * (0.28 + (difficulty / 30.0)) * (0.75 + (retrievability * 0.15)),
            ),
        )

    private fun introducedTodayCount(items: List<StudyItemEntity>, nowTs: Long): Int {
        val dayStart = nowTs - (nowTs % SECONDS_PER_DAY)
        return items.count { (it.firstIntroducedTs ?: 0L) >= dayStart }
    }

    private fun guideMode(level: Int): String =
        when (level.coerceIn(0, 3)) {
            0 -> "trace"
            1 -> "outline"
            2 -> "minimal-hints"
            else -> "blind-recall"
        }

    private fun toQueuePreview(
        item: StudyItemEntity,
        nowTs: Long,
    ): StudyQueuePreviewSnapshot {
        val problem = latestProblemSnapshot(item)
        return StudyQueuePreviewSnapshot(
            kanji = item.kanji,
            itemStatus = item.itemStatus,
            dueAt = dueAt(item),
            dueNow = item.itemStatus != "new" && item.dueTs <= nowTs,
            guideLevelLabel = guideLevelLabel(item.guideLevel),
            supportDeficit = problem.optInt("supportDeficit"),
            suspendedExpressionCount = problem.optInt("suspendedExpressionCount"),
        )
    }

    private fun latestProblemSnapshot(item: StudyItemEntity): JSONObject =
        runCatching { JSONObject(item.latestProblemSnapshotJson) }.getOrElse { JSONObject() }

    private fun buildNewSeedItem(
        row: ProblemKanjiSnapshotEntity,
        profile: String,
        nowTs: Long,
        itemStatus: String,
    ): StudyItemEntity =
        StudyItemEntity(
            profile = profile,
            kanji = row.kanji,
            dueTs = nowTs,
            itemStatus = itemStatus,
            isProblemSeed = true,
            guideLevel = 0,
            consecutiveWritingSuccesses = 0,
            consecutiveWritingFailures = 0,
            stability = 1.2,
            difficulty = 5.5,
            totalReviews = 0,
            totalLapses = 0,
            lastPromptType = null,
            latestProblemSnapshotJson = row.toProblemSnapshotJson(),
            prioritySuspendedCount = row.suspendedExpressionCount,
            prioritySupportDeficit = row.supportDeficit,
            priorityActiveRecurringCount = row.activeRecurringExpressionCount,
            priorityRank = row.jitenRank,
            createdTs = nowTs,
            updatedTs = nowTs,
            lastReviewedTs = null,
            activeReviewToken = null,
            activePromptType = null,
            activeSessionIssuedTs = null,
            learningStep = 0,
            reviewCycleIndex = 0,
            firstIntroducedTs = null,
            inactiveReason = if (itemStatus == "inactive") "queue-cap" else null,
            retiredTs = null,
            retirementContextJson = "{}",
        )

    private fun ProblemKanjiSnapshotEntity.toProblemSnapshotJson(): String =
        JSONObject()
            .put("supportDeficit", supportDeficit)
            .put("suspendedExpressionCount", suspendedExpressionCount)
            .put("activeRecurringExpressionCount", activeRecurringExpressionCount)
            .put("collectionExpressionCount", collectionExpressionCount)
            .put("matureSupportCount", matureSupportCount)
            .put("jitenRank", jitenRank)
            .put("isUnknown", isUnknown)
            .put("browserSearch", browserSearch)
            .toString()

    private fun priorityComparator(): Comparator<StudyItemEntity> =
        compareBy<StudyItemEntity>(
            { -it.prioritySuspendedCount },
            { -it.prioritySupportDeficit },
            { -it.priorityActiveRecurringCount },
            { if (it.priorityRank == null) 1 else 0 },
            { it.priorityRank ?: Double.MAX_VALUE },
            { it.kanji },
        )

    private fun dueItemComparator(): Comparator<StudyItemEntity> =
        compareBy<StudyItemEntity>(
            { it.dueTs },
            { -it.prioritySuspendedCount },
            { -it.prioritySupportDeficit },
            { -it.priorityActiveRecurringCount },
            { if (it.priorityRank == null) 1 else 0 },
            { it.priorityRank ?: Double.MAX_VALUE },
            { it.kanji },
        )

    private fun previewComparator(nowTs: Long): Comparator<StudyItemEntity> =
        compareBy<StudyItemEntity>(
            { previewBucket(it, nowTs) },
            { it.dueTs },
            { -it.prioritySuspendedCount },
            { -it.prioritySupportDeficit },
            { -it.priorityActiveRecurringCount },
            { if (it.priorityRank == null) 1 else 0 },
            { it.priorityRank ?: Double.MAX_VALUE },
            { it.kanji },
        )

    private fun previewBucket(item: StudyItemEntity, nowTs: Long): Int =
        when {
            item.itemStatus == "learning" && item.dueTs <= nowTs -> 0
            item.itemStatus == "review" && item.dueTs <= nowTs -> 1
            item.itemStatus == "learning" -> 2
            item.itemStatus == "review" -> 3
            else -> 4
        }

    private fun productionContext(detail: KanjiDetailSnapshot): List<String> =
        orderedDistinct(
            detail.activeRecurringExamples,
            detail.collectionExamples,
            detail.suspendedExamples,
            detail.matureExamples,
        ).take(3)

    private fun recognitionContext(detail: KanjiDetailSnapshot): List<String> =
        orderedDistinct(
            detail.matureExamples,
            detail.collectionExamples,
            detail.activeRecurringExamples,
            detail.suspendedExamples,
        ).take(3)

    private fun supportWords(detail: KanjiDetailSnapshot): List<String> =
        orderedDistinct(
            detail.activeRecurringExamples,
            detail.matureExamples,
            detail.collectionExamples,
        ).take(4)

    private fun orderedDistinct(vararg groups: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        groups.forEach { group ->
            group.forEach { value ->
                val normalized = value.trim()
                if (normalized.isNotEmpty()) {
                    seen.add(normalized)
                }
            }
        }
        return seen.toList()
    }

    private fun normalizeRating(rating: String): String = rating.trim().lowercase()
}

private data class ScheduledReview(
    val dueTs: Long,
    val nextStatus: String,
    val stability: Double,
    val difficulty: Double,
    val totalLapses: Int,
)
