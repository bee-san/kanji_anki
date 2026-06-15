package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyStatsStoreRepairEvidenceTest {
    @Test
    fun repairEvidenceWrapsCoreEvidenceWithoutLosingNormalizedFields() {
        val coreEvidence = KanjiRepairEvidencePolicy.Evidence(
            kanjiArg = "  漢  ",
            statusArg = KanjiRepairEvidencePolicy.Status.IMPROVING,
            reasonArg = " strong_drop ",
            explanationArg = " better after review ",
            beforeWeaknessArg = -4,
            afterWeaknessArg = 12,
            beforeMatureSupportArg = 1,
            afterMatureSupportArg = -9,
            kaniReviewsArg = 6,
            writingFailuresArg = -2,
            lastMistakeAtMillisArg = -55L,
            lastSyncAtMillisArg = 1_234L,
            confidenceArg = 1.7,
            confidenceReasonArg = " enough samples ",
        )

        val appEvidence = StudyStatsStore.repairEvidence(coreEvidence)

        assertEquals("漢", appEvidence.kanji)
        assertEquals(KanjiRepairEvidencePolicy.Status.IMPROVING, appEvidence.status)
        assertEquals("strong_drop", appEvidence.reason)
        assertEquals("better after review", appEvidence.explanation)
        assertEquals(0, appEvidence.beforeWeakness ?: -1)
        assertEquals(12, appEvidence.afterWeakness ?: -1)
        assertEquals(1, appEvidence.beforeMatureSupport ?: -1)
        assertEquals(0, appEvidence.afterMatureSupport ?: -1)
        assertEquals(6, appEvidence.kaniReviews)
        assertEquals(0, appEvidence.writingFailures)
        assertEquals(0L, appEvidence.lastMistakeAtMillis)
        assertEquals(1_234L, appEvidence.lastSyncAtMillis)
        assertEquals(1.0, appEvidence.confidence, 0.0001)
        assertEquals("enough samples", appEvidence.confidenceReason)
    }

    @Test
    fun repairEvidenceCohortStatsSortsAndCountsEvidenceByStatusAndConfidence() {
        val cohort = StudyStatsStore.repairEvidenceCohortStats(
            listOf(
                evidence(
                    kanji = "危",
                    status = KanjiRepairEvidencePolicy.Status.REGRESSING,
                    reason = "regressing_after_review",
                    confidence = 0.79,
                    beforeWeakness = 44,
                    afterWeakness = 58,
                    beforeSupport = 2,
                    afterSupport = 2,
                    kaniReviews = 4,
                    lastMistakeAtMillis = 2_000L,
                    lastSyncAtMillis = 5_000L,
                ),
                evidence(
                    kanji = "伸",
                    status = KanjiRepairEvidencePolicy.Status.IMPROVING,
                    reason = "improved_after_review",
                    confidence = 0.84,
                    beforeWeakness = 70,
                    afterWeakness = 40,
                    beforeSupport = 0,
                    afterSupport = 3,
                    kaniReviews = 3,
                    lastMistakeAtMillis = 2_200L,
                    lastSyncAtMillis = 5_200L,
                ),
                evidence(
                    kanji = "未",
                    status = KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE,
                    reason = "no_sync_since_review",
                    confidence = 0.20,
                    explanation = "Waiting for sync after the latest review.",
                    confidenceReason = "No sync has landed since the latest Kani review.",
                    lastSyncAtMillis = 0L,
                ),
                evidence(
                    kanji = "疑",
                    status = KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE,
                    reason = "too_few_post_review_samples",
                    confidence = 0.10,
                    explanation = "Too few post-review samples to judge confidently.",
                    confidenceReason = "Only one post-review sample is available.",
                    lastSyncAtMillis = 0L,
                ),
                evidence(
                    kanji = "安",
                    status = KanjiRepairEvidencePolicy.Status.STABLE,
                    reason = "stable_after_reviews",
                    confidence = 0.62,
                    beforeWeakness = 45,
                    afterWeakness = 46,
                    beforeSupport = 1,
                    afterSupport = 1,
                    kaniReviews = 5,
                    lastMistakeAtMillis = 2_400L,
                    lastSyncAtMillis = 5_400L,
                ),
            )
        )

        assertEquals(5, cohort.totalCount)
        assertEquals(1, cohort.improvingCount)
        assertEquals(1, cohort.stableCount)
        assertEquals(1, cohort.regressingCount)
        assertEquals(2, cohort.insufficientEvidenceCount)
        assertEquals(2, cohort.highConfidenceCount)
        assertEquals(1, cohort.mediumConfidenceCount)
        assertEquals(2, cohort.lowConfidenceCount)
        assertEquals(listOf("危", "伸", "未", "疑", "安"), cohort.examples.map { it.kanji })
        assertEquals(listOf("regressing_after_review", "improved_after_review", "no_sync_since_review", "too_few_post_review_samples", "stable_after_reviews"), cohort.examples.map { it.reason })
    }

    private fun evidence(
        kanji: String,
        status: KanjiRepairEvidencePolicy.Status,
        reason: String,
        confidence: Double,
        explanation: String = reason,
        confidenceReason: String = reason,
        beforeWeakness: Int = 50,
        afterWeakness: Int = 50,
        beforeSupport: Int = 0,
        afterSupport: Int = 0,
        kaniReviews: Int = 1,
        writingFailures: Int = 0,
        lastMistakeAtMillis: Long = 1_000L,
        lastSyncAtMillis: Long = 2_000L,
    ): StudyStatsStore.KanjiRepairEvidence {
        return StudyStatsStore.repairEvidence(
            KanjiRepairEvidencePolicy.Evidence(
                kanjiArg = kanji,
                statusArg = status,
                reasonArg = reason,
                explanationArg = explanation,
                beforeWeaknessArg = beforeWeakness,
                afterWeaknessArg = afterWeakness,
                beforeMatureSupportArg = beforeSupport,
                afterMatureSupportArg = afterSupport,
                kaniReviewsArg = kaniReviews,
                writingFailuresArg = writingFailures,
                lastMistakeAtMillisArg = lastMistakeAtMillis,
                lastSyncAtMillisArg = lastSyncAtMillis,
                confidenceArg = confidence,
                confidenceReasonArg = confidenceReason,
            )
        )
    }
}
