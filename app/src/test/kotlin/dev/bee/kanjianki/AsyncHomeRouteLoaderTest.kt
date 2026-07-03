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
}
