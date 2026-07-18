package dev.bee.kanjianki.core

import dev.bee.kanjianki.core.RecordsStudyModels.StudyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MidSyncReviewMergePolicyTest {
    private fun item(
        kanji: String,
        totalReviews: Int,
        lastRealReviewDueAt: Long,
        answerSignature: String = "",
        dueAt: Long = 0L,
        schedulerRevision: Long = 0L,
    ): StudyItem {
        return StudyItem(kanji, "review", dueAt, 1.0, 5.0, totalReviews, 0, 0, 0, null, 0L)
            .copyBuilder()
            .answerSignature(answerSignature)
            .lastRealReviewDueAtMillis(lastRealReviewDueAt)
            .schedulerRevision(schedulerRevision)
            .build()
    }

    @Test
    fun emptyPersistedReturnsSeededUnchanged() {
        val seeded = listOf(item("痛", totalReviews = 0, lastRealReviewDueAt = 0L))
        val merged = MidSyncReviewMergePolicy.merge(seeded, seeded, emptyList())
        assertSame(seeded, merged)
    }

    @Test
    fun keepsSeededWhenNoReviewLandedMidSync() {
        val baseline = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L))
        // Sync seeds a new due date but no review happened; persisted matches baseline.
        val seeded = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 999L))
        val persisted = baseline
        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted)
        assertEquals(1, merged.size)
        assertEquals(999L, merged[0].dueAtMillis)
    }

    @Test
    fun keepsPersistedWhenReviewCountAdvancedMidSync() {
        val baseline = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L))
        // A review landed: persisted totalReviews advanced and due slot changed.
        val persisted = listOf(item("痛", totalReviews = 4, lastRealReviewDueAt = 5_000L, dueAt = 7_000L))
        // Sync computed pre-review state (would overwrite the review).
        val seeded = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 200L))

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted)

        assertEquals(1, merged.size)
        assertEquals("mid-sync review must survive", 4, merged[0].totalReviews)
        assertEquals(7_000L, merged[0].dueAtMillis)
    }

    @Test
    fun keepsPersistedWhenOnlyDueSlotChanged() {
        val baseline = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L))
        val persisted = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 6_000L))
        val seeded = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L))

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted)

        assertEquals(6_000L, merged[0].lastRealReviewDueAtMillis)
    }

    @Test
    fun keepsSeededForNewFamilyWithoutBaseline() {
        val baseline = emptyList<StudyItem>()
        // Persisted has stray review evidence, but the sync is introducing this family.
        val persisted = listOf(item("痛", totalReviews = 2, lastRealReviewDueAt = 500L))
        val seeded = listOf(item("痛", totalReviews = 0, lastRealReviewDueAt = 0L, dueAt = 42L))

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted)

        assertEquals(0, merged[0].totalReviews)
        assertEquals(42L, merged[0].dueAtMillis)
    }

    @Test
    fun mergesPerFamilyKeyIndependently() {
        val baseline = listOf(
            item("痛", totalReviews = 3, lastRealReviewDueAt = 100L),
            item("弱", totalReviews = 1, lastRealReviewDueAt = 50L),
        )
        val persisted = listOf(
            item("痛", totalReviews = 4, lastRealReviewDueAt = 9_000L), // reviewed mid-sync
            item("弱", totalReviews = 1, lastRealReviewDueAt = 50L), // untouched
        )
        val seeded = listOf(
            item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 1L),
            item("弱", totalReviews = 1, lastRealReviewDueAt = 50L, dueAt = 2L),
        )

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted).associateBy { it.kanji }

        assertEquals(4, merged["痛"]!!.totalReviews)
        assertEquals(2L, merged["弱"]!!.dueAtMillis)
    }

    @Test
    fun differentAnswerSignaturesAreDistinctFamilies() {
        val baseline = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, answerSignature = "a"))
        val persisted = listOf(
            item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, answerSignature = "a"),
            item("痛", totalReviews = 5, lastRealReviewDueAt = 800L, answerSignature = "b"),
        )
        val seeded = listOf(
            item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, answerSignature = "a", dueAt = 3L),
        )

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted)

        // Only family "a" is seeded; it did not change, so seeded state wins.
        assertEquals(1, merged.size)
        assertEquals(3L, merged[0].dueAtMillis)
    }

    @Test
    fun sameMeaningSignatureReshuffleKeepsMidSyncReviewAndAdoptsNewIdentity() {
        val oldSignature = "痛|痛む|いたむ|pain"
        val newSignature = "痛|苦痛|くつう|pain"
        val baseline = listOf(
            item(
                "痛",
                totalReviews = 3,
                lastRealReviewDueAt = 100L,
                answerSignature = oldSignature,
                schedulerRevision = 7L,
            ),
        )
        val persisted = listOf(
            item(
                "痛",
                totalReviews = 4,
                lastRealReviewDueAt = 5_000L,
                answerSignature = oldSignature,
                dueAt = 7_000L,
                schedulerRevision = 8L,
            ),
        )
        val seeded = listOf(
            item(
                "痛",
                totalReviews = 3,
                lastRealReviewDueAt = 100L,
                answerSignature = newSignature,
                dueAt = 200L,
                schedulerRevision = 7L,
            ),
        )

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted).single()

        assertEquals(newSignature, merged.answerSignature)
        assertEquals(4, merged.totalReviews)
        assertEquals(7_000L, merged.dueAtMillis)
        assertEquals(8L, merged.schedulerRevision)
    }

    @Test
    fun duplicateLegacyFamilyReviewSurvivesCanonicalization() {
        val canonicalSignature = "痛|痛む|いたむ|pain"
        val legacySignature = ""
        val baseline = listOf(
            item("痛", 3, 100L, canonicalSignature, schedulerRevision = 7L),
            item("痛", 2, 50L, legacySignature, schedulerRevision = 6L),
        )
        val persisted = listOf(
            baseline[0],
            item("痛", 4, 5_000L, legacySignature, dueAt = 7_000L, schedulerRevision = 8L),
        )
        val seeded = listOf(
            item("痛", 3, 100L, canonicalSignature, dueAt = 200L, schedulerRevision = 7L),
        )

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted).single()

        assertEquals(canonicalSignature, merged.answerSignature)
        assertEquals(4, merged.totalReviews)
        assertEquals(7_000L, merged.dueAtMillis)
        assertEquals(8L, merged.schedulerRevision)
    }

    @Test
    fun compatibleLegacyFamilyReviewWinsOverStrongerDifferentMeaningRow() {
        val compatibleSignature = "痛|痛む|いたむ|pain"
        val incompatibleSignature = "痛|傷む|いたむ|be damaged"
        val baseline = listOf(
            item("痛", 3, 100L, compatibleSignature, schedulerRevision = 7L),
            item("痛", 9, 900L, incompatibleSignature, schedulerRevision = 20L),
        )
        val persisted = listOf(
            item("痛", 4, 5_000L, compatibleSignature, dueAt = 7_000L, schedulerRevision = 8L),
            item("痛", 9, 900L, incompatibleSignature, schedulerRevision = 20L),
        )
        val seeded = listOf(
            item("痛", 3, 100L, compatibleSignature, dueAt = 200L, schedulerRevision = 7L),
        )

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted).single()

        assertEquals(compatibleSignature, merged.answerSignature)
        assertEquals(4, merged.totalReviews)
        assertEquals(7_000L, merged.dueAtMillis)
        assertEquals(8L, merged.schedulerRevision)
    }

    @Test
    fun meaningChangeDoesNotClaimReviewFromOldFamily() {
        val baseline = listOf(
            item("痛", 3, 100L, answerSignature = "痛|痛む|いたむ|pain", schedulerRevision = 7L),
        )
        val persisted = listOf(
            item("痛", 4, 5_000L, answerSignature = "痛|痛む|いたむ|pain", schedulerRevision = 8L),
        )
        val seeded = listOf(
            item("痛", 0, 0L, answerSignature = "痛|傷む|いたむ|be damaged", dueAt = 200L, schedulerRevision = 8L),
        )

        val merged = MidSyncReviewMergePolicy.merge(seeded, baseline, persisted).single()

        assertEquals("痛|傷む|いたむ|be damaged", merged.answerSignature)
        assertEquals(0, merged.totalReviews)
        assertEquals(200L, merged.dueAtMillis)
    }
}
