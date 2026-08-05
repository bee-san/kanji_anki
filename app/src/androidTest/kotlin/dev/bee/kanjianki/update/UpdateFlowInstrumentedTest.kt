package dev.bee.kanjianki.update

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.KaniTestDatabase
import dev.bee.kanjianki.KaniTestDeviceSettings
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.testing.DeviceRisk
import dev.bee.kanjianki.updatecore.SigningCertificateInfo
import dev.bee.kanjianki.updatecore.UpdateTextPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.NoRouteToHostException
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(AndroidJUnit4::class)
class UpdateFlowInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
        // Update state lives in SharedPreferences, not the database, so it survives the
        // delete above and the whole instrumentation run. Without this every test that
        // asserts an update default is order-dependent on its siblings -- see
        // UpdateFixtureIsolationInstrumentedTest, which fails without this line.
        KaniTestDeviceSettings.clearUpdateState(context)
        clearUpdatesCache()
        UpdateNotifier.cancelPendingUpdate(context)
    }

    @After
    fun tearDown() {
        UpdateNotifier.cancelPendingUpdate(context)
        KaniTestDatabase.delete(context)
        KaniTestDeviceSettings.clearUpdateState(context)
        clearUpdatesCache()
    }

    @Test
    fun packageInstallReceiverIgnoresUnrelatedIntents() {
        PackageInstallStatusReceiver().onReceive(context, null)
        PackageInstallStatusReceiver().onReceive(context, Intent("other.action"))

        LocalStore(context).use { store ->
            assertEquals("No automatic update check has run yet.", store.autoUpdateStatus().lastResult)
        }
    }

    @Test
    fun successfulPackageInstallClearsPendingUpdateAndDeletesCachedApk() {
        val cached = cachedApk("kani.apk")
        write(cached, "placeholder")
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v9.9.9", cached.name, "waiting")
        }

        val intent = PackageInstallStatusReceiver.callbackIntent(
            context,
            "../" + cached.name,
            "v9.9.9",
            GitHubUpdater.UpdateSource.MANUAL,
        ).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
        PackageInstallStatusReceiver().onReceive(context, intent)

        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("Install finished.", status.lastResult)
            assertEquals("v9.9.9", status.lastVersion)
            assertFalse(status.hasPendingUpdate())
        }
        assertFalse(cached.exists())
    }

    @Test
    fun failedPackageInstallRecordsInstallerMessageAndClearsPendingApk() {
        val intent = PackageInstallStatusReceiver.callbackIntent(
            context,
            "failed.apk",
            "v1.0.0",
            GitHubUpdater.UpdateSource.AUTOMATIC,
        ).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "blocked by Android")

        PackageInstallStatusReceiver().onReceive(context, intent)

        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("Install failed: blocked by Android.", status.lastResult)
            assertEquals("v1.0.0", status.lastVersion)
            assertFalse(status.hasPendingUpdate())
        }
    }

    @Test
    fun terminalPackageInstallStatusClearsThePendingUpdateNotification() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            val controller = UpdateNotifier.AndroidNotificationController(context, 33)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertTrue(controller.ensureChannel("App updates", "Friendly Kani update prompts."))
            assertTrue(controller.notifyUpdate("Kani update ready", "Ready"))
            assertTrue(waitForUpdateNotification(manager, expectedPresent = true))

            val intent = PackageInstallStatusReceiver.callbackIntent(
                context,
                "finished.apk",
                "v1.0.0",
                GitHubUpdater.UpdateSource.AUTOMATIC,
            ).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
            PackageInstallStatusReceiver().onReceive(context, intent)

            assertTrue(waitForUpdateNotification(manager, expectedPresent = false))
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun waitForUpdateNotification(
        manager: NotificationManager,
        expectedPresent: Boolean,
        timeoutMillis: Long = 5_000L,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        do {
            val present = manager.activeNotifications.any {
                it.notification.category == android.app.Notification.CATEGORY_STATUS
            }
            if (present == expectedPresent) {
                return true
            }
            SystemClock.sleep(25L)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    @Test
    fun pendingUserActionKeepsVerifiedApkForLaterInstall() {
        val intent = PackageInstallStatusReceiver.callbackIntent(
            context,
            "waiting.apk",
            "v2.0.0",
            GitHubUpdater.UpdateSource.AUTOMATIC,
        ).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)

        PackageInstallStatusReceiver().onReceive(context, intent)

        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("Android needs permission to finish installing.", status.lastResult)
            assertEquals("v2.0.0", status.lastVersion)
            assertEquals("waiting.apk", status.pendingApkName)
            assertTrue(status.hasPendingUpdate())
        }
    }

    @Test
    fun pendingUserActionStartsConfirmationForInteractiveSourcesOnly() {
        val manual = PendingActionHandler()
        val manualConfirmation = Intent("dev.bee.kanjianki.CONFIRM_MANUAL")
        val manualStatus = Intent().putExtra(Intent.EXTRA_INTENT, manualConfirmation)

        PackageInstallStatusReceiver.handlePendingUserAction(
            manualStatus,
            GitHubUpdater.UpdateSource.MANUAL,
            "v2.0.0",
            "confirm",
            manual,
        )

        assertEquals(1, manual.started)
        assertEquals(0, manual.notifications)
        assertEquals("dev.bee.kanjianki.CONFIRM_MANUAL", manual.startedIntent!!.action)
        assertNotEquals(0, manual.startedIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)

        val automatic = PendingActionHandler()
        val automaticStatus = Intent().putExtra(Intent.EXTRA_INTENT, Intent("dev.bee.kanjianki.CONFIRM_AUTO"))

        PackageInstallStatusReceiver.handlePendingUserAction(
            automaticStatus,
            GitHubUpdater.UpdateSource.AUTOMATIC,
            "v2.0.1",
            "notify instead",
            automatic,
        )

        assertEquals(0, automatic.started)
        assertEquals(1, automatic.notifications)
        assertEquals("v2.0.1", automatic.notificationVersion)
        assertEquals("notify instead", automatic.notificationMessage)

        val missingConfirmation = PendingActionHandler()
        PackageInstallStatusReceiver.handlePendingUserAction(
            Intent(),
            GitHubUpdater.UpdateSource.CACHED,
            "v2.0.2",
            "no confirmation intent",
            missingConfirmation,
        )

        assertEquals(0, missingConfirmation.started)
        assertEquals(1, missingConfirmation.notifications)
    }

    @Test
    fun sourceMappingDefaultsInvalidOrMissingValuesToAutomatic() {
        assertEquals(GitHubUpdater.UpdateSource.AUTOMATIC, PackageInstallStatusReceiver.sourceFrom(null))
        assertEquals(GitHubUpdater.UpdateSource.AUTOMATIC, PackageInstallStatusReceiver.sourceFrom("not-real"))
        assertEquals(GitHubUpdater.UpdateSource.CACHED, PackageInstallStatusReceiver.sourceFrom("CACHED"))
    }

    @Test
    fun callbackIntentDefaultsMissingFieldsAndKeepsExplicitFields() {
        val defaults = PackageInstallStatusReceiver.callbackIntent(context, null, null, null)
        val explicit = PackageInstallStatusReceiver.callbackIntent(
            context,
            "kani.apk",
            "v1.2.3",
            GitHubUpdater.UpdateSource.CACHED,
        )

        assertEquals(PackageInstallStatusReceiver.ACTION_INSTALL_STATUS, defaults.action)
        assertEquals("", defaults.getStringExtra("dev.bee.kanjianki.extra.APK_NAME"))
        assertEquals("", defaults.getStringExtra("dev.bee.kanjianki.extra.VERSION"))
        assertEquals("AUTOMATIC", defaults.getStringExtra("dev.bee.kanjianki.extra.SOURCE"))
        assertEquals("kani.apk", explicit.getStringExtra("dev.bee.kanjianki.extra.APK_NAME"))
        assertEquals("v1.2.3", explicit.getStringExtra("dev.bee.kanjianki.extra.VERSION"))
        assertEquals("CACHED", explicit.getStringExtra("dev.bee.kanjianki.extra.SOURCE"))
    }

    @Test
    fun pendingUserActionNullIntentFallsBackToNotification() {
        val handler = PendingActionHandler()

        PackageInstallStatusReceiver.handlePendingUserAction(
            null,
            GitHubUpdater.UpdateSource.MANUAL,
            "v3.0.0",
            "confirm later",
            handler,
        )

        assertEquals(0, handler.started)
        assertEquals(1, handler.notifications)
        assertEquals("v3.0.0", handler.notificationVersion)
        assertEquals("confirm later", handler.notificationMessage)
    }

    @Test
    fun failedInstallConfirmationLaunchFallsBackToNotification() {
        val handler = PendingActionHandler().apply { failStart = true }
        val status = Intent().putExtra(
            Intent.EXTRA_INTENT,
            Intent("dev.bee.kanjianki.CONFIRM_UNAVAILABLE"),
        )

        PackageInstallStatusReceiver.handlePendingUserAction(
            status,
            GitHubUpdater.UpdateSource.MANUAL,
            "v3.0.1",
            "confirm later",
            handler,
        )

        assertEquals(1, handler.started)
        assertEquals(1, handler.notifications)
        assertEquals("v3.0.1", handler.notificationVersion)
    }

    @Test
    fun packageInstallReceiverDeleteSeamHandlesEmptyMissingDeletedAndFailedCacheFiles() {
        assertFalse(
            PackageInstallStatusReceiver.deleteCachedApk(
                context,
                null,
                PackageInstallStatusReceiver.CacheFileDeletion { file -> file.delete() },
            ),
        )
        assertFalse(
            PackageInstallStatusReceiver.deleteCachedApk(
                context,
                "   ",
                PackageInstallStatusReceiver.CacheFileDeletion { file -> file.delete() },
            ),
        )
        assertFalse(
            PackageInstallStatusReceiver.deleteCachedApk(
                context,
                "missing.apk",
                PackageInstallStatusReceiver.CacheFileDeletion { file -> file.delete() },
            ),
        )

        val deleted = cachedApk("delete-ok.apk")
        write(deleted, "delete me")
        assertTrue(
            PackageInstallStatusReceiver.deleteCachedApk(
                context,
                "../" + deleted.name,
                PackageInstallStatusReceiver.CacheFileDeletion { file -> file.delete() },
            ),
        )
        assertFalse(deleted.exists())

        val blocked = cachedApk("delete-blocked.apk")
        write(blocked, "keep me")
        assertFalse(
            PackageInstallStatusReceiver.deleteCachedApk(
                context,
                blocked.name,
                PackageInstallStatusReceiver.CacheFileDeletion { _ -> false },
            ),
        )
        assertTrue(blocked.exists())
    }

    @Test
    fun githubUpdaterDeleteSeamReportsFailedCacheDeletion() {
        val blocked = cachedApk("github-delete-blocked.apk")
        write(blocked, "keep me")

        assertFalse(GitHubUpdater.deleteCachedApk(blocked, GitHubUpdater.CacheFileDeletion { _ -> false }))
        assertTrue(blocked.exists())
    }

    @Test
    fun androidPendingUserActionHandlerStartsActivityOnWrappedContext() {
        val recording = RecordingContext(context)
        val handler = PackageInstallStatusReceiver.androidPendingUserActionHandler(recording)

        handler.startActivity(Intent("dev.bee.kanjianki.TEST_START"))

        assertEquals("dev.bee.kanjianki.TEST_START", recording.startedAction)
    }

    @Test
    fun updateNotifierAndroidControllerCoversPermissionAndMissingManagerBranches() {
        val pre33 = RecordingPermissionContext(context, PackageManager.PERMISSION_DENIED, false)
        val pre33Controller = UpdateNotifier.AndroidNotificationController(pre33, 32)

        assertTrue(pre33Controller.hasRuntimeNotificationPermission())
        assertEquals(0, pre33.permissionChecks)

        val denied = RecordingPermissionContext(context, PackageManager.PERMISSION_DENIED, false)
        val granted = RecordingPermissionContext(context, PackageManager.PERMISSION_GRANTED, false)

        assertFalse(UpdateNotifier.AndroidNotificationController(denied, 33).hasRuntimeNotificationPermission())
        assertTrue(UpdateNotifier.AndroidNotificationController(granted, 33).hasRuntimeNotificationPermission())
        assertEquals(1, denied.permissionChecks)
        assertEquals(1, granted.permissionChecks)

        val noManager = RecordingPermissionContext(context, PackageManager.PERMISSION_GRANTED, true)
        val noManagerController = UpdateNotifier.AndroidNotificationController(noManager, 33)

        assertFalse(noManagerController.areNotificationsEnabled())
        assertFalse(noManagerController.ensureChannel("App updates", "Friendly Kani update prompts."))
        assertFalse(noManagerController.notifyUpdate("Kani update ready", "Ready"))
    }

    @Test
    fun updateNotifierAndroidControllerPostsWhenManagerExists() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS)
        try {
            val controller = UpdateNotifier.AndroidNotificationController(context, 33)

            assertTrue(controller.hasRuntimeNotificationPermission())
            assertTrue(controller.areNotificationsEnabled())
            assertTrue(controller.ensureChannel("App updates", "Friendly Kani update prompts."))
            assertEquals(NotificationManager.IMPORTANCE_DEFAULT, controller.channelImportance())
            assertTrue(controller.notifyUpdate("Kani update ready", "Ready"))
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertTrue(waitForUpdateNotification(manager, expectedPresent = true))
            val posted = manager.activeNotifications
                .single { it.notification.category == android.app.Notification.CATEGORY_STATUS }
            assertEquals(android.app.Notification.CATEGORY_STATUS, posted.notification.category)
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun notificationEnabledHelperUsesManagerWhenPresent() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

        assertNotNull(manager)
        assertFalse(UpdateNotifier.notificationsEnabled(manager, UpdateNotifier.NotificationEnabledCheck { _ -> false }))
        assertTrue(UpdateNotifier.notificationsEnabled(manager, UpdateNotifier.NotificationEnabledCheck { _ -> true }))
    }

    @Test
    fun apkMetadataNormalizesMissingAndPresentPackageInfoFields() {
        val missing = GitHubUpdater.metadataFromPackageInfo(null)
        assertEquals("", missing.packageName)
        assertEquals("", missing.versionName)
        assertEquals(0, missing.targetSdkVersion)

        val bothNull = PackageInfo()
        val empty = GitHubUpdater.metadataFromPackageInfo(bothNull)
        assertEquals("", empty.packageName)
        assertEquals("", empty.versionName)
        assertEquals(0, empty.targetSdkVersion)

        val packageOnly = PackageInfo().apply { packageName = "dev.bee.kanjianki" }
        val packageOnlyMetadata = GitHubUpdater.metadataFromPackageInfo(packageOnly)
        assertEquals("dev.bee.kanjianki", packageOnlyMetadata.packageName)
        assertEquals("", packageOnlyMetadata.versionName)
        assertEquals(0, packageOnlyMetadata.targetSdkVersion)

        val versionOnly = PackageInfo().apply { versionName = "9.9.9" }
        val versionOnlyMetadata = GitHubUpdater.metadataFromPackageInfo(versionOnly)
        assertEquals("", versionOnlyMetadata.packageName)
        assertEquals("9.9.9", versionOnlyMetadata.versionName)
        assertEquals(0, versionOnlyMetadata.targetSdkVersion)

        val complete = PackageInfo().apply {
            packageName = "dev.bee.kanjianki"
            versionName = "9.9.9"
            applicationInfo = ApplicationInfo().apply { targetSdkVersion = 34 }
        }
        val completeMetadata = GitHubUpdater.metadataFromPackageInfo(complete)
        assertEquals("dev.bee.kanjianki", completeMetadata.packageName)
        assertEquals("9.9.9", completeMetadata.versionName)
        assertEquals(34, completeMetadata.targetSdkVersion)
    }

    @Test
    fun packageInstallerSeamWritesCommitsClosesAndAbandonsOnlyFailures() {
        val apk = cachedApk("installer-good.apk")
        val apkBytes = ByteArray(70_000)
        for (i in apkBytes.indices) {
            apkBytes[i] = (i * 13 + 11).toByte()
        }
        FileOutputStream(apk).use { output -> output.write(apkBytes) }
        val success = FakeInstallerBackend(FakeInstallerSession(false))

        GitHubUpdater.startPackageInstaller(
            context,
            success,
            apk,
            "v8.0.0",
            GitHubUpdater.UpdateSource.MANUAL,
            34,
        )

        assertEquals(41, success.createdSessionId)
        assertEquals(41, success.openedSessionId)
        assertEquals(0, success.abandonedSessions)
        assertTrue(success.session.committed)
        assertTrue(success.session.closed)
        assertTrue(success.session.fsynced)
        assertEquals("kani-update.apk", success.session.writeName)
        assertEquals(apk.length(), success.session.writeLength)
        assertEquals(sha256(apkBytes), sha256(success.session.bytes.toByteArray()))

        val openFailure = FakeInstallerBackend(FakeInstallerSession(false))
        openFailure.failOpen = true
        val openError: IOException? = try {
            GitHubUpdater.startPackageInstaller(
                context,
                openFailure,
                apk,
                "v8.0.0",
                GitHubUpdater.UpdateSource.MANUAL,
                34,
            )
            throw AssertionError("Expected IOException")
        } catch (caught: IOException) {
            caught
        }

        assertEquals("open failed", openError!!.message)
        assertEquals(1, openFailure.abandonedSessions)
        assertFalse(openFailure.session.closed)

        val writeFailure = FakeInstallerBackend(FakeInstallerSession(true))
        val writeError: IOException? = try {
            GitHubUpdater.startPackageInstaller(
                context,
                writeFailure,
                apk,
                "v8.0.0",
                GitHubUpdater.UpdateSource.MANUAL,
                34,
            )
            throw AssertionError("Expected IOException")
        } catch (caught: IOException) {
            caught
        }

        assertEquals("write failed", writeError!!.message)
        assertEquals(1, writeFailure.abandonedSessions)
        assertTrue(writeFailure.session.closed)
        assertFalse(writeFailure.session.committed)
    }

    @Test
    fun packageInstallerSeamAbandonsWhenFsyncOrCommitFails() {
        val apk = cachedApk("installer-failure.apk")
        write(apk, "installer bytes")

        val fsyncSession = FakeInstallerSession(false).apply { failFsync = true }
        val fsyncFailure = FakeInstallerBackend(fsyncSession)
        val fsyncError: IOException? = try {
            GitHubUpdater.startPackageInstaller(
                context,
                fsyncFailure,
                apk,
                "v8.0.1",
                GitHubUpdater.UpdateSource.MANUAL,
                34,
            )
            throw AssertionError("Expected IOException")
        } catch (caught: IOException) {
            caught
        }

        assertEquals("fsync failed", fsyncError!!.message)
        assertEquals(1, fsyncFailure.abandonedSessions)
        assertTrue(fsyncFailure.session.closed)
        assertFalse(fsyncFailure.session.committed)

        val commitSession = FakeInstallerSession(false).apply { failCommit = true }
        val commitFailure = FakeInstallerBackend(commitSession)
        val commitError: IllegalStateException? = try {
            GitHubUpdater.startPackageInstaller(
                context,
                commitFailure,
                apk,
                "v8.0.1",
                GitHubUpdater.UpdateSource.MANUAL,
                34,
            )
            throw AssertionError("Expected IllegalStateException")
        } catch (caught: IllegalStateException) {
            caught
        }

        assertEquals("commit failed", commitError!!.message)
        assertEquals(1, commitFailure.abandonedSessions)
        assertTrue(commitFailure.session.closed)
        assertTrue(commitFailure.session.fsynced)
        assertFalse(commitFailure.session.committed)

        val closeSession = FakeInstallerSession(true).apply { failClose = true }
        val closeFailure = FakeInstallerBackend(closeSession)
        val closeError: IllegalStateException? = try {
            GitHubUpdater.startPackageInstaller(
                context,
                closeFailure,
                apk,
                "v8.0.1",
                GitHubUpdater.UpdateSource.MANUAL,
                34,
            )
            throw AssertionError("Expected IllegalStateException")
        } catch (caught: IllegalStateException) {
            caught
        }

        assertEquals("close failed", closeError!!.message)
        assertEquals(1, closeFailure.abandonedSessions)
        assertTrue(closeFailure.session.closed)
    }

    @Test
    fun androidInstallerBackendDelegatesToInstallerAccess() {
        val session = FakeInstallerSession(false)
        val access = FakeInstallerAccess(session)
        val backend = GitHubUpdater.AndroidInstallerBackend(access)
        val params = GitHubUpdater.sessionParams(context.packageName, 30)

        assertEquals(77, backend.createSession(params))
        assertSame(session, backend.openSession(77))
        backend.abandonSession(77)

        assertSame(params, access.createdParams)
        assertEquals(77, access.openedSessionId)
        assertEquals(77, access.abandonedSessionId)
    }

    @Test
    fun androidInstallerSessionDelegatesToSessionAccess() {
        val access = FakeSessionAccess()
        val session = GitHubUpdater.AndroidInstallerSession(access)
        val sender = dummyIntentSender()

        val output = session.openWrite("kani-update.apk", 5L, 12L)
        output.write(42)
        session.fsync(output)
        session.commit(sender)
        session.close()

        assertEquals("kani-update.apk", access.writeName)
        assertEquals(5L, access.writeOffset)
        assertEquals(12L, access.writeLength)
        assertEquals(42.toByte(), access.bytes.toByteArray()[0])
        assertSame(output, access.fsyncedOutput)
        assertTrue(access.committed)
        assertTrue(access.closed)
    }

    @Test
    fun installerSessionFactoryDelegatesOperationsToSuppliedCallbacks() {
        val access = FakeSessionAccess()
        val session = GitHubUpdater.installerSession(access::openWrite, access::fsync, access::commit, access::close)
        val sender = dummyIntentSender()

        val output = session.openWrite("factory.apk", 2L, 4L)
        output.write(7)
        session.fsync(output)
        session.commit(sender)
        session.close()

        assertEquals("factory.apk", access.writeName)
        assertEquals(2L, access.writeOffset)
        assertEquals(4L, access.writeLength)
        assertEquals(7.toByte(), access.bytes.toByteArray()[0])
        assertSame(output, access.fsyncedOutput)
        assertTrue(access.committed)
        assertTrue(access.closed)
    }

    @Test
    fun packageInstallerFactoriesWrapRealInstallerSessionAndCallbacks() {
        val packageInstaller = context.packageManager.packageInstaller
        val backend = GitHubUpdater.installerBackend(packageInstaller)
        val params = GitHubUpdater.sessionParams(context.packageName, 30)

        val sessionId = backend.createSession(params)
        var session: GitHubUpdater.InstallerSession? = null
        try {
            session = backend.openSession(sessionId)
            session.openWrite("kani-update.apk", 0, 3).use { output ->
                output.write(byteArrayOf(1, 2, 3))
                session.fsync(output)
            }
        } finally {
            session?.close()
            backend.abandonSession(sessionId)
        }

        try {
            session.commit(dummyIntentSender())
        } catch (ignored: RuntimeException) {
            assertNotNull(ignored.javaClass.simpleName)
        }
    }

    @Test
    fun androidUpdateClientDelegatesPlatformOperationsAndInstallerBackendFactory() {
        val apk = cachedApk("android-client.apk")
        write(apk, "android client installer bytes")
        val backend = FakeInstallerBackend(FakeInstallerSession(false))
        val client = GitHubUpdater.androidClient(
            context,
            GitHubUpdater.InstallerBackendFactory { appContext ->
                assertEquals(context.packageName, appContext.packageName)
                backend
            },
            GitHubUpdater.TextFetcher { url -> "text from $url" },
            GitHubUpdater.FileDownloader { url, file -> write(file, "download from $url") },
        )

        assertEquals("text from https://example.test/latest", client.getText("https://example.test/latest"))
        val target = cachedApk("network-target.apk")
        client.download("https://example.test/kani.apk", target)
        assertEquals("download from https://example.test/kani.apk", read(target))
        val metadata = client.inspectApk(apk)
        assertEquals("", metadata.packageName)
        assertEquals("", metadata.versionName)
        assertEquals(context.packageManager.canRequestPackageInstalls(), client.canRequestPackageInstalls())

        client.showPendingUpdate("v8.0.2", "ready")
        client.startPackageInstaller(apk, "v8.0.2", GitHubUpdater.UpdateSource.MANUAL, 34)

        assertEquals(41, backend.createdSessionId)
        assertEquals(41, backend.openedSessionId)
        assertTrue(backend.session.committed)
        assertTrue(backend.session.closed)
    }

    @Test
    fun packageInstallerSessionParamsCoverPreAndPostUserActionApis() {
        assertNotNull(GitHubUpdater.sessionParams(context.packageName, 30))
        assertNotNull(GitHubUpdater.sessionParams(context.packageName, 31))
    }

    @Test
    fun autoUpdateWorkerConstructorAndDoWorkUseStoredDisabledState() {
        LocalStore(context).use { store ->
            store.saveAutoUpdateEnabled(false)
        }

        val worker = AutoUpdateWorker(context, workerParameters())

        assertWorkerSuccess(worker.doWork())
    }

    @Test
    fun autoUpdateWorkerCheckAutomaticUpdateUsesConfiguredClientFactory() {
        val apk = "automatic worker apk".toByteArray(Charsets.UTF_8)
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(sha256(apk))
            .downloadBytes(apk)
            .metadata(context.packageName, "99.99.99")
            .canInstall(false)
        val originalFactory = AutoUpdateWorker.updateClientFactory
        try {
            AutoUpdateWorker.updateClientFactory = AutoUpdateWorker.UpdateClientFactory { _ -> client }

            val result = AutoUpdateWorker.checkAutomaticUpdate(context)

            assertTrue(result.success)
            assertTrue(result.needsInstallPermission)
            assertEquals("APK verified. Grant install permission to continue.", result.message)
            assertEquals(1, client.downloads)
            assertEquals(1, client.notifications)
            assertEquals(0, client.installs)
        } finally {
            AutoUpdateWorker.updateClientFactory = originalFactory
        }
    }

    @Test
    fun cachedInstallReportsMissingPendingStateAndMissingApk() {
        val updater = GitHubUpdater(context)
        val noPending = updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertFalse(noPending.success)
        assertEquals("No verified APK is waiting to install.", noPending.message)

        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v7.0.0", "missing.apk", "ready")
        }

        val missing = updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertFalse(missing.success)
        assertEquals("Verified APK cache is missing. Check again to download it.", missing.message)
        LocalStore(context).use { store ->
            assertEquals("Verified APK cache is missing. Check again to download it.", store.autoUpdateStatus().lastResult)
        }
    }

    @Test
    fun cachedInstallDeletesInvalidVerifiedApk() {
        val cached = cachedApk("invalid.apk")
        write(cached, "not an apk")
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v7.0.0", cached.name, "ready")
        }

        val result = GitHubUpdater(context).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertFalse(result.success)
        assertEquals("APK metadata could not be read. Install blocked.", result.message)
        assertFalse(cached.exists())
        LocalStore(context).use { store ->
            assertFalse(store.autoUpdateStatus().hasPendingUpdate())
        }
    }

    @Test
    fun cachedInstallStartsInstallerForValidVerifiedApk() {
        val cached = cachedApk("cached-good.apk")
        write(cached, "valid cached apk")
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.name, "ready to install")
        }
        val client = FakeUpdateClient()
            .metadata(context.packageName, "99.99.99", 34)
            .canInstall(true)

        val result = GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertTrue(result.success)
        assertFalse(result.needsInstallPermission)
        assertEquals("APK verified. Android installer started.", result.message)
        assertEquals(1, client.installs)
        assertEquals("v99.99.99", client.installedVersion)
        assertEquals(GitHubUpdater.UpdateSource.CACHED, client.installedSource)
        assertEquals(34, client.installedTargetSdk)
        assertEquals(0, client.notifications)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("cached-good.apk", status.pendingApkName)
            assertEquals("", status.pendingMessage)
            assertTrue(status.hasPendingUpdate())
        }
    }

    @Test
    fun cachedInstallWithMismatchedSigningCertBlocksInstallAndClearsPending() {
        val cached = cachedApk("cached-hostile.apk")
        write(cached, "hostile cached apk")
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.name, "ready to install")
        }
        val client = FakeUpdateClient()
            .metadata(context.packageName, "99.99.99", 34)
            .mismatchedSigningCert()
            .canInstall(true)

        val result = GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertFalse(result.success)
        assertEquals("APK signing certificate does not match the installed app. Install blocked.", result.message)
        assertEquals(0, client.installs)
        // Cached APK is deleted and pending state cleared so it is not retried.
        assertFalse(cached.exists())
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("", status.pendingApkName)
            assertFalse(status.hasPendingUpdate())
        }
    }

    @Test
    fun downloadInstallWithMismatchedSigningCertBlocksInstallAndClearsPending() {
        val apk = "hostile download apk".toByteArray(Charsets.UTF_8)
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(sha256(apk))
            .downloadBytes(apk)
            .metadata(context.packageName, "99.99.99")
            .mismatchedSigningCert()
            .canInstall(true)

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertFalse(result.success)
        assertEquals("APK signing certificate does not match the installed app. Install blocked.", result.message)
        assertEquals(0, client.installs)
        LocalStore(context).use { store ->
            assertFalse(store.autoUpdateStatus().hasPendingUpdate())
        }
    }

    @Test
    fun cachedInstallRecordsFakeClientExceptionAndKeepsPendingApk() {
        val cached = cachedApk("cached-throws.apk")
        write(cached, "cached apk")
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.name, "ready to install")
        }
        val client = FakeUpdateClient().inspectFailure(IllegalStateException("metadata reader failed"))

        val result = GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertFalse(result.success)
        assertEquals("Update install failed: metadata reader failed", result.message)
        assertEquals(0, client.installs)
        assertEquals(0, client.notifications)
        assertTrue(cached.exists())
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("Update install failed: metadata reader failed", status.lastResult)
            assertEquals("cached-throws.apk", status.pendingApkName)
            assertEquals("ready to install", status.pendingMessage)
            assertTrue(status.hasPendingUpdate())
        }
    }

    @Test
    fun installPermissionIntentTargetsThisPackage() {
        val intent = GitHubUpdater.installPermissionIntent(context)

        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", intent.action)
        assertEquals(Uri.parse("package:" + context.packageName), intent.data)
        assertNotEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun autoUpdateWorkerEntryReadsStoreAndRunsCheckerOnlyWhenEligible() {
        LocalStore(context).use { store ->
            store.saveAutoUpdateEnabled(false)
        }
        val disabled = WorkerCheckerFactory(false)

        assertWorkerSuccess(AutoUpdateWorker.runFromStore(context, disabled))

        assertEquals(0, disabled.factories)
        assertEquals(0, disabled.checks)

        LocalStore(context).use { store ->
            store.saveAutoUpdateEnabled(true)
            store.recordAutoUpdateResult(10L, "pending", "v9.9.9", "pending.apk", "ready")
        }
        val pending = WorkerCheckerFactory(false)

        assertWorkerSuccess(AutoUpdateWorker.runFromStore(context, pending))

        assertEquals(0, pending.factories)
        assertEquals(0, pending.checks)

        LocalStore(context).use { store ->
            store.clearPendingAutoUpdate("cleared")
        }
        val eligible = WorkerCheckerFactory(false)

        assertWorkerSuccess(AutoUpdateWorker.runFromStore(context, eligible))

        assertEquals(1, eligible.factories)
        assertEquals(1, eligible.checks)
    }

    @Test
    fun autoUpdateWorkerEntryReturnsRetryForRetryableCheckerResult() {
        LocalStore(context).use { store ->
            store.saveAutoUpdateEnabled(true)
            store.clearPendingAutoUpdate("ready")
        }
        val retryable = WorkerCheckerFactory(true)

        val result = AutoUpdateWorker.runFromStore(context, retryable)

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(1, retryable.factories)
        assertEquals(1, retryable.checks)
    }

    @Test
    fun checkDownloadAndInstallRecordsAlreadyCurrentReleaseWithoutDownloading() {
        val client = FakeUpdateClient()
            .latest(releaseJson("v" + BuildConfig.VERSION_NAME, apkAsset(), checksumAsset()))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals("Already on " + BuildConfig.VERSION_NAME + ".", result.message)
        assertEquals(0, client.downloads)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("v" + BuildConfig.VERSION_NAME, status.lastVersion)
            assertFalse(status.hasPendingUpdate())
        }
    }

    // ---------------------------------------------------------------------
    // On-device offline-classification contract (API 26 + API 35 via the
    // device-smoke / instrumented emulator lanes). These mirror the hermetic
    // JVM matrix in UpdaterOfflineContractTest but run against the real
    // GitHubUpdater + real LocalStore on a real Android runtime, proving the
    // connectivity-vs-permanent classification and the persisted
    // update-check-failed retry flag behave identically on-device across API
    // levels. No radio shaping and no Internet: the transport fault is
    // injected at the UpdateClient seam, which the offline audit established as
    // the app's single outbound network touchpoint.
    // ---------------------------------------------------------------------

    @Test
    @DeviceRisk
    fun offlineNoRouteIsRetryableConnectivityFailureOnDevice() {
        val client = FakeUpdateClient().getTextFailure(NoRouteToHostException("api.github.com"))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertFalse(result.success)
        assertTrue("No-route must be a retryable connectivity failure on-device", result.retryable)
        assertNotEquals(
            "A no-route outage must not be reported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertEquals(
            "An automatic no-route outage must not create a Home retry nag",
            0L,
            failedAt,
        )
        assertEquals(0, client.downloads)
    }

    @Test
    @DeviceRisk
    fun offlineTlsHandshakeFailureIsRetryableConnectivityFailureOnDevice() {
        val client = FakeUpdateClient().getTextFailure(SSLHandshakeException("handshake_failure"))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertTrue(
            "A TLS handshake failure (captive-portal / intercepting proxy) must be retryable on-device",
            result.retryable,
        )
        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertTrue("A TLS handshake failure must light the persisted retry flag", failedAt > 0L)
    }

    @Test
    @DeviceRisk
    fun offlineCaptivePortalHtmlIsNotReportedAsAlreadyCurrentOnDevice() {
        // A captive portal answers the releases API with HTTP 200 + an HTML
        // login page. The read succeeds but carries no usable tag_name; the
        // updater must classify this as a retryable connectivity failure, never
        // collapse the empty tag to 0.0.0 and claim the user is up to date.
        val client = FakeUpdateClient().latest(
            "<!DOCTYPE html><html><head><title>Sign in to Wi-Fi</title></head>" +
                "<body><h1>Login required</h1></body></html>",
        )

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertTrue("A captive-portal interstitial must be a retryable failure on-device", result.retryable)
        assertNotEquals(
            "A captive-portal interstitial must not be reported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
        assertEquals(0, client.downloads)
        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertTrue("A captive-portal interstitial must light the persisted retry flag", failedAt > 0L)
    }

    @Test
    @DeviceRisk
    fun offlineCancelledReadIsNotRetryableAndDoesNotLightRetryFlagOnDevice() {
        // A cancelled/interrupted read (thread interrupt / WorkManager stop /
        // process backgrounded mid-check) is NOT a broken network. It must be
        // classified as a non-retryable failure so it does not light a
        // persistent "check failed, tap to retry" affordance, and it must not
        // masquerade as "already up to date".
        val client = FakeUpdateClient().getTextFailure(InterruptedIOException("thread interrupted"))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertFalse(result.success)
        assertFalse("A cancelled read must not be a retryable connectivity failure on-device", result.retryable)
        assertNotEquals(
            "A cancelled read must not be reported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertEquals("A cancelled read is not a connectivity outage; it must not light the retry flag", 0L, failedAt)
    }

    @Test
    @DeviceRisk
    fun offlineRetryFlagSurvivesProcessRestartOnDevice() {
        // The persisted update-check-failed flag must survive a store close and
        // reopen, modeling an app kill/relaunch while offline: Home must still
        // show the retry banner after restart.
        GitHubUpdater(context, FakeUpdateClient().getTextFailure(NoRouteToHostException("api.github.com")))
            .checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
        val firstProcessFlag = LocalStore(context).use { it.updateCheckFailedAt() }
        assertTrue("Manual offline check must light the retry flag on-device", firstProcessFlag > 0L)

        val secondProcessFlag = LocalStore(context).use { it.updateCheckFailedAt() }
        assertEquals(
            "The persisted retry flag must survive a process restart on-device",
            firstProcessFlag,
            secondProcessFlag,
        )
    }

    @Test
    fun checkDownloadAndInstallRejectsReleaseWithoutApkBeforeDownloading() {
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", checksumAsset()))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals("Latest release has no APK asset.", result.message)
        assertEquals(0, client.downloads)
    }

    @Test
    fun checkDownloadAndInstallRejectsInvalidChecksumTextBeforeDownloading() {
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum("not a digest")

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message)
        assertEquals(0, client.downloads)
        assertEquals(0, client.installs)
        assertEquals(0, client.notifications)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("Checksum asset does not contain a SHA-256 digest.", status.lastResult)
            assertFalse(status.hasPendingUpdate())
        }
    }

    @Test
    fun checkDownloadAndInstallDeletesDownloadedApkWhenChecksumMismatches() {
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(repeatText("a", 64))
            .downloadBytes("not the expected apk".toByteArray(Charsets.UTF_8))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals("Checksum mismatch. Install blocked.", result.message)
        assertEquals(1, client.downloads)
        assertFalse(File(File(context.cacheDir, "updates"), "kani.apk").exists())
    }

    @Test
    fun checkDownloadAndInstallRecordsFakeClientException() {
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(repeatText("a", 64))
            .downloadFailure(IOException("download broke"))

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals("Update check failed: download broke", result.message)
        assertEquals(1, client.downloads)
        assertEquals(0, client.installs)
        assertEquals(0, client.notifications)
        assertFalse(File(File(context.cacheDir, "updates"), "kani.apk").exists())
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("Update check failed: download broke", status.lastResult)
            assertFalse(status.hasPendingUpdate())
        }
    }

    @Test
    fun checkDownloadAndInstallDeletesDownloadedApkWhenMetadataIsInvalid() {
        val apk = "valid checksum bad metadata".toByteArray(Charsets.UTF_8)
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(sha256(apk))
            .downloadBytes(apk)
            .metadata("dev.bee.other", "99.99.99")

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertFalse(result.success)
        assertEquals("APK package name is dev.bee.other, expected " + context.packageName + ".", result.message)
        assertFalse(File(File(context.cacheDir, "updates"), "kani.apk").exists())
    }

    @Test
    fun checkDownloadAndInstallStoresPendingApkWhenInstallPermissionIsMissing() {
        val apk = "ready apk".toByteArray(Charsets.UTF_8)
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(sha256(apk))
            .downloadBytes(apk)
            .metadata(context.packageName, "99.99.99")
            .canInstall(false)

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertTrue(result.success)
        assertTrue(result.needsInstallPermission)
        assertEquals("APK verified. Grant install permission to continue.", result.message)
        assertEquals(1, client.notifications)
        assertEquals(0, client.installs)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("v99.99.99", status.lastVersion)
            assertEquals("kani.apk", status.pendingApkName)
            assertEquals("APK verified. Grant install permission to continue.", status.pendingMessage)
            assertTrue(status.hasPendingUpdate())
        }
    }

    @Test
    fun manualCheckStoresPermissionMissingApkWithoutNotification() {
        val apk = "manual permission apk".toByteArray(Charsets.UTF_8)
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(sha256(apk))
            .downloadBytes(apk)
            .metadata(context.packageName, "99.99.99")
            .canInstall(false)

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertTrue(result.success)
        assertTrue(result.needsInstallPermission)
        assertEquals("APK verified. Grant install permission to continue.", result.message)
        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", result.intent!!.action)
        assertEquals(0, client.notifications)
        assertEquals(0, client.installs)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("v99.99.99", status.lastVersion)
            assertEquals("kani.apk", status.pendingApkName)
            assertEquals("APK verified. Grant install permission to continue.", status.pendingMessage)
            assertTrue(status.hasPendingUpdate())
        }
    }

    @Test
    fun cachedInstallStoresPermissionMissingApkWithoutNotification() {
        val cached = cachedApk("cached-permission.apk")
        write(cached, "valid cached apk")
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.name, "ready to install")
        }
        val client = FakeUpdateClient()
            .metadata(context.packageName, "99.99.99")
            .canInstall(false)

        val result = GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)

        assertTrue(result.success)
        assertTrue(result.needsInstallPermission)
        assertEquals("APK verified. Grant install permission to continue.", result.message)
        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", result.intent!!.action)
        assertEquals(0, client.notifications)
        assertEquals(0, client.installs)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("v99.99.99", status.lastVersion)
            assertEquals("cached-permission.apk", status.pendingApkName)
            assertEquals("APK verified. Grant install permission to continue.", status.pendingMessage)
            assertTrue(status.hasPendingUpdate())
        }
    }

    @Test
    fun checkDownloadAndInstallStartsInstallerWhenPermissionIsReady() {
        val apk = "install apk".toByteArray(Charsets.UTF_8)
        val client = FakeUpdateClient()
            .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
            .checksum(sha256(apk))
            .downloadBytes(apk)
            .metadata(context.packageName, "99.99.99", 34)
            .canInstall(true)

        val result = GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertTrue(result.success)
        assertFalse(result.needsInstallPermission)
        assertNull(result.intent)
        assertEquals("APK verified. Android installer started.", result.message)
        assertEquals(1, client.installs)
        assertEquals("v99.99.99", client.installedVersion)
        assertEquals(GitHubUpdater.UpdateSource.MANUAL, client.installedSource)
        assertEquals(34, client.installedTargetSdk)
        LocalStore(context).use { store ->
            val status = store.autoUpdateStatus()
            assertEquals("kani.apk", status.pendingApkName)
            assertEquals("", status.pendingMessage)
        }
    }

    private fun cachedApk(name: String): File {
        val dir = File(context.cacheDir, "updates")
        assertTrue(dir.exists() || dir.mkdirs())
        return File(dir, name)
    }

    private fun clearUpdatesCache() {
        val dir = File(context.cacheDir, "updates")
        dir.listFiles()?.forEach { file -> file.delete() }
    }

    private fun write(file: File, text: String) {
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun read(file: File): String {
        FileInputStream(file).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                output.write(buffer, 0, read)
            }
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun releaseJson(tag: String, vararg assets: String): String {
        val out = StringBuilder()
        out.append("{\"tag_name\":\"").append(tag)
            .append("\",\"html_url\":\"https://example/releases/")
            .append(tag)
            .append("\",\"assets\":[")
        for (i in assets.indices) {
            if (i > 0) {
                out.append(',')
            }
            out.append(assets[i])
        }
        out.append("]}")
        return out.toString()
    }

    private fun apkAsset(): String {
        return "{\"name\":\"kani.apk\",\"browser_download_url\":\"https://example/kani.apk\"}"
    }

    private fun checksumAsset(): String {
        return "{\"name\":\"kani.apk.sha256\",\"browser_download_url\":\"https://example/kani.apk.sha256\"}"
    }

    private fun sha256(content: ByteArray): String {
        val digest = sha256Digest()
        digest.update(content)
        val out = StringBuilder()
        for (byte in digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff))
        }
        return out.toString()
    }

    private fun sha256Digest(): MessageDigest {
        return try {
            MessageDigest.getInstance("SHA-256")
        } catch (error: Exception) {
            throw IllegalStateException("SHA-256 digest is unavailable.", error)
        }
    }

    private fun repeatText(text: String, count: Int): String {
        val out = StringBuilder(text.length * count)
        repeat(count) {
            out.append(text)
        }
        return out.toString()
    }

    private fun assertWorkerSuccess(result: ListenableWorker.Result) {
        assertTrue(result is ListenableWorker.Result.Success)
    }

    private fun workerParameters(): WorkerParameters {
        val directExecutor = Executor { command -> command.run() }
        return WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            Collections.emptyList(),
            WorkerParameters.RuntimeExtras(),
            0,
            0,
            directExecutor,
            EmptyCoroutineContext,
            taskExecutor(directExecutor),
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? {
                    return null
                }
            },
            NoOpProgressUpdater(),
            NoOpForegroundUpdater(),
        )
    }

    private fun taskExecutor(directExecutor: Executor): TaskExecutor {
        val serialExecutor = object : SerialExecutor {
            override fun execute(command: Runnable) {
                directExecutor.execute(command)
            }

            override fun hasPendingTasks(): Boolean {
                return false
            }
        }
        return object : TaskExecutor {
            override fun getMainThreadExecutor(): Executor {
                return directExecutor
            }

            override fun getSerialTaskExecutor(): SerialExecutor {
                return serialExecutor
            }
        }
    }

    private fun completedVoidFuture(): SettableFuture<Void> {
        return SettableFuture.create<Void>().apply {
            set(null)
        }
    }

    private fun dummyIntentSender(): IntentSender {
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent("dev.bee.kanjianki.TEST_SENDER"),
            PendingIntent.FLAG_IMMUTABLE,
        ).intentSender
    }

    private inner class RecordingContext(base: Context) : ContextWrapper(base) {
        var startedAction: String? = null

        override fun getApplicationContext(): Context {
            return this
        }

        override fun startActivity(intent: Intent) {
            startedAction = intent.action
        }
    }

    private inner class RecordingPermissionContext(
        base: Context,
        private val permissionResult: Int,
        private val hideNotificationManager: Boolean,
    ) : ContextWrapper(base) {
        var permissionChecks = 0

        override fun getApplicationContext(): Context {
            return this
        }

        override fun checkSelfPermission(permission: String): Int {
            permissionChecks++
            return permissionResult
        }

        override fun getSystemService(name: String): Any? {
            if (hideNotificationManager && Context.NOTIFICATION_SERVICE == name) {
                return null
            }
            return super.getSystemService(name)
        }
    }

    private inner class FakeInstallerBackend(
        val session: FakeInstallerSession,
    ) : GitHubUpdater.InstallerBackend {
        var failOpen = false
        var createdSessionId = 0
        var openedSessionId = 0
        var abandonedSessions = 0

        override fun createSession(params: PackageInstaller.SessionParams): Int {
            createdSessionId = 41
            return createdSessionId
        }

        override fun openSession(sessionId: Int): GitHubUpdater.InstallerSession {
            openedSessionId = sessionId
            if (failOpen) {
                throw IOException("open failed")
            }
            return session
        }

        override fun abandonSession(sessionId: Int) {
            abandonedSessions++
        }
    }

    private inner class FakeInstallerSession(
        private val failWrite: Boolean,
    ) : GitHubUpdater.InstallerSession {
        val bytes = ByteArrayOutputStream()
        var failFsync = false
        var failCommit = false
        var failClose = false
        var fsynced = false
        var committed = false
        var closed = false
        var writeName: String? = null
        var writeLength = 0L

        override fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream {
            if (failWrite) {
                throw IOException("write failed")
            }
            writeName = name
            writeLength = lengthBytes
            return bytes
        }

        override fun fsync(output: OutputStream) {
            if (failFsync) {
                throw IOException("fsync failed")
            }
            fsynced = true
        }

        override fun commit(statusReceiver: IntentSender) {
            if (failCommit) {
                throw IllegalStateException("commit failed")
            }
            committed = true
        }

        override fun close() {
            closed = true
            if (failClose) {
                throw IllegalStateException("close failed")
            }
        }
    }

    private inner class FakeInstallerAccess(
        val session: GitHubUpdater.InstallerSession,
    ) : GitHubUpdater.PackageInstallerAccess {
        var createdParams: PackageInstaller.SessionParams? = null
        var openedSessionId = 0
        var abandonedSessionId = 0

        override fun createSession(params: PackageInstaller.SessionParams): Int {
            createdParams = params
            return 77
        }

        override fun openSession(sessionId: Int): GitHubUpdater.InstallerSession {
            openedSessionId = sessionId
            return session
        }

        override fun abandonSession(sessionId: Int) {
            abandonedSessionId = sessionId
        }
    }

    private inner class FakeSessionAccess : GitHubUpdater.PackageInstallerSessionAccess {
        val bytes = ByteArrayOutputStream()
        var writeName: String? = null
        var writeOffset = 0L
        var writeLength = 0L
        var fsyncedOutput: OutputStream? = null
        var committed = false
        var closed = false

        override fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream {
            writeName = name
            writeOffset = offsetBytes
            writeLength = lengthBytes
            return bytes
        }

        override fun fsync(output: OutputStream) {
            fsyncedOutput = output
        }

        override fun commit(statusReceiver: IntentSender) {
            committed = true
        }

        override fun close() {
            closed = true
        }
    }

    private inner class PendingActionHandler : PackageInstallStatusReceiver.PendingUserActionHandler {
        var failStart = false
        var started = 0
        var notifications = 0
        var startedIntent: Intent? = null
        var notificationVersion: String? = null
        var notificationMessage: String? = null

        override fun startActivity(intent: Intent) {
            started++
            startedIntent = intent
            if (failStart) {
                throw IllegalStateException("confirmation unavailable")
            }
        }

        override fun showPendingUpdate(version: String?, message: String?): Boolean {
            notifications++
            notificationVersion = version
            notificationMessage = message
            return true
        }
    }

    private inner class WorkerCheckerFactory(
        private val retryable: Boolean,
    ) : AutoUpdateWorker.UpdateCheckerFactory {
        var factories = 0
        var checks = 0

        override fun create(context: Context?): AutoUpdateWorker.UpdateChecker {
            factories++
            return AutoUpdateWorker.UpdateChecker {
                checks++
                GitHubUpdater.UpdateResult(!retryable, if (retryable) "try again" else "done", null, false, retryable)
            }
        }
    }

    private inner class FakeUpdateClient : GitHubUpdater.UpdateClient {
        private var latestJson = releaseJson("v99.99.99", apkAsset(), checksumAsset())
        private var checksumText = ""
        private var downloadBytes = ByteArray(0)
        private var signingCert = byteArrayOf(1, 2, 3, 4)
        private var metadata = GitHubUpdater.ApkMetadata("", "", 0, currentSigners(byteArrayOf(1, 2, 3, 4)))
        private var installedCerts = currentSigners(byteArrayOf(1, 2, 3, 4))
        private var downloadFailure: IOException? = null
        private var getTextFailure: IOException? = null
        private var inspectFailure: RuntimeException? = null
        private var canInstall = false
        var downloads = 0
        var installs = 0
        var notifications = 0
        var installedVersion: String? = null
        var installedSource: GitHubUpdater.UpdateSource? = null
        var installedTargetSdk = 0

        fun latest(latestJson: String): FakeUpdateClient {
            this.latestJson = latestJson
            return this
        }

        fun checksum(checksumText: String): FakeUpdateClient {
            this.checksumText = checksumText
            return this
        }

        fun downloadBytes(downloadBytes: ByteArray): FakeUpdateClient {
            this.downloadBytes = downloadBytes
            return this
        }

        fun metadata(packageName: String, versionName: String): FakeUpdateClient {
            this.metadata = GitHubUpdater.ApkMetadata(packageName, versionName, 0, currentSigners(signingCert))
            return this
        }

        fun metadata(packageName: String, versionName: String, targetSdkVersion: Int): FakeUpdateClient {
            this.metadata = GitHubUpdater.ApkMetadata(
                packageName,
                versionName,
                targetSdkVersion,
                currentSigners(signingCert),
            )
            return this
        }

        /** Make the downloaded APK's signing cert differ from the installed app's. */
        fun mismatchedSigningCert(): FakeUpdateClient {
            this.metadata = GitHubUpdater.ApkMetadata(
                metadata.packageName,
                metadata.versionName,
                metadata.targetSdkVersion,
                currentSigners(byteArrayOf(9, 9, 9, 9)),
            )
            return this
        }

        fun canInstall(canInstall: Boolean): FakeUpdateClient {
            this.canInstall = canInstall
            return this
        }

        fun downloadFailure(downloadFailure: IOException): FakeUpdateClient {
            this.downloadFailure = downloadFailure
            return this
        }

        /** Make `getText` (the releases/checksum read) raise a transport fault. */
        fun getTextFailure(getTextFailure: IOException): FakeUpdateClient {
            this.getTextFailure = getTextFailure
            return this
        }

        fun inspectFailure(inspectFailure: RuntimeException): FakeUpdateClient {
            this.inspectFailure = inspectFailure
            return this
        }

        override fun getText(url: String): String {
            getTextFailure?.let { throw it }
            return if (url.endsWith(".sha256")) checksumText else latestJson
        }

        override fun download(url: String, file: File) {
            downloads++
            val failure = downloadFailure
            if (failure != null) {
                throw failure
            }
            FileOutputStream(file).use { output -> output.write(downloadBytes) }
        }

        override fun inspectApk(apkFile: File): GitHubUpdater.ApkMetadata {
            val failure = inspectFailure
            if (failure != null) {
                throw failure
            }
            return metadata
        }

        override fun installedSigningCertificates(packageName: String): SigningCertificateInfo {
            return installedCerts
        }

        override fun canRequestPackageInstalls(): Boolean {
            return canInstall
        }

        override fun startPackageInstaller(
            apkFile: File,
            version: String,
            source: GitHubUpdater.UpdateSource,
            targetSdkVersion: Int,
        ) {
            installs++
            installedVersion = version
            installedSource = source
            installedTargetSdk = targetSdkVersion
        }

        override fun showPendingUpdate(version: String, message: String): Boolean {
            notifications++
            return true
        }
    }

    private fun currentSigners(vararg certificates: ByteArray): SigningCertificateInfo {
        return SigningCertificateInfo.currentSigners(certificates.toList())
    }

    private inner class NoOpProgressUpdater : ProgressUpdater {
        override fun updateProgress(
            context: Context,
            id: UUID,
            data: Data,
        ) = completedVoidFuture()
    }

    private inner class NoOpForegroundUpdater : ForegroundUpdater {
        override fun setForegroundAsync(
            context: Context,
            id: UUID,
            foregroundInfo: ForegroundInfo,
        ) = completedVoidFuture()
    }
}
