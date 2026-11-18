// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.math.abs

class RwkvEngineTest {
    private val engine = RwkvEngine(RwkvFixtures.model)

    @Test
    fun mersenneTwisterMatchesTheReferenceSequence() {
        // init_genrand(5489), the MT19937 reference seed: first outputs are
        // 3499211612, 581869302, 3890346734 (Matsumoto & Nishimura).
        val state = IntArray(624)
        state[0] = 5489
        for (i in 1 until 624) {
            val previous = state[i - 1].toLong() and 0xffffffffL
            state[i] = ((1812433253L * (previous xor (previous ushr 30)) + i) and 0xffffffffL).toInt()
        }
        val rng = TorchIdRng.fromState(state, 624)
        assertEquals(3499211612L, rng.nextU32().toLong() and 0xffffffffL)
        assertEquals(581869302L, rng.nextU32().toLong() and 0xffffffffL)
        assertEquals(3890346734L, rng.nextU32().toLong() and 0xffffffffL)
    }

    @Test
    fun idEncodingsAreAllocatedInFirstAnswerOrderAndPreviewedWithoutConsumingTheRng() {
        val store = RwkvMemoryFeatureStore()
        val first = answerInput(card = 1, note = 10)
        val preview = RwkvFeatureBuilder.featuresFor(store, first)
        assertTrue(store.globals.rng.contentEquals(TorchIdRng.initial()))
        RwkvFeatureBuilder.storeReview(store, first)
        val stored = RwkvFeatureBuilder.featuresFor(store, first)
        assertArrayEquals(preview.copyOfRange(24, 64), stored.copyOfRange(24, 64), 0f)
        for (value in stored.copyOfRange(24, 64)) {
            assertTrue(value in setOf(-1.5f, -0.5f, 0.5f, 1.5f))
        }
        val second = answerInput(card = 2, note = 10)
        val secondFeatures = RwkvFeatureBuilder.featuresFor(store, second)
        // The note, deck and preset are shared; only the card encoding is new.
        assertArrayEquals(stored.copyOfRange(36, 64), secondFeatures.copyOfRange(36, 64), 0f)
        assertFalse(stored.copyOfRange(24, 36).contentEquals(secondFeatures.copyOfRange(24, 36)))
    }

    @Test
    fun stateCodecRoundTripsAtBothPrecisions() {
        val state = trainedState().moduleState(RwkvArchitecture.DECK, DECK)!!
        val f32 = RwkvStateCodec.decode(RwkvStateCodec.encode(state, RwkvStatePrecision.F32))
        assertTrue(state.contentEquals(f32))
        val quantized = RwkvStateCodec.quantize(state, RwkvStatePrecision.INT8)
        val int8 = RwkvStateCodec.decode(RwkvStateCodec.encode(state, RwkvStatePrecision.INT8))
        assertTrue("decoded int8 equals in-memory quantization", quantized.contentEquals(int8))
        assertTrue("quantization is idempotent", RwkvStateCodec.quantize(quantized, RwkvStatePrecision.INT8).contentEquals(quantized))
        assertTrue(RwkvStateCodec.encode(state, RwkvStatePrecision.INT8).size * 3 < RwkvStateCodec.encode(state, RwkvStatePrecision.F32).size)
        assertEquals(4, state.layerCount())
    }

    @Test
    fun corruptStatesAreRejected() {
        val bytes = RwkvStateCodec.encode(trainedState().moduleState(RwkvArchitecture.CARD, 1)!!, RwkvStatePrecision.INT8)
        assertThrows { RwkvStateCodec.decode(bytes.copyOf(bytes.size - 1)) }
        assertThrows { RwkvStateCodec.decode(bytes + byteArrayOf(0)) }
        assertThrows { RwkvStateCodec.decode(byteArrayOf(1, 2, 3, 4, 0, 1)) }
        val badPrecision = bytes.copyOf().also { it[4] = 3 }
        assertThrows { RwkvStateCodec.decode(badPrecision) }
        assertThrows { RwkvGlobalFeatures.decode(ByteArray(8)) }
        assertThrows { RwkvCardHistory.decode(ByteArray(8)) }
        assertThrows { RwkvCardFeatures.decode(ByteArray(8)) }
        assertThrows { RwkvEncodingCodec.decode(RwkvIdKind.CARD, ByteArray(8)) }
        assertThrows { RwkvWeights.parse(ByteArray(16), expectedSha256 = null) }
        assertThrows { RwkvWeights.parse(RwkvFixtures.weightsBytes.copyOf(100)) }
    }

