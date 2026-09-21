package dev.bee.kanjianki.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPackageInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun streamsApkFsyncsAndCommitsBeforeClosing() {
        val apk = temporaryFolder.newFile("kani.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val backend = RecordingBackend()
        val installer = AndroidPackageInstaller(context, backend, runtimeSdk = 35)

        installer.install(request(apk))

        assertNotNull(backend.params)
        assertEquals("kani-update.apk", backend.session.openedName)
        assertArrayEquals(apk.readBytes(), backend.session.output.toByteArray())
        assertTrue(backend.session.fsynced)
        assertNotNull(backend.session.committed)
        assertTrue(backend.session.closed)
        assertFalse(backend.abandoned)
    }

    @Test
    fun failedWriteClosesAndAbandonsSession() {
        val apk = temporaryFolder.newFile("broken.apk").apply { writeText("apk") }
        val backend = RecordingBackend(failWrite = true)
        val installer = AndroidPackageInstaller(context, backend, runtimeSdk = 35)

        assertThrows(IOException::class.java) {
            installer.install(request(apk))
        }

        assertTrue(backend.session.closed)
        assertTrue(backend.abandoned)
        assertFalse(backend.session.fsynced)
    }

    @Test
    fun frameworkBackendCanCommitARealPackageInstallerSession() {
        val apk = temporaryFolder.newFile("kani.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }

        AndroidPackageInstaller(context).install(request(apk))
    }

    @Test
    fun permissionIntentTargetsThisPackage() {
        val intent = AndroidPackageInstaller.installPermissionIntent(context)

        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private fun request(apk: File) =
        AndroidPackageInstaller.InstallRequest(
            apkFile = apk,
            packageName = context.packageName,
            sessionName = "kani-update.apk",
            callbackIntent = Intent(context, InstallStatusReceiver::class.java),
            allowWithoutUserAction = true,
        )

    private class RecordingBackend(
        failWrite: Boolean = false,
    ) : AndroidPackageInstaller.Backend {
        val session = RecordingSession(failWrite)
        var params: PackageInstaller.SessionParams? = null
        var abandoned = false

        override fun createSession(params: PackageInstaller.SessionParams): Int {
            this.params = params
            return 17
        }

        override fun openSession(sessionId: Int): AndroidPackageInstaller.Session {
            assertEquals(17, sessionId)
            return session
        }

        override fun abandonSession(sessionId: Int) {
            assertEquals(17, sessionId)
            abandoned = true
        }
    }

    private class RecordingSession(
        private val failWrite: Boolean,
    ) : AndroidPackageInstaller.Session {
        val output = ByteArrayOutputStream()
        var openedName: String? = null
        var fsynced = false
        var committed: IntentSender? = null
        var closed = false

        override fun openWrite(
            name: String,
            offsetBytes: Long,
            lengthBytes: Long,
        ): OutputStream {
            if (failWrite) {
                throw IOException("write failed")
            }
            openedName = name
            return output
        }

        override fun fsync(output: OutputStream) {
            fsynced = true
        }

        override fun commit(statusReceiver: IntentSender) {
            committed = statusReceiver
        }

        override fun close() {
            closed = true
        }
    }

    private class InstallStatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = Unit
    }
}
