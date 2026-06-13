package dev.bee.kanjianki

internal class ManualLoadingTaskScheduler : LoadingTaskScheduler {
    private var pendingTask: Runnable? = null
    private var cancelled = false

    override fun schedule(delayMs: Long, task: Runnable): LoadingTaskHandle {
        pendingTask = task
        cancelled = false
        return LoadingTaskHandle { cancelled = true }
    }

    fun runPendingTask() {
        val task = pendingTask ?: return
        pendingTask = null
        if (!cancelled) {
            task.run()
        }
    }

    fun hasPendingTask(): Boolean = pendingTask != null && !cancelled
}
