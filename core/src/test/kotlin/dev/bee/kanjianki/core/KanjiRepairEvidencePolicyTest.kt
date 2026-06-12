package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiRepairEvidencePolicyTest {
    @Test
    fun improvingWhenWeaknessDropsAfterPostReviewSync() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                beforeWeakness = 92,
                beforeSupport = 1,
                afterWeakness = 80,
                afterSupport = 1,
                reviews = 4,
                samples = 3,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.IMPROVING, evidence.status())
        assertEquals("improved_weakness_after_reviews", evidence.reason())
        assertEquals("After Kani reviews, AnkiDroid weakness moved 92 → 80.", evidence.explanation())
        assertEquals(92, evidence.beforeWeakness())
        assertEquals(80, evidence.afterWeakness())
        assertEquals(1, evidence.beforeMatureSupport())
        assertEquals(1, evidence.afterMatureSupport())
        assertEquals(4, evidence.kaniReviews())
        assertEquals(0, evidence.writingFailures())
        assertEquals(0.84, evidence.confidence(), 0.0001)
        assertEquals("Weakness moved 92 → 80 after 4 Kani reviews and 3 post-review samples.", evidence.confidenceReason())
    }

    @Test
    fun improvingWhenMatureSupportIncreasesAfterPostReviewSync() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                beforeWeakness = 65,
                beforeSupport = 1,
                afterWeakness = 64,
                afterSupport = 3,
                reviews = 3,
                samples = 2,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.IMPROVING, evidence.status())
        assertEquals("improved_support_after_reviews", evidence.reason())
        assertEquals("After Kani reviews, mature AnkiDroid support moved 1 → 3.", evidence.explanation())
        assertEquals(1, evidence.beforeMatureSupport())
        assertEquals(3, evidence.afterMatureSupport())
        assertEquals(0.84, evidence.confidence(), 0.0001)
        assertEquals("Mature support moved 1 → 3 after 3 Kani reviews and 2 post-review samples.", evidence.confidenceReason())
    }

    @Test
    fun insufficientEvidenceWhenNoKaniReviews() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                reviews = 0,
                samples = 0,
                lastReviewAtMillis = 2_000L,
                lastSyncAtMillis = 3_000L,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, evidence.status())
        assertEquals("no_kani_reviews", evidence.reason())
        assertEquals("No Kani reviews recorded for this kanji yet.", evidence.explanation())
        assertEquals(0.05, evidence.confidence(), 0.0001)
    }

    @Test
    fun insufficientEvidenceWhenNoSyncSinceStudying() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                reviews = 4,
                samples = 3,
                lastReviewAtMillis = 2_000L,
                lastSyncAtMillis = 2_000L,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, evidence.status())
        assertEquals("no_sync_since_review", evidence.reason())
        assertEquals("No sync has landed since the latest Kani review.", evidence.explanation())
        assertEquals(0.10, evidence.confidence(), 0.0001)
    }

    @Test
    fun insufficientEvidenceWhenTooFewPostReviewSamples() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                beforeWeakness = 70,
                beforeSupport = 2,
                afterWeakness = 68,
                afterSupport = 2,
                reviews = 2,
                samples = 1,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, evidence.status())
        assertEquals("too_few_post_review_samples", evidence.reason())
        assertEquals("Too few post-review samples to judge confidently.", evidence.explanation())
        assertEquals(0.20, evidence.confidence(), 0.0001)
    }

    @Test
    fun regressingWhenWeaknessWorsensAndMistakesContinue() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                beforeWeakness = 50,
                beforeSupport = 2,
                afterWeakness = 58,
                afterSupport = 1,
                reviews = 4,
                samples = 3,
                writingFailures = 2,
                lastMistakeAtMillis = 3_500L,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.REGRESSING, evidence.status())
        assertEquals("regressing_weakness_after_reviews", evidence.reason())
        assertEquals(
            "After Kani reviews, AnkiDroid weakness moved 50 → 58 and mistakes are still showing up.",
            evidence.explanation(),
        )
        assertEquals(50, evidence.beforeWeakness())
        assertEquals(58, evidence.afterWeakness())
        assertEquals(2, evidence.writingFailures())
        assertEquals(0.79, evidence.confidence(), 0.0001)
        assertEquals("Weakness moved 50 → 58 after 4 Kani reviews and 3 post-review samples.", evidence.confidenceReason())
    }

    @Test
    fun stableWhenEnoughEvidenceButNoMeaningfulDelta() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                beforeWeakness = 50,
                beforeSupport = 2,
                afterWeakness = 52,
                afterSupport = 2,
                reviews = 4,
                samples = 3,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.STABLE, evidence.status())
        assertEquals("stable_after_reviews", evidence.reason())
        assertEquals("Evidence is about unchanged after Kani reviews.", evidence.explanation())
        assertEquals(0.62, evidence.confidence(), 0.0001)
    }

    @Test
    fun stillFailingDespiteReviewsUsesLowConfidenceAntiOverclaimingExplanation() {
        val evidence = KanjiRepairEvidencePolicy.summarize(
            input(
                beforeWeakness = 50,
                beforeSupport = 2,
                afterWeakness = 50,
                afterSupport = 2,
                reviews = 4,
                samples = 3,
                writingFailures = 1,
                lastMistakeAtMillis = 3_500L,
            )
        )

        assertEquals(KanjiRepairEvidencePolicy.Status.STABLE, evidence.status())
        assertEquals("still_failing_after_reviews", evidence.reason())
        assertEquals(
            "Still seeing mistakes/writing failures after Kani reviews, but the sync evidence has not moved enough to call a regression yet.",
            evidence.explanation(),
        )
        assertEquals(0.44, evidence.confidence(), 0.0001)
        assertEquals(
            "4 Kani reviews and 3 post-review samples are enough to compare, but recent mistakes/writing failures are still showing up.",
            evidence.confidenceReason(),
        )
    }

    @Test
    fun unsafeInputsAreClampedAndBlankKanjiDoesNotCrash() {
        val input = KanjiRepairEvidencePolicy.Input(
            kanjiArg = "　",
            beforeArg = KanjiRepairEvidencePolicy.Snapshot(
                weaknessScoreArg = -1,
                matureSupportCountArg = -2,
                sampledAtMillisArg = -3L,
                activeExampleCountArg = -4,
                suspendedExampleCountArg = -5,
                reasonCodeArg = "  before  ",
            ),
            afterArg = KanjiRepairEvidencePolicy.Snapshot(
                weaknessScoreArg = -6,
                matureSupportCountArg = -7,
                sampledAtMillisArg = -8L,
                activeExampleCountArg = -9,
                suspendedExampleCountArg = -10,
                reasonCodeArg = "  after  ",
            ),
            kaniReviewsArg = -4,
            postReviewSamplesArg = -5,
            writingFailuresArg = -6,
            lastMistakeAtMillisArg = -7L,
            firstReviewAtMillisArg = -8L,
            lastReviewAtMillisArg = -9L,
            lastSyncAtMillisArg = -10L,
            ladderArg = KanjiRepairEvidencePolicy.Ladder(
                rungArg = null,
                phaseArg = null,
                realPassStreakArg = -1,
                realAgainStreakArg = -2,
                matureIntervalDaysArg = -3,
            ),
        )

        assertEquals("", input.kanji())
        assertNotNull(input.before())
        assertEquals(0, input.before()!!.weaknessScore())
        assertEquals(0, input.before()!!.matureSupportCount())
        assertEquals(0L, input.before()!!.sampledAtMillis())
        assertEquals(0, input.before()!!.activeExampleCount())
        assertEquals(0, input.before()!!.suspendedExampleCount())
        assertEquals("before", input.before()!!.reasonCode())
        assertNotNull(input.after())
        assertEquals(0, input.after()!!.weaknessScore())
        assertEquals(0, input.after()!!.matureSupportCount())
        assertEquals(0L, input.after()!!.sampledAtMillis())
        assertEquals(0, input.after()!!.activeExampleCount())
        assertEquals(0, input.after()!!.suspendedExampleCount())
        assertEquals("after", input.after()!!.reasonCode())
        assertEquals(0, input.kaniReviews())
        assertEquals(0, input.postReviewSamples())
        assertEquals(0, input.writingFailures())
        assertEquals(0L, input.lastMistakeAtMillis())
        assertEquals(0L, input.firstReviewAtMillis())
        assertEquals(0L, input.lastReviewAtMillis())
        assertEquals(0L, input.lastSyncAtMillis())
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, input.ladder()!!.rung())
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, input.ladder()!!.phase())
        assertEquals(0, input.ladder()!!.realPassStreak())
        assertEquals(0, input.ladder()!!.realAgainStreak())
        assertEquals(0, input.ladder()!!.matureIntervalDays())

        val nullEvidence = KanjiRepairEvidencePolicy.summarize(null)
        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, nullEvidence.status())
        assertEquals("", nullEvidence.kanji())

        val direct = KanjiRepairEvidencePolicy.Evidence(confidenceArg = 1.5)
        assertEquals(1.0, direct.confidence(), 0.0)
        val directLow = KanjiRepairEvidencePolicy.Evidence(confidenceArg = -0.5)
        assertEquals(0.0, directLow.confidence(), 0.0)

        val evidence = KanjiRepairEvidencePolicy.summarize(input)
        assertEquals(KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE, evidence.status())
        assertEquals("", evidence.kanji())
        assertEquals(0, evidence.beforeWeakness())
        assertEquals(0, evidence.afterWeakness())
        assertEquals(0, evidence.beforeMatureSupport())
        assertEquals(0, evidence.afterMatureSupport())
        assertEquals(0, evidence.kaniReviews())
        assertEquals(0, evidence.writingFailures())
        assertEquals(0L, evidence.lastMistakeAtMillis())
        assertEquals(0L, evidence.lastSyncAtMillis())
    }

    private fun input(
        kanji: String? = "弱",
        beforeWeakness: Int = 90,
        beforeSupport: Int = 1,
        afterWeakness: Int = 78,
        afterSupport: Int = 1,
        reviews: Int = 4,
        samples: Int = 3,
        writingFailures: Int = 0,
        lastMistakeAtMillis: Long = 0L,
        firstReviewAtMillis: Long = 1_000L,
        lastReviewAtMillis: Long = 2_000L,
        lastSyncAtMillis: Long = 3_000L,
    ): KanjiRepairEvidencePolicy.Input {
        return KanjiRepairEvidencePolicy.Input(
            kanjiArg = kanji,
            beforeArg = beforeSnapshot(beforeWeakness, beforeSupport),
            afterArg = afterSnapshot(afterWeakness, afterSupport),
            kaniReviewsArg = reviews,
            postReviewSamplesArg = samples,
            writingFailuresArg = writingFailures,
            lastMistakeAtMillisArg = lastMistakeAtMillis,
            firstReviewAtMillisArg = firstReviewAtMillis,
            lastReviewAtMillisArg = lastReviewAtMillis,
            lastSyncAtMillisArg = lastSyncAtMillis,
            ladderArg = ladder(),
        )
    }

    private fun beforeSnapshot(weaknessScore: Int, matureSupportCount: Int): KanjiRepairEvidencePolicy.Snapshot {
        return KanjiRepairEvidencePolicy.Snapshot(
            weaknessScoreArg = weaknessScore,
            matureSupportCountArg = matureSupportCount,
            sampledAtMillisArg = 1_000L,
            activeExampleCountArg = 3,
            suspendedExampleCountArg = 1,
            reasonCodeArg = "baseline",
        )
    }

    private fun afterSnapshot(weaknessScore: Int, matureSupportCount: Int): KanjiRepairEvidencePolicy.Snapshot {
        return KanjiRepairEvidencePolicy.Snapshot(
            weaknessScoreArg = weaknessScore,
            matureSupportCountArg = matureSupportCount,
            sampledAtMillisArg = 3_000L,
            activeExampleCountArg = 4,
            suspendedExampleCountArg = 1,
            reasonCodeArg = "after",
        )
    }

    private fun ladder(): KanjiRepairEvidencePolicy.Ladder {
        return KanjiRepairEvidencePolicy.Ladder(
            rungArg = RecordsBase.LadderRung.KANJI_MEANING,
            phaseArg = RecordsBase.SchedulerPhase.REVIEW,
            realPassStreakArg = 2,
            realAgainStreakArg = 0,
            matureIntervalDaysArg = 7,
        )
    }
}
