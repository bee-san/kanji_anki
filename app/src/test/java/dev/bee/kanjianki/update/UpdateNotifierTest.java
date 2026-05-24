package dev.bee.kanjianki.update;

import android.app.NotificationManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UpdateNotifierTest {
    @Test
    public void showPendingUpdateStopsBeforeChannelWhenRuntimePermissionIsMissing() {
        Controller controller = new Controller();
        controller.runtimePermission = false;
        controller.notificationsEnabled = true;

        boolean shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ready", controller);

        assertFalse(shown);
        assertFalse(controller.notificationsQueried);
        assertFalse(controller.channelCreated);
        assertFalse(controller.notified);
    }

    @Test
    public void showPendingUpdateStopsBeforeChannelWhenNotificationsAreDisabled() {
        Controller controller = new Controller();
        controller.runtimePermission = true;
        controller.notificationsEnabled = false;

        boolean shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ready", controller);

        assertFalse(shown);
        assertTrue(controller.notificationsQueried);
        assertFalse(controller.channelCreated);
        assertFalse(controller.notified);
    }

    @Test
    public void showPendingUpdateCreatesChannelBeforePostingResolvedNotificationCopy() {
        Controller controller = new Controller();
        controller.runtimePermission = true;
        controller.notificationsEnabled = true;

        boolean shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ignored", controller);

        assertTrue(shown);
        assertTrue(controller.notificationsQueried);
        assertTrue(controller.channelCreated);
        assertTrue(controller.notified);
        assertEquals("channel notify", controller.events.toString());
        assertEquals("Kani update needs confirmation", controller.title);
        assertEquals("Version 0.4.3 is verified. Open Kani to confirm installation and keep the app current.", controller.body);
    }

    @Test
    public void notificationEnabledHelperRequiresManagerAndEnabledState() {
        NotificationManager manager = null;

        assertFalse(UpdateNotifier.notificationsEnabled(manager, ignored -> true));
    }

    private static final class Controller implements UpdateNotifier.NotificationController {
        boolean runtimePermission;
        boolean notificationsEnabled;
        boolean notificationsQueried;
        boolean channelCreated;
        boolean notified;
        String title;
        String body;
        StringBuilder events = new StringBuilder();

        @Override
        public boolean hasRuntimeNotificationPermission() {
            return runtimePermission;
        }

        @Override
        public boolean areNotificationsEnabled() {
            notificationsQueried = true;
            return notificationsEnabled;
        }

        @Override
        public void ensureChannel() {
            channelCreated = true;
            events.append("channel");
        }

        @Override
        public void notifyUpdate(String title, String body) {
            notified = true;
            this.title = title;
            this.body = body;
            events.append(" notify");
        }
    }
}
