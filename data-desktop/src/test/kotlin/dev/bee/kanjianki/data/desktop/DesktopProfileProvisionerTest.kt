package dev.bee.kanjianki.data.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DesktopProfileProvisionerTest {
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
    fun createsMissingProfileDirectoryTreeOwnerOnly() {
        val root = tempRoot()
        val profile = root.resolve("nested/profile-dir")
        val provisioned = DesktopProfileProvisioner.provisionDirectory(profile)

        assertTrue(Files.isDirectory(profile))
        assertEquals(profile, provisioned.directory)
        if (provisioned.hardened) {
            assertEquals(OWNER_ONLY_DIR, Files.getPosixFilePermissions(profile))
            // The created intermediate parent is also owner-only.
            assertEquals(OWNER_ONLY_DIR, Files.getPosixFilePermissions(root.resolve("nested")))
        }
    }

    @Test
    fun retightensAnExistingLoosenedDirectory() {
        assumeTrue(posixSupported())
        val profile = tempRoot().resolve("loose-profile")
        Files.createDirectories(profile)
        Files.setPosixFilePermissions(
            profile,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ,
            ),
        )

        val provisioned = DesktopProfileProvisioner.provisionDirectory(profile)

        assertTrue(provisioned.hardened)
        assertEquals(OWNER_ONLY_DIR, Files.getPosixFilePermissions(profile))
    }

    @Test
    fun hardensAProfileFileToOwnerOnly() {
        assumeTrue(posixSupported())
        val profile = tempRoot().resolve("profile")
        DesktopProfileProvisioner.provisionDirectory(profile)
        val database = profile.resolve("kanji_anki_simple.db")
        Files.write(database, byteArrayOf(1, 2, 3))

        assertTrue(DesktopProfileProvisioner.hardenFile(database))
        assertEquals(OWNER_ONLY_FILE, Files.getPosixFilePermissions(database))
    }

    @Test
    fun hardeningAMissingFileIsANoOp() {
        val missing = tempRoot().resolve("absent.db")
        assertFalse(DesktopProfileProvisioner.hardenFile(missing))
    }

    private fun posixSupported(): Boolean =
        java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-provision-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        val OWNER_ONLY_DIR: Set<PosixFilePermission> = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
        )
        val OWNER_ONLY_FILE: Set<PosixFilePermission> = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )
    }
}
