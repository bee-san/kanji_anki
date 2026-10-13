package dev.bee.kanjianki.data.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProfileRegistryStoreTest {
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
    fun theRegistryLivesInTheConfigDirectoryNotTheDataDirectory() {
        // The data directory is what a profile transfer moves. A registry that
        // travelled with it would name UUIDs that do not exist on the receiving
        // machine, so this placement is the contract, not a detail.
        val directories = directories()
        val file = DesktopProfileRegistryStore.registryFile(directories)

        assertEquals(
            Paths.get(directories.configDir).resolve(DesktopProfileRegistryStore.FILE_NAME),
            file,
        )
        assertFalse(file.startsWith(Paths.get(directories.dataDir)))
    }

    @Test
    fun firstRunCreatesADefaultProfileAndPersistsTheSelection() {
        val directories = directories()

        val resolved = DesktopProfileRegistryStore.resolveSelected(directories) { FIXED_PROFILE_ID }

        assertTrue(resolved.created)
        assertEquals(FIXED_PROFILE_ID, resolved.entry.id)
        assertEquals(DesktopProfileRegistry.DEFAULT_DISPLAY_NAME, resolved.entry.displayName)
        assertEquals(
            Paths.get(DesktopStorageLayout.profileDir(directories, FIXED_PROFILE_ID)),
            resolved.profileDir,
        )
        // Persisted, so the second launch opens the same profile rather than
        // creating another one and hiding the first.
        assertEquals(
            resolved.registry,
            DesktopProfileRegistryStore.read(DesktopProfileRegistryStore.registryFile(directories)),
        )
    }

    @Test
    fun asecondLaunchReopensTheSameProfileWithoutRewritingTheRegistry() {
        val directories = directories()
        val first = DesktopProfileRegistryStore.resolveSelected(directories) { FIXED_PROFILE_ID }
        val file = DesktopProfileRegistryStore.registryFile(directories)
        val writtenAt = Files.getLastModifiedTime(file)

        val second = DesktopProfileRegistryStore.resolveSelected(directories) {
            throw AssertionError("an existing selection must not mint a new profile id")
        }

        assertFalse(second.created)
        assertEquals(first.entry, second.entry)
        assertEquals(first.profileDir, second.profileDir)
        // No write at all on the common path: a full or read-only config
        // directory must not be able to stop Kani from opening a profile it
        // already knows about.
        assertEquals(writtenAt, Files.getLastModifiedTime(file))
    }

    @Test
    fun aRegistryWithProfilesButNoSelectionAdoptsTheFirstOne() {
        // Reachable through `withoutProfile` on the selected profile, and through
        // a hand-edited file. Adopting beats both refusing to start and adding an
        // "unselected" state to every caller.
        val directories = directories()
        val file = DesktopProfileRegistryStore.registryFile(directories)
        val unselected = DesktopProfileRegistry(
            listOf(DesktopProfileEntry(FIXED_PROFILE_ID, "Kept")),
            selectedProfileId = null,
        )
        DesktopProfileRegistryStore.write(file, unselected)

        val resolved = DesktopProfileRegistryStore.resolveSelected(directories) {
            throw AssertionError("an existing profile must not mint a new id")
        }

        assertFalse(resolved.created)
        assertEquals(FIXED_PROFILE_ID, resolved.entry.id)
        assertEquals("Kept", resolved.entry.displayName)
        assertEquals(FIXED_PROFILE_ID, resolved.registry.selectedProfileId)
        assertEquals(resolved.registry, DesktopProfileRegistryStore.read(file))
        assertNotEquals(unselected, resolved.registry)
    }

    @Test
    fun everyUnusableRegistryReadsAsEmptyRatherThanFailingStartup() {
        val directories = directories()
        val file = DesktopProfileRegistryStore.registryFile(directories)

        // Absent.
        assertEquals(DesktopProfileRegistry.empty(), DesktopProfileRegistryStore.read(file))

        // A directory where the file should be: `isRegularFile` is false, so this
        // is the same "nothing usable here" answer and not an exception.
        Files.createDirectories(file)
        assertEquals(DesktopProfileRegistry.empty(), DesktopProfileRegistryStore.read(file))
        Files.delete(file)

        // Truncated and outright malformed.
        Files.write(file, "{\"profiles\":".toByteArray(StandardCharsets.UTF_8))
        assertEquals(DesktopProfileRegistry.empty(), DesktopProfileRegistryStore.read(file))
        Files.write(file, "not json at all".toByteArray(StandardCharsets.UTF_8))
        assertEquals(DesktopProfileRegistry.empty(), DesktopProfileRegistryStore.read(file))

        // And startup still reaches a usable profile.
        val resolved = DesktopProfileRegistryStore.resolveSelected(directories) { FIXED_PROFILE_ID }
        assertTrue(resolved.created)
        assertEquals(FIXED_PROFILE_ID, resolved.entry.id)
    }

    @Test
    fun writingReplacesTheRegistryInPlaceAndLeavesNoTemporaryFileBehind() {
        val directories = directories()
        val file = DesktopProfileRegistryStore.registryFile(directories)
        DesktopProfileRegistryStore.write(file, DesktopProfileRegistry.withDefault(FIXED_PROFILE_ID))

        val second = DesktopProfileRegistry.withDefault(OTHER_PROFILE_ID)
        DesktopProfileRegistryStore.write(file, second)

        assertEquals(second, DesktopProfileRegistryStore.read(file))
        val leftovers = Files.list(Paths.get(directories.configDir)).use { paths ->
            paths.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(listOf(DesktopProfileRegistryStore.FILE_NAME), leftovers)
    }

    @Test
    fun writingCreatesTheConfigDirectoryWhenItDoesNotExistYet() {
        // First run on a fresh machine: nothing under the config root exists, and
        // the store has to provision it rather than assume a prior launch did.
        val directories = directories()
        assertFalse(Files.exists(Paths.get(directories.configDir)))

        DesktopProfileRegistryStore.write(
            DesktopProfileRegistryStore.registryFile(directories),
            DesktopProfileRegistry.withDefault(FIXED_PROFILE_ID),
        )

        assertTrue(Files.isDirectory(Paths.get(directories.configDir)))
    }

    /** Layout rooted at a throwaway home, so no test touches a real profile. */
    private fun directories(): DesktopStorageLayout.Directories {
        val home = Files.createTempDirectory("kani-desktop-registry-")
        temporaryDirectories.add(home)
        return DesktopStorageLayout.directories(
            os = DesktopStorageLayout.Os.LINUX,
            env = { null },
            userHome = home.toString(),
        )
    }

    private companion object {
        const val FIXED_PROFILE_ID = "3f2b1a09-4c5d-4e6f-8a9b-0c1d2e3f4a5b"
        const val OTHER_PROFILE_ID = "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a"
    }
}
