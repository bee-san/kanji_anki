package dev.bee.kanjianki.reminders;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class ReminderReceiverInstrumentedTest {
    private static final int REMINDER_REQUEST_CODE = 2701;
    private static final long FUTURE_ALARM_AT_MILLIS = 4_102_444_800_000L;

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @After
    public void tearDown() {
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void receiverIgnoresNullAndDisabledDailyReminder() {
        ReminderReceiver receiver = new ReminderReceiver();
        receiver.onReceive(context, null);
        try (LocalStore store = new LocalStore(context)) {
            store.saveReminderSettings(new LocalStore.ReminderSettings(false, 8, 30));
        }

        receiver.onReceive(context, new Intent(ReminderScheduler.ACTION_DAILY_REMINDER));

        try (LocalStore store = new LocalStore(context)) {
            assertFalse(store.reminderSettings().enabled);
        }
    }

    @Test
    public void receiverKeepsEnabledReminderAfterDailyNotificationAttempt() {
        try (LocalStore store = new LocalStore(context)) {
            store.saveReminderSettings(new LocalStore.ReminderSettings(true, 9, 45));
        }

        new ReminderReceiver().onReceive(context, new Intent(ReminderScheduler.ACTION_DAILY_REMINDER));

        try (LocalStore store = new LocalStore(context)) {
            LocalStore.ReminderSettings settings = store.reminderSettings();
            assertEquals(9, settings.hour);
            assertEquals(45, settings.minute);
        }
    }

    @Test
    public void receiverSchedulesFromStoredSettingsOnBoot() {
        clearPendingReminderIntent();
        try (LocalStore store = new LocalStore(context)) {
            store.saveReminderSettings(new LocalStore.ReminderSettings(true, 10, 15));
        }

        new ReminderReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));

        try (LocalStore store = new LocalStore(context)) {
            LocalStore.ReminderSettings settings = store.reminderSettings();
            assertEquals(10, settings.hour);
            assertEquals(15, settings.minute);
        }
        clearPendingReminderIntent();
    }

    @Test
    public void schedulerNotificationGateReturnsFalseWhenRuntimePermissionIsMissing() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            boolean expected = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            assertEquals(expected, ReminderScheduler.hasRuntimeNotificationPermission(context));
        }
    }

    @Test
    public void schedulerPermissionHelperCoversSdkBranches() {
        Context preAndroidThirteen = new ContextWrapper(context) {
            @Override
            public int checkSelfPermission(String permission) {
                throw new AssertionError("pre-Android 13 must not check POST_NOTIFICATIONS");
            }
        };

        assertTrue(ReminderScheduler.hasRuntimeNotificationPermission(preAndroidThirteen, 32));
        assertFalse(ReminderScheduler.hasRuntimeNotificationPermission(permissionContext(PackageManager.PERMISSION_DENIED), 33));
        assertTrue(ReminderScheduler.hasRuntimeNotificationPermission(permissionContext(PackageManager.PERMISSION_GRANTED), 33));
    }

    @Test
    public void showReminderNotificationStopsWhenNotificationManagerIsMissing() {
        FakeReminderServices services = new FakeReminderServices();

        ReminderScheduler.showReminderNotification(new NullSystemServiceContext(context), services);

        assertEquals(1, services.ensureCount);
    }

    @Test
    public void showReminderNotificationSkipsChannelWhenNotificationsAreBlocked() {
        FakeReminderServices services = new FakeReminderServices();
        services.runtimePermission = false;

        ReminderScheduler.showReminderNotification(new NullSystemServiceContext(context), services);

        assertEquals(0, services.ensureCount);
    }

    @Test
    public void bootReceiverHandleReadsNonNullIntentAction() {
        FakeRescheduleActions actions = new FakeRescheduleActions();

        BootReminderReceiver.handle(context, new Intent(Intent.ACTION_TIME_CHANGED), actions);

        assertEquals(1, actions.scheduleCount);
    }

    @Test
    public void bootReceiverHandleIgnoresNullIntent() {
        FakeRescheduleActions actions = new FakeRescheduleActions();

        BootReminderReceiver.handle(context, (Intent) null, actions);

        assertEquals(0, actions.scheduleCount);
    }

    @Test
    public void bootReceiverOnReceiveIgnoresSpoofedActionsBeforeDelegating() {
        FakeRescheduleActions actions = new FakeRescheduleActions();
        BootReminderReceiver receiver = new BootReminderReceiver(actions);

        receiver.onReceive(context, null);
        receiver.onReceive(context, new Intent("dev.bee.kanjianki.SPOOFED_BOOT"));

        assertEquals(0, actions.scheduleCount);

        receiver.onReceive(context, new Intent(Intent.ACTION_TIMEZONE_CHANGED));

        assertEquals(1, actions.scheduleCount);
    }

    @Test
    public void androidReminderServicesHandlesMissingAndPresentSystemManagers() {
        ReminderScheduler.ReminderServices missingServices =
                ReminderScheduler.androidReminderServices(new NullSystemServiceContext(context));

        missingServices.scheduleAlarm(FUTURE_ALARM_AT_MILLIS);
        missingServices.cancelAlarm();
        assertFalse(missingServices.areNotificationsEnabled());
        assertEquals(Integer.valueOf(NotificationManager.IMPORTANCE_NONE), missingServices.reminderChannelImportance());
        missingServices.ensureNotificationChannel();

        clearPendingReminderIntent();
        ReminderScheduler.ReminderServices realServices = ReminderScheduler.androidReminderServices(context);
        realServices.cancelAlarm();
        realServices.scheduleAlarm(FUTURE_ALARM_AT_MILLIS);
        realServices.cancelAlarm();
        realServices.areNotificationsEnabled();
        realServices.ensureNotificationChannel();
        realServices.reminderChannelImportance();
        clearPendingReminderIntent();
    }

    private Context permissionContext(int result) {
        return new ContextWrapper(context) {
            @Override
            public int checkSelfPermission(String permission) {
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, permission);
                return result;
            }
        };
    }

    private void clearPendingReminderIntent() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                new Intent(context, ReminderReceiver.class).setAction(ReminderScheduler.ACTION_DAILY_REMINDER),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            pendingIntent.cancel();
        }
    }

    private static final class NullSystemServiceContext extends ContextWrapper {
        NullSystemServiceContext(Context base) {
            super(base);
        }

        @Override
        public Object getSystemService(String name) {
            return null;
        }
    }

    private static final class FakeReminderServices implements ReminderScheduler.ReminderServices {
        boolean runtimePermission = true;
        boolean notificationsEnabled = true;
        Integer channelImportance = null;
        int ensureCount;

        @Override
        public void scheduleAlarm(long triggerAtMillis) {
            // This fake only tracks notification behavior.
        }

        @Override
        public void cancelAlarm() {
            // This fake only tracks notification behavior.
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

    private static final class FakeRescheduleActions implements BootReminderReceiver.RescheduleActions {
        int scheduleCount;

        @Override
        public void schedule(Context context) {
            scheduleCount++;
        }
    }
}
