package dev.bee.kanjianki.updatecore

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallStatusPolicyTest {
    @Test
    fun successStatusMapsToFinishedCallback() {
        withLocale(Locale.ENGLISH) {
            val mapped = PackageInstallStatusPolicy.mapInstallStatus(
                PackageInstallStatusPolicy.STATUS_SUCCESS,
                "ignored",
            )

            assertFalse(mapped.pendingUserAction())
            assertTrue(mapped.success())
            assertEquals("Install finished.", mapped.message())
            assertEquals("Install finished.", PackageInstallStatusPolicy.installFinishedMessage())
        }
    }

    @Test
    fun pendingUserActionStatusMapsToConfirmationCallback() {
        withLocale(Locale.ENGLISH) {
            val mapped = PackageInstallStatusPolicy.mapInstallStatus(
                PackageInstallStatusPolicy.STATUS_PENDING_USER_ACTION,
                "ignored",
            )

            assertTrue(mapped.pendingUserAction())
            assertFalse(mapped.success())
            assertEquals("Android needs permission to finish installing.", mapped.message())
            assertEquals(
                "Android needs permission to finish installing.",
                PackageInstallStatusPolicy.installPermissionNeededMessage(),
            )
        }
    }

    @Test
    fun failureMessageIncludesTrimmedInstallerDetails() {
        withLocale(Locale.ENGLISH) {
            val mapped = PackageInstallStatusPolicy.mapInstallStatus(
                12,
                "  blocked by policy  ",
            )

            assertFalse(mapped.pendingUserAction())
            assertFalse(mapped.success())
            assertEquals("Install failed: blocked by policy.", mapped.message())
            assertEquals(
                "Install failed: blocked by policy.",
                PackageInstallStatusPolicy.installFailedMessage("  blocked by policy  "),
            )
        }
    }

    @Test
    fun failureMessageHandlesMissingInstallerDetails() {
        withLocale(Locale.ENGLISH) {
            val nullMessage = PackageInstallStatusPolicy.mapInstallStatus(12, null)
            val blankMessage = PackageInstallStatusPolicy.mapInstallStatus(13, "  ")

            assertEquals("Install failed.", nullMessage.message())
            assertEquals("Install failed.", blankMessage.message())
        }
    }

    @Test
    fun japaneseLocaleTranslatesInstallStatusMessages() {
        withLocale(Locale.JAPANESE) {
            val success = PackageInstallStatusPolicy.mapInstallStatus(
                PackageInstallStatusPolicy.STATUS_SUCCESS,
                "ignored",
            )
            val pending = PackageInstallStatusPolicy.mapInstallStatus(
                PackageInstallStatusPolicy.STATUS_PENDING_USER_ACTION,
                "ignored",
            )
            val failed = PackageInstallStatusPolicy.mapInstallStatus(12, "  blocked by policy  ")
            val failedWithoutDetails = PackageInstallStatusPolicy.mapInstallStatus(13, "  ")

            assertTrue(success.success())
            assertEquals("インストールが完了しました。", success.message())
            assertTrue(pending.pendingUserAction())
            assertEquals("インストールを完了するにはAndroidの許可が必要です。", pending.message())
            assertEquals("インストールに失敗しました: blocked by policy。", failed.message())
            assertEquals("インストールに失敗しました。", failedWithoutDetails.message())
            assertEquals("インストールが完了しました。", PackageInstallStatusPolicy.installFinishedMessage())
            assertEquals(
                "インストールを完了するにはAndroidの許可が必要です。",
                PackageInstallStatusPolicy.installPermissionNeededMessage(),
            )
            assertEquals(
                "インストールに失敗しました: blocked by policy。",
                PackageInstallStatusPolicy.installFailedMessage("  blocked by policy  "),
            )
        }
    }

    @Test
    fun sourceNameDefaultsMissingOrUnknownSourceToAutomatic() {
        assertEquals(PackageInstallStatusPolicy.SOURCE_AUTOMATIC, PackageInstallStatusPolicy.sourceNameOrDefault(null))
        assertEquals(PackageInstallStatusPolicy.SOURCE_AUTOMATIC, PackageInstallStatusPolicy.sourceNameOrDefault("not-real"))
        assertEquals(PackageInstallStatusPolicy.SOURCE_CACHED, PackageInstallStatusPolicy.sourceNameOrDefault("CACHED"))
    }

    @Test
    fun installConfirmationLaunchesForManualAndCachedSourcesOnly() {
        assertTrue(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation("MANUAL"))
        assertTrue(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation("CACHED"))
        assertFalse(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation("AUTOMATIC"))
        assertFalse(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation(null))
        assertFalse(PackageInstallStatusPolicy.shouldLaunchInstallConfirmation("not-real"))
    }

    @Test
    fun installerCanSkipExtraUserActionOnlyWhenAppAndRuntimeSupportIt() {
        assertFalse(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(28, 31))
        assertTrue(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(29, 31))
        assertFalse(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(29, 33))
        assertTrue(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(30, 33))
        assertFalse(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(30, 34))
        assertTrue(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(31, 34))
        assertFalse(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(32, 35))
        assertTrue(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(33, 35))
        assertFalse(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(33, 36))
        assertTrue(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(34, 36))
        assertFalse(PackageInstallStatusPolicy.shouldAllowInstallerWithoutExtraUserAction(31, 30))
    }

    @Test
    fun installerUserActionMinimumTargetSdkFollowsRuntimeRelease() {
        assertEquals(Int.MAX_VALUE, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(30))
        assertEquals(29, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(31))
        assertEquals(29, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(32))
        assertEquals(30, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(33))
        assertEquals(31, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(34))
        assertEquals(33, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(35))
        assertEquals(34, PackageInstallStatusPolicy.minimumTargetSdkForInstallerWithoutExtraUserAction(36))
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
