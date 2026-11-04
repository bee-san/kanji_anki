package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.DeviceSettingKeys
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopDeviceSettingsStoreTest {
    private val temporaryRoots = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryRoots.asReversed().forEach { root ->
            if (!Files.exists(root)) return@forEach
            // Restore write on every directory first: the durability test leaves a
            // root at r-x to make a write fail, and deleting a *file* needs write on
            // its parent, so a single reverse-order pass could not clean up after it.
            if (posixSupported(root)) {
                Files.walk(root).use { paths ->
                    paths.filter(Files::isDirectory).forEach { directory ->
                        runCatching {
                            Files.setPosixFilePermissions(
                                directory,
                                PosixFilePermissions.fromString("rwx------"),
                            )
                        }
                    }
                }
            }
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun writesSurviveReopeningTheSameFile() {
        val file = settingsFile()
        DesktopDeviceSettingsStore.open(file).edit {
            put(DeviceSettingKeys.autoSyncEnabled, true)
            put(DeviceSettingKeys.windowWidth, 1280)
            put(DeviceSettingKeys.autoSyncLastSuccessAt, 1_700_000_000_000L)
            put(DeviceSettingKeys.providerEndpoint, "http://127.0.0.1:8765")
        }

        val reopened = DesktopDeviceSettingsStore.open(file)

        assertEquals(true, reopened.read(DeviceSettingKeys.autoSyncEnabled))
        assertEquals(1280, reopened.read(DeviceSettingKeys.windowWidth))
        assertEquals(
            1_700_000_000_000L,
            reopened.read(DeviceSettingKeys.autoSyncLastSuccessAt),
        )
        assertEquals(
            "http://127.0.0.1:8765",
            reopened.read(DeviceSettingKeys.providerEndpoint),
        )
    }

    @Test
    fun anAbsentFileReadsAsEmptyRatherThanFailingToStart() {
        val store = DesktopDeviceSettingsStore.open(settingsFile())

        assertFalse(store.contains(DeviceSettingKeys.autoSyncEnabled))
        assertNull(store.read(DeviceSettingKeys.autoSyncEnabled))
    }

    @Test
    fun removingAKeyMakesItAbsentOnDiskToo() {
        val file = settingsFile()
        val store = DesktopDeviceSettingsStore.open(file)
        store.edit { put(DeviceSettingKeys.trayEnabled, true) }

        store.edit { remove(DeviceSettingKeys.trayEnabled) }

        assertFalse(store.contains(DeviceSettingKeys.trayEnabled))
        assertFalse(
            DesktopDeviceSettingsStore.open(file).contains(DeviceSettingKeys.trayEnabled),
        )
    }

    @Test
    fun anEditBlockSeesItsOwnPendingWritesBeforeItCommits() {
        // A grouped decision -- "if the user enabled auto-sync, also stamp the next
        // run" -- has to read what the same block just put, or each caller needs its
        // own bookkeeping.
        val store = DesktopDeviceSettingsStore.open(settingsFile())

        store.edit {
            put(DeviceSettingKeys.autoSyncEnabled, true)
            assertTrue(contains(DeviceSettingKeys.autoSyncEnabled))
            assertEquals(true, read(DeviceSettingKeys.autoSyncEnabled))
            remove(DeviceSettingKeys.autoSyncEnabled)
            assertFalse(contains(DeviceSettingKeys.autoSyncEnabled))
        }

        assertFalse(store.contains(DeviceSettingKeys.autoSyncEnabled))
    }

    @Test
    fun aSnapshotIsFrozenAgainstLaterEdits() {
        val store = DesktopDeviceSettingsStore.open(settingsFile())
        store.edit { put(DeviceSettingKeys.reminderMaxPerDay, 3) }

        val snapshot = store.snapshot()
        store.edit { put(DeviceSettingKeys.reminderMaxPerDay, 9) }

        assertEquals(3, snapshot.read(DeviceSettingKeys.reminderMaxPerDay))
        assertTrue(snapshot.contains(DeviceSettingKeys.reminderMaxPerDay))
        assertEquals(9, store.read(DeviceSettingKeys.reminderMaxPerDay))
    }

    @Test
    fun anEditThatChangesNothingDoesNotRewriteTheFile() {
        val file = settingsFile()
        val store = DesktopDeviceSettingsStore.open(file)
        store.edit { put(DeviceSettingKeys.debugLogEnabled, true) }
        val before = Files.readAllBytes(file)

        store.edit { /* a caller whose condition turned out to be false */ }

        assertEquals(
            String(before, StandardCharsets.UTF_8),
            String(Files.readAllBytes(file), StandardCharsets.UTF_8),
        )
    }

    @Test
    fun aStoredValueOfTheWrongTypeReadsAsAbsent() {
        // The fail-open case: a hand-edited file, or one written by a version that
        // typed a key differently, must fall back to the product default rather
        // than crash at startup.
        val file = settingsFile()
        Files.writeString(
            file,
            "${DeviceSettingKeys.windowWidth.storageName}=not-a-number\n" +
                "${DeviceSettingKeys.autoSyncEnabled.storageName}=yes\n" +
                "${DeviceSettingKeys.autoSyncNextRunAt.storageName}=12.5\n",
        )

        val store = DesktopDeviceSettingsStore.open(file)

        assertNull(store.read(DeviceSettingKeys.windowWidth))
        assertNull(store.read(DeviceSettingKeys.autoSyncEnabled))
        assertNull(store.read(DeviceSettingKeys.autoSyncNextRunAt))
        // Present on disk, so contains() still reports it: the value is unusable,
        // not unwritten, and a caller distinguishing those gets the truth.
        assertTrue(store.contains(DeviceSettingKeys.windowWidth))
    }

    @Test
    fun aMalformedFileReadsAsEmptyInsteadOfBlockingStartup() {
        val file = settingsFile()
        // An invalid unicode escape is what Properties.load rejects outright.
        Files.writeString(file, "provider_endpoint=\\uZZZZ\n")

        val store = DesktopDeviceSettingsStore.open(file)

        assertFalse(store.contains(DeviceSettingKeys.providerEndpoint))
    }

    @Test
    fun aValueContainingSeparatorsAndNewlinesRoundTrips() {
        // Written with Properties syntax and read with Properties.load, so the two
        // must agree about escaping; a host path with '=' or ':' is ordinary on
        // Windows, and a signature value can hold anything.
        val file = settingsFile()
        val awkward = "C:\\Users\\a b=c:d\t#e!f\n g"
        DesktopDeviceSettingsStore.open(file).edit {
            put(DeviceSettingKeys.hostProfilePath, awkward)
            put(DeviceSettingKeys.reminderLastPostedSignature, " leading space")
        }

        val reopened = DesktopDeviceSettingsStore.open(file)

        assertEquals(awkward, reopened.read(DeviceSettingKeys.hostProfilePath))
        assertEquals(
            " leading space",
            reopened.read(DeviceSettingKeys.reminderLastPostedSignature),
        )
    }

    @Test
    fun putRejectsAValueThatDoesNotMatchTheKeysDeclaredType() {
        val store = DesktopDeviceSettingsStore.open(settingsFile())

        @Suppress("UNCHECKED_CAST")
        val misTyped = DeviceSettingKeys.windowWidth as
            dev.bee.kanjianki.platform.DeviceSettingKey<Any>

        assertThrows(IllegalArgumentException::class.java) {
            store.edit { put(misTyped, "1280") }
        }
    }

    @Test
    fun aFailedWriteThrowsAndLatchesUntilRestart() {
        // The durability contract: a lost write must be loud. Carrying on with
        // in-memory values the disk does not have means the user's choice silently
        // reverts at next launch.
        val root = temporaryRoot()
        assumeTrue(posixSupported(root))
        val file = root.resolve(DesktopDeviceSettingsStore.FILE_NAME)
        val store = DesktopDeviceSettingsStore.open(file)
        store.edit { put(DeviceSettingKeys.trayEnabled, true) }
        // Read-only directory: the temp file cannot be created.
        Files.setPosixFilePermissions(
            root,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            store.edit { put(DeviceSettingKeys.trayEnabled, false) }
        }

        assertTrue(failure.message!!.contains("restart required"))
        // Latched: every later operation reports the loss rather than serving
        // values that never reached the disk.
        val latched = assertThrows(IllegalStateException::class.java) {
            store.read(DeviceSettingKeys.trayEnabled)
        }
        assertTrue(latched.message!!.contains("previously failed"))
        assertThrows(IllegalStateException::class.java) {
            store.contains(DeviceSettingKeys.trayEnabled)
        }
        assertThrows(IllegalStateException::class.java) { store.snapshot() }
        assertThrows(IllegalStateException::class.java) { store.edit { } }
    }

    @Test
    fun theSettingsFileIsOwnerOnlyOnPosixHosts() {
        val file = settingsFile()
        assumeTrue(posixSupported(file.parent))

        DesktopDeviceSettingsStore.open(file).edit {
            put(DeviceSettingKeys.providerAuthReference, "ankiconnect.api-key")
        }

        assertEquals(
            PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(file),
        )
    }

    @Test
    fun noPartialFileIsLeftBehindAfterASuccessfulWrite() {
        val file = settingsFile()
        DesktopDeviceSettingsStore.open(file).edit {
            put(DeviceSettingKeys.windowMaximized, true)
        }

        val leftovers = Files.list(file.parent).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".partial") }.count()
        }

        assertEquals(0L, leftovers)
    }

    @Test
    fun everyDeviceKeyTypeRoundTripsThroughTheFile() {
        // Exhaustive over the value types rather than the keys: a type this adapter
        // cannot serialize would be a silent data loss for whichever key uses it.
        val file = settingsFile()
        DesktopDeviceSettingsStore.open(file).edit {
            put(DeviceSettingKeys.runAtLogin, false)
            put(DeviceSettingKeys.reminderHour, 0)
            put(DeviceSettingKeys.reviewReminderDayStart, Long.MIN_VALUE)
            put(DeviceSettingKeys.autoUpdateLastVersion, "")
        }

        val reopened = DesktopDeviceSettingsStore.open(file)

        assertEquals(false, reopened.read(DeviceSettingKeys.runAtLogin))
        assertEquals(0, reopened.read(DeviceSettingKeys.reminderHour))
        assertEquals(
            Long.MIN_VALUE,
            reopened.read(DeviceSettingKeys.reviewReminderDayStart),
        )
        assertEquals("", reopened.read(DeviceSettingKeys.autoUpdateLastVersion))
    }

    private fun settingsFile(): Path =
        temporaryRoot().resolve(DesktopDeviceSettingsStore.FILE_NAME)

    private fun temporaryRoot(): Path =
        Files.createTempDirectory("kani-device-settings").also(temporaryRoots::add)

    private fun posixSupported(path: Path): Boolean =
        path.fileSystem.supportedFileAttributeViews().contains("posix")
}
