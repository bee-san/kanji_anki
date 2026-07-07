package dev.bee.kanjianki

import android.os.SystemClock
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal fun interface LoadingTaskHandle {
    fun cancel()
}

internal fun interface LoadingTaskScheduler {
    fun schedule(delayMs: Long, task: Runnable): LoadingTaskHandle
}

internal class AsyncHomeRouteLoader(
    private val background: Executor,
    private val postToMain: ((Runnable) -> Unit),
    private val loadingTaskScheduler: LoadingTaskScheduler = RealLoadingTaskScheduler,
    private val onQueueWait: (route: String, waitNanos: Long) -> Unit = ::logAsyncLoadQueueWait,
    private val nanoClock: () -> Long = ::defaultLoaderNanoTime,
) {
    // Mutated on the main thread (cancelPending / load) and read on background and
    // loading-guard threads, so it must be atomically published.
    private val generation = AtomicInteger(0)

    fun cancelPending() {
        generation.incrementAndGet()
    }

    fun <T> load(
        showLoading: () -> Unit,
        load: () -> T,
        render: (T) -> Unit,
        renderError: (Throwable) -> Unit = { throw it },
        traceLabel: String = "home-route",
        showLoadingAfterMs: Long = 0,
    ) {
        val token = generation.incrementAndGet()
        val finished = AtomicBoolean(false)

        if (showLoadingAfterMs <= 0) {
            withAsyncLoadTrace(traceLabel, "show-loading") {
                showLoading()
            }
        }

        // Schedule the deferred loading-screen guard at ENQUEUE time (caller thread), not inside
        // the background task. The io executor is single-threaded, so a load can sit in the queue
        // for seconds behind other work; if the guard were scheduled inside background.execute it
        // would not start counting until the load dequeues, leaving the UI frozen with no loading
        // screen. Scheduling it here means a queued-but-not-yet-started load still shows loading.
        val loadingHandle: LoadingTaskHandle? =
            if (showLoadingAfterMs <= 0) {
                null
            } else {
                loadingTaskScheduler.schedule(
                    showLoadingAfterMs,
                    Runnable {
                        if (token != generation.get() || finished.get()) {
                            return@Runnable
                        }
                        postToMain(
                            Runnable {
                                if (token != generation.get() || finished.get()) {
                                    return@Runnable
                                }
                                withAsyncLoadTrace(traceLabel, "show-loading") {
                                    showLoading()
                                }
                            }
                        )
                    },
                )
            }

        val enqueuedAtNanos = nanoClock()

        background.execute {
            // Surface how long this load waited in the (single-threaded) executor queue before it
            // started running. withAsyncLoadTrace only measures on-thread execution, so without
            // this the debug log hides head-of-line blocking: each load looks fast even when the
            // user waited seconds for it to even start.
            runCatching { onQueueWait(traceLabel, nanoClock() - enqueuedAtNanos) }

            val result = withAsyncLoadTrace(traceLabel, "load") {
                runCatching(load)
            }

            finished.set(true)
            loadingHandle?.cancel()

            postToMain(
                Runnable {
                    if (token != generation.get()) {
                        return@Runnable
                    }
                    withAsyncLoadTrace(traceLabel, "render") {
                        result.fold(render, renderError)
                    }
                }
            )
        }
    }
}

/** Monotonic clock for queue-wait measurement; falls back off-Android for plain JVM tests. */
private fun defaultLoaderNanoTime(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}

private val loaderLoadingScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "kani-loader-loading-guard").apply {
        isDaemon = true
    }
}

private object RealLoadingTaskScheduler : LoadingTaskScheduler {
    override fun schedule(delayMs: Long, task: Runnable): LoadingTaskHandle {
        val future: ScheduledFuture<*> = loaderLoadingScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        return LoadingTaskHandle { future.cancel(false) }
    }
}
