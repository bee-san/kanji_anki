package dev.bee.kanjianki.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.bee.kanjianki.backup.DatabaseBackupScheduler;
import dev.bee.kanjianki.core.ReminderReceiverPolicy;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.update.AutoUpdateScheduler;

public final class BootReminderReceiver extends BroadcastReceiver {
    private static final ActionReader<Intent> INTENT_ACTION_READER = new IntentActionReader();
    private final RescheduleActions actions;

    public BootReminderReceiver() {
        this(new AndroidRescheduleActions());
    }

    BootReminderReceiver(RescheduleActions actions) {
        this.actions = actions;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!shouldReschedule(action)) {
            return;
        }
        handle(context, action, actions);
    }

    static void handle(Context context, Intent intent, RescheduleActions actions) {
        handle(context, actionOrEmpty(intent, INTENT_ACTION_READER), actions);
    }

    static void handle(Context context, String action, RescheduleActions actions) {
        if (shouldReschedule(action)) {
            actions.schedule(context);
        }
    }

    static <T> String actionOrEmpty(T source, ActionReader<T> reader) {
        if (source == null) {
            return "";
        }
        return reader.read(source);
    }

    static boolean shouldReschedule(String action) {
        return ReminderReceiverPolicy.shouldReschedule(action);
    }

    interface ActionReader<T> {
        String read(T source);
    }

    interface RescheduleActions {
        void schedule(Context context);
    }

    private static final class IntentActionReader implements ActionReader<Intent> {
        @Override
        public String read(Intent source) {
            return source.getAction();
        }
    }

    private static final class AndroidRescheduleActions implements RescheduleActions {
        @Override
        public void schedule(Context context) {
            ReminderScheduler.schedule(context);
            AutoSyncScheduler.schedule(context);
            AutoUpdateScheduler.schedule(context);
            DatabaseBackupScheduler.schedule(context);
        }
    }
}
