package dev.bee.kanjianki.memory

import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RwkvMemoryModel
import dev.bee.kanjianki.core.RwkvSeedBurstPolicy
import dev.bee.kanjianki.core.RwkvStreamKeys
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.RwkvReviewCommit
import dev.bee.kanjianki.data.RwkvSeedBurst
import dev.bee.kanjianki.data.RwkvStateStore
import dev.bee.rwkv.RwkvEngine

/**
 * The RWKV state one Study answer runs against: both core cards of the
 * answered kanji (a recognition pass may preview the contextual card on
 * promotion), loaded from the store, plus the model bound to it.
 *
 * An evidence-seeded memory Kani has never reviewed is warmed with its
 * synthetic prior history first (see [RwkvSeedBurstPolicy]), inside the same
 * snapshot, so the burst commits atomically with the answer and an undo
 * removes both.
 */
internal class RwkvReviewBinding private constructor(
    val model: RwkvMemoryModel,
    private val snapshot: RwkvStateStore.Snapshot,
    private val burst: RwkvSeedBurst?,
) {
    /** The writes to persist with the review. Call once, after the scheduler applied the answer. */
    fun commit(): RwkvReviewCommit {
        val fed = model.fedAnswers
        check(fed.size <= 1) { "one Study answer fed the memory model ${fed.size} times" }
        var seq = snapshot.nextSeq
        val burstSeq = burst?.let { seq++ }
        val fedSeq = fed.firstOrNull()?.let { seq }
        return RwkvReviewCommit(
            expectedStateVersion = snapshot.stateVersion,
            burst = burst,
            burstSeq = burstSeq,
            fed = fed.firstOrNull(),
            fedSeq = fedSeq,
            delta = snapshot.view.delta(),
            undo = snapshot.view.undoDelta(),
        )
    }

    companion object {
        fun forItem(
            engine: RwkvEngine,
            store: LocalStore,
            item: RecordsStudyModels.StudyItem,
            nowMillis: Long,
            durationMillis: Long?,
            dayOffsetOf: (Long) -> Long,
        ): RwkvReviewBinding {
            val recognition = RwkvStreamKeys.idsFor(item.kanji, CoreSkill.RECOGNITION, item.answerSignature)
            val contextual = RwkvStreamKeys.idsFor(item.kanji, CoreSkill.CONTEXTUAL_READING, item.answerSignature)
            val snapshot = store.loadRwkvSnapshot(listOf(recognition, contextual))
            var burst: RwkvSeedBurst? = null
            val route = AdaptiveStudyItemPolicy.routeState(item)
            if (route != null && !route.isRepairActive()) {
                val ids = if (route.activeCore == CoreSkill.RECOGNITION) recognition else contextual
                val memory = AdaptiveStudyItemPolicy.coreMemory(item, route.activeCore)
                if (RwkvSeedBurstPolicy.isEvidenceSeed(memory) && snapshot.view.history(ids.card) == null) {
                    val answers = RwkvSeedBurstPolicy.answers(memory.stability, nowMillis, dayOffsetOf)
                    engine.warmCard(snapshot.view, ids, answers)
                    burst = RwkvSeedBurst(ids, answers)
                }
            }
            val model = RwkvMemoryModel(engine, snapshot.view, dayOffsetOf, durationMillis)
            return RwkvReviewBinding(model, snapshot, burst)
        }
    }
}
