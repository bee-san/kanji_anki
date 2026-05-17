package dev.bee.kanjianki.time;

@FunctionalInterface
public interface AppClock {
    AppClock SYSTEM = System::currentTimeMillis;

    long nowMillis();

    static AppClock systemClock() {
        return SYSTEM;
    }

    static AppClock orSystem(AppClock clock) {
        return clock == null ? SYSTEM : clock;
    }
}
