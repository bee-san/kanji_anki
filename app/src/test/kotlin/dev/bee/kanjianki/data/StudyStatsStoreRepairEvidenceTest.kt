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
}
