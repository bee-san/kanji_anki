package dev.bee.kanjianki

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class AsyncHomeRouteLoader(
    private val background: Executor,
    private val postToMain: ((Runnable) -> Unit),
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
            val loadingHandle: ScheduledFuture<*>? =
                if (showLoadingAfterMs <= 0) {
                    null
                } else {
                    loaderLoadingScheduler.schedule(
                        {
                            if (token != generation || finished.get()) {
                                return@schedule
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
                        showLoadingAfterMs,
                        TimeUnit.MILLISECONDS,
                    )
                }

            val result = withAsyncLoadTrace(traceLabel, "load") {
                runCatching(load)
            }

            finished.set(true)
            loadingHandle?.cancel(false)

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

    private companion object {
        val loaderLoadingScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "kani-loader-loading-guard").apply {
                isDaemon = true
            }
        }
    }
}
