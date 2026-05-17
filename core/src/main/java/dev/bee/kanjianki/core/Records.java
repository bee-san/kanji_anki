package dev.bee.kanjianki.core;

public final class Records {
    private Records() {
    }

    static Object arg(Object[] args, int index, String context) {
        return RecordsBase.arg(args, index, context);
    }
}
