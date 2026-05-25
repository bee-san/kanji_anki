package dev.bee.kanjianki

import java.util.concurrent.Executor

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
    ) {
        val token = ++generation
        showLoading()
        background.execute {
            val result = runCatching(load)
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
}
