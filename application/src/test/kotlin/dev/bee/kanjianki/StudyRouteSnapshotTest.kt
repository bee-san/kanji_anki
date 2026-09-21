package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRouteSnapshotTest {
    @Test
    fun canonicalProgressRejectsNegativeAndInvertedCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            StudySessionProgressUiState(completedCount = -1, targetCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudySessionProgressUiState(completedCount = 2, targetCount = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudySessionProgressUiState(movedForwardCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudySessionProgressUiState(missedCount = -1)
        }
    }

    @Test
    fun displayedCountsAreDerivedFromTheCanonicalProgressPair() {
        val snapshot = snapshot(completed = 5, target = 7)

        assertEquals(5, snapshot.displayedCompletedCount)
        assertEquals(7, snapshot.displayedTargetCount)
        assertEquals(2, snapshot.remainingCount)
        assertFalse(
            StudyRouteSnapshot::class.java.declaredFields.any {
                it.name == "displayedCompletedCount" || it.name == "displayedTargetCount"
            },
        )
    }

    @Test
    fun displayedTargetIncludesAnAcceptedActiveTaskBeyondTheOriginalPlan() {
        val snapshot = snapshot(completed = 7, target = 7, activeTask = true)

        assertEquals(7, snapshot.displayedCompletedCount)
        assertEquals(8, snapshot.displayedTargetCount)
        assertEquals(0, snapshot.remainingCount)
    }

    @Test
    fun snapshotsCannotSilentlyRewriteTheirDenominatorOrCompleteThemselves() {
        val methodNames = StudyRouteSnapshot::class.java.declaredMethods.map { it.name }.toSet()

        assertFalse("target reconciliation is an accepted route transition", "withTerminalCounts" in methodNames)
        assertFalse("completion is an accepted route transition", "complete" in methodNames)
        assertEquals(7, snapshot(completed = 5, target = 7).progress.targetCount)
    }

    @Test
    fun acceptedRouteHasNoDetachedPendingWorkAuthority() {
        val methodNames = StudyRouteSnapshot::class.java.declaredMethods.map { it.name }.toSet()

        assertFalse("canonical progress owns completion blockers", "getPendingWork" in methodNames)
    }

    @Test
    fun versionTokensAdvanceMonotonicallyAndIndependently() {
        assertEquals(StudyRouteVersion(8L), StudyRouteVersion(7L).next())
        assertEquals(StudySessionGeneration(4L), StudySessionGeneration(3L).next())
    }

    @Test
    fun versionTokensCanReachTheLongBoundaryWithoutWrapping() {
        assertEquals(StudyRouteVersion(Long.MAX_VALUE), StudyRouteVersion(Long.MAX_VALUE - 1L).next())
        assertEquals(
            StudySessionGeneration(Long.MAX_VALUE),
            StudySessionGeneration(Long.MAX_VALUE - 1L).next(),
        )
    }

    @Test
    fun versionTokensRejectOverflowAtTheLongBoundary() {
        assertThrows(ArithmeticException::class.java) {
            StudyRouteVersion(Long.MAX_VALUE).next()
        }
        assertThrows(ArithmeticException::class.java) {
            StudySessionGeneration(Long.MAX_VALUE).next()
        }
    }

    private fun snapshot(
        completed: Int,
        target: Int,
        phase: StudySessionPhase = StudySessionPhase.ACTIVE,
        activeTask: Boolean = false,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        version = StudyRouteVersion(7L),
        sessionGeneration = StudySessionGeneration(3L),
        sessionToken = "token-1",
        phase = phase,
        progress = StudySessionProgressUiState(
            targetCount = target,
            completedCount = completed,
            activeTask = activeTask,
        ),
    )
}
