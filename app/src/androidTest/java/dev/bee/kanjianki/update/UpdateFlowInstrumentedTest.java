package dev.bee.kanjianki.update;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInstaller;
import android.net.Uri;

import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.BuildConfig;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import kotlin.coroutines.EmptyCoroutineContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class UpdateFlowInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        clearUpdatesCache();
    }

    @After
    public void tearDown() {
        context.deleteDatabase("kanji_anki_simple.db");
        clearUpdatesCache();
    }

    @Test
    public void packageInstallReceiverIgnoresUnrelatedIntents() {
        new PackageInstallStatusReceiver().onReceive(context, null);
        new PackageInstallStatusReceiver().onReceive(context, new Intent("other.action"));

        try (LocalStore store = new LocalStore(context)) {
            assertEquals("No automatic update check has run yet.", store.autoUpdateStatus().lastResult);
        }
    }

    @Test
    public void successfulPackageInstallClearsPendingUpdateAndDeletesCachedApk() throws Exception {
        File cached = cachedApk("kani.apk");
        write(cached, "placeholder");
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(10L, "pending", "v9.9.9", cached.getName(), "waiting");
        }

        Intent intent = PackageInstallStatusReceiver.callbackIntent(
                context,
                "../" + cached.getName(),
                "v9.9.9",
                GitHubUpdater.UpdateSource.MANUAL
        ).putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS);
        new PackageInstallStatusReceiver().onReceive(context, intent);

        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("Install finished.", status.lastResult);
            assertEquals("v9.9.9", status.lastVersion);
            assertFalse(status.hasPendingUpdate());
        }
        assertFalse(cached.exists());
    }

    @Test
    public void failedPackageInstallRecordsInstallerMessageAndClearsPendingApk() {
        Intent intent = PackageInstallStatusReceiver.callbackIntent(
                        context,
                        "failed.apk",
                        "v1.0.0",
                        GitHubUpdater.UpdateSource.AUTOMATIC
                )
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "blocked by Android");

        new PackageInstallStatusReceiver().onReceive(context, intent);

        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("Install failed: blocked by Android.", status.lastResult);
            assertEquals("v1.0.0", status.lastVersion);
            assertFalse(status.hasPendingUpdate());
        }
    }

    @Test
    public void pendingUserActionKeepsVerifiedApkForLaterInstall() {
        Intent intent = PackageInstallStatusReceiver.callbackIntent(
                        context,
                        "waiting.apk",
                        "v2.0.0",
                        GitHubUpdater.UpdateSource.AUTOMATIC
                )
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION);

        new PackageInstallStatusReceiver().onReceive(context, intent);

        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("Android needs confirmation to finish installing.", status.lastResult);
            assertEquals("v2.0.0", status.lastVersion);
            assertEquals("waiting.apk", status.pendingApkName);
            assertTrue(status.hasPendingUpdate());
        }
    }

    @Test
    public void pendingUserActionStartsConfirmationForInteractiveSourcesOnly() {
        PendingActionHandler manual = new PendingActionHandler();
        Intent manualConfirmation = new Intent("dev.bee.kanjianki.CONFIRM_MANUAL");
        Intent manualStatus = new Intent().putExtra(Intent.EXTRA_INTENT, manualConfirmation);

        PackageInstallStatusReceiver.handlePendingUserAction(
                manualStatus,
                GitHubUpdater.UpdateSource.MANUAL,
                "v2.0.0",
                "confirm",
                manual
        );

        assertEquals(1, manual.started);
        assertEquals(0, manual.notifications);
        assertEquals("dev.bee.kanjianki.CONFIRM_MANUAL", manual.startedIntent.getAction());
        assertTrue((manual.startedIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);

        PendingActionHandler automatic = new PendingActionHandler();
        Intent automaticStatus = new Intent().putExtra(Intent.EXTRA_INTENT, new Intent("dev.bee.kanjianki.CONFIRM_AUTO"));

        PackageInstallStatusReceiver.handlePendingUserAction(
                automaticStatus,
                GitHubUpdater.UpdateSource.AUTOMATIC,
                "v2.0.1",
                "notify instead",
                automatic
        );

        assertEquals(0, automatic.started);
        assertEquals(1, automatic.notifications);
        assertEquals("v2.0.1", automatic.notificationVersion);
        assertEquals("notify instead", automatic.notificationMessage);

        PendingActionHandler missingConfirmation = new PendingActionHandler();
        PackageInstallStatusReceiver.handlePendingUserAction(
                new Intent(),
                GitHubUpdater.UpdateSource.CACHED,
                "v2.0.2",
                "no confirmation intent",
                missingConfirmation
        );

        assertEquals(0, missingConfirmation.started);
        assertEquals(1, missingConfirmation.notifications);
    }

    @Test
    public void sourceMappingDefaultsInvalidOrMissingValuesToAutomatic() {
        assertEquals(GitHubUpdater.UpdateSource.AUTOMATIC, PackageInstallStatusReceiver.sourceFrom(null));
        assertEquals(GitHubUpdater.UpdateSource.AUTOMATIC, PackageInstallStatusReceiver.sourceFrom("not-real"));
        assertEquals(GitHubUpdater.UpdateSource.CACHED, PackageInstallStatusReceiver.sourceFrom("CACHED"));
    }

    @Test
    public void callbackIntentDefaultsMissingFieldsAndKeepsExplicitFields() {
        Intent defaults = PackageInstallStatusReceiver.callbackIntent(context, null, null, null);
        Intent explicit = PackageInstallStatusReceiver.callbackIntent(
                context,
                "kani.apk",
                "v1.2.3",
                GitHubUpdater.UpdateSource.CACHED
        );

        assertEquals(PackageInstallStatusReceiver.ACTION_INSTALL_STATUS, defaults.getAction());
        assertEquals("", defaults.getStringExtra("dev.bee.kanjianki.extra.APK_NAME"));
        assertEquals("", defaults.getStringExtra("dev.bee.kanjianki.extra.VERSION"));
        assertEquals("AUTOMATIC", defaults.getStringExtra("dev.bee.kanjianki.extra.SOURCE"));
        assertEquals("kani.apk", explicit.getStringExtra("dev.bee.kanjianki.extra.APK_NAME"));
        assertEquals("v1.2.3", explicit.getStringExtra("dev.bee.kanjianki.extra.VERSION"));
        assertEquals("CACHED", explicit.getStringExtra("dev.bee.kanjianki.extra.SOURCE"));
    }

    @Test
    public void pendingUserActionNullIntentFallsBackToNotification() {
        PendingActionHandler handler = new PendingActionHandler();

        PackageInstallStatusReceiver.handlePendingUserAction(
                null,
                GitHubUpdater.UpdateSource.MANUAL,
                "v3.0.0",
                "confirm later",
                handler
        );

        assertEquals(0, handler.started);
        assertEquals(1, handler.notifications);
        assertEquals("v3.0.0", handler.notificationVersion);
        assertEquals("confirm later", handler.notificationMessage);
    }

    @Test
    public void packageInstallReceiverDeleteSeamHandlesEmptyMissingDeletedAndFailedCacheFiles() throws Exception {
        assertFalse(PackageInstallStatusReceiver.deleteCachedApk(context, null, File::delete));
        assertFalse(PackageInstallStatusReceiver.deleteCachedApk(context, "   ", File::delete));
        assertFalse(PackageInstallStatusReceiver.deleteCachedApk(context, "missing.apk", File::delete));

        File deleted = cachedApk("delete-ok.apk");
        write(deleted, "delete me");
        assertTrue(PackageInstallStatusReceiver.deleteCachedApk(context, "../" + deleted.getName(), File::delete));
        assertFalse(deleted.exists());

        File blocked = cachedApk("delete-blocked.apk");
        write(blocked, "keep me");
        assertFalse(PackageInstallStatusReceiver.deleteCachedApk(context, blocked.getName(), file -> false));
        assertTrue(blocked.exists());
    }

    @Test
    public void githubUpdaterDeleteSeamReportsFailedCacheDeletion() throws Exception {
        File blocked = cachedApk("github-delete-blocked.apk");
        write(blocked, "keep me");

        assertFalse(GitHubUpdater.deleteCachedApk(blocked, file -> false));
        assertTrue(blocked.exists());
    }

    @Test
    public void androidPendingUserActionHandlerStartsActivityOnWrappedContext() {
        RecordingContext recording = new RecordingContext(context);
        PackageInstallStatusReceiver.PendingUserActionHandler handler =
                PackageInstallStatusReceiver.androidPendingUserActionHandler(recording);

        handler.startActivity(new Intent("dev.bee.kanjianki.TEST_START"));

        assertEquals("dev.bee.kanjianki.TEST_START", recording.startedAction);
    }

    @Test
    public void updateNotifierAndroidControllerCoversPermissionAndMissingManagerBranches() {
        RecordingPermissionContext pre33 = new RecordingPermissionContext(context, PackageManager.PERMISSION_DENIED, false);
        UpdateNotifier.AndroidNotificationController pre33Controller = new UpdateNotifier.AndroidNotificationController(pre33, 32);

        assertTrue(pre33Controller.hasRuntimeNotificationPermission());
        assertEquals(0, pre33.permissionChecks);

        RecordingPermissionContext denied = new RecordingPermissionContext(context, PackageManager.PERMISSION_DENIED, false);
        RecordingPermissionContext granted = new RecordingPermissionContext(context, PackageManager.PERMISSION_GRANTED, false);

        assertFalse(new UpdateNotifier.AndroidNotificationController(denied, 33).hasRuntimeNotificationPermission());
        assertTrue(new UpdateNotifier.AndroidNotificationController(granted, 33).hasRuntimeNotificationPermission());
        assertEquals(1, denied.permissionChecks);
        assertEquals(1, granted.permissionChecks);

        RecordingPermissionContext noManager = new RecordingPermissionContext(context, PackageManager.PERMISSION_GRANTED, true);
        UpdateNotifier.AndroidNotificationController noManagerController = new UpdateNotifier.AndroidNotificationController(noManager, 33);

        assertFalse(noManagerController.areNotificationsEnabled());
        noManagerController.ensureChannel();
        noManagerController.notifyUpdate("Kani update ready", "Ready");
    }

    @Test
    public void updateNotifierAndroidControllerPostsWhenManagerExists() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.POST_NOTIFICATIONS);
        try {
            UpdateNotifier.AndroidNotificationController controller = new UpdateNotifier.AndroidNotificationController(context, 33);

            assertTrue(controller.hasRuntimeNotificationPermission());
            assertTrue(controller.areNotificationsEnabled());
            controller.ensureChannel();
            controller.notifyUpdate("Kani update ready", "Ready");
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void notificationEnabledHelperUsesManagerWhenPresent() {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        assertNotNull(manager);
        assertFalse(UpdateNotifier.notificationsEnabled(manager, ignored -> false));
        assertTrue(UpdateNotifier.notificationsEnabled(manager, ignored -> true));
    }

    @Test
    public void apkMetadataNormalizesMissingAndPresentPackageInfoFields() {
        GitHubUpdater.ApkMetadata missing = GitHubUpdater.metadataFromPackageInfo(null);
        assertEquals("", missing.packageName);
        assertEquals("", missing.versionName);

        PackageInfo bothNull = new PackageInfo();
        GitHubUpdater.ApkMetadata empty = GitHubUpdater.metadataFromPackageInfo(bothNull);
        assertEquals("", empty.packageName);
        assertEquals("", empty.versionName);

        PackageInfo packageOnly = new PackageInfo();
        packageOnly.packageName = "dev.bee.kanjianki";
        GitHubUpdater.ApkMetadata packageOnlyMetadata = GitHubUpdater.metadataFromPackageInfo(packageOnly);
        assertEquals("dev.bee.kanjianki", packageOnlyMetadata.packageName);
        assertEquals("", packageOnlyMetadata.versionName);

        PackageInfo versionOnly = new PackageInfo();
        versionOnly.versionName = "9.9.9";
        GitHubUpdater.ApkMetadata versionOnlyMetadata = GitHubUpdater.metadataFromPackageInfo(versionOnly);
        assertEquals("", versionOnlyMetadata.packageName);
        assertEquals("9.9.9", versionOnlyMetadata.versionName);

        PackageInfo complete = new PackageInfo();
        complete.packageName = "dev.bee.kanjianki";
        complete.versionName = "9.9.9";
        GitHubUpdater.ApkMetadata completeMetadata = GitHubUpdater.metadataFromPackageInfo(complete);
        assertEquals("dev.bee.kanjianki", completeMetadata.packageName);
        assertEquals("9.9.9", completeMetadata.versionName);
    }

    @Test
    public void packageInstallerSeamWritesCommitsClosesAndAbandonsOnlyFailures() throws Exception {
        File apk = cachedApk("installer-good.apk");
        byte[] apkBytes = new byte[70_000];
        for (int i = 0; i < apkBytes.length; i++) {
            apkBytes[i] = (byte) (i * 13 + 11);
        }
        try (FileOutputStream output = new FileOutputStream(apk)) {
            output.write(apkBytes);
        }
        FakeInstallerBackend success = new FakeInstallerBackend(new FakeInstallerSession(false));

        GitHubUpdater.startPackageInstaller(context, success, apk, "v8.0.0", GitHubUpdater.UpdateSource.MANUAL);

        assertEquals(41, success.createdSessionId);
        assertEquals(41, success.openedSessionId);
        assertEquals(0, success.abandonedSessions);
        assertTrue(success.session.committed);
        assertTrue(success.session.closed);
        assertTrue(success.session.fsynced);
        assertEquals("kani-update.apk", success.session.writeName);
        assertEquals(apk.length(), success.session.writeLength);
        assertEquals(sha256(apkBytes), sha256(success.session.bytes.toByteArray()));

        FakeInstallerBackend openFailure = new FakeInstallerBackend(new FakeInstallerSession(false));
        openFailure.failOpen = true;
        IOException openError;
        try {
            GitHubUpdater.startPackageInstaller(context, openFailure, apk, "v8.0.0", GitHubUpdater.UpdateSource.MANUAL);
            throw new AssertionError("Expected IOException");
        } catch (IOException caught) {
            openError = caught;
        }

        assertEquals("open failed", openError.getMessage());
        assertEquals(1, openFailure.abandonedSessions);
        assertFalse(openFailure.session.closed);

        FakeInstallerBackend writeFailure = new FakeInstallerBackend(new FakeInstallerSession(true));
        IOException writeError;
        try {
            GitHubUpdater.startPackageInstaller(context, writeFailure, apk, "v8.0.0", GitHubUpdater.UpdateSource.MANUAL);
            throw new AssertionError("Expected IOException");
        } catch (IOException caught) {
            writeError = caught;
        }

        assertEquals("write failed", writeError.getMessage());
        assertEquals(1, writeFailure.abandonedSessions);
        assertTrue(writeFailure.session.closed);
        assertFalse(writeFailure.session.committed);
    }

    @Test
    public void packageInstallerSeamAbandonsWhenFsyncOrCommitFails() throws Exception {
        File apk = cachedApk("installer-failure.apk");
        write(apk, "installer bytes");

        FakeInstallerSession fsyncSession = new FakeInstallerSession(false);
        fsyncSession.failFsync = true;
        FakeInstallerBackend fsyncFailure = new FakeInstallerBackend(fsyncSession);
        IOException fsyncError;
        try {
            GitHubUpdater.startPackageInstaller(context, fsyncFailure, apk, "v8.0.1", GitHubUpdater.UpdateSource.MANUAL);
            throw new AssertionError("Expected IOException");
        } catch (IOException caught) {
            fsyncError = caught;
        }

        assertEquals("fsync failed", fsyncError.getMessage());
        assertEquals(1, fsyncFailure.abandonedSessions);
        assertTrue(fsyncFailure.session.closed);
        assertFalse(fsyncFailure.session.committed);

        FakeInstallerSession commitSession = new FakeInstallerSession(false);
        commitSession.failCommit = true;
        FakeInstallerBackend commitFailure = new FakeInstallerBackend(commitSession);
        IllegalStateException commitError;
        try {
            GitHubUpdater.startPackageInstaller(context, commitFailure, apk, "v8.0.1", GitHubUpdater.UpdateSource.MANUAL);
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException caught) {
            commitError = caught;
        }

        assertEquals("commit failed", commitError.getMessage());
        assertEquals(1, commitFailure.abandonedSessions);
        assertTrue(commitFailure.session.closed);
        assertTrue(commitFailure.session.fsynced);
        assertFalse(commitFailure.session.committed);
    }

    @Test
    public void androidInstallerBackendDelegatesToInstallerAccess() throws Exception {
        FakeInstallerSession session = new FakeInstallerSession(false);
        FakeInstallerAccess access = new FakeInstallerAccess(session);
        GitHubUpdater.AndroidInstallerBackend backend = new GitHubUpdater.AndroidInstallerBackend(access);
        PackageInstaller.SessionParams params = GitHubUpdater.sessionParams(context.getPackageName(), 30);

        assertEquals(77, backend.createSession(params));
        assertSame(session, backend.openSession(77));
        backend.abandonSession(77);

        assertSame(params, access.createdParams);
        assertEquals(77, access.openedSessionId);
        assertEquals(77, access.abandonedSessionId);
    }

    @Test
    public void androidInstallerSessionDelegatesToSessionAccess() throws Exception {
        FakeSessionAccess access = new FakeSessionAccess();
        GitHubUpdater.AndroidInstallerSession session = new GitHubUpdater.AndroidInstallerSession(access);

        OutputStream output = session.openWrite("kani-update.apk", 5L, 12L);
        output.write(42);
        session.fsync(output);
        session.commit(null);
        session.close();

        assertEquals("kani-update.apk", access.writeName);
        assertEquals(5L, access.writeOffset);
        assertEquals(12L, access.writeLength);
        assertEquals(42, access.bytes.toByteArray()[0]);
        assertSame(output, access.fsyncedOutput);
        assertTrue(access.committed);
        assertTrue(access.closed);
    }

    @Test
    public void installerSessionFactoryDelegatesOperationsToSuppliedCallbacks() throws Exception {
        FakeSessionAccess access = new FakeSessionAccess();
        GitHubUpdater.InstallerSession session = GitHubUpdater.installerSession(
                access::openWrite,
                access::fsync,
                access::commit,
                access::close
        );

        OutputStream output = session.openWrite("factory.apk", 2L, 4L);
        output.write(7);
        session.fsync(output);
        session.commit(null);
        session.close();

        assertEquals("factory.apk", access.writeName);
        assertEquals(2L, access.writeOffset);
        assertEquals(4L, access.writeLength);
        assertEquals(7, access.bytes.toByteArray()[0]);
        assertSame(output, access.fsyncedOutput);
        assertTrue(access.committed);
        assertTrue(access.closed);
    }

    @Test
    public void packageInstallerFactoriesWrapRealInstallerSessionAndCallbacks() throws Exception {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        GitHubUpdater.InstallerBackend backend = GitHubUpdater.installerBackend(packageInstaller);
        PackageInstaller.SessionParams params = GitHubUpdater.sessionParams(context.getPackageName(), 30);

        int sessionId = backend.createSession(params);
        GitHubUpdater.InstallerSession session = null;
        try {
            session = backend.openSession(sessionId);
            try (OutputStream output = session.openWrite("kani-update.apk", 0, 3)) {
                output.write(new byte[]{1, 2, 3});
                session.fsync(output);
            }
        } finally {
            if (session != null) {
                session.close();
            }
            backend.abandonSession(sessionId);
        }

        try {
            session.commit(null);
        } catch (RuntimeException ignored) {
            assertNotNull(ignored.getClass().getSimpleName());
        }
    }

    @Test
    public void androidUpdateClientDelegatesPlatformOperationsAndInstallerBackendFactory() throws Exception {
        File apk = cachedApk("android-client.apk");
        write(apk, "android client installer bytes");
        FakeInstallerBackend backend = new FakeInstallerBackend(new FakeInstallerSession(false));
        GitHubUpdater.UpdateClient client = GitHubUpdater.androidClient(
                context,
                appContext -> {
                    assertEquals(context.getPackageName(), appContext.getPackageName());
                    return backend;
                },
                url -> "text from " + url,
                (url, file) -> write(file, "download from " + url)
        );

        assertEquals("text from https://example.test/latest", client.getText("https://example.test/latest"));
        File target = cachedApk("network-target.apk");
        client.download("https://example.test/kani.apk", target);
        assertEquals("download from https://example.test/kani.apk", read(target));
        GitHubUpdater.ApkMetadata metadata = client.inspectApk(apk);
        assertEquals("", metadata.packageName);
        assertEquals("", metadata.versionName);
        assertEquals(context.getPackageManager().canRequestPackageInstalls(), client.canRequestPackageInstalls());

        client.showPendingUpdate("v8.0.2", "ready");
        client.startPackageInstaller(apk, "v8.0.2", GitHubUpdater.UpdateSource.MANUAL);

        assertEquals(41, backend.createdSessionId);
        assertEquals(41, backend.openedSessionId);
        assertTrue(backend.session.committed);
        assertTrue(backend.session.closed);
    }

    @Test
    public void packageInstallerSessionParamsCoverPreAndPostUserActionApis() {
        assertNotNull(GitHubUpdater.sessionParams(context.getPackageName(), 30));
        assertNotNull(GitHubUpdater.sessionParams(context.getPackageName(), 31));
    }

    @Test
    public void autoUpdateWorkerConstructorAndDoWorkUseStoredDisabledState() {
        try (LocalStore store = new LocalStore(context)) {
            store.saveAutoUpdateEnabled(false);
        }

        AutoUpdateWorker worker = new AutoUpdateWorker(context, workerParameters());

        assertWorkerSuccess(worker.doWork());
    }

    @Test
    public void autoUpdateWorkerCheckAutomaticUpdateUsesConfiguredClientFactory() throws Exception {
        byte[] apk = "automatic worker apk".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(sha256(apk))
                .downloadBytes(apk)
                .metadata(context.getPackageName(), "99.99.99")
                .canInstall(false);
        AutoUpdateWorker.UpdateClientFactory originalFactory = AutoUpdateWorker.updateClientFactory;
        try {
            AutoUpdateWorker.updateClientFactory = ignored -> client;

            GitHubUpdater.UpdateResult result = AutoUpdateWorker.checkAutomaticUpdate(context);

            assertTrue(result.success);
            assertTrue(result.needsInstallPermission);
            assertEquals("APK verified. Grant install permission to continue.", result.message);
            assertEquals(1, client.downloads);
            assertEquals(1, client.notifications);
            assertEquals(0, client.installs);
        } finally {
            AutoUpdateWorker.updateClientFactory = originalFactory;
        }
    }

    @Test
    public void cachedInstallReportsMissingPendingStateAndMissingApk() {
        GitHubUpdater updater = new GitHubUpdater(context);
        GitHubUpdater.UpdateResult noPending = updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED);

        assertFalse(noPending.success);
        assertEquals("No verified APK is waiting to install.", noPending.message);

        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(10L, "pending", "v7.0.0", "missing.apk", "ready");
        }

        GitHubUpdater.UpdateResult missing = updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED);

        assertFalse(missing.success);
        assertEquals("Verified APK cache is missing. Check again to download it.", missing.message);
        try (LocalStore store = new LocalStore(context)) {
            assertEquals("Verified APK cache is missing. Check again to download it.", store.autoUpdateStatus().lastResult);
        }
    }

    @Test
    public void cachedInstallDeletesInvalidVerifiedApk() throws Exception {
        File cached = cachedApk("invalid.apk");
        write(cached, "not an apk");
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(10L, "pending", "v7.0.0", cached.getName(), "ready");
        }

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED);

        assertFalse(result.success);
        assertEquals("APK metadata could not be read. Install blocked.", result.message);
        assertFalse(cached.exists());
        try (LocalStore store = new LocalStore(context)) {
            assertFalse(store.autoUpdateStatus().hasPendingUpdate());
        }
    }

    @Test
    public void cachedInstallStartsInstallerForValidVerifiedApk() throws Exception {
        File cached = cachedApk("cached-good.apk");
        write(cached, "valid cached apk");
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.getName(), "ready to install");
        }
        FakeUpdateClient client = new FakeUpdateClient()
                .metadata(context.getPackageName(), "99.99.99")
                .canInstall(true);

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED);

        assertTrue(result.success);
        assertFalse(result.needsInstallPermission);
        assertEquals("APK verified. Android installer started.", result.message);
        assertEquals(1, client.installs);
        assertEquals("v99.99.99", client.installedVersion);
        assertEquals(GitHubUpdater.UpdateSource.CACHED, client.installedSource);
        assertEquals(0, client.notifications);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("cached-good.apk", status.pendingApkName);
            assertEquals("", status.pendingMessage);
            assertTrue(status.hasPendingUpdate());
        }
    }

    @Test
    public void cachedInstallRecordsFakeClientExceptionAndKeepsPendingApk() throws Exception {
        File cached = cachedApk("cached-throws.apk");
        write(cached, "cached apk");
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.getName(), "ready to install");
        }
        FakeUpdateClient client = new FakeUpdateClient()
                .inspectFailure(new IllegalStateException("metadata reader failed"));

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED);

        assertFalse(result.success);
        assertEquals("Update install failed: metadata reader failed", result.message);
        assertEquals(0, client.installs);
        assertEquals(0, client.notifications);
        assertTrue(cached.exists());
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("Update install failed: metadata reader failed", status.lastResult);
            assertEquals("cached-throws.apk", status.pendingApkName);
            assertEquals("ready to install", status.pendingMessage);
            assertTrue(status.hasPendingUpdate());
        }
    }

    @Test
    public void installPermissionIntentTargetsThisPackage() {
        Intent intent = GitHubUpdater.installPermissionIntent(context);

        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", intent.getAction());
        assertEquals(Uri.parse("package:" + context.getPackageName()), intent.getData());
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void autoUpdateWorkerEntryReadsStoreAndRunsCheckerOnlyWhenEligible() {
        try (LocalStore store = new LocalStore(context)) {
            store.saveAutoUpdateEnabled(false);
        }
        WorkerCheckerFactory disabled = new WorkerCheckerFactory(false);

        assertWorkerSuccess(AutoUpdateWorker.runFromStore(context, disabled));

        assertEquals(0, disabled.factories);
        assertEquals(0, disabled.checks);

        try (LocalStore store = new LocalStore(context)) {
            store.saveAutoUpdateEnabled(true);
            store.recordAutoUpdateResult(10L, "pending", "v9.9.9", "pending.apk", "ready");
        }
        WorkerCheckerFactory pending = new WorkerCheckerFactory(false);

        assertWorkerSuccess(AutoUpdateWorker.runFromStore(context, pending));

        assertEquals(0, pending.factories);
        assertEquals(0, pending.checks);

        try (LocalStore store = new LocalStore(context)) {
            store.clearPendingAutoUpdate("cleared");
        }
        WorkerCheckerFactory eligible = new WorkerCheckerFactory(false);

        assertWorkerSuccess(AutoUpdateWorker.runFromStore(context, eligible));

        assertEquals(1, eligible.factories);
        assertEquals(1, eligible.checks);
    }

    @Test
    public void autoUpdateWorkerEntryReturnsRetryForRetryableCheckerResult() {
        try (LocalStore store = new LocalStore(context)) {
            store.saveAutoUpdateEnabled(true);
            store.clearPendingAutoUpdate("ready");
        }
        WorkerCheckerFactory retryable = new WorkerCheckerFactory(true);

        ListenableWorker.Result result = AutoUpdateWorker.runFromStore(context, retryable);

        assertTrue(result instanceof ListenableWorker.Result.Retry);
        assertEquals(1, retryable.factories);
        assertEquals(1, retryable.checks);
    }

    @Test
    public void checkDownloadAndInstallRecordsAlreadyCurrentReleaseWithoutDownloading() {
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v" + BuildConfig.VERSION_NAME, apkAsset(), checksumAsset()));

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertFalse(result.success);
        assertEquals("Already on " + BuildConfig.VERSION_NAME + ".", result.message);
        assertEquals(0, client.downloads);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("v" + BuildConfig.VERSION_NAME, status.lastVersion);
            assertFalse(status.hasPendingUpdate());
        }
    }

    @Test
    public void checkDownloadAndInstallRejectsReleaseWithoutApkBeforeDownloading() {
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", checksumAsset()));

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertFalse(result.success);
        assertEquals("Latest release has no APK asset.", result.message);
        assertEquals(0, client.downloads);
    }

    @Test
    public void checkDownloadAndInstallRejectsInvalidChecksumTextBeforeDownloading() {
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum("not a digest");

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertFalse(result.success);
        assertEquals("Checksum asset does not contain a SHA-256 digest.", result.message);
        assertEquals(0, client.downloads);
        assertEquals(0, client.installs);
        assertEquals(0, client.notifications);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("Checksum asset does not contain a SHA-256 digest.", status.lastResult);
            assertFalse(status.hasPendingUpdate());
        }
    }

    @Test
    public void checkDownloadAndInstallDeletesDownloadedApkWhenChecksumMismatches() throws Exception {
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(repeat("a", 64))
                .downloadBytes("not the expected apk".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertFalse(result.success);
        assertEquals("Checksum mismatch. Install blocked.", result.message);
        assertEquals(1, client.downloads);
        assertFalse(new File(new File(context.getCacheDir(), "updates"), "kani.apk").exists());
    }

    @Test
    public void checkDownloadAndInstallRecordsFakeClientException() {
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(repeat("a", 64))
                .downloadFailure(new Exception("download broke"));

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertFalse(result.success);
        assertEquals("Update check failed: download broke", result.message);
        assertEquals(1, client.downloads);
        assertEquals(0, client.installs);
        assertEquals(0, client.notifications);
        assertFalse(new File(new File(context.getCacheDir(), "updates"), "kani.apk").exists());
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("Update check failed: download broke", status.lastResult);
            assertFalse(status.hasPendingUpdate());
        }
    }

    @Test
    public void checkDownloadAndInstallDeletesDownloadedApkWhenMetadataIsInvalid() throws Exception {
        byte[] apk = "valid checksum bad metadata".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(sha256(apk))
                .downloadBytes(apk)
                .metadata("dev.bee.other", "99.99.99");

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertFalse(result.success);
        assertEquals("APK package name is dev.bee.other, expected " + context.getPackageName() + ".", result.message);
        assertFalse(new File(new File(context.getCacheDir(), "updates"), "kani.apk").exists());
    }

    @Test
    public void checkDownloadAndInstallStoresPendingApkWhenInstallPermissionIsMissing() throws Exception {
        byte[] apk = "ready apk".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(sha256(apk))
                .downloadBytes(apk)
                .metadata(context.getPackageName(), "99.99.99")
                .canInstall(false);

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC);

        assertTrue(result.success);
        assertTrue(result.needsInstallPermission);
        assertEquals("APK verified. Grant install permission to continue.", result.message);
        assertEquals(1, client.notifications);
        assertEquals(0, client.installs);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("v99.99.99", status.lastVersion);
            assertEquals("kani.apk", status.pendingApkName);
            assertEquals("APK verified. Grant install permission to continue.", status.pendingMessage);
            assertTrue(status.hasPendingUpdate());
        }
    }

    @Test
    public void manualCheckStoresPermissionMissingApkWithoutNotification() throws Exception {
        byte[] apk = "manual permission apk".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(sha256(apk))
                .downloadBytes(apk)
                .metadata(context.getPackageName(), "99.99.99")
                .canInstall(false);

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertTrue(result.success);
        assertTrue(result.needsInstallPermission);
        assertEquals("APK verified. Grant install permission to continue.", result.message);
        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", result.intent.getAction());
        assertEquals(0, client.notifications);
        assertEquals(0, client.installs);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("v99.99.99", status.lastVersion);
            assertEquals("kani.apk", status.pendingApkName);
            assertEquals("APK verified. Grant install permission to continue.", status.pendingMessage);
            assertTrue(status.hasPendingUpdate());
        }
    }

    @Test
    public void cachedInstallStoresPermissionMissingApkWithoutNotification() throws Exception {
        File cached = cachedApk("cached-permission.apk");
        write(cached, "valid cached apk");
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(10L, "pending", "v99.99.99", cached.getName(), "ready to install");
        }
        FakeUpdateClient client = new FakeUpdateClient()
                .metadata(context.getPackageName(), "99.99.99")
                .canInstall(false);

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED);

        assertTrue(result.success);
        assertTrue(result.needsInstallPermission);
        assertEquals("APK verified. Grant install permission to continue.", result.message);
        assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", result.intent.getAction());
        assertEquals(0, client.notifications);
        assertEquals(0, client.installs);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("v99.99.99", status.lastVersion);
            assertEquals("cached-permission.apk", status.pendingApkName);
            assertEquals("APK verified. Grant install permission to continue.", status.pendingMessage);
            assertTrue(status.hasPendingUpdate());
        }
    }

    @Test
    public void checkDownloadAndInstallStartsInstallerWhenPermissionIsReady() throws Exception {
        byte[] apk = "install apk".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FakeUpdateClient client = new FakeUpdateClient()
                .latest(releaseJson("v99.99.99", apkAsset(), checksumAsset()))
                .checksum(sha256(apk))
                .downloadBytes(apk)
                .metadata(context.getPackageName(), "99.99.99")
                .canInstall(true);

        GitHubUpdater.UpdateResult result = new GitHubUpdater(context, client).checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL);

        assertTrue(result.success);
        assertFalse(result.needsInstallPermission);
        assertNull(result.intent);
        assertEquals("APK verified. Android installer started.", result.message);
        assertEquals(1, client.installs);
        assertEquals("v99.99.99", client.installedVersion);
        assertEquals(GitHubUpdater.UpdateSource.MANUAL, client.installedSource);
        try (LocalStore store = new LocalStore(context)) {
            LocalStore.AutoUpdateStatus status = store.autoUpdateStatus();
            assertEquals("kani.apk", status.pendingApkName);
            assertEquals("", status.pendingMessage);
        }
    }

    private File cachedApk(String name) {
        File dir = new File(context.getCacheDir(), "updates");
        assertTrue(dir.exists() || dir.mkdirs());
        return new File(dir, name);
    }

    private void clearUpdatesCache() {
        File dir = new File(context.getCacheDir(), "updates");
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    private static void write(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static String releaseJson(String tag, String... assets) {
        StringBuilder out = new StringBuilder();
        out.append("{\"tag_name\":\"").append(tag).append("\",\"html_url\":\"https://example/releases/").append(tag).append("\",\"assets\":[");
        for (int i = 0; i < assets.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(assets[i]);
        }
        out.append("]}");
        return out.toString();
    }

    private static String apkAsset() {
        return "{\"name\":\"kani.apk\",\"browser_download_url\":\"https://example/kani.apk\"}";
    }

    private static String checksumAsset() {
        return "{\"name\":\"kani.apk.sha256\",\"browser_download_url\":\"https://example/kani.apk.sha256\"}";
    }

    private static String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder out = new StringBuilder();
        for (byte b : hash) {
            out.append(String.format(Locale.ROOT, "%02x", b));
        }
        return out.toString();
    }

    private static String repeat(String text, int count) {
        StringBuilder out = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            out.append(text);
        }
        return out.toString();
    }

    private static void assertWorkerSuccess(ListenableWorker.Result result) {
        assertTrue(result instanceof ListenableWorker.Result.Success);
    }

    private static WorkerParameters workerParameters() {
        return new WorkerParameters(
                UUID.randomUUID(),
                Data.EMPTY,
                Collections.emptyList(),
                new WorkerParameters.RuntimeExtras(),
                0,
                0,
                Runnable::run,
                EmptyCoroutineContext.INSTANCE,
                null,
                new WorkerFactory() {
                    @Override
                    public ListenableWorker createWorker(Context appContext, String workerClassName, WorkerParameters workerParameters) {
                        return null;
                    }
                },
                null,
                null
        );
    }

    private static final class RecordingContext extends ContextWrapper {
        String startedAction;

        RecordingContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public void startActivity(Intent intent) {
            startedAction = intent.getAction();
        }
    }

    private static final class RecordingPermissionContext extends ContextWrapper {
        private final int permissionResult;
        private final boolean hideNotificationManager;
        int permissionChecks;

        RecordingPermissionContext(Context base, int permissionResult, boolean hideNotificationManager) {
            super(base);
            this.permissionResult = permissionResult;
            this.hideNotificationManager = hideNotificationManager;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public int checkSelfPermission(String permission) {
            permissionChecks++;
            return permissionResult;
        }

        @Override
        public Object getSystemService(String name) {
            if (hideNotificationManager && Context.NOTIFICATION_SERVICE.equals(name)) {
                return null;
            }
            return super.getSystemService(name);
        }
    }

    private static final class FakeInstallerBackend implements GitHubUpdater.InstallerBackend {
        private final FakeInstallerSession session;
        private boolean failOpen;
        private int createdSessionId;
        private int openedSessionId;
        private int abandonedSessions;

        FakeInstallerBackend(FakeInstallerSession session) {
            this.session = session;
        }

        @Override
        public int createSession(PackageInstaller.SessionParams params) {
            createdSessionId = 41;
            return createdSessionId;
        }

        @Override
        public GitHubUpdater.InstallerSession openSession(int sessionId) throws IOException {
            openedSessionId = sessionId;
            if (failOpen) {
                throw new IOException("open failed");
            }
            return session;
        }

        @Override
        public void abandonSession(int sessionId) {
            abandonedSessions++;
        }
    }

    private static final class FakeInstallerSession implements GitHubUpdater.InstallerSession {
        private final boolean failWrite;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean failFsync;
        private boolean failCommit;
        private boolean fsynced;
        private boolean committed;
        private boolean closed;
        private String writeName;
        private long writeLength;

        FakeInstallerSession(boolean failWrite) {
            this.failWrite = failWrite;
        }

        @Override
        public OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
            if (failWrite) {
                throw new IOException("write failed");
            }
            writeName = name;
            writeLength = lengthBytes;
            return bytes;
        }

        @Override
        public void fsync(OutputStream output) throws IOException {
            if (failFsync) {
                throw new IOException("fsync failed");
            }
            fsynced = true;
        }

        @Override
        public void commit(android.content.IntentSender statusReceiver) {
            if (failCommit) {
                throw new IllegalStateException("commit failed");
            }
            committed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeInstallerAccess implements GitHubUpdater.PackageInstallerAccess {
        private final GitHubUpdater.InstallerSession session;
        private PackageInstaller.SessionParams createdParams;
        private int openedSessionId;
        private int abandonedSessionId;

        FakeInstallerAccess(GitHubUpdater.InstallerSession session) {
            this.session = session;
        }

        @Override
        public int createSession(PackageInstaller.SessionParams params) {
            createdParams = params;
            return 77;
        }

        @Override
        public GitHubUpdater.InstallerSession openSession(int sessionId) {
            openedSessionId = sessionId;
            return session;
        }

        @Override
        public void abandonSession(int sessionId) {
            abandonedSessionId = sessionId;
        }
    }

    private static final class FakeSessionAccess implements GitHubUpdater.PackageInstallerSessionAccess {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private String writeName;
        private long writeOffset;
        private long writeLength;
        private OutputStream fsyncedOutput;
        private boolean committed;
        private boolean closed;

        @Override
        public OutputStream openWrite(String name, long offsetBytes, long lengthBytes) {
            writeName = name;
            writeOffset = offsetBytes;
            writeLength = lengthBytes;
            return bytes;
        }

        @Override
        public void fsync(OutputStream output) {
            fsyncedOutput = output;
        }

        @Override
        public void commit(IntentSender statusReceiver) {
            committed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class PendingActionHandler implements PackageInstallStatusReceiver.PendingUserActionHandler {
        int started;
        int notifications;
        Intent startedIntent;
        String notificationVersion;
        String notificationMessage;

        @Override
        public void startActivity(Intent intent) {
            started++;
            startedIntent = intent;
        }

        @Override
        public boolean showPendingUpdate(String version, String message) {
            notifications++;
            notificationVersion = version;
            notificationMessage = message;
            return true;
        }
    }

    private static final class WorkerCheckerFactory implements AutoUpdateWorker.UpdateCheckerFactory {
        private final boolean retryable;
        private int factories;
        private int checks;

        WorkerCheckerFactory(boolean retryable) {
            this.retryable = retryable;
        }

        @Override
        public AutoUpdateWorker.UpdateChecker create(Context context) {
            factories++;
            return () -> {
                checks++;
                return new GitHubUpdater.UpdateResult(!retryable, retryable ? "try again" : "done", null, false, retryable);
            };
        }
    }

    private static final class FakeUpdateClient implements GitHubUpdater.UpdateClient {
        private String latestJson = releaseJson("v99.99.99", apkAsset(), checksumAsset());
        private String checksumText = "";
        private byte[] downloadBytes = new byte[0];
        private GitHubUpdater.ApkMetadata metadata = new GitHubUpdater.ApkMetadata("", "");
        private Exception downloadFailure;
        private RuntimeException inspectFailure;
        private boolean canInstall;
        private int downloads;
        private int installs;
        private int notifications;
        private String installedVersion;
        private GitHubUpdater.UpdateSource installedSource;

        FakeUpdateClient latest(String latestJson) {
            this.latestJson = latestJson;
            return this;
        }

        FakeUpdateClient checksum(String checksumText) {
            this.checksumText = checksumText;
            return this;
        }

        FakeUpdateClient downloadBytes(byte[] downloadBytes) {
            this.downloadBytes = downloadBytes;
            return this;
        }

        FakeUpdateClient metadata(String packageName, String versionName) {
            this.metadata = new GitHubUpdater.ApkMetadata(packageName, versionName);
            return this;
        }

        FakeUpdateClient canInstall(boolean canInstall) {
            this.canInstall = canInstall;
            return this;
        }

        FakeUpdateClient downloadFailure(Exception downloadFailure) {
            this.downloadFailure = downloadFailure;
            return this;
        }

        FakeUpdateClient inspectFailure(RuntimeException inspectFailure) {
            this.inspectFailure = inspectFailure;
            return this;
        }

        @Override
        public String getText(String url) {
            return url.endsWith(".sha256") ? checksumText : latestJson;
        }

        @Override
        public void download(String url, File file) throws Exception {
            downloads++;
            if (downloadFailure != null) {
                throw downloadFailure;
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(downloadBytes);
            }
        }

        @Override
        public GitHubUpdater.ApkMetadata inspectApk(File apkFile) {
            if (inspectFailure != null) {
                throw inspectFailure;
            }
            return metadata;
        }

        @Override
        public boolean canRequestPackageInstalls() {
            return canInstall;
        }

        @Override
        public void startPackageInstaller(File apkFile, String version, GitHubUpdater.UpdateSource source) {
            installs++;
            installedVersion = version;
            installedSource = source;
        }

        @Override
        public boolean showPendingUpdate(String version, String message) {
            notifications++;
            return true;
        }
    }
}
