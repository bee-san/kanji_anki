package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test

class StudySessionBadgeCountUnitTest {
    @Test
    fun inFlightSessionUsesLiveTrackerRemaining() {
        // Mid-session the badge must decrease with every answered card, even when the
        // cached plan value is stale (the "19 due while I only study 5" bug).
        assertEquals(5, studySessionBadgeCount(trackerTargetCount = 5, trackerCompletedCount = 0, cachedPlanRemaining = 19))
        assertEquals(3, studySessionBadgeCount(trackerTargetCount = 5, trackerCompletedCount = 2, cachedPlanRemaining = 19))
        assertEquals(1, studySessionBadgeCount(trackerTargetCount = 5, trackerCompletedCount = 4, cachedPlanRemaining = 19))
    }

    @Test
    fun completedSessionFallsBackToLatestPlanRemaining() {
        // Once the run hits its target the tracker is exhausted; the badge should
        // show whatever the freshest adaptive plan says is still worth studying.
        assertEquals(2, studySessionBadgeCount(trackerTargetCount = 5, trackerCompletedCount = 5, cachedPlanRemaining = 2))
        assertEquals(0, studySessionBadgeCount(trackerTargetCount = 5, trackerCompletedCount = 6, cachedPlanRemaining = 0))
    }

    @Test
    fun idleStateUsesCachedPlanRemaining() {
        assertEquals(5, studySessionBadgeCount(trackerTargetCount = 0, trackerCompletedCount = 0, cachedPlanRemaining = 5))
        assertEquals(0, studySessionBadgeCount(trackerTargetCount = 0, trackerCompletedCount = 0, cachedPlanRemaining = 0))
    }

    @Test
    fun unknownCacheStaysNonPositiveSoBadgeHides() {
        assertEquals(-1, studySessionBadgeCount(trackerTargetCount = 0, trackerCompletedCount = 0, cachedPlanRemaining = -1))
    }

    @Test
    fun pendingTasksGrowTheInFlightRemaining() {
        // includePendingTask() bumps the tracker target (e.g. due writing repairs);
        // those extra tasks must show up in the badge as well.
        assertEquals(4, studySessionBadgeCount(trackerTargetCount = 6, trackerCompletedCount = 2, cachedPlanRemaining = 0))
    }
}
