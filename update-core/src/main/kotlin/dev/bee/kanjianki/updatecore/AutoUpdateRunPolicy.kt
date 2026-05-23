package dev.bee.kanjianki.updatecore

object AutoUpdateRunPolicy {
    @JvmStatic
    fun shouldRun(enabled: Boolean, hasPendingUpdate: Boolean): Boolean {
        return enabled && !hasPendingUpdate
    }

    @JvmStatic
    fun workerOutcome(retryable: Boolean): WorkerOutcome {
        return if (retryable) WorkerOutcome.RETRY else WorkerOutcome.SUCCESS
    }

    enum class WorkerOutcome {
        SUCCESS,
        RETRY,
    }
}