    @Test
    fun recordCodecsRoundTrip() {
        val state = trainedState()
        val globals = RwkvGlobalFeatures.decode(state.globals.encode())
        assertTrue(globals.contentEquals(state.globals))
        val card = state.cards.getValue(1L)
        assertEquals(card, RwkvCardFeatures.decode(card.encode()))
        val history = state.histories.getValue(1L)
        assertTrue(history.contentEquals(RwkvCardHistory.decode(history.encode())))
        val key = RwkvIdKey(RwkvIdKind.NOTE, 101)
        val encoding = state.encodings.getValue(key)
        assertArrayEquals(encoding, RwkvEncodingCodec.decode(RwkvIdKind.NOTE, RwkvEncodingCodec.encode(encoding)), 0f)
        for (kind in RwkvIdKind.entries) {
            assertEquals(kind, RwkvIdKind.fromCode(kind.code))
        }
        assertThrows { RwkvIdKind.fromCode(9) }
    }

    @Test
    fun halfFloatsRoundToNearestEven() {
        assertEquals(0x3c00, RwkvHalf.toHalfBits(1f))
        assertEquals(0xc000, RwkvHalf.toHalfBits(-2f))
        assertEquals(0x7bff, RwkvHalf.toHalfBits(65504f))
        assertEquals(0x7c00, RwkvHalf.toHalfBits(1e6f))
        assertEquals(0x0001, RwkvHalf.toHalfBits(5.9604645E-8f))
        assertEquals(0x0000, RwkvHalf.toHalfBits(1e-10f))
        // 1 + 2^-11 is halfway between 1 and the next half; ties go to even (1).
        assertEquals(0x3c00, RwkvHalf.toHalfBits(1f + 1f / 2048f))
        assertEquals(0x3c02, RwkvHalf.toHalfBits(1f + 3f / 2048f))
        assertEquals(1f, RwkvHalf.fromHalfBits(0x3c00), 0f)
        assertEquals(5.9604645E-8f, RwkvHalf.fromHalfBits(0x0001), 0f)
        assertEquals(Float.NEGATIVE_INFINITY, RwkvHalf.fromHalfBits(0xfc00), 0f)
        assertTrue(RwkvHalf.fromHalfBits(0x7e00).isNaN())
        assertEquals(0x7e00, RwkvHalf.toHalfBits(Float.NaN) and 0x7e00)
    }

    @Test
    fun intervalsFollowTheTargetAndTheGradeOrder() {
        val state = trainedState()
        val history = state.histories.getValue(1L)
        val at90 = RwkvIntervals.unroundedInterval(history.curve, 0.9f, 36_500)!!
        val at80 = RwkvIntervals.unroundedInterval(history.curve, 0.8f, 36_500)!!
        val at97 = RwkvIntervals.unroundedInterval(history.curve, 0.97f, 36_500)!!
        assertTrue("$at97 <= $at90 <= $at80", at97 <= at90 && at90 <= at80)
        assertEquals(abs(history.curve.predict(at90 * 86_400f) - 0.9f), 0f, 1e-3f)
        assertNull(RwkvIntervals.unroundedInterval(history.curve, 1.5f, 36_500))
        assertNull(RwkvIntervals.unroundedInterval(history.curve, 0.9f, 0))
        assertEquals(1, RwkvIntervals.roundedDays(0.01f, 36_500))
        assertEquals(36_500, RwkvIntervals.roundedDays(1e9f, 36_500))
        assertArrayEquals(floatArrayOf(1f, 2f, 2f, 3f), RwkvIntervals.pavaNonDecreasing(floatArrayOf(1f, 2.5f, 1.5f, 3f)), 1e-6f)
        assertArrayEquals((1..10).toList().toIntArray(), RwkvIntervals.intervalSearchDays(10))
        val grid = RwkvIntervals.intervalSearchDays(36_500)
        assertEquals(31, grid.indexOf(67))
        assertEquals(36_500, grid.last())
        assertTrue(RwkvIntervals.answerIntervals(Array(4) { history.curve }, floatArrayOf(0.9f, 0.9f, 2f, 0.9f), 365, true).all { it == null })
        val unordered = RwkvIntervals.answerIntervals(Array(4) { history.curve }, floatArrayOf(0.9f, 0.9f, 2f, 0.9f), 365, false)
        assertNull(unordered[2])
        assertNotNull(unordered[0])
    }

