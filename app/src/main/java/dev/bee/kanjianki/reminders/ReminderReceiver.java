package dev.bee.kanjianki.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.bee.kanjianki.core.ReminderReceiverPolicy;
import dev.bee.kanjianki.data.LocalStore;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        handle(action, new AndroidReceiverActions(context));
    }

    static void handle(String action, ReceiverActions actions) {
        switch (ReminderReceiverPolicy.commandFor(action, ReminderScheduler.ACTION_DAILY_REMINDER)) {
            case SCHEDULE_FROM_STORED_SETTINGS:
                actions.scheduleFromStoredSettings();
                return;
            case HANDLE_DAILY_REMINDER:
                actions.handleDailyReminder();
                return;
            case NONE:
            default:
                return;
        }
    }

    static void handleDailyReminder(LocalStore.ReminderSettings settings, DailyReminderActions actions) {
        if (!ReminderReceiverPolicy.shouldHandleDailyReminder(settings.enabled)) {
            return;
        }
        actions.showReminderNotification();
        actions.schedule(settings);
    }

    interface ReceiverActions {
        void scheduleFromStoredSettings();

        void handleDailyReminder();
    }

    interface DailyReminderActions {
        void showReminderNotification();

        void schedule(LocalStore.ReminderSettings settings);
    }

    private static final class AndroidReceiverActions implements ReceiverActions {
        private final Context context;

        AndroidReceiverActions(Context context) {
            this.context = context;
        }

        @Override
        public void scheduleFromStoredSettings() {
            ReminderScheduler.schedule(context);
        }

        @Override
        public void handleDailyReminder() {
            try (LocalStore store = new LocalStore(context)) {
                ReminderReceiver.handleDailyReminder(
                        store.reminderSettings(),
                        new ReminderReceiverDailyActions(context)
                );
            }
        }
    }
}
