package dev.bee.kanjianki.reminders;

import android.content.Context;

import dev.bee.kanjianki.data.LocalStore;

final class ReminderReceiverDailyActions implements ReminderReceiver.DailyReminderActions {
    private final Context context;

    ReminderReceiverDailyActions(Context context) {
        this.context = context;
    }

    @Override
    public void showReminderNotification() {
        ReminderScheduler.showReminderNotification(context);
    }

    @Override
    public void schedule(LocalStore.ReminderSettings settings) {
        ReminderScheduler.schedule(context, settings);
    }
}