    @Test
    fun answerOutcomesAreSaneAndGradeOrdered() {
        val state = trainedState()
        val ids = idsFor(7)
        val reviewedAt = BASE_MILLIS + 40 * DAY
        val outcomes = (1..4).map { rating ->
            val copy = copyOf(state)
            engine.answer(copy, ids, RwkvAnswer(rating, 6_000, reviewedAt, BASE_DAY + 40, 2), 0.9f, 36_500)
        }
        for (outcome in outcomes) {
            assertTrue(outcome.intervalDays >= 1)
            assertEquals(outcome.intervalDays, RwkvIntervals.roundedDays(outcome.unroundedIntervalDays, 36_500))
            assertEquals(outcome.promotionIntervalDays, RwkvIntervals.roundedDays(outcome.s90Days, 36_500))
            assertEquals(1f, outcome.buttonProbabilities.sum(), 1e-4f)
            assertNotNull(outcome.recallBefore)
        }
        for (index in 1 until 4) {
            assertTrue(outcomes[index - 1].unroundedIntervalDays <= outcomes[index].unroundedIntervalDays)
        }
        assertTrue("good beats again", outcomes[2].unroundedIntervalDays > outcomes[0].unroundedIntervalDays)
    }

    @Test
    fun liveAnswersAndFullReplaysProduceTheSameState() {
        val live = RwkvCollectionState()
        val replayed = RwkvCollectionState()
        for (review in syntheticHistory()) {
            engine.answer(live, review.ids, review.answer, 0.9f, 36_500)
            engine.replay(replayed, review.ids, review.answer)
        }
        assertStatesEqual(live, replayed)
    }

    @Test
    fun snapshotDeltasMatchDirectAnswersAndUndoRestoresTheLoadedValues() {
        val state = trainedState()
        val direct = copyOf(state)
        val ids = idsFor(3)
        val answer = RwkvAnswer(3, 4_000, BASE_MILLIS + 41 * DAY, BASE_DAY + 41, 2)
        val directOutcome = engine.answer(direct, ids, answer, 0.85f, 36_500)

        val snapshot = snapshotOf(state, ids)
        val snapshotOutcome = engine.answer(snapshot, ids, answer, 0.85f, 36_500)
        assertEquals(directOutcome.unroundedIntervalDays, snapshotOutcome.unroundedIntervalDays, 0f)
        val applied = copyOf(state)
        apply(applied, snapshot.delta())
        assertStatesEqual(direct, applied)

        apply(applied, snapshot.undoDelta())
        assertStatesEqual(state, applied)
    }

