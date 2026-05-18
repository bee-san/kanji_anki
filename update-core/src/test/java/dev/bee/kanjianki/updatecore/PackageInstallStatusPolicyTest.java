package dev.bee.kanjianki.updatecore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackageInstallStatusPolicyTest {
    @Test
    public void successStatusMapsToFinishedCallback() {
        PackageInstallStatusPolicy.InstallCallback mapped = PackageInstallStatusPolicy.mapInstallStatus(
                PackageInstallStatusPolicy.STATUS_SUCCESS,
                "ignored"
        );

        assertFalse(mapped.pendingUserAction());
        assertFalse(mapped.pendingUserAction);
        assertTrue(mapped.success());
        assertTrue(mapped.success);
        assertEquals("Install finished.", mapped.message());
        assertEquals("Install finished.", mapped.message);
    }

    @Test
    public void pendingUserActionStatusMapsToConfirmationCallback() {
        PackageInstallStatusPolicy.InstallCallback mapped = PackageInstallStatusPolicy.mapInstallStatus(
                PackageInstallStatusPolicy.STATUS_PENDING_USER_ACTION,
                "ignored"
        );

        assertTrue(mapped.pendingUserAction());
        assertFalse(mapped.success());
        assertEquals("Android needs confirmation to finish installing.", mapped.message());
    }

    @Test
    public void failureMessageIncludesTrimmedInstallerDetails() {
        PackageInstallStatusPolicy.InstallCallback mapped = PackageInstallStatusPolicy.mapInstallStatus(
                12,
                "  blocked by policy  "
        );

        assertFalse(mapped.pendingUserAction());
        assertFalse(mapped.success());
        assertEquals("Install failed: blocked by policy.", mapped.message());
    }

    @Test
    public void failureMessageHandlesMissingInstallerDetails() {
        PackageInstallStatusPolicy.InstallCallback nullMessage = PackageInstallStatusPolicy.mapInstallStatus(12, null);
        PackageInstallStatusPolicy.InstallCallback blankMessage = PackageInstallStatusPolicy.mapInstallStatus(13, "  ");

        assertEquals("Install failed.", nullMessage.message());
        assertEquals("Install failed.", blankMessage.message());
    }
}
