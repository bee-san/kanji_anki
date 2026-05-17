package dev.bee.kanjianki.fsrs

import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsRating
import dev.bee.fsrs.FsrsReviewInput
import org.junit.Assert.assertEquals
import org.junit.Test

class JavaBackedKaniFsrsEngineTest {
    @Test
    fun reviewMatchesCurrentJavaEngine() {
        val current = FsrsEngine.latestDefault()
        val wrapper = JavaBackedKaniFsrsEngine(current)

        val direct = current.review(
            FsrsReviewInput(
                FsrsMemoryState(8.0, 5.0),
                FsrsRating.GOOD,
                4,
                0.9,
                36_500,
            ),
        )

        val wrapped = wrapper.review(
            FsrsReviewRequest(
                previousMemory = FsrsMemory(stability = 8.0, difficulty = 5.0),
                rating = FsrsReviewRating.GOOD,
                elapsedDays = 4,
                desiredRetention = 0.9,
            ),
        )

        assertEquals(direct.nextState().stability(), wrapped.nextMemory.stability, 0.0)
        assertEquals(direct.nextState().difficulty(), wrapped.nextMemory.difficulty, 0.0)
        assertEquals(direct.retrievability(), wrapped.retrievability, 0.0)
        assertEquals(direct.nextIntervalDays(), wrapped.nextIntervalDays)
    }

    @Test
    fun provenanceExposesPinnedSnapshot() {
        assertEquals(FsrsAlgorithmInfo.UPSTREAM_REPOSITORY, FsrsProvenance.upstreamRepository)
        assertEquals(FsrsAlgorithmInfo.UPSTREAM_RELEASE, FsrsProvenance.upstreamRelease)
        assertEquals(FsrsAlgorithmInfo.UPSTREAM_COMMIT, FsrsProvenance.upstreamCommit)
        assertEquals(FsrsAlgorithmInfo.UPSTREAM_SCHEDULER_BLOB, FsrsProvenance.upstreamSchedulerBlob)
        assertEquals(FsrsAlgorithmInfo.PARAMETER_COUNT, FsrsProvenance.parameterCount)
    }
}
