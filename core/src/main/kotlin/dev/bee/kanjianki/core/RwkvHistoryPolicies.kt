package dev.bee.kanjianki.core

import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvStreamIds
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Synthetic prior histories for evidence-seeded kanji.
 *
 * Kani cannot read Anki's review log through the provider, so a kanji seeded
 * from Anki's memory state reaches RWKV with no history and would read as a
 * brand-new card. Before such a card's first Kani answer the model is warmed
 * with a short all-Good history whose gaps grow geometrically to the imported
 * stability, ending one stability-length before the first Kani review (so that
 * review is "on time" at about 90% recall). Only the card and note streams
 * see the burst; see `RwkvEngine.warmCard`.
 */
object RwkvSeedBurstPolicy {
    /** Below this a stored stability is a new-card placeholder, not imported evidence. */
    const val MIN_SEED_STABILITY_DAYS: Double = 1.0
    private const val MAX_SEED_STABILITY_DAYS: Double = 3_650.0
    private const val MIN_ANSWERS = 2
    private const val MAX_ANSWERS = 8
    private const val GOOD = 3
    private const val REVIEW_KIND = 2

    /**
     * True for a core memory carrying imported Anki evidence that Kani has
     * never reviewed. Admission seeding (`StudyQueueSeeder.applySeed`) writes
     * the imported stability with no last-review time; a contextual core
     * seeded on promotion also has no reviews yet but records the promotion
     * as its last review, and its stability is the model's own preview, so it
     * is not imported evidence and must not be warmed.
     */
    @JvmStatic
    fun isEvidenceSeed(memory: RecordsStudyModels.TaskMemory): Boolean =
        memory.totalReviews <= 0 &&
            memory.lastReviewedAtMillis <= 0L &&
            memory.stability.isFinite() &&
            memory.stability >= MIN_SEED_STABILITY_DAYS

    /**
     * The synthetic answers for a seed of [stabilityDays] whose first real Kani
     * review happens at [firstReviewAtMillis]. Oldest first.
     */
    @JvmStatic
    fun answers(stabilityDays: Double, firstReviewAtMillis: Long, dayOffsetOf: (Long) -> Long): List<RwkvAnswer> {
        val stability = stabilityDays.coerceIn(MIN_SEED_STABILITY_DAYS, MAX_SEED_STABILITY_DAYS)
        val count = (1 + kotlin.math.ceil(ln(stability) / ln(2.0)).toInt()).coerceIn(MIN_ANSWERS, MAX_ANSWERS)
        val times = LongArray(count)
        var at = firstReviewAtMillis
        for (index in count - 1 downTo 0) {
            val gapDays = stability.pow((index + 1).toDouble() / count)
            at -= (gapDays * MemoryReviewResult.DAY_MILLIS).roundToLong()
            times[index] = at
        }
        return times.map { millis -> RwkvAnswer(GOOD, null, millis, dayOffsetOf(millis), REVIEW_KIND) }
    }
}

/**
 * Classifies the review log written before the RWKV migration (database v34)
 * into the answers RWKV is fed, applying the same history rule live reviews
 * follow: every legacy-routing answer (learning and relearning steps included)
 * and every adaptive core or revalidation answer is fed; adaptive repair
 * appearances are not. Rows must be visited in commit (`review_log.id`) order.
 */
class RwkvHistoryBackfill {
    /** One `review_log` row as persisted. */
    class Row(
        val kanji: String,
        val answerSignature: String,
        val taskType: String,
        val rating: String,
        val reviewedAtMillis: Long,
        val reviewDayStartMillis: Long,
        val coreSkill: String,
        val schedulerStateBeforeJson: String,
        val memoryBefore: String,
        val activeElapsedMillis: Long?,
    )

    /** A fed row: its stream ids and model input, and the burst to warm first, if it starts a seeded card. */
    class Fed(val ids: RwkvStreamIds, val answer: RwkvAnswer, val warmFirst: List<RwkvAnswer>)

    private val seenCards = HashSet<Long>()

    /** Records cards already fed in an earlier (resumed) pass, so they are not warmed again. */
    fun markSeen(cardIds: Collection<Long>) {
        seenCards += cardIds
    }

    /** The model input for [row], or null when the row is not a core answer. */
    fun classify(row: Row): Fed? {
        val before = CompactJson.decodeObject(row.schedulerStateBeforeJson)
        val phase = phaseOf(before?.get("phase") as? String)
        val routingVersion = (before?.get("routing_version") as? Number)?.toInt() ?: 1
        val core: CoreSkill
        if (routingVersion >= AdaptiveStudyItemPolicy.ROUTING_VERSION) {
            val route = AdaptiveRouteStateCodec.decode(before?.get("adaptive_route_state_json") as? String)
            if (route != null && route.isRepairActive()) {
                return null
            }
            core = route?.activeCore ?: fallbackCore(row, before)
        } else {
            core = fallbackCore(row, before)
        }
        val ids = RwkvStreamKeys.idsFor(row.kanji, core, row.answerSignature)
        val dayStart = row.reviewDayStartMillis.takeIf { it > 0L } ?: LocalDayPolicy.localDayStart(row.reviewedAtMillis)
        val answer = RwkvAnswer(
            rating = RwkvMemoryModel.ratingCode(row.rating),
            durationMillis = row.activeElapsedMillis?.takeIf { it >= 0L },
            reviewedAtMillis = row.reviewedAtMillis,
            dayOffset = RwkvDays.fromDayStart(dayStart),
            reviewKind = kindOf(phase),
        )
        val firstForCard = seenCards.add(ids.card)
        val memory = RecordsStudyModels.TaskMemory.decode(row.memoryBefore, null)
        val warm = if (firstForCard && phase == RecordsBase.SchedulerPhase.REVIEW && row.memoryBefore.isNotEmpty() &&
            RwkvSeedBurstPolicy.isEvidenceSeed(memory)
        ) {
            RwkvSeedBurstPolicy.answers(memory.stability, row.reviewedAtMillis) { millis ->
                RwkvDays.fromDayStart(LocalDayPolicy.localDayStart(millis))
            }
        } else {
            emptyList()
        }
        return Fed(ids, answer, warm)
    }

    private fun fallbackCore(row: Row, before: Map<String, Any?>?): CoreSkill =
        CoreSkill.fromWireName(row.coreSkill)
            ?: AdaptiveCorePolicy.coreForTaskType(row.taskType)
            ?: AdaptiveCorePolicy.coreForRung(rungOf(before?.get("rung") as? String))
            ?: CoreSkill.RECOGNITION

    private fun phaseOf(wire: String?): RecordsBase.SchedulerPhase? =
        RecordsBase.SchedulerPhase.entries.firstOrNull { it.wireName() == wire }

    private fun rungOf(wire: String?): RecordsBase.LadderRung? =
        RecordsBase.LadderRung.entries.firstOrNull { it.wireName() == wire }

    private fun kindOf(phase: RecordsBase.SchedulerPhase?): Int =
        when (phase) {
            RecordsBase.SchedulerPhase.NEW_LEARNING -> MemoryAnswerKind.NEW_LEARNING_STEP.rwkvReviewKind
            RecordsBase.SchedulerPhase.RELEARNING -> MemoryAnswerKind.RELEARNING_STEP.rwkvReviewKind
            else -> MemoryAnswerKind.REVIEW.rwkvReviewKind
        }
}
