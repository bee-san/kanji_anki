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
    fun snapshotsCannotSilentlyRewriteTheirDenominatorOrCompleteThemselves() {
        val methodNames = StudyRouteSnapshot::class.java.declaredMethods.map { it.name }.toSet()

        assertFalse("target reconciliation is an accepted route transition", "withTerminalCounts" in methodNames)
        assertFalse("completion is an accepted route transition", "complete" in methodNames)
        assertEquals(7, snapshot(completed = 5, target = 7).progress.targetCount)
    }

    @Test
    fun pendingWorkNormalizesDuplicateAndBlankKeysWithoutLosingTheirKinds() {
        val work = StudyRoutePendingWork.of(
            pendingTaskKeys = listOf("shared", "pending", "pending", ""),
            requeuedTaskKeys = listOf("shared", "wrong", "wrong", " "),
            learnAheadRepeatTaskKeys = listOf("shared", "repeat", "repeat"),
            repairTaskKeys = listOf("shared", "repair", "repair"),
        )

        assertEquals(setOf("shared", "pending"), work.pendingTaskKeys)
        assertEquals(setOf("shared", "wrong"), work.requeuedTaskKeys)
        assertEquals(setOf("shared", "repeat"), work.learnAheadRepeatTaskKeys)
        assertEquals(setOf("shared", "repair"), work.repairTaskKeys)
        assertEquals(setOf("shared", "pending", "wrong", "repeat", "repair"), work.taskKeys)
        assertEquals(5, work.blockerCount)
        assertTrue(work.hasBlockers)
    }

    @Test
    fun terminalCountsWithRepresentedWorkAreNotComplete() {
        val snapshot = snapshot(
            completed = 5,
            target = 5,
            phase = StudySessionPhase.COMPLETE,
            pendingWork = StudyRoutePendingWork.of(repairTaskKeys = listOf("repair")),
        )

        assertFalse(snapshot.canComplete)
        assertFalse(snapshot.isComplete)
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
        pendingWork: StudyRoutePendingWork = StudyRoutePendingWork.NONE,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        version = StudyRouteVersion(7L),
        sessionGeneration = StudySessionGeneration(3L),
        sessionToken = "token-1",
        phase = phase,
        progress = StudySessionProgressUiState(
            targetCount = target,
            completedCount = completed,
        ),
        pendingWork = pendingWork,
    )
}
