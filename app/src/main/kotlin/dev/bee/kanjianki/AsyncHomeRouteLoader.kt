package dev.bee.kanjianki

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
) {
    private var generation = 0

    fun cancelPending() {
        generation++
    }

    fun <T> load(
        showLoading: () -> Unit,
        load: () -> T,
        render: (T) -> Unit,
        renderError: (Throwable) -> Unit = { throw it },
        traceLabel: String = "home-route",
        showLoadingAfterMs: Long = 0,
    ) {
        val token = ++generation
        val finished = AtomicBoolean(false)

        if (showLoadingAfterMs <= 0) {
            withAsyncLoadTrace(traceLabel, "show-loading") {
                showLoading()
            }
        }

        background.execute {
            val loadingHandle: LoadingTaskHandle? =
                if (showLoadingAfterMs <= 0) {
                    null
                } else {
                    loadingTaskScheduler.schedule(
                        showLoadingAfterMs,
                        Runnable {
                            if (token != generation || finished.get()) {
                                return@Runnable
                            }
                            postToMain(
                                Runnable {
                                    if (token != generation || finished.get()) {
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

            val result = withAsyncLoadTrace(traceLabel, "load") {
                runCatching(load)
            }

            finished.set(true)
            loadingHandle?.cancel()

            postToMain(
                Runnable {
                    if (token != generation) {
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
