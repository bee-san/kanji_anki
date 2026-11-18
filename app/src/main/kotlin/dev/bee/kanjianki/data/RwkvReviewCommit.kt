package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RwkvFedAnswer
import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvStateDelta
import dev.bee.rwkv.RwkvStateUndo
import dev.bee.rwkv.RwkvStreamIds

/** A synthetic prior history fed to one card before its first Kani answer. */
class RwkvSeedBurst(val ids: RwkvStreamIds, val answers: List<RwkvAnswer>)

/**
 * The RWKV writes of one review, persisted inside the review transaction:
 * an optional seed burst, the answer the model was fed (none for repairs),
 * the sequence numbers they take in the history, the state delta, and the
 * pre-review values undo restores. [expectedStateVersion] is the
 * compare-and-swap expectation from when the state was loaded.
 */
class RwkvReviewCommit(
    val expectedStateVersion: Long,
    val burst: RwkvSeedBurst?,
    val burstSeq: Long?,
    val fed: RwkvFedAnswer?,
    val fedSeq: Long?,
    val delta: RwkvStateDelta,
    val undo: RwkvStateUndo,
) {
    init {
        require((burst == null) == (burstSeq == null)) { "a burst needs a sequence number" }
        require((fed == null) == (fedSeq == null)) { "a fed answer needs a sequence number" }
    }

    fun writesState(): Boolean = burst != null || fed != null
}
