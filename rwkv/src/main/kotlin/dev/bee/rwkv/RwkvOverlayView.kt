// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

/**
 * A copy-on-write view over [base]: reads fall through, writes stay in the
 * overlay. Simulations (forecasts, previews of many answers) run on it so the
 * persisted state is never touched. Stored states and records are treated as
 * immutable values, so sharing them with [base] is safe.
 */
class RwkvOverlayView(private val base: RwkvStateView) : RwkvStateView {
    override val globals: RwkvGlobalFeatures = base.globals.copy()

    private val states = HashMap<RwkvStateKey, RwkvModuleState>()
    private val cards = HashMap<Long, RwkvCardFeatures>()
    private val encodings = HashMap<RwkvIdKey, FloatArray>()
    private val histories = HashMap<Long, RwkvCardHistory>()

    override fun moduleState(stream: Int, id: Long): RwkvModuleState? =
        states[RwkvStateKey(stream, id)] ?: base.moduleState(stream, id)

    override fun putModuleState(stream: Int, id: Long, state: RwkvModuleState) {
        states[RwkvStateKey(stream, id)] = state
    }

    override fun card(cardId: Long): RwkvCardFeatures? = cards[cardId] ?: base.card(cardId)

    override fun putCard(cardId: Long, features: RwkvCardFeatures) {
        cards[cardId] = features
    }

    override fun encoding(key: RwkvIdKey): FloatArray? = encodings[key] ?: base.encoding(key)

    override fun putEncoding(key: RwkvIdKey, values: FloatArray) {
        encodings[key] = values
    }

    override fun history(cardId: Long): RwkvCardHistory? = histories[cardId] ?: base.history(cardId)

    override fun putHistory(cardId: Long, history: RwkvCardHistory) {
        histories[cardId] = history
    }

    /** Overlay writes so far (for tests and diagnostics). */
    fun writtenStateCount(): Int = states.size
}
