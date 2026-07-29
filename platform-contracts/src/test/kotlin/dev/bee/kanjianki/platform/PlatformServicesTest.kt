package dev.bee.kanjianki.platform

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformServicesTest {
    @Test
    fun clockAndLoggerRemainHostNeutral() {
        val events = mutableListOf<AppLogEvent>()
        val logger = AppLogger(events::add)

        logger.debug("start")
        logger.warning("retry", IllegalStateException("busy"))

        assertEquals(2, events.size)
        assertEquals(AppLogLevel.DEBUG, events[0].level)
        assertEquals(AppLogLevel.WARNING, events[1].level)
        assertEquals(42L, AppClock { 42L }.nowMillis())
        assertEquals(42L, AppClock.orSystem(AppClock { 42L }).nowMillis())
        assertTrue(AppClock.systemClock().nowMillis() > 0L)
        assertTrue(AppClock.SYSTEM.nowMillis() > 0L)
        AppLogger.NONE.info("ignored")
        AppLogger.NONE.error("ignored")
    }

    @Test
    fun opaqueFileAndSecretReferencesDoNotLeakValues() {
        val file = PlatformFileReference.create(
            "content://private/document/123",
            "kani-backup.gz",
        )
        val reference = SecretReference.create("ankiconnect.profile-1")
        val secret = SecretValue.create("correct horse battery staple")

        assertFalse(file.toString().contains("document/123"))
        assertFalse(reference.toString().contains("profile-1"))
        assertFalse(secret.toString().contains("correct horse"))
        assertEquals("content://private/document/123", file.opaqueId)
        assertEquals(reference, SecretReference.create("ankiconnect.profile-1"))
        assertEquals(
            reference.hashCode(),
            SecretReference.create("ankiconnect.profile-1").hashCode(),
        )
        assertEquals(
            "correct horse battery staple",
            secret.withValue(::String),
        )
        secret.close()
        assertThrows(IllegalStateException::class.java) {
            secret.withValue(::String)
        }
    }

    @Test
    fun fileAndMediaContractsRejectPathTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            PlatformFileReference.create("opaque", "../collection.anki2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FilePickerRequest(FilePickerPurpose.SAVE, "folder/backup.gz")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadingMediaMetadata("../secret", 1L, 1L)
        }
    }

    @Test
    fun requestsPinRequiredPayloadAndSchedulingBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareRequest(title = "Share")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackgroundTaskRequest(
                kind = BackgroundTaskKind.AUTO_SYNC,
                earliestRunAtMillis = 0L,
                repeatIntervalMillis = 0L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DatabaseSnapshotResult(0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedUpdatePackage(
                Path.of("update.msi"),
                "1.0.0",
                UpdatePackageKind.MSI,
                "not-a-digest",
            )
        }
    }

    @Test
    fun navigationAndAppEventsCarryNoHostImplementationTypes() {
        val urls = mutableListOf<URI>()
        val queries = mutableListOf<String>()
        val navigator = object : ExternalNavigator {
            override fun openUrl(uri: URI): Boolean = urls.add(uri)

            override fun openCollectionBrowser(query: String): Boolean =
                queries.add(query)
        }
        val event = AppEvent(AppEventType.SYNC_COMMITTED, 100L)

        assertTrue(navigator.openUrl(URI("https://example.invalid/help")))
        assertTrue(navigator.openCollectionBrowser("tag:kani_repaired"))
        assertEquals("https", urls.single().scheme)
        assertEquals("tag:kani_repaired", queries.single())
        assertEquals(100L, event.occurredAtMillis)
    }

    @Test
    fun everyServicePortCanBeHostedWithoutPlatformImplementationTypes() {
        val directories = AppDirectories(
            data = Path.of("data"),
            cache = Path.of("cache"),
            backups = Path.of("backups"),
        )
        val directoriesProvider = AppDirectoriesProvider { directories }
        val filter = FileTypeFilter("Kani backups", setOf("gz"))
        val pickerRequest = FilePickerRequest(
            FilePickerPurpose.OPEN,
            filters = listOf(filter),
        )
        val file = PlatformFileReference.create("opaque-backup", "backup.gz")
        var picked: PlatformFileReference? = null
        val picker = FilePicker { request, callback ->
            assertEquals(pickerRequest, request)
            callback(file)
        }
        val written = ByteArrayOutputStream()
        val fileAccess = object : PlatformFileAccess {
            override fun openInput(file: PlatformFileReference) =
                ByteArrayInputStream("backup".toByteArray())

            override fun openOutput(file: PlatformFileReference) = written
        }
        val snapshotter = DatabaseSnapshotService { DatabaseSnapshotResult(7L) }

        assertEquals(directories, directoriesProvider.directories())
        picker.launch(pickerRequest) { picked = it }
        assertEquals("backup.gz", picked?.displayName)
        assertEquals("backup", fileAccess.openInput(file).reader().readText())
        fileAccess.openOutput(file).use { it.write("copy".toByteArray()) }
        assertEquals("copy", written.toString())
        assertEquals(7L, snapshotter.createSnapshot(Path.of("snapshot.db")).bytesWritten)

        val clipboard = ClipboardService { label, text ->
            label == "Kani" && text == "tag:kani"
        }
        val share = ShareService { request -> request.attachments.single() === file }
        assertTrue(clipboard.setText("Kani", "tag:kani"))
        assertTrue(share.share(ShareRequest("Backup", attachments = listOf(file))))

        val notification = NotificationRequest(
            "due",
            NotificationCategory.REMINDER,
            "Study",
            "Three cards are due.",
        )
        val notifications = RecordingNotifications()
        assertTrue(notifications.isAvailable())
        assertTrue(notifications.post(notification))
        assertTrue(notifications.cancel("due"))
        assertEquals(listOf(notification), notifications.posted)

        val lifecycle = RecordingLifecycle()
        var lifecycleState: AppLifecycleState? = null
        lifecycle.observe { lifecycleState = it }.use {
            lifecycle.moveTo(AppLifecycleState.BACKGROUND)
        }
        assertEquals(AppLifecycleState.BACKGROUND, lifecycleState)
        assertEquals(AppLifecycleState.BACKGROUND, lifecycle.currentState())

        val scheduled = mutableListOf<BackgroundTaskRequest>()
        val scheduler = object : BackgroundScheduler {
            override fun schedule(request: BackgroundTaskRequest): Boolean =
                scheduled.add(request)

            override fun cancel(kind: BackgroundTaskKind): Boolean =
                scheduled.removeAll { it.kind == kind }
        }
        val task = BackgroundTaskRequest(
            BackgroundTaskKind.UPDATE_CHECK,
            earliestRunAtMillis = 10L,
            repeatIntervalMillis = 20L,
            requiresNetwork = true,
        )
        assertTrue(scheduler.schedule(task))
        assertTrue(scheduler.cancel(BackgroundTaskKind.UPDATE_CHECK))

        val events = RecordingEvents()
        var observedEvent: AppEvent? = null
        events.observe { observedEvent = it }.use {
            events.publish(AppEvent(AppEventType.STUDY_COMMITTED, 20L))
        }
        assertEquals(AppEventType.STUDY_COMMITTED, observedEvent?.type)

        val secrets = InMemorySecretStore()
        val secretReference = SecretReference.create("ankiconnect.key")
        val value = SecretValue.create(charArrayOf('k', 'e', 'y'))
        try {
            assertTrue(secrets.write(secretReference, value))
        } finally {
            value.close()
        }
        val loadedSecret = requireNotNull(secrets.read(secretReference))
        try {
            loadedSecret.withValue { loaded ->
                assertEquals("key", String(loaded))
            }
        } finally {
            loadedSecret.close()
        }
        assertEquals(SecretPersistence.SESSION_ONLY, secrets.persistence)
        assertTrue(secrets.delete(secretReference))
        assertNull(secrets.read(secretReference))

        val media = object : ReadingMediaSource {
            override fun metadata(name: String) =
                ReadingMediaMetadata(name, 3L, 4L)

            override fun read(name: String, maximumBytes: Int): ByteArray? =
                "abc".toByteArray().takeIf { it.size <= maximumBytes }
        }
        assertEquals(3L, media.metadata("stats.json").sizeBytes)
        assertEquals("abc", media.read("stats.json", 3)?.decodeToString())

        val update = VerifiedUpdatePackage(
            Path.of("update.msi"),
            "1.2.3",
            UpdatePackageKind.MSI,
            "a".repeat(64),
        )
        val delivery = UpdateDelivery { UpdateDeliveryResult.OPENED }
        assertEquals(UpdateDeliveryResult.OPENED, delivery.deliver(update))
        assertEquals(UpdateDeliveryResult.UNSUPPORTED, UpdateDeliveryResult.UNSUPPORTED)
        assertEquals(UpdatePackageKind.APK, UpdatePackageKind.APK)
        assertEquals(SecretPersistence.OS_CREDENTIAL_STORE, SecretPersistence.OS_CREDENTIAL_STORE)
    }

    private class RecordingNotifications : NotificationService {
        val posted = mutableListOf<NotificationRequest>()

        override fun isAvailable(): Boolean = true

        override fun post(request: NotificationRequest): Boolean = posted.add(request)

        override fun cancel(id: String): Boolean = id == "due"
    }

    private class RecordingLifecycle : AppLifecycle {
        private var state = AppLifecycleState.FOREGROUND
        private val observers = mutableListOf<(AppLifecycleState) -> Unit>()

        override fun currentState(): AppLifecycleState = state

        override fun observe(observer: (AppLifecycleState) -> Unit): PlatformSubscription {
            observers += observer
            return PlatformSubscription { observers -= observer }
        }

        fun moveTo(state: AppLifecycleState) {
            this.state = state
            observers.toList().forEach { it(state) }
        }
    }

    private class RecordingEvents : AppEventBus {
        private val observers = mutableListOf<(AppEvent) -> Unit>()

        override fun publish(event: AppEvent) {
            observers.toList().forEach { it(event) }
        }

        override fun observe(observer: (AppEvent) -> Unit): PlatformSubscription {
            observers += observer
            return PlatformSubscription { observers -= observer }
        }
    }

    private class InMemorySecretStore : SecretStore {
        override val persistence = SecretPersistence.SESSION_ONLY
        private val values = mutableMapOf<String, CharArray>()

        override fun read(reference: SecretReference): SecretValue? =
            values[reference.value]?.let(SecretValue::create)

        override fun write(reference: SecretReference, value: SecretValue): Boolean {
            value.withValue { values[reference.value] = it.copyOf() }
            return true
        }

        override fun delete(reference: SecretReference): Boolean =
            values.remove(reference.value) != null
    }
}
