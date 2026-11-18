// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

/**
 * The slice of persisted model state that answering one card reads: its five
 * stream states, its feature counters and history, the four id encodings and
 * the global counters. Writes are recorded instead of applied so the caller
 * can persist them in the same transaction as the review (and journal the
 * loaded values for undo).
 *
 * Reading anything outside the loaded slice is a programming error: the
 * caller must load the complete footprint of the cards it will answer.
 */
class RwkvSnapshotView(
    globals: RwkvGlobalFeatures,
    private val loadedStates: Map<RwkvStateKey, RwkvModuleState?>,
    private val loadedCards: Map<Long, RwkvCardFeatures?>,
    private val loadedEncodings: Map<RwkvIdKey, FloatArray?>,
    private val loadedHistories: Map<Long, RwkvCardHistory?>,
) : RwkvStateView {
    /** The globals as loaded; never mutated. */
    val loadedGlobals: RwkvGlobalFeatures = globals.copy()
    override val globals: RwkvGlobalFeatures = globals.copy()

    private val states = LinkedHashMap<RwkvStateKey, RwkvModuleState>()
    private val cards = LinkedHashMap<Long, RwkvCardFeatures>()
    private val encodings = LinkedHashMap<RwkvIdKey, FloatArray>()
    private val histories = LinkedHashMap<Long, RwkvCardHistory>()

    override fun moduleState(stream: Int, id: Long): RwkvModuleState? {
        val key = RwkvStateKey(stream, id)
        states[key]?.let { return it }
        check(loadedStates.containsKey(key)) { "RWKV snapshot did not load $key" }
        return loadedStates[key]
    }

    override fun putModuleState(stream: Int, id: Long, state: RwkvModuleState) {
        states[RwkvStateKey(stream, id)] = state
    }

    override fun card(cardId: Long): RwkvCardFeatures? {
        cards[cardId]?.let { return it }
        check(loadedCards.containsKey(cardId)) { "RWKV snapshot did not load card $cardId" }
        return loadedCards[cardId]
    }

    override fun putCard(cardId: Long, features: RwkvCardFeatures) {
        cards[cardId] = features
    }

    override fun encoding(key: RwkvIdKey): FloatArray? {
        encodings[key]?.let { return it }
        check(loadedEncodings.containsKey(key)) { "RWKV snapshot did not load encoding $key" }
        return loadedEncodings[key]
    }

    override fun putEncoding(key: RwkvIdKey, values: FloatArray) {
        encodings[key] = values
    }

    override fun history(cardId: Long): RwkvCardHistory? {
        histories[cardId]?.let { return it }
        check(loadedHistories.containsKey(cardId)) { "RWKV snapshot did not load history $cardId" }
        return loadedHistories[cardId]
    }

    override fun putHistory(cardId: Long, history: RwkvCardHistory) {
        histories[cardId] = history
    }

    /** Everything written since loading. */
    fun delta(): RwkvStateDelta =
        RwkvStateDelta(
            globals = globals.copy(),
            states = LinkedHashMap(states),
            cards = LinkedHashMap(cards),
            encodings = LinkedHashMap(encodings),
            histories = LinkedHashMap(histories),
        )

    /** The loaded values of everything [delta] overwrites (nulls were absent): undo restores these. */
    fun undoDelta(): RwkvStateUndo =
        RwkvStateUndo(
            globals = loadedGlobals.copy(),
            states = states.keys.associateWith { loadedStates[it] },
            cards = cards.keys.associateWith { loadedCards[it] },
            encodings = encodings.keys.associateWith { loadedEncodings[it] },
            histories = histories.keys.associateWith { loadedHistories[it] },
        )

    companion object {
        /** The stream keys one card's answer reads. */
        @JvmStatic
        fun footprint(ids: RwkvStreamIds): List<RwkvStateKey> =
            (0 until RwkvArchitecture.STREAMS).map { RwkvStateKey(it, ids.forStream(it)) }

        /** The id-encoding keys one card's answer reads. */
        @JvmStatic
        fun encodingFootprint(ids: RwkvStreamIds): List<RwkvIdKey> =
            listOf(
                RwkvIdKey(RwkvIdKind.CARD, ids.card),
                RwkvIdKey(RwkvIdKind.NOTE, ids.note),
                RwkvIdKey(RwkvIdKind.DECK, ids.deck),
                RwkvIdKey(RwkvIdKind.PRESET, ids.preset),
            )
    }
}

/** A stream state's key: the stream index ([RwkvArchitecture.CARD] …) and its id. */
data class RwkvStateKey(val stream: Int, val id: Long)

/** The writes of one or more answers, to persist atomically. */
class RwkvStateDelta(
    val globals: RwkvGlobalFeatures,
    val states: Map<RwkvStateKey, RwkvModuleState>,
    val cards: Map<Long, RwkvCardFeatures>,
    val encodings: Map<RwkvIdKey, FloatArray>,
    val histories: Map<Long, RwkvCardHistory>,
)

/** The pre-answer values of a [RwkvStateDelta]'s keys; a null value means the key did not exist. */
class RwkvStateUndo(
    val globals: RwkvGlobalFeatures,
    val states: Map<RwkvStateKey, RwkvModuleState?>,
    val cards: Map<Long, RwkvCardFeatures?>,
    val encodings: Map<RwkvIdKey, FloatArray?>,
    val histories: Map<Long, RwkvCardHistory?>,
)
