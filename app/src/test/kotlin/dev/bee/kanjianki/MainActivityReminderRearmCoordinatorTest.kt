package dev.bee.kanjianki

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityReminderRearmCoordinatorTest {
    @Test
    fun requestWaitsForCurrentAcceptedRouteAndStaleSettlementDoesNotReleaseIt() {
        val executor = ManualExecutor()
        val rearms = mutableListOf<Set<String>>()
        val coordinator = MainActivityReminderRearmCoordinator(executor, rearms::add)

        coordinator.routeRequested(10)
        coordinator.request("resume")
        coordinator.routeRequested(11)

        assertFalse(coordinator.routeSettled(10))
        assertEquals(0, executor.pendingCount())

        assertTrue(coordinator.routeSettled(11))
        assertEquals(1, executor.pendingCount())
        executor.runNext()

        assertEquals(listOf(setOf("resume")), rearms)
    }

    @Test
    fun cancelClearsOnlyCurrentRouteAndDoesNotCountAsFirstSettlement() {
        val executor = ManualExecutor()
        val rearms = mutableListOf<Set<String>>()
        val coordinator = MainActivityReminderRearmCoordinator(executor, rearms::add)

        coordinator.routeRequested(1)
        coordinator.request("resume")

        assertFalse(coordinator.routeCanceled(999))
        assertEquals(0, executor.pendingCount())
        assertTrue(coordinator.routeCanceled(1))
        assertEquals(0, executor.pendingCount())

        coordinator.routeRequested(2)
        coordinator.routeSettled(2)
        executor.runNext()

        assertEquals(listOf(setOf("resume")), rearms)
    }

    @Test
    fun queuedWorkerParksWhenAReplacementRouteStartsBeforeItRuns() {
        val executor = ManualExecutor()
        val rearms = mutableListOf<Set<String>>()
        val coordinator = MainActivityReminderRearmCoordinator(executor, rearms::add)

        coordinator.routeRequested(1)
        coordinator.routeSettled(1)
        coordinator.request("resume")
        assertEquals(1, executor.pendingCount())

        coordinator.routeRequested(2)
        executor.runNext()
        assertEquals(emptyList<Set<String>>(), rearms)

        coordinator.routeSettled(2)
        assertEquals(1, executor.pendingCount())
        executor.runNext()

        assertEquals(listOf(setOf("resume")), rearms)
    }

    @Test
    fun requestsReceivedDuringRearmCollapseIntoOneFinalRefresh() {
        val executor = ManualExecutor()
        val rearms = mutableListOf<Set<String>>()
        lateinit var coordinator: MainActivityReminderRearmCoordinator
        coordinator = MainActivityReminderRearmCoordinator(
            executor = executor,
            rearm = { reasons ->
                rearms.add(reasons)
                if (rearms.size == 1) {
                    coordinator.request("review")
                    coordinator.request("resume")
                    coordinator.request("review")
                }
            },
        )

        coordinator.routeRequested(1)
        coordinator.routeSettled(1)
        coordinator.request("initial-route")
        executor.runNext()

        assertEquals(
            listOf(setOf("initial-route"), setOf("review", "resume")),
            rearms,
        )
        assertEquals(0, executor.pendingCount())
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun pendingCount(): Int = tasks.size

        fun runNext() {
            tasks.removeFirst().run()
        }
    }
}
