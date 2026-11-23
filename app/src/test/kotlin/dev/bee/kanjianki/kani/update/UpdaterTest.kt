package dev.bee.kanjianki.kani.update

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdaterTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")

    @Test
    fun installsSilentlyOnlyWhenKaniMayInstallAndInstalledItself() {
        val pm = shadowOf(context.packageManager)
        val updater = Updater(context)
        pm.setInstallSourceInfo(context.packageName, context.packageName, context.packageName)
        pm.setCanRequestPackageInstalls(false)
        assertFalse(updater.installsSilently())

        pm.setCanRequestPackageInstalls(true)
        assertTrue(updater.installsSilently())

        // Installed from a browser: Android asks for the first update, so Kani leaves it to a tap.
        pm.setInstallSourceInfo(context.packageName, "com.android.chrome", "com.google.android.packageinstaller")
        assertFalse(updater.installsSilently())
    }

    @Test
    fun anAutomaticInstallNeverAsksAndIsDropped() {
        val installer = context.packageManager.packageInstaller
        val id = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        assertNotNull(installer.getSessionInfo(id))

        InstallReceiver().onReceive(
            context,
            status(PackageInstaller.STATUS_PENDING_USER_ACTION, auto = true)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
                .putExtra(Intent.EXTRA_INTENT, confirm),
        )

        assertNull(shadowOf(context).nextStartedActivity)
        assertNull(installer.getSessionInfo(id))
    }

    @Test
    fun aTappedInstallShowsAndroidsConfirmation() {
        InstallReceiver().onReceive(
            context,
            status(PackageInstaller.STATUS_PENDING_USER_ACTION, auto = false).putExtra(Intent.EXTRA_INTENT, confirm),
        )
        assertEquals(confirm.action, shadowOf(context).nextStartedActivity?.action)
    }

    @Test
    fun onlyATappedInstallReportsItsFailure() {
        InstallReceiver().onReceive(context, status(PackageInstaller.STATUS_FAILURE, auto = true).putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "no room"))
        assertNull(InstallReceiver.takeError(context))

        InstallReceiver().onReceive(context, status(PackageInstaller.STATUS_FAILURE, auto = false).putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "no room"))
        assertEquals("no room", InstallReceiver.takeError(context))
        assertNull(InstallReceiver.takeError(context))
    }

    private fun status(status: Int, auto: Boolean) = Intent(Updater.ACTION_INSTALL_STATUS)
        .putExtra(PackageInstaller.EXTRA_STATUS, status)
        .putExtra(Updater.EXTRA_AUTO, auto)
}
