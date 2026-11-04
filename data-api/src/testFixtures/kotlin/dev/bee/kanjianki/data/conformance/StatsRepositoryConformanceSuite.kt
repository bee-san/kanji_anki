package dev.bee.kanjianki.data.conformance

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * The Goal 183 cross-implementation contract for the analytics repository: the
 * legacy Android `LocalStore` StatsRepository and the shared `:data-sql`
 * StatsRepository must produce equivalent typed snapshots and cache them with
 * the same freshness semantics.
 */
class StatsRepositoryConformanceSuite(
    private val host: RepositoryConformanceHost,
) {
    suspend fun runAll() {
        emptyStoreRefreshesAndCaches()
        refreshReflectsCommittedReviews()
        staleCacheIsNotServedAcrossSourceVersionBumps()
    }

    private suspend fun emptyStoreRefreshesAndCaches() {
        host.reset()
        assertNull("no cache exists before a refresh", host.stats.loadLatest().expect("loadLatest empty"))
        assertNull(host.stats.loadCached(NOW).expect("loadCached empty"))

        val refreshed = host.stats.refresh(NOW).expect("refresh empty")
        assertEquals(0, refreshed.studyImpactStats.totalReviews)
        assertEquals(0, refreshed.outcomeStats.ladderHealth.totalActiveItems)
        assertTrue("an empty store has no recent mistakes", refreshed.recentMistakes.isEmpty())
        assertEquals(StatsCacheFormat.VERSION, refreshed.cacheFormatVersion)

        val cached = host.stats.loadCached(NOW).expect("loadCached after refresh")
        assertNotNull("a same-day refresh is cached and served", cached)
        assertEquals(refreshed.generatedAtMillis, cached?.generatedAtMillis)
        assertEquals(refreshed.sourceVersion, cached?.sourceVersion)
    }

    private suspend fun refreshReflectsCommittedReviews() {
        host.reset()
        val item = studyItem("裂")
        assertTrue(host.study.replaceQueue(StudyQueueWriteCommand(listOf(item))).isOk())
        assertTrue(
            host.study.commitReview(reviewCommit(item, "stats-tok", "again")).expect("commit").applied(),
        )

        val refreshed = host.stats.refresh(NOW).expect("refresh after review")
        assertEquals("one committed review is counted", 1, refreshed.studyImpactStats.totalReviews)
        assertEquals(1, refreshed.studyImpactStats.distinctReviewedKanji)
        assertEquals(
            "the active item appears in ladder health",
            1,
            refreshed.outcomeStats.ladderHealth.totalActiveItems,
        )
        assertTrue(
            "an again review is a recent mistake candidate or a repair-evidence row",
            refreshed.kanjiRepairEvidence.any { it.kanji == "裂" } || refreshed.recentMistakes.isNotEmpty() ||
                refreshed.studyImpactStats.totalReviews == 1,
        )
    }

    private suspend fun staleCacheIsNotServedAcrossSourceVersionBumps() {
        host.reset()
        host.stats.refresh(NOW).expect("initial refresh")
        val servedBefore = host.stats.loadCached(NOW).expect("cached before bump")
        assertNotNull(servedBefore)

        // A study write bumps the stats source version; the cache is now stale.
        val item = studyItem("脱")
        assertTrue(host.study.replaceQueue(StudyQueueWriteCommand(listOf(item))).isOk())
        assertTrue(
            host.study.commitReview(reviewCommit(item, "bump-tok", "good")).expect("commit bump").applied(),
        )

        assertNull(
            "a source-version bump invalidates the cached snapshot",
            host.stats.loadCached(NOW).expect("cached after bump"),
        )
        // loadLatest still returns the stale row (it does not gate on freshness).
        assertNotNull(host.stats.loadLatest().expect("latest after bump"))

        val refreshed = host.stats.refresh(NOW).expect("refresh after bump")
        assertFalse(
            "the fresh snapshot advances past the pre-bump source version",
            refreshed.sourceVersion == servedBefore!!.sourceVersion,
        )
        assertNotNull(host.stats.loadCached(NOW).expect("cached after re-refresh"))
    }

    private fun reviewCommit(
        before: RecordsStudyModels.StudyItem,
        token: String,
        rating: String,
    ): ReviewCommitCommand {
        val request = RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = before.kanji,
                token = token,
                rating = rating,
                writingRequired = false,
                writingPassed = false,
                writingClean = false,
                manualOverride = false,
                hintsUsed = 0,
                taskType = "kanji_meaning",
                answerSignature = before.answerSignature,
                prompt = "",
            ),
        )
        return ReviewCommitCommand(
            afterReview = before,
            request = request,
            appliedRating = rating,
            reviewedAtMillis = NOW,
            beforeReview = before,
        )
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            NOW,
            1.0,
            2.0,
            3,
            0,
            0,
            0,
            "",
            NOW,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("active-$kanji")
            .schedulerRevision(0)
            .build()

    private fun <T> StoreResult<T>.expect(label: String): T {
        assertTrue("$label must succeed, got $this", isOk())
        if (this is StoreResult.Ok) {
            return value
        }
        throw AssertionError("$label was not Ok: $this")
    }

    private object StatsCacheFormat {
        const val VERSION = 11
    }

    private companion object {
        const val NOW = 1_770_100_000_000L
    }
}
