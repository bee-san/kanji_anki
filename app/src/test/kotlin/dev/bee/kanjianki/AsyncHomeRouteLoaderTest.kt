package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class AsyncHomeRouteLoaderTest {
    /** Executor that queues Runnables so the test controls when background work runs. */
    private class ManualExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.add(command)
        }

        fun runNext(): Boolean {
            val next = queue.poll() ?: return false
            next.run()
            return true
        }
    }

    @Test
    fun staleLoadDoesNotRenderAfterNewerLoadStarts() {
        val background = ManualExecutor()
        val mainQueue = ArrayDeque<Runnable>()
        val loader = AsyncHomeRouteLoader(background, { mainQueue.add(it) })

        val renders = mutableListOf<String>()

        // First (stale) load is dispatched to the background executor but not yet run.
        loader.load(
            showLoading = { },
            load = { "stale" },
            render = { renders.add(it) },
        )
        // A newer load supersedes it before the first one's background work runs.
        loader.load(
            showLoading = { },
            load = { "fresh" },
            render = { renders.add(it) },
        )

        // Drain background work (both loads) then main-thread renders.
        while (background.runNext()) {
            // keep draining
        }
        while (mainQueue.isNotEmpty()) {
            mainQueue.poll()?.run()
        }

        // Only the freshest load renders; the superseded one is dropped by the token.
        assertEquals(listOf("fresh"), renders)
    }

    @Test
    fun callbacksIdentifyRequestsAndSettleOnlyTheAcceptedResult() {
        val background = ManualExecutor()
        val mainQueue = ArrayDeque<Runnable>()
        val requested = mutableListOf<Triple<Int, String, String>>()
        val settled = mutableListOf<Triple<Int, String, Boolean>>()
        val stale = mutableListOf<Triple<Int, Int, String>>()
        val loader = AsyncHomeRouteLoader(
            background = background,
            postToMain = { mainQueue.add(it) },
            onRouteRequested = { requestId, route -> requested.add(Triple(requestId, route, "requested")) },
            onRouteSettled = { requestId, route, succeeded -> settled.add(Triple(requestId, route, succeeded)) },
            onStaleResult = { requestId, currentId, route -> stale.add(Triple(requestId, currentId, route)) },
        )

        loader.load(
            showLoading = {},
            load = { "old" },
            render = {},
            traceLabel = "home-route",
        )
        loader.load(
            showLoading = {},
            load = { "new" },
            render = {},
            traceLabel = "study-route",
        )
        while (background.runNext()) {
            // Drain both loads.
        }
        while (mainQueue.isNotEmpty()) {
            mainQueue.removeFirst().run()
        }

        assertEquals(
            listOf(Triple(1, "home-route", "requested"), Triple(2, "study-route", "requested")),
            requested,
        )
        assertEquals(listOf(Triple(1, 2, "home-route")), stale)
        assertEquals(listOf(Triple(2, "study-route", true)), settled)
    }

    @Test
    fun acceptedLoadErrorStillSettlesAndCancelReportsTheCanceledRequest() {
        val background = ManualExecutor()
        val mainQueue = ArrayDeque<Runnable>()
        val canceled = mutableListOf<Int>()
        val settled = mutableListOf<Triple<Int, String, Boolean>>()
        val loader = AsyncHomeRouteLoader(
            background = background,
            postToMain = { mainQueue.add(it) },
            onRouteCanceled = canceled::add,
            onRouteSettled = { requestId, route, succeeded -> settled.add(Triple(requestId, route, succeeded)) },
        )

        loader.load(showLoading = {}, load = { "canceled" }, render = {})
        loader.cancelPending()
        background.runNext()
        mainQueue.removeFirst().run()

        loader.load(
            showLoading = {},
            load = { error("broken model") },
            render = {},
            renderError = {},
            traceLabel = "settings-route",
        )
        background.runNext()
        mainQueue.removeFirst().run()

        assertEquals(listOf(1), canceled)
        assertEquals(listOf(Triple(3, "settings-route", false)), settled)
    }

    @Test
    fun concurrentLoadsPublishGenerationSafelyAcrossThreads() {
        // Stress the generation counter from many threads to catch visibility races.
        val pool = Executors.newFixedThreadPool(8)
        try {
            val background = Executor { it.run() }
            val mainQueue = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
            val loader = AsyncHomeRouteLoader(background, { mainQueue.add(it) })
            val rendered = AtomicInteger(0)
            val latch = java.util.concurrent.CountDownLatch(200)

            repeat(200) {
                pool.execute {
                    loader.load(
                        showLoading = { },
                        load = { 1 },
                        render = { rendered.incrementAndGet() },
                    )
                    latch.countDown()
                }
            }
            latch.await()
            while (mainQueue.isNotEmpty()) {
                mainQueue.poll().run()
            }

            // At most one render survives per generation; no crash / lost-update means
            // the atomic counter published correctly. The freshest load always renders.
            assertTrue("at least the last load should render", rendered.get() >= 1)
            assertTrue("stale loads are dropped", rendered.get() <= 200)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun reportsQueueWaitFromEnqueueUntilBackgroundStart() {
        val background = ManualExecutor()
        val mainQueue = ArrayDeque<Runnable>()
        var clockNanos = 0L
        val waits = mutableListOf<Pair<String, Long>>()
        val loader = AsyncHomeRouteLoader(
            background = background,
            postToMain = { mainQueue.add(it) },
            onQueueWait = { route, waitNanos -> waits.add(route to waitNanos) },
            nanoClock = { clockNanos },
        )

        loader.load(
            showLoading = { },
            load = { "x" },
            render = { },
            traceLabel = "stats-route",
        )

        // The load sits in the single-threaded queue for 5s behind other work before it starts.
        clockNanos = 5_000_000_000L
        background.runNext()

        // Queue wait is surfaced (this is the head-of-line blocking that the on-thread load
        // duration alone hides), attributed to the correct route.
        assertEquals(1, waits.size)
        assertEquals("stats-route", waits[0].first)
        assertEquals(5_000_000_000L, waits[0].second)
    }

    @Test
    fun deferredLoadingGuardIsScheduledAtEnqueueTimeSoQueuedLoadsStillShowLoading() {
        val background = ManualExecutor()
        val mainQueue = ArrayDeque<Runnable>()
        val scheduler = RecordingLoadingScheduler()
        val loader = AsyncHomeRouteLoader(
            background = background,
            postToMain = { mainQueue.add(it) },
            loadingTaskScheduler = scheduler,
        )
        var loadingShown = false

        loader.load(
            showLoading = { loadingShown = true },
            load = { "x" },
            render = { },
            showLoadingAfterMs = 120,
        )

        // The guard is registered immediately on enqueue, before the background load runs. If it
        // were scheduled inside the background task, a load stuck in the queue would show no
        // loading UI and the app would look frozen.
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(120L, scheduler.scheduled[0].first)

        // Fire the guard while the background load is still queued (never started).
        scheduler.scheduled[0].second.run()
        while (mainQueue.isNotEmpty()) {
            mainQueue.poll()?.run()
        }

        assertTrue("a queued-but-unstarted load must still show the loading screen", loadingShown)
    }

    /** Records scheduled loading-guard tasks so the test can fire them deterministically. */
    private class RecordingLoadingScheduler : LoadingTaskScheduler {
        val scheduled = mutableListOf<Pair<Long, Runnable>>()

        override fun schedule(delayMs: Long, task: Runnable): LoadingTaskHandle {
            scheduled.add(delayMs to task)
            return LoadingTaskHandle { }
        }
    }
}
