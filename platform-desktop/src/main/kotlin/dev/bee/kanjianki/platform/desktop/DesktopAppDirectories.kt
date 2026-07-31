package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.AppDirectories
import dev.bee.kanjianki.platform.AppDirectoriesProvider
import java.nio.file.Path

/**
 * The host-OS data, cache, and backup directories for one open profile.
 *
 * Path *arithmetic* is `DesktopStorageLayout`'s in `:data-desktop`, which this
 * module cannot see (the reviewed DAG gives `:platform-desktop` exactly one edge,
 * to `:platform-contracts`). That separation is deliberate rather than awkward:
 * the layout is pure per-OS string math that must be testable for Windows and
 * macOS on a Linux host, while this adapter is the thing that hands already
 * resolved paths to the rest of the app through the platform port. The
 * composition root owns the join, and it is the only place that knows both.
 *
 * Directories are reported, not created. Creation and owner-only hardening
 * belong to the profile provisioner, which runs behind the profile lock during
 * the startup gate; a directories *provider* that quietly did filesystem I/O on
 * every read would mean any caller could recreate a directory the startup gate
 * had deliberately refused.
 */
class DesktopAppDirectories(
    private val directories: AppDirectories,
) : AppDirectoriesProvider {
    override fun directories(): AppDirectories = directories

    companion object {
        /**
         * Builds a provider for a profile rooted at [profileDirectory], with
         * [cacheDirectory] outside it.
         *
         * Cache is a separate argument rather than a subdirectory of the profile
         * because every host OS puts caches somewhere the OS may evict or a
         * migration tool may skip (`~/.cache`, `~/Library/Caches`,
         * `%LOCALAPPDATA%\Kani\cache`). Backups are *inside* the profile for the
         * opposite reason: they are the profile's own recovery state and must
         * travel with it.
         */
        fun forProfile(
            profileDirectory: Path,
            cacheDirectory: Path,
            backupsDirectoryName: String,
        ): DesktopAppDirectories {
            require(backupsDirectoryName.isNotBlank()) {
                "backups directory name must not be blank"
            }
            return DesktopAppDirectories(
                AppDirectories(
                    data = profileDirectory,
                    cache = cacheDirectory,
                    backups = profileDirectory.resolve(backupsDirectoryName),
                ),
            )
        }
    }
}
