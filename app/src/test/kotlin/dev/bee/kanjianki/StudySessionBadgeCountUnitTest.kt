package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test

class StudySessionBadgeCountUnitTest {
    @Test
    fun inFlightSessionUsesLiveTrackerRemaining() {
        // Mid-session the badge must decrease with every answered card, even when the
        // cached plan value is stale (the "19 due while I only study 5" bug).
        assertEquals(5, badge(active = true, target = 5, completed = 0, cached = 19))
        assertEquals(3, badge(active = true, target = 5, completed = 2, cached = 19))
        assertEquals(1, badge(active = true, target = 5, completed = 4, cached = 19))
    }

    @Test
    fun completedSessionFallsBackToLatestStudyNowCount() {
        // Once the run hits its target the tracker is exhausted; the badge should
        // show whatever the freshest scheduler preview says is selectable.
        assertEquals(2, badge(active = false, target = 5, completed = 5, cached = 2))
        assertEquals(0, badge(active = false, target = 5, completed = 6, cached = 0))
    }

    @Test
    fun idleStateUsesCachedStudyNowCount() {
        assertEquals(5, badge(active = false, target = 0, completed = 0, cached = 5))
        assertEquals(0, badge(active = false, target = 0, completed = 0, cached = 0))
    }

    @Test
    fun emptyStudyScreenIgnoresStaleTrackerTarget() {
        // The plan may still say four daily-focus kanji remain even though none is
        // selectable. Without the active-card gate, the empty route kept a "4" badge.
        assertEquals(0, badge(active = false, target = 4, completed = 0, cached = 0))
    }

    @Test
    fun unknownCacheStaysNonPositiveSoBadgeHides() {
        assertEquals(-1, badge(active = false, target = 0, completed = 0, cached = -1))
    }

    @Test
    fun pendingTasksGrowTheInFlightRemaining() {
        // includePendingTask() bumps the tracker target (e.g. due writing repairs);
        // those extra tasks must show up in the badge as well.
        assertEquals(4, badge(active = true, target = 6, completed = 2, cached = 0))
    }

    @Test
    fun practiceOnlyRepeatDoesNotGrowCompletedUniqueCardCount() {
        // Learn-ahead can show another practice step after the sole unique task has
        // completed. The repeat intentionally stays outside session-card progress.
        assertEquals(0, badge(active = true, target = 1, completed = 1, cached = 0))
    }

    @Test
    fun inactiveStudyNavigationStartsFreshOnlyAfterThePreviousRunFinished() {
        assertEquals(
            true,
            shouldStartNewStudyRunFromNavigation(
                studySessionActive = false,
                currentRunAtHardCap = true,
            ),
        )
        assertEquals(
            false,
            shouldStartNewStudyRunFromNavigation(
                studySessionActive = true,
                currentRunAtHardCap = true,
            ),
        )
        assertEquals(
            false,
            shouldStartNewStudyRunFromNavigation(
                studySessionActive = false,
                currentRunAtHardCap = false,
            ),
        )
    }

    private fun badge(active: Boolean, target: Int, completed: Int, cached: Int): Int {
        return studySessionBadgeCount(
            studySessionActive = active,
            trackerTargetCount = target,
            trackerCompletedCount = completed,
            cachedStudyNowCount = cached,
        )
    }
}
