// SPDX-License-Identifier: AGPL-3.0-or-later
// Mirrors upstream's `check_published_vectors` and `check_show_time_intervals`
// (rslib/src/rwkv/sm40_tests.rs) for the SM92 package.

package dev.bee.rwkv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RwkvPublishedVectorsTest {
    @Test
    fun forwardPassMatchesPublishedFp32Vectors() {
        val model = RwkvFixtures.model
        var maximum = 0f
        var rows = 0
        for (user in RwkvFixtures.vectors) {
            val states = StreamStates()
            for ((index, row) in user.rows.withIndex()) {
                val step = model.step(row.features, states.statesFor(row.ids))
                val actual = ArrayList<Float>()
                actual += step.retrievability.coerceIn(1e-5f, 1f - 1e-5f)
                step.buttonProbabilities.forEach { actual += it }
                actual += step.curve.predict(row.elapsed)
                user.sweep.forEach { actual += step.curve.predict(it) }
                assertEquals("${user.name} row $index head count", row.fp32.size, actual.size)
                for (head in actual.indices) {
                    val error = abs(actual[head] - row.fp32[head])
                    maximum = maxOf(maximum, error)
                    assertTrue(
                        "${user.name} row $index head $head: ${actual[head]} != ${row.fp32[head]}",
                        error <= TOLERANCE,
                    )
                }
                if (!row.skip) {
                    states.store(row.ids, step.next)
                }
                rows++
            }
        }
        println("SM92 fp32 published vectors: $rows rows, maximum absolute probability error $maximum")
        assertTrue(rows > 0)
    }

    @Test
    fun featureBuilderMatchesShowTimeIntervalInputs() {
        var maximum = 0f
        var queries = 0
        for (user in RwkvFixtures.vectors) {
            val store = RwkvMemoryFeatureStore()
            for ((index, row) in user.rows.withIndex()) {
                val interval = requireNotNull(row.interval) { "show-time vectors carry the row intervals" }
                val input = RwkvReviewInput(
                    cardId = row.ids[0],
                    deckId = row.ids[1],
                    noteId = row.ids[2],
                    presetId = row.ids[3],
                    isQuery = row.skip,
                    ease = null,
                    durationMillis = null,
                    cardType = null,
                    dayOffset = null,
                    elapsedSeconds = interval[0].toLong(),
                    elapsedDays = interval[1].toLong(),
                )
                val actual = RwkvFeatureBuilder.featuresFor(store, input)
                for (feature in 0 until 8) {
                    val error = abs(actual[feature] - row.features[feature])
                    maximum = maxOf(maximum, error)
                    assertTrue(
                        "${user.name} row $index feature $feature: ${actual[feature]} != ${row.features[feature]}",
                        error <= TOLERANCE,
                    )
                }
                if (row.skip) {
                    queries++
                } else {
                    RwkvFeatureBuilder.storeReview(store, input)
                }
            }
        }
        println("SM92 interval features: $queries queries, maximum absolute error $maximum")
        assertTrue(queries > 0)
    }

    /** The five streams' states keyed like upstream's `ReviewStateMaps`. */
    private class StreamStates {
        private val card = HashMap<Long, RwkvModuleState>()
        private val deck = HashMap<Long, RwkvModuleState>()
        private val note = HashMap<Long, RwkvModuleState>()
        private val preset = HashMap<Long, RwkvModuleState>()
        private var user: RwkvModuleState? = null

        fun statesFor(ids: LongArray): Array<RwkvModuleState?> =
            arrayOf(card[ids[0]], deck[ids[1]], note[ids[2]], preset[ids[3]], user)

        fun store(ids: LongArray, next: Array<RwkvModuleState>) {
            card[ids[0]] = next[RwkvArchitecture.CARD]
            deck[ids[1]] = next[RwkvArchitecture.DECK]
            note[ids[2]] = next[RwkvArchitecture.NOTE]
            preset[ids[3]] = next[RwkvArchitecture.PRESET]
            user = next[RwkvArchitecture.USER]
        }
    }

    private companion object {
        /** Upstream's own bound for every SM package (`check_published_vectors`). */
        const val TOLERANCE = 1e-5f
    }
}
