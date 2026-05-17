package dev.bee.kanjianki.reminders;

import android.content.Context;

import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.Records;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ReminderSchedulerTest {
    @Test
    public void nextTriggerUsesTodayWhenReminderTimeIsStillAhead() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = utc(2026, Calendar.MAY, 15, 7, 15);

            long trigger = ReminderScheduler.nextTriggerMillis(new LocalStore.ReminderSettings(true, 8, 30), now);

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), trigger);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void nextTriggerMovesToTomorrowWhenReminderTimeHasPassedOrMatchesNow() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = utc(2026, Calendar.MAY, 15, 8, 30);

            assertEquals(
                    utc(2026, Calendar.MAY, 16, 8, 30),
                    ReminderScheduler.nextTriggerMillis(new LocalStore.ReminderSettings(true, 8, 30), now)
            );
            assertEquals(
                    utc(2026, Calendar.MAY, 16, 7, 0),
                    ReminderScheduler.nextTriggerMillis(new LocalStore.ReminderSettings(true, 7, 0), now)
            );
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void scheduleDelegatesToServicesAndCancelsWhenDisabled() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            FakeReminderServices services = new FakeReminderServices();
            long now = utc(2026, Calendar.MAY, 15, 7, 15);

            ReminderScheduler.schedule(null, services, () -> now);
            assertEquals(1, services.cancelCount);

            ReminderScheduler.schedule(new LocalStore.ReminderSettings(false, 8, 30), services, () -> now);
            assertEquals(2, services.cancelCount);

            ReminderScheduler.schedule(new LocalStore.ReminderSettings(true, 8, 30), services, () -> now);

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), services.scheduledAtMillis);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void notificationsAllowedAppliesPlatformFallbackGates() {
        FakeReminderServices services = new FakeReminderServices();
        services.runtimePermission = false;
        assertFalse(ReminderScheduler.notificationsAllowed(services));

        services.runtimePermission = true;
        services.notificationsEnabled = false;
        assertFalse(ReminderScheduler.notificationsAllowed(services));

        services.notificationsEnabled = true;
        services.channelImportance = android.app.NotificationManager.IMPORTANCE_NONE;
        assertFalse(ReminderScheduler.notificationsAllowed(services));

        services.channelImportance = null;
        assertTrue(ReminderScheduler.notificationsAllowed(services));

        services.channelImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT;
        assertTrue(ReminderScheduler.notificationsAllowed(services));

        services.ensureNotificationChannel();
        assertEquals(1, services.ensureCount);
    }

    @Test
    public void showReminderNotificationStopsBeforeBuildingWhenNotificationsAreBlocked() {
        FakeReminderServices services = new FakeReminderServices();
        services.runtimePermission = false;

        ReminderScheduler.showReminderNotification(null, services);

        assertEquals(0, services.ensureCount);
    }

    @Test
    public void notificationStatusHelperHandlesMissingDisabledAndEnabledStates() {
        assertFalse(ReminderScheduler.areNotificationsEnabled(null));
        assertFalse(ReminderScheduler.areNotificationsEnabled(() -> false));
        assertTrue(ReminderScheduler.areNotificationsEnabled(() -> true));
    }

    @Test
    public void bootReceiverReschedulesOnlyForSystemRecoveryActions() {
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_BOOT_COMPLETED));
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_MY_PACKAGE_REPLACED));
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_TIME_CHANGED));
        assertTrue(BootReminderReceiver.shouldReschedule(android.content.Intent.ACTION_TIMEZONE_CHANGED));

        assertFalse(BootReminderReceiver.shouldReschedule(null));
        assertFalse(BootReminderReceiver.shouldReschedule("dev.bee.kanjianki.OTHER"));
    }

    @Test
    public void bootReceiverHandleIgnoresNullIntent() {
        FakeRescheduleActions actions = new FakeRescheduleActions();

        BootReminderReceiver.handle(null, (android.content.Intent) null, actions);

        assertEquals(0, actions.scheduleCount);
    }

    @Test
    public void bootReceiverActionOrEmptyReadsOnlyPresentSources() {
        assertEquals("", BootReminderReceiver.<String>actionOrEmpty(null, source -> {
            throw new AssertionError("Null sources must not be read");
        }));
        assertEquals(
                android.content.Intent.ACTION_TIME_CHANGED,
                BootReminderReceiver.actionOrEmpty("present", source -> android.content.Intent.ACTION_TIME_CHANGED));
    }

    @Test
    public void bootReceiverHandleSchedulesForBootIntent() {
        FakeRescheduleActions actions = new FakeRescheduleActions();

        BootReminderReceiver.handle(null, android.content.Intent.ACTION_BOOT_COMPLETED, actions);

        assertEquals(1, actions.scheduleCount);
    }

    @Test
    public void reminderReceiverDispatchesBootDailyAndIgnoresOtherActions() {
        FakeReceiverActions actions = new FakeReceiverActions();

        ReminderReceiver.handle(android.content.Intent.ACTION_BOOT_COMPLETED, actions);
        ReminderReceiver.handle(ReminderScheduler.ACTION_DAILY_REMINDER, actions);
        ReminderReceiver.handle("dev.bee.kanjianki.OTHER", actions);

        assertEquals("boot,daily", actions.events.joined);
    }

    @Test
    public void dailyReminderShowsAndReschedulesOnlyWhenEnabled() {
        FakeDailyReminderActions actions = new FakeDailyReminderActions();
        LocalStore.ReminderSettings disabled = new LocalStore.ReminderSettings(false, 8, 30);
        LocalStore.ReminderSettings enabled = new LocalStore.ReminderSettings(true, 9, 45);

        ReminderReceiver.handleDailyReminder(disabled, actions);
        assertEquals("", actions.events.joined);

        ReminderReceiver.handleDailyReminder(enabled, actions);

        assertEquals("show,schedule", actions.events.joined);
        assertSame(enabled, actions.scheduledSettings);
    }

    @Test
    public void reminderCopyAsksForSyncBeforeAnyActiveKanjiExist() {
        ReminderScheduler.ReminderCopy copy = ReminderScheduler.reminderCopy(
                Collections.emptyList(),
                Collections.emptyList(),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE,
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
                utc(2026, Calendar.MAY, 15, 8, 0)
        );

        assertEquals("Sync Kani", copy.title);
        assertEquals("Sync AnkiDroid to find the kanji your reviews keep exposing.", copy.message);
    }

    @Test
    public void reminderCopyPlansActiveRowsBeforeFormattingMessage() {
        long now = utc(2026, Calendar.MAY, 15, 8, 0);

        ReminderScheduler.ReminderCopy copy = ReminderScheduler.reminderCopy(
                Collections.singletonList(row("裂", 80)),
                Collections.singletonList(new Records.StudyItem("裂", "review", now - 1L, 1.0, 5.0, 2, 0, 2, 1, null, now)),
                new Records.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                0,
                Collections.emptySet(),
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE,
                1,
                now
        );

        assertEquals("Kani focus is ready", copy.title);
        assertEquals("1 focus kanji is left today. Draw one now.", copy.message);
    }

    @Test
    public void nextTriggerWrapperUsesInjectedClock() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            long now = utc(2026, Calendar.MAY, 15, 7, 15);

            long trigger = ReminderScheduler.nextTriggerMillis(new LocalStore.ReminderSettings(true, 8, 30), () -> now);

            assertEquals(utc(2026, Calendar.MAY, 15, 8, 30), trigger);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void reminderCopyFormatsFocusRecoveryAndRestMessages() {
        ReminderScheduler.ReminderCopy oneFocus = ReminderScheduler.reminderCopyFor(1, 4);
        ReminderScheduler.ReminderCopy manyFocus = ReminderScheduler.reminderCopyFor(3, 4);
        ReminderScheduler.ReminderCopy oneDue = ReminderScheduler.reminderCopyFor(0, 1);
        ReminderScheduler.ReminderCopy manyDue = ReminderScheduler.reminderCopyFor(0, 2);
        ReminderScheduler.ReminderCopy rest = ReminderScheduler.reminderCopyFor(0, 0);

        assertEquals("Kani focus is ready", oneFocus.title);
        assertEquals("1 focus kanji is left today. Draw one now.", oneFocus.message);
        assertEquals("3 focus kanji are left today. Draw one now.", manyFocus.message);
        assertEquals("Kani recovery is due", oneDue.title);
        assertEquals("1 problem kanji is ready. Draw one now.", oneDue.message);
        assertEquals("2 problem kanji are ready. Draw one now.", manyDue.message);
        assertEquals("Check Kani", rest.title);
        assertEquals("Your queue can rest today. Open Kani if you want an extra problem kanji rep.", rest.message);
    }

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(year, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static Records.DashboardRow row(String kanji, int score) {
        return new Records.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                score,
                "reason",
                "reason text",
                1,
                score > 15 ? 1 : 0,
                0,
                Collections.emptyList()
        );
    }

    private static final class FakeReminderServices implements ReminderScheduler.ReminderServices {
        int cancelCount;
        long scheduledAtMillis = -1L;
        boolean runtimePermission = true;
        boolean notificationsEnabled = true;
        Integer channelImportance = null;
        int ensureCount;

        @Override
        public void scheduleAlarm(long triggerAtMillis) {
            scheduledAtMillis = triggerAtMillis;
        }

        @Override
        public void cancelAlarm() {
            cancelCount++;
        }

        @Override
        public boolean hasRuntimeNotificationPermission() {
            return runtimePermission;
        }

        @Override
        public boolean areNotificationsEnabled() {
            return notificationsEnabled;
        }

        @Override
        public Integer reminderChannelImportance() {
            return channelImportance;
        }

        @Override
        public void ensureNotificationChannel() {
            ensureCount++;
        }
    }

    private static final class FakeReceiverActions implements ReminderReceiver.ReceiverActions {
        final Events events = new Events();

        @Override
        public void scheduleFromStoredSettings() {
            events.record("boot");
        }

        @Override
        public void handleDailyReminder() {
            events.record("daily");
        }
    }

    private static final class FakeRescheduleActions implements BootReminderReceiver.RescheduleActions {
        int scheduleCount;

        @Override
        public void schedule(Context context) {
            scheduleCount++;
        }
    }

    private static final class FakeDailyReminderActions implements ReminderReceiver.DailyReminderActions {
        final Events events = new Events();
        LocalStore.ReminderSettings scheduledSettings;

        @Override
        public void showReminderNotification() {
            events.record("show");
        }

        @Override
        public void schedule(LocalStore.ReminderSettings settings) {
            scheduledSettings = settings;
            events.record("schedule");
        }
    }

    private static final class Events {
        String joined = "";

        void record(String event) {
            if (!joined.isEmpty()) {
                joined += ",";
            }
            joined += event;
        }
    }
}
