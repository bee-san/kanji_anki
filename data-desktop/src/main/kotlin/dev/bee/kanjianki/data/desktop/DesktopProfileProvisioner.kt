package dev.bee.kanjianki.data.desktop

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Creates and hardens a desktop profile's private directory tree. On POSIX
 * filesystems the profile directory is owner-only (`0700`) and its contained
 * files owner-only (`0600`) so a shared-host user account cannot read another
 * user's collection. On filesystems without POSIX permissions (Windows), the
 * directory is created without a permission bit change and callers rely on the
 * per-user profile root under `LOCALAPPDATA` for isolation.
 *
 * This is filesystem I/O, not policy: the preflight decision
 * ([DesktopProfilePreflightPolicy]) must already have allowed the location.
 */
object DesktopProfileProvisioner {
    private val DIRECTORY_0700: Set<PosixFilePermission> =
        PosixFilePermissions.fromString("rwx------")
    private val FILE_0600: Set<PosixFilePermission> =
        PosixFilePermissions.fromString("rw-------")

    data class Provisioned(
        val directory: Path,
        /** True when POSIX owner-only permissions were applied. */
        val hardened: Boolean,
    )

    /**
     * Ensures [directory] exists as an owner-private directory, creating any
     * missing parents. Existing directories have their permissions re-applied so
     * a loosened directory is tightened back to `0700`. Returns whether POSIX
     * hardening was applied (false on non-POSIX filesystems).
     */
    fun provisionDirectory(directory: Path): Provisioned {
        val posix = supportsPosix(directory)
        if (posix) {
            createDirectoriesPosix(directory)
            Files.setPosixFilePermissions(directory, DIRECTORY_0700)
        } else {
            Files.createDirectories(directory)
        }
        return Provisioned(directory, hardened = posix)
    }

    /**
     * Applies owner-only (`0600`) permissions to an existing profile file when
     * the filesystem is POSIX. A no-op elsewhere. Returns whether hardening was
     * applied.
     */
    fun hardenFile(file: Path): Boolean {
        if (!Files.exists(file) || !supportsPosix(file)) return false
        Files.setPosixFilePermissions(file, FILE_0600)
        return true
    }

    private fun createDirectoriesPosix(directory: Path) {
        // Create each missing ancestor with 0700 rather than the process umask so
        // no intermediate profile directory is ever briefly group/other-readable.
        val attribute = PosixFilePermissions.asFileAttribute(DIRECTORY_0700)
        val missing = ArrayList<Path>()
        var current: Path? = directory
        while (current != null && !Files.exists(current)) {
            missing.add(current)
            current = current.parent
        }
        missing.asReversed().forEach { path ->
            try {
                Files.createDirectory(path, attribute)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                // A concurrent creator won the race; that is fine.
            }
        }
    }

    private fun supportsPosix(path: Path): Boolean = try {
        val probe = firstExistingAncestor(path) ?: path
        probe.fileSystem.supportedFileAttributeViews().contains("posix")
    } catch (_: IOException) {
        false
    }

    private fun firstExistingAncestor(path: Path): Path? {
        var current: Path? = path
        while (current != null && !Files.exists(current)) {
            current = current.parent
        }
        return current
    }
}
