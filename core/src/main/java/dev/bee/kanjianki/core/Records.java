package dev.bee.kanjianki.core;

public final class Records extends RecordsSchedulerModels {
    private Records() {
    }

    protected static Object arg(Object[] args, int index, String context) {
        return RecordsBase.arg(args, index, context);
    }
}