    @Test
    fun snapshotsRefuseReadsOutsideTheirFootprint() {
        val snapshot = snapshotOf(trainedState(), idsFor(3))
        try {
            snapshot.moduleState(RwkvArchitecture.CARD, 999)
            fail("read outside the footprint")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun previewsAndOverlaysNeverWriteThrough() {
        val state = trainedState()
        val before = copyOf(state)
        val ids = idsFor(3)
        val answer = RwkvAnswer(3, 4_000, BASE_MILLIS + 41 * DAY, BASE_DAY + 41, 2)
        val preview = engine.preview(state, ids, answer, 0.9f, 36_500)
        assertStatesEqual(before, state)
        val committed = engine.answer(copyOf(state), ids, answer, 0.9f, 36_500)
        assertEquals(committed.unroundedIntervalDays, preview.unroundedIntervalDays, 0f)

        val overlay = RwkvOverlayView(state)
        val single = engine.answerSinglePass(overlay, ids, answer, 0.9f, 36_500)
        assertTrue(single.intervalDays >= 1)
        assertNotNull(single.recallBefore)
        assertEquals(RwkvArchitecture.STREAMS, overlay.writtenStateCount())
        assertStatesEqual(before, state)
        // The overlay reads its own writes and falls through for the rest.
        assertEquals(state.histories.getValue(ids.card).answers + 1, overlay.history(ids.card)!!.answers)
        assertEquals(state.cards[1L], overlay.card(1L))
        assertArrayEquals(state.encodings.getValue(RwkvIdKey(RwkvIdKind.PRESET, PRESET)), overlay.encoding(RwkvIdKey(RwkvIdKind.PRESET, PRESET)), 0f)
        engine.answerSinglePass(overlay, idsFor(91), answer, 0.9f, 36_500)
        assertNotNull(overlay.card(91L))
        assertNull(state.cards[91L])
    }

    @Test
    fun warmingACardKeepsTheSharedStreamsAndCountersUnchanged() {
        val state = trainedState()
        val before = copyOf(state)
        val ids = idsFor(77)
        val burst = (0 until 5).map { step ->
            RwkvAnswer(3, null, BASE_MILLIS - (200 - step * 30) * DAY, BASE_DAY - (200 - step * 30), 2)
        }
        engine.warmCard(state, ids, burst)
        for (stream in listOf(RwkvArchitecture.DECK, RwkvArchitecture.PRESET, RwkvArchitecture.USER)) {
            val id = ids.forStream(stream)
            assertTrue("stream $stream unchanged", before.moduleState(stream, id)!!.contentEquals(state.moduleState(stream, id)!!))
        }
        assertEquals(before.globals.reviewIndex, state.globals.reviewIndex)
        assertEquals(before.globals.today, state.globals.today)
        assertEquals(before.globals.todayReviews, state.globals.todayReviews)
        assertEquals(before.globals.previousDayOffset, state.globals.previousDayOffset)
        assertEquals(before.globals.cardCount + 1, state.globals.cardCount)
        assertFalse("encodings stay distinct", before.globals.rng.contentEquals(state.globals.rng))
        assertNotNull(state.moduleState(RwkvArchitecture.CARD, ids.card))
        assertEquals(5L, state.histories.getValue(ids.card).answers)
        assertEquals(state.globals.reviewIndex - 1, state.cards.getValue(ids.card).lastI)

        // A long, passed history should read as a strong memory.
        val s90 = state.histories.getValue(ids.card).s90Days(36_500)!!
        assertTrue("warmed S90 $s90", s90 > 5f)
        engine.warmCard(state, ids, emptyList())
    }

    @Test
    fun int8StateStaysCloseToFp32() {
        val fp32 = RwkvEngine(RwkvFixtures.model, RwkvStatePrecision.F32, RwkvStatePrecision.F32)
        val exact = RwkvCollectionState()
        val compact = RwkvCollectionState()
        var maximum = 0f
        for (review in syntheticHistory()) {
            val a = fp32.answer(exact, review.ids, review.answer, 0.9f, 36_500)
            val b = engine.answer(compact, review.ids, review.answer, 0.9f, 36_500)
            maximum = maxOf(maximum, abs(a.buttonProbabilities[0] - b.buttonProbabilities[0]))
            for (seconds in floatArrayOf(3_600f, 86_400f, 30 * 86_400f)) {
                val pa = exact.histories.getValue(review.ids.card).curve.predict(seconds)
                val pb = compact.histories.getValue(review.ids.card).curve.predict(seconds)
                maximum = maxOf(maximum, abs(pa - pb))
            }
        }
        println("RWKV int8 card/note state: maximum absolute probability drift $maximum")
        assertTrue("int8 drift $maximum", maximum < INT8_DRIFT_BOUND)
    }

    @Test
    fun answerLatencyBenchmark() {
        val state = trainedState()
        val ids = idsFor(5)
        val answer = RwkvAnswer(3, 5_000, BASE_MILLIS + 50 * DAY, BASE_DAY + 50, 2)
        repeat(20) { engine.answer(copyOf(state), ids, answer, 0.9f, 36_500) }
        val runs = 50
        val started = System.nanoTime()
        repeat(runs) { engine.answer(copyOf(state), ids, answer, 0.9f, 36_500) }
        val answerMillis = (System.nanoTime() - started) / 1e6 / runs
        val history = syntheticHistory()
        repeat(2) {
            val warm = RwkvCollectionState()
            history.forEach { engine.replay(warm, it.ids, it.answer) }
        }
        val replayState = RwkvCollectionState()
        val replayStarted = System.nanoTime()
        history.forEach { engine.replay(replayState, it.ids, it.answer) }
        val replayMicros = (System.nanoTime() - replayStarted) / 1e3 / history.size
        println(
            "RWKV JVM benchmark: answer %.2f ms (4 passes + search), replay %.0f µs/review over %d reviews"
                .format(answerMillis, replayMicros, history.size),
        )
    }

    // --- fixtures -------------------------------------------------------------

    private class Review(val ids: RwkvStreamIds, val answer: RwkvAnswer)

    private fun idsFor(card: Int): RwkvStreamIds =
        RwkvStreamIds(card = card.toLong(), deck = DECK + (card % 2), note = 100L + card / 2, preset = PRESET)

    /** A deterministic Kani-like history: 30 cards over 40 days, mostly passes. */
    private fun syntheticHistory(): List<Review> {
        val random = java.util.Random(7)
        val due = HashMap<Int, Long>()
        val interval = HashMap<Int, Long>()
        val out = ArrayList<Review>()
        for (day in 0 until 40) {
            val today = BASE_MILLIS + day * DAY
            if (day < 15) {
                val card = day * 2
                due[card] = today
                due[card + 1] = today
            }
            val dueCards = due.filterValues { it <= today }.keys.sorted()
            for ((slot, card) in dueCards.withIndex()) {
                val rating = if (random.nextDouble() < 0.12) 1 else if (random.nextDouble() < 0.1) 2 else 3
                out += Review(idsFor(card), RwkvAnswer(rating, 2_000L + random.nextInt(9_000), today + slot * 20_000L, BASE_DAY + day, 2))
                val next = if (rating == 1) 1L else maxOf(1L, (interval[card] ?: 1L) * 2)
                interval[card] = next
                due[card] = today + next * DAY
            }
        }
        return out
    }

    private fun trainedState(): RwkvCollectionState {
        val state = RwkvCollectionState()
        for (review in syntheticHistory()) {
            engine.replay(state, review.ids, review.answer)
        }
        return state
    }

    private fun copyOf(state: RwkvCollectionState): RwkvCollectionState {
        val copy = RwkvCollectionState(state.globals.copy())
        for (stream in 0 until RwkvArchitecture.STREAMS) {
            state.streams[stream].forEach { (id, value) -> copy.streams[stream][id] = value.copy() }
        }
        copy.cards.putAll(state.cards)
        state.encodings.forEach { (key, value) -> copy.encodings[key] = value.copyOf() }
        copy.histories.putAll(state.histories)
        return copy
    }

    private fun snapshotOf(state: RwkvCollectionState, ids: RwkvStreamIds): RwkvSnapshotView =
        RwkvSnapshotView(
            state.globals.copy(),
            RwkvSnapshotView.footprint(ids).associateWith { state.moduleState(it.stream, it.id)?.copy() },
            mapOf(ids.card to state.cards[ids.card]),
            RwkvSnapshotView.encodingFootprint(ids).associateWith { state.encodings[it]?.copyOf() },
            mapOf(ids.card to state.histories[ids.card]),
        )

    private fun apply(state: RwkvCollectionState, delta: RwkvStateDelta) {
        state.globals.copyFrom(delta.globals)
        delta.states.forEach { (key, value) -> state.streams[key.stream][key.id] = value }
        state.cards.putAll(delta.cards)
        state.encodings.putAll(delta.encodings)
        state.histories.putAll(delta.histories)
    }

    private fun apply(state: RwkvCollectionState, undo: RwkvStateUndo) {
        state.globals.copyFrom(undo.globals)
        undo.states.forEach { (key, value) -> if (value == null) state.streams[key.stream].remove(key.id) else state.streams[key.stream][key.id] = value }
        undo.cards.forEach { (key, value) -> if (value == null) state.cards.remove(key) else state.cards[key] = value }
        undo.encodings.forEach { (key, value) -> if (value == null) state.encodings.remove(key) else state.encodings[key] = value }
        undo.histories.forEach { (key, value) -> if (value == null) state.histories.remove(key) else state.histories[key] = value }
    }

    private fun RwkvGlobalFeatures.copyFrom(other: RwkvGlobalFeatures) {
        firstDayOffset = other.firstDayOffset
        previousDayOffset = other.previousDayOffset
        today = other.today
        todayReviews = other.todayReviews
        todayNewCards = other.todayNewCards
        reviewIndex = other.reviewIndex
        cardCount = other.cardCount
        rng.copyFrom(other.rng)
    }

    private fun assertStatesEqual(expected: RwkvCollectionState, actual: RwkvCollectionState) {
        assertTrue("globals", expected.globals.contentEquals(actual.globals))
        for (stream in 0 until RwkvArchitecture.STREAMS) {
            assertEquals("stream $stream ids", expected.streams[stream].keys, actual.streams[stream].keys)
            for ((id, value) in expected.streams[stream]) {
                assertTrue("stream $stream id $id", value.contentEquals(actual.streams[stream].getValue(id)))
            }
        }
        assertEquals(expected.cards, actual.cards)
        assertEquals(expected.encodings.keys, actual.encodings.keys)
        expected.encodings.forEach { (key, value) -> assertArrayEquals(value, actual.encodings.getValue(key), 0f) }
        assertEquals(expected.histories.keys, actual.histories.keys)
        expected.histories.forEach { (key, value) -> assertTrue("history $key", value.contentEquals(actual.histories.getValue(key))) }
    }

    private fun answerInput(card: Long, note: Long): RwkvReviewInput =
        RwkvReviewInput(card, note, DECK, PRESET, false, 3, 5_000, 2, BASE_DAY, 1, 86_400)

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val DAY = 86_400_000L
        const val BASE_MILLIS = 1_760_000_000_000L
        const val BASE_DAY = 20_370L
        const val DECK = 9_001L
        const val PRESET = 42L

        /** Measured int8 drift on the synthetic history is far below this; it guards regressions. */
        const val INT8_DRIFT_BOUND = 0.05f
    }
}
