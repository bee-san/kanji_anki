package dev.bee.kanjianki.sync;

import android.content.Context;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AutoSyncSchedulePolicy;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.time.AppClock;

public final class AutoSyncRunner {
    private final Context context;
    private final LocalStore store;
    private final CollectionGateway gateway;
    private final AppClock clock;

    public AutoSyncRunner(Context context, LocalStore store, CollectionGateway gateway) {
        this(context, store, gateway, AppClock.systemClock());
    }

    public AutoSyncRunner(Context context, LocalStore store, CollectionGateway gateway, AppClock clock) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.gateway = gateway;
        this.clock = AppClock.orSystem(clock);
    }

    public Result run() {
        return run(clock.nowMillis(), clock);
    }

    Result run(long now) {
        return run(now, () -> now);
    }

    private Result run(long now, AppClock syncClock) {
        LocalStore.AutoSyncSettings settings = store.autoSyncSettings();
        if (!settings.enabled) {
            return Result.skipped("Daily Anki sync is off.");
        }
        if (store.hasSuccessfulSyncSince(AutoSyncSchedulePolicy.localDayStart(now))) {
            return Result.skipped("AnkiDroid already synced today.");
        }
        if (gateway instanceof AnkiDroidGateway ankiDroidGateway) {
            AnkiDroidGateway.ProviderStatus provider = ankiDroidGateway.status();
            if (!provider.canSync) {
                store.recordAutoSyncAttempt(now, false);
                store.saveFailedSync(now, now, "config_error", "permanent", provider.message);
                return Result.failed(provider.message);
            }
        }

        ManualSyncEngine.SyncResult sync = new ManualSyncEngine(
                context,
                store,
                gateway,
                SyncSettings.fromStore(store),
                SyncProgress.NONE,
                syncClock
        ).run();
        if (!sync.skipped) {
            store.recordAutoSyncAttempt(now, sync.success);
        }
        if (sync.success) {
            return Result.success(sync.message);
        }
        if (sync.skipped) {
            return Result.skipped(sync.message);
        }
        return Result.failed(sync.message);
    }

    public static final class Result {
        public final boolean ran;
        public final boolean success;
        public final String message;

        private Result(boolean ran, boolean success, String message) {
            this.ran = ran;
            this.success = success;
            this.message = message;
        }

        private static Result success(String message) {
            return new Result(true, true, message);
        }

        private static Result failed(String message) {
            return new Result(true, false, message);
        }

        private static Result skipped(String message) {
            return new Result(false, false, message);
        }
    }
}
