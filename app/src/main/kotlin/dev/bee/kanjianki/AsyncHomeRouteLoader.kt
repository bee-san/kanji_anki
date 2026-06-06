package dev.bee.kanjianki

import java.util.concurrent.Executor
import java.util.concurrent.Executors
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
        showLoadingAfterMs: Long = 0,
    ) {
        val token = ++generation
        val finished = AtomicBoolean(false)
        if (showLoadingAfterMs <= 0) {
            showLoading()
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
                            showLoading()
                        }
                    )
                },
                showLoadingAfterMs,
                TimeUnit.MILLISECONDS,
            )
        }

        background.execute {
            val result = runCatching(load)
            finished.set(true)
            postToMain(
                Runnable {
                    if (token != generation) {
                        return@Runnable
                    }
                    result.fold(render, renderError)
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
