package dev.bee.kanjianki.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.bee.kanjianki.data.LocalStore;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            ReminderScheduler.schedule(context);
            return;
        }
        if (ReminderScheduler.ACTION_DAILY_REMINDER.equals(action)) {
            LocalStore store = new LocalStore(context);
            try {
                LocalStore.ReminderSettings settings = store.reminderSettings();
                if (!settings.enabled) {
                    return;
                }
                ReminderScheduler.showReminderNotification(context);
                ReminderScheduler.schedule(context, settings);
            } finally {
                store.close();
            }
        }
    }
}
