package dev.bee.kanjianki.data.desktop

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Reads and writes the [DesktopProfileRegistry] file, and answers the question a
 * desktop host asks at startup: which profile directory do I open?
 *
 * The registry is the one piece of Kani's desktop state that lives outside every
 * profile, so it goes in the *config* directory rather than the data directory —
 * the data directory is what a profile transfer moves, and a registry that
 * travelled with it would name profile UUIDs that do not exist on the receiving
 * machine.
 *
 * Reads are total: a missing, unreadable, truncated, or malformed registry
 * decodes to [DesktopProfileRegistry.empty] and [resolveSelected] then creates a
 * fresh default profile. Refusing to start because a selection file is corrupt
 * would put the user's study data behind a file that holds no study data.
 * Writes are atomic (temp file + rename), because the failure this replaces is
 * worse than losing the last selection: a half-written registry read on the next
 * launch would create a second profile and silently hide the first.
 */
object DesktopProfileRegistryStore {
    const val FILE_NAME: String = "profiles.json"

    /** A registry with a selected profile whose directory is ready to open. */
    data class Resolved(
        val registry: DesktopProfileRegistry,
        val entry: DesktopProfileEntry,
        val profileDir: Path,
        /** True when this launch created the profile entry. */
        val created: Boolean,
    )

    /** The registry file for one host's resolved directories. */
    fun registryFile(directories: DesktopStorageLayout.Directories): Path =
        Paths.get(directories.configDir).resolve(FILE_NAME)

    /**
     * Reads the registry at [file]. Any absent or unusable file reads as
     * [DesktopProfileRegistry.empty]; see the class comment for why that is not
     * a silent-failure bug.
     */
    fun read(file: Path): DesktopProfileRegistry {
        val encoded = try {
            if (Files.isRegularFile(file)) {
                String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            } else {
                null
            }
        } catch (_: IOException) {
            null
        }
        return DesktopProfileRegistry.decode(encoded)
    }

    /**
     * Writes [registry] to [file] atomically, hardened to owner-only where the
     * filesystem supports it.
     */
    @Throws(IOException::class)
    fun write(file: Path, registry: DesktopProfileRegistry) {
        val parent = file.parent ?: throw IOException("registry file has no parent: $file")
        DesktopProfileProvisioner.provisionDirectory(parent)
        val temp = parent.resolve("$FILE_NAME.tmp")
        Files.write(
            temp,
            registry.encode().toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        DesktopProfileProvisioner.hardenFile(temp)
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Resolves the profile to open for [directories], creating and persisting a
     * default profile on first run.
     *
     * The registry is rewritten only when this call changed it, so a normal
     * launch of an existing profile performs no write at all — one less way for
     * a full or read-only config directory to stop Kani from starting.
     *
     * @param newProfileId supplies the id for a created profile; injectable so
     *   tests can pin the directory name.
     */
    @Throws(IOException::class)
    fun resolveSelected(
        directories: DesktopStorageLayout.Directories,
        newProfileId: () -> String = { UUID.randomUUID().toString() },
    ): Resolved {
        val file = registryFile(directories)
        val stored = read(file)
        val selected = stored.selected()
        val resolved = when {
            selected != null -> Resolved(stored, selected, profileDir(directories, selected), created = false)
            // Profiles exist but none is selected: adopt the first rather than
            // add a third state to the model. `withoutProfile` can leave this
            // when the removed profile was the selected one and, on an older
            // registry, so can a hand-edited file.
            stored.profiles.isNotEmpty() -> {
                val first = stored.profiles.first()
                Resolved(stored.select(first.id), first, profileDir(directories, first), created = false)
            }
            else -> {
                val registry = DesktopProfileRegistry.withDefault(newProfileId())
                val entry = checkNotNull(registry.selected()) { "a default registry must select its profile" }
                Resolved(registry, entry, profileDir(directories, entry), created = true)
            }
        }
        if (resolved.registry != stored) write(file, resolved.registry)
        return resolved
    }

    private fun profileDir(
        directories: DesktopStorageLayout.Directories,
        entry: DesktopProfileEntry,
    ): Path = Paths.get(DesktopStorageLayout.profileDir(directories, entry.id))
}
