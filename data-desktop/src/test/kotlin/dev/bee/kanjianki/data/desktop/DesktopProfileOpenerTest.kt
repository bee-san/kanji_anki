package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaTransitionKind
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopProfileOpenerTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach { directory ->
            if (!Files.exists(directory)) return@forEach
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun opensAFreshProfileEndToEnd() = runBlocking {
        val profile = tempRoot().resolve("profile")
        val result = DesktopProfileOpener.open(profile, migrationContext())
        assertTrue("expected Opened but was $result", result is DesktopProfileOpener.Result.Opened)
        val opened = result as DesktopProfileOpener.Result.Opened
        try {
            assertEquals(SchemaTransitionKind.CREATED, opened.transition.kind)
            assertTrue(opened.lock.isHeld)
            val settings = SqlSettingsRepository(opened.database) { FIXED_CLOCK }
            assertTrue(settings.save(SettingsSaveCommand.StudyAhead(minutes = 30)).isOk())
            assertEquals(30, settings.load().valueOrNull()?.studyAheadMinutes)
        } finally {
            opened.close()
        }
        assertTrue(!opened.lock.isHeld)
    }

    @Test
    fun refusesWhenTheProbeRejectsTheDirectory() = runBlocking {
        val profile = tempRoot().resolve("profile")
        val result = DesktopProfileOpener.open(
            profile,
            migrationContext(),
            probe = probeOf(
                DesktopProfilePreflightPolicy.ProfileDirectoryFacts(
                    exists = true,
                    isDirectory = true,
                    isSymlink = true,
                    worldWritable = false,
                    onNetworkShare = false,
                    supportsAtomicMove = true,
                    supportsExclusiveLock = true,
                ),
            ),
        )
        assertEquals(
            DesktopProfileOpener.Result.Refused(DesktopProfilePreflightPolicy.Refusal.SYMLINKED),
            result,
        )
    }

    @Test
    fun reportsLockUnavailableWhenAnotherHolderExists() = runBlocking {
        val profile = tempRoot().resolve("profile")
        DesktopProfileProvisioner.provisionDirectory(profile)
        val held = DesktopProfileLock.tryAcquire(
            profile.resolve(DesktopStorageLayout.LOCK_FILE_NAME),
        )
        val first = (held as DesktopProfileLock.Result.Acquired).lock
        try {
            val result = DesktopProfileOpener.open(profile, migrationContext(), allowingProbe())
            assertEquals(DesktopProfileOpener.Result.LockUnavailable, result)
        } finally {
            first.close()
        }
    }

    @Test
    fun reportsIoFailureWhenTheDirectoryCannotBeProvisioned() = runBlocking {
        // A regular file stands where the profile's parent directory should be,
        // so provisioning the profile directory throws an IOException.
        val root = tempRoot()
        val blocker = root.resolve("blocker")
        Files.write(blocker, byteArrayOf(0))
        val profile = blocker.resolve("profile")
        val result = DesktopProfileOpener.open(profile, migrationContext(), allowingProbe())
        assertTrue("expected IoFailure but was $result", result is DesktopProfileOpener.Result.IoFailure)
    }

    @Test
    fun realProbeSeesAHardenedDirectoryAndAllows() {
        val profile = tempRoot().resolve("profile")
        DesktopProfileProvisioner.provisionDirectory(profile)
        val facts = DesktopProfileOpener.RealFilesystemProbe.factsFor(profile)
        assertTrue(facts.exists)
        assertTrue(facts.isDirectory)
        assertTrue(DesktopProfilePreflightPolicy.isAllowed(facts))
    }

    @Test
    fun realProbeFlagsAWorldWritableDirectory() {
        assumeTrue(posixSupported())
        val profile = tempRoot().resolve("open-profile")
        Files.createDirectories(profile)
        Files.setPosixFilePermissions(
            profile,
            java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"),
        )
        val facts = DesktopProfileOpener.RealFilesystemProbe.factsFor(profile)
        assertTrue(facts.worldWritable)
    }

    @Test
    fun realProbeReportsAMissingDirectoryAsAllowed() {
        val profile = tempRoot().resolve("not/created/yet")
        val facts = DesktopProfileOpener.RealFilesystemProbe.factsFor(profile)
        assertTrue(!facts.exists)
        assertTrue(DesktopProfilePreflightPolicy.isAllowed(facts))
    }

    @Test
    fun exposesADefaultProfilesRootForTheHostOs() {
        val root = DesktopProfileOpener.defaultProfilesRoot(DesktopStorageLayout.Os.LINUX)
        assertTrue(root.toString().endsWith("profiles"))
    }

    private fun allowingProbe(): DesktopProfileOpener.FilesystemProbe = probeOf(
        DesktopProfilePreflightPolicy.ProfileDirectoryFacts(
            exists = true,
            isDirectory = true,
            isSymlink = false,
            worldWritable = false,
            onNetworkShare = false,
            supportsAtomicMove = true,
            supportsExclusiveLock = true,
        ),
    )

    private fun probeOf(
        facts: DesktopProfilePreflightPolicy.ProfileDirectoryFacts,
    ) = object : DesktopProfileOpener.FilesystemProbe {
        override fun factsFor(profileDir: Path) = facts
    }

    private fun migrationContext(): MigrationContext =
        MigrationContext(clock = MigrationClock { FIXED_CLOCK })

    private fun posixSupported(): Boolean =
        java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-opener-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
