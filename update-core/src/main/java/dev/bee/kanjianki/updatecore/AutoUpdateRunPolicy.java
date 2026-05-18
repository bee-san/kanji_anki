package dev.bee.kanjianki.updatecore;

public final class AutoUpdateRunPolicy {
    private AutoUpdateRunPolicy() {
    }

    public static boolean shouldRun(boolean enabled, boolean hasPendingUpdate) {
        return enabled && !hasPendingUpdate;
    }

    public static WorkerOutcome workerOutcome(boolean retryable) {
        return retryable ? WorkerOutcome.RETRY : WorkerOutcome.SUCCESS;
    }

    public enum WorkerOutcome {
        SUCCESS,
        RETRY
    }
}
