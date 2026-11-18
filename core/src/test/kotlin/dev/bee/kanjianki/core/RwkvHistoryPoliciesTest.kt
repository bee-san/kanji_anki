package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating
import dev.bee.fsrs.FsrsReviewInput
import dev.bee.rwkv.RwkvAnswer
import dev.bee.rwkv.RwkvCollectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RwkvHistoryPoliciesTest {
    private val engine = TestMemoryModels.rwkvEngine

    @Test
    fun seedBurstsGrowGeometricallyToTheImportedStability() {
        val firstReview = 1_760_000_000_000L
        val burst = RwkvSeedBurstPolicy.answers(60.0, firstReview) { Math.floorDiv(it, DAY) }
        assertEquals(7, burst.size)
        assertTrue(burst.all { it.rating == 3 && it.durationMillis == null })
        assertTrue(burst.zipWithNext().all { (a, b) -> a.reviewedAtMillis < b.reviewedAtMillis })
        assertEquals("the first review is one stability after the last", firstReview - 60 * DAY, burst.last().reviewedAtMillis)
        val gaps = burst.zipWithNext { a, b -> (b.reviewedAtMillis - a.reviewedAtMillis).toDouble() / DAY }
        assertTrue(gaps.zipWithNext().all { (a, b) -> b > a })
        assertEquals(2, RwkvSeedBurstPolicy.answers(0.2, firstReview) { 0L }.size)
        assertEquals(8, RwkvSeedBurstPolicy.answers(100_000.0, firstReview) { 0L }.size)
    }

    @Test
    fun onlyReviewedNothingAndRealEvidenceCountsAsASeed() {
        assertTrue(RwkvSeedBurstPolicy.isEvidenceSeed(memory(stability = 45.0, totalReviews = 0)))
        assertFalse("a new-card placeholder", RwkvSeedBurstPolicy.isEvidenceSeed(memory(stability = 0.4, totalReviews = 0)))
        assertFalse("already reviewed in Kani", RwkvSeedBurstPolicy.isEvidenceSeed(memory(stability = 45.0, totalReviews = 1)))
        assertFalse(RwkvSeedBurstPolicy.isEvidenceSeed(memory(stability = Double.NaN, totalReviews = 0)))
        // A contextual core seeded on promotion: no reviews yet, but it records
        // the promotion time and its stability is the model's preview.
        val promoted = RecordsStudyModels.TaskMemory.fromFields(
            RecordsStudyModels.TaskMemory.Fields(
                state = StudyLadderRules.STATE_REVIEW,
                dueAtMillis = REVIEWED_AT + 6 * DAY,
                stability = 9.0,
                difficulty = 5.0,
                totalReviews = 0,
                lapses = 0,
                learningStep = 0,
                lastRating = "",
                matureIntervalDays = 6,
                lastReviewedAtMillis = REVIEWED_AT,
            ),
        )
        assertFalse("a promotion seed is not imported evidence", RwkvSeedBurstPolicy.isEvidenceSeed(promoted))
    }

    /**
     * Acceptance band for warm-started seeds in a collection with real history:
     * the interval after an on-time Good stays within 0.5–2× of what FSRS-6
     * would have continued with across typical mature seeds. Very old memories
     * (S >= 365) are scheduled more conservatively by RWKV but still months out.
     */
    @Test
    fun warmedSeedsScheduleCloseToTheirImportedMemory() {
        val fsrs = FsrsEngine.latestDefault()
        val firstReview = START + 400 * DAY
        val base = populatedCollection()
        for (stability in listOf(7.0, 21.0, 30.0, 60.0, 120.0, 365.0, 1000.0)) {
            val state = copyOf(base)
            val ids = RwkvStreamKeys.idsFor("脱", CoreSkill.CONTEXTUAL_READING, "")
            engine.warmCard(state, ids, RwkvSeedBurstPolicy.answers(stability, firstReview) { Math.floorDiv(it, DAY) })
            val good = engine.answer(state, ids, RwkvAnswer(3, null, firstReview, Math.floorDiv(firstReview, DAY), 2), 0.9f, 36_500)
            val fsrsNext = fsrs.review(
                FsrsReviewInput(FsrsMemoryState(stability, 5.0), FsrsRating.GOOD, stability.toInt(), 0.9, 36_500),
            ).nextIntervalDays
            val ratio = good.intervalDays.toDouble() / fsrsNext
            println("RWKV seed S=$stability: next ${good.intervalDays} d vs FSRS-6 $fsrsNext d (ratio %.2f)".format(ratio))
            if (stability <= 120.0) {
                assertTrue("S=$stability ratio $ratio", ratio in 0.5..2.0)
            } else {
                assertTrue("S=$stability next ${good.intervalDays}", good.intervalDays >= 180 && good.intervalDays <= fsrsNext)
            }
        }
    }

    @Test
    fun backfillFeedsLegacyAnswersIncludingLearningSteps() {
        val backfill = RwkvHistoryBackfill()
        val learning = backfill.classify(
            row(taskType = StudyTaskTypes.KANJI_MEANING, before = legacyJson("new_learning", "kanji_meaning"), rating = "good"),
        )!!
        assertEquals(RwkvStreamKeys.idsFor("脱", CoreSkill.RECOGNITION, SIGNATURE), learning.ids)
        assertEquals(1, learning.answer.reviewKind)
        assertEquals(3, learning.answer.rating)
        assertEquals(RwkvDays.fromDayStart(DAY_START), learning.answer.dayOffset)
        assertEquals(4_200L, learning.answer.durationMillis)

        val reading = backfill.classify(
            row(taskType = StudyTaskTypes.WORD_READING, before = legacyJson("relearning", "word_reading"), rating = "again"),
        )!!
        assertEquals(CoreSkill.CONTEXTUAL_READING.let { RwkvStreamKeys.idsFor("脱", it, SIGNATURE).card }, reading.ids.card)
        assertEquals(3, reading.answer.reviewKind)
        assertEquals(1, reading.answer.rating)
    }

    @Test
    fun backfillSkipsAdaptiveRepairButFeedsCoreAndRevalidationAnswers() {
        val backfill = RwkvHistoryBackfill()
        val repairing = AdaptiveRouteState(
            activeCore = CoreSkill.RECOGNITION,
            activeRepairTasks = listOf(StudyTaskTypes.MEANING_KANJI),
        )
        assertNull(backfill.classify(row(taskType = StudyTaskTypes.MEANING_KANJI, before = adaptiveJson("relearning", repairing))))
        val revalidation = AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING, revalidationPending = true)
        val fed = backfill.classify(row(taskType = StudyTaskTypes.SENTENCE_READING, before = adaptiveJson("review", revalidation)))!!
        assertEquals(RwkvStreamKeys.idsFor("脱", CoreSkill.CONTEXTUAL_READING, SIGNATURE), fed.ids)
        assertEquals(2, fed.answer.reviewKind)
    }

    @Test
    fun backfillWarmsACardWhoseHistoryBeganFromAnEvidenceSeed() {
        val backfill = RwkvHistoryBackfill()
        val seeded = memory(stability = 80.0, totalReviews = 0).encode()
        val route = AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING)
        val first = backfill.classify(
            row(taskType = StudyTaskTypes.WORD_READING, before = adaptiveJson("review", route), memoryBefore = seeded),
        )!!
        assertEquals(RwkvSeedBurstPolicy.answers(80.0, REVIEWED_AT) { 0L }.size, first.warmFirst.size)
        assertEquals(REVIEWED_AT - 80 * DAY, first.warmFirst.last().reviewedAtMillis)
        val second = backfill.classify(
            row(taskType = StudyTaskTypes.WORD_READING, before = adaptiveJson("review", route), memoryBefore = seeded),
        )!!
        assertTrue("only a card's first fed row is warmed", second.warmFirst.isEmpty())
    }

    @Test
    fun backfillToleratesOldRowsWithoutSchedulerSnapshots() {
        val fed = RwkvHistoryBackfill().classify(
            row(taskType = "", before = "", rating = "bogus", dayStart = 0L, duration = null, coreSkill = ""),
        )!!
        assertEquals(RwkvStreamKeys.idsFor("脱", CoreSkill.RECOGNITION, SIGNATURE), fed.ids)
        assertEquals(1, fed.answer.rating)
        assertEquals(2, fed.answer.reviewKind)
        assertNull(fed.answer.durationMillis)
        assertEquals(RwkvDays.fromDayStart(LocalDayPolicy.localDayStart(REVIEWED_AT)), fed.answer.dayOffset)
        assertTrue(fed.warmFirst.isEmpty())
        val fromRung = RwkvHistoryBackfill().classify(
            row(taskType = "", before = legacyJson("review", "sentence_reading"), coreSkill = ""),
        )
        assertNotNull(fromRung)
        assertEquals(RwkvStreamKeys.idsFor("脱", CoreSkill.CONTEXTUAL_READING, SIGNATURE).card, fromRung!!.ids.card)
    }

    // --- fixtures -------------------------------------------------------------

    private fun row(
        taskType: String,
        before: String,
        rating: String = "good",
        memoryBefore: String = "",
        dayStart: Long = DAY_START,
        duration: Long? = 4_200L,
        coreSkill: String = "",
    ) = RwkvHistoryBackfill.Row(
        kanji = "脱",
        answerSignature = SIGNATURE,
        taskType = taskType,
        rating = rating,
        reviewedAtMillis = REVIEWED_AT,
        reviewDayStartMillis = dayStart,
        coreSkill = coreSkill,
        schedulerStateBeforeJson = before,
        memoryBefore = memoryBefore,
        activeElapsedMillis = duration,
    )

    private fun legacyJson(phase: String, rung: String): String =
        "{\"phase\":\"$phase\",\"rung\":\"$rung\",\"routing_version\":1,\"adaptive_route_state_json\":\"\"}"

    private fun adaptiveJson(phase: String, route: AdaptiveRouteState): String =
        CompactJson.encodeObject(
            mapOf(
                "phase" to phase,
                "rung" to "kanji_meaning",
                "routing_version" to AdaptiveStudyItemPolicy.ROUTING_VERSION.toLong(),
                "adaptive_route_state_json" to AdaptiveRouteStateCodec.encode(route),
            ),
        )

    private fun memory(stability: Double, totalReviews: Int) =
        RecordsStudyModels.TaskMemory(StudyLadderRules.STATE_REVIEW, REVIEWED_AT, stability, 5.0, totalReviews, 0, 0, "", 0)

    /** A deterministic collection with 400 days of history over 100 cards. */
    private fun populatedCollection(): RwkvCollectionState {
        val state = RwkvCollectionState()
        val random = java.util.Random(3)
        val due = HashMap<Int, Long>()
        for (day in 0 until 400) {
            val today = START + day * DAY
            if (day < 200 && day % 2 == 0) {
                due[day] = today
            }
            for ((slot, card) in due.filterValues { it <= today }.keys.sorted().withIndex()) {
                val rating = if (random.nextDouble() < 0.1) 1 else 3
                val core = if (card % 3 == 0) CoreSkill.CONTEXTUAL_READING else CoreSkill.RECOGNITION
                val ids = RwkvStreamKeys.idsFor("k$card", core, "")
                val answer = RwkvAnswer(rating, 4_000L + random.nextInt(6_000), today + slot * 15_000L, Math.floorDiv(today, DAY), 2)
                val outcome = engine.answer(state, ids, answer, 0.9f, 36_500)
                due[card] = today + outcome.intervalDays * DAY
            }
        }
        return state
    }

    private fun copyOf(source: RwkvCollectionState): RwkvCollectionState {
        val copy = RwkvCollectionState(source.globals.copy())
        source.streams.forEachIndexed { stream, map -> copy.streams[stream].putAll(map) }
        copy.cards.putAll(source.cards)
        copy.encodings.putAll(source.encodings)
        copy.histories.putAll(source.histories)
        return copy
    }

    private companion object {
        const val DAY = MemoryReviewResult.DAY_MILLIS
        const val START = 1_700_000_000_000L
        const val REVIEWED_AT = 1_760_000_123_000L
        const val DAY_START = 1_759_968_000_000L
        const val SIGNATURE = "脱|脱出|だっしゅつ|escape"
    }
}
