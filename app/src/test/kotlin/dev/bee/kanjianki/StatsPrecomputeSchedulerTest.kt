package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class StatsPrecomputeSchedulerTest {
    @Test
    fun scheduleIfStaleRefreshesInBackground() {
        val executor = QueuedExecutor()
        var fresh = false
        var now = 1_000L
        val refreshTimes = mutableListOf<Long>()
        val scheduler = StatsPrecomputeScheduler(
            background = executor,
            isFresh = { fresh },
            refresh = { generatedAt ->
                refreshTimes.add(generatedAt)
                fresh = true
            },
            clock = { now },
            minIntervalMillis = 100L,
        )

        assertTrue(scheduler.scheduleIfStale())
        assertEquals(1, executor.pendingCount())

        executor.runNext()

        assertEquals(listOf(1_000L), refreshTimes)
        assertFalse(scheduler.scheduleIfStale())
    }

    @Test
    fun scheduleIfStaleSuppressesConcurrentAndRateLimitedRefreshes() {
        val executor = QueuedExecutor()
        var now = 1_000L
        var refreshCount = 0
        val scheduler = StatsPrecomputeScheduler(
            background = executor,
            isFresh = { false },
            refresh = { refreshCount++ },
            clock = { now },
            minIntervalMillis = 100L,
        )

        assertTrue(scheduler.scheduleIfStale())
        assertFalse(scheduler.scheduleIfStale())
        assertEquals(1, executor.pendingCount())

        executor.runNext()
        assertEquals(1, refreshCount)
        now += 99L
        assertFalse(scheduler.scheduleIfStale())
        now += 1L
        assertTrue(scheduler.scheduleIfStale())
    }

    @Test
    fun refreshFailureIsReportedAndReleasesTheRunningClaim() {
        val executor = QueuedExecutor()
        val failures = mutableListOf<Throwable>()
        var now = 1_000L
        val scheduler = StatsPrecomputeScheduler(
            background = executor,
            isFresh = { false },
            refresh = { throw IllegalStateException("closed database") },
            onError = failures::add,
            clock = { now },
            minIntervalMillis = 100L,
        )

        assertTrue(scheduler.scheduleIfStale())
        executor.runNext()

        assertEquals(listOf("closed database"), failures.map { it.message })
        now += 100L
        assertTrue(scheduler.scheduleIfStale())
    }

    @Test
    fun freshnessFailureIsReportedWithoutDispatchingRefresh() {
        val executor = QueuedExecutor()
        val failures = mutableListOf<Throwable>()
        val scheduler = StatsPrecomputeScheduler(
            background = executor,
            isFresh = { throw IllegalStateException("closed database") },
            refresh = { throw AssertionError("refresh must not run") },
            onError = failures::add,
        )

        assertFalse(scheduler.scheduleIfStale())
        assertEquals(0, executor.pendingCount())
        assertEquals(listOf("closed database"), failures.map { it.message })
    }

    @Test
    fun loggerFailureDoesNotEscapeOrLeaveTheRunningClaimHeld() {
        val executor = QueuedExecutor()
        var now = 1_000L
        val scheduler = StatsPrecomputeScheduler(
            background = executor,
            isFresh = { false },
            refresh = { throw IllegalStateException("refresh failed") },
            onError = { throw IllegalStateException("logger failed") },
            clock = { now },
            minIntervalMillis = 100L,
        )

        assertTrue(scheduler.scheduleIfStale())
        executor.runNext()

        now += 100L
        assertTrue(scheduler.scheduleIfStale())
    }

    @Test
    fun rejectedDispatchIsReportedAndReleasesTheRunningClaim() {
        val failures = mutableListOf<Throwable>()
        val executor = QueuedExecutor()
        var reject = true
        var now = 1_000L
        val scheduler = StatsPrecomputeScheduler(
            background = Executor { command ->
                if (reject) {
                    throw RejectedExecutionException("activity destroyed")
                }
                executor.execute(command)
            },
            isFresh = { false },
            refresh = { },
            onError = failures::add,
            clock = { now },
            minIntervalMillis = 100L,
        )

        assertFalse(scheduler.scheduleIfStale())
        assertEquals(listOf("activity destroyed"), failures.map { it.message })

        reject = false
        now += 100L
        assertTrue(scheduler.scheduleIfStale())
        assertEquals(1, executor.pendingCount())
    }

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.add(command)
        }

        fun pendingCount(): Int = tasks.size

        fun runNext() {
            tasks.removeFirst().run()
        }
    }
}
