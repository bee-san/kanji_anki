package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.sql.MigrationContext
import java.io.IOException
import java.nio.file.FileStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

/**
 * Opens a desktop profile safely: probes the profile directory, runs the
 * [DesktopProfilePreflightPolicy], provisions the private directory tree,
 * acquires the exclusive [DesktopProfileLock], and finally opens the migrated
 * database through [DesktopDatabaseFactory]. This is the single entry point the
 * desktop app uses to obtain a ready-to-use profile database.
 *
 * The steps run in a fixed order so that a failure at any stage releases the
 * resources acquired before it: preflight → provision → lock → open. If opening
 * the database fails, the lock is released.
 */
object DesktopProfileOpener {
    /**
     * The outcome of the preflight/provision/lock half of opening a profile.
     *
     * This half is separable because a desktop host has work to do between
     * holding the lock and opening the database: a staged restore replaces the
     * database file, so it must run after the lock excludes a second process and
     * before any connection is opened. [open] is that sequence with nothing in
     * between; [lock] plus [openLocked] is the same sequence with a seam.
     */
    sealed interface LockResult {
        /** The profile directory is provisioned and exclusively held. */
        data class Locked(
            val profileDir: Path,
            val lock: DesktopProfileLock,
            val hardened: Boolean,
        ) : LockResult, AutoCloseable {
            /** Releases the lock. Opening the database transfers ownership. */
            override fun close() = lock.close()
        }

        data class Refused(val reason: DesktopProfilePreflightPolicy.Refusal) : LockResult

        data object LockUnavailable : LockResult

        data class IoFailure(val cause: IOException) : LockResult
    }

    sealed interface Result {
        data class Opened(
            val database: dev.bee.kanjianki.data.sql.SqlDatabase,
            val transition: dev.bee.kanjianki.data.sql.SchemaTransition,
            val lock: DesktopProfileLock,
            val hardened: Boolean,
        ) : Result, AutoCloseable {
            /** Releases the database and the profile lock. */
            override fun close() {
                try {
                    database.close()
                } finally {
                    lock.close()
                }
            }
        }

        data class Refused(val reason: DesktopProfilePreflightPolicy.Refusal) : Result

        data object LockUnavailable : Result

        data class IoFailure(val cause: IOException) : Result
    }

    /**
     * @param profileDir the profile's directory (holds the db, lock, backups).
     * @param probe filesystem probe; defaults to the real filesystem.
     */
    suspend fun open(
        profileDir: Path,
        migrationContext: MigrationContext = MigrationContext.system(),
        probe: FilesystemProbe = RealFilesystemProbe,
    ): Result = when (val locked = lock(profileDir, probe)) {
        is LockResult.Locked -> openLocked(locked, migrationContext)
        is LockResult.Refused -> Result.Refused(locked.reason)
        LockResult.LockUnavailable -> Result.LockUnavailable
        is LockResult.IoFailure -> Result.IoFailure(locked.cause)
    }

    /**
     * Runs preflight, provisions the directory, and acquires the exclusive lock
     * without opening the database.
     *
     * Split out of [open] so a host can apply a staged restore in the one window
     * where it is safe: after the lock rules out a second Kani process, and
     * before any connection exists to be invalidated by replacing the file
     * underneath it.
     */
    fun lock(
        profileDir: Path,
        probe: FilesystemProbe = RealFilesystemProbe,
    ): LockResult {
        val decision = DesktopProfilePreflightPolicy.evaluate(probe.factsFor(profileDir))
        if (decision is DesktopProfilePreflightPolicy.Decision.Refuse) {
            return LockResult.Refused(decision.reason)
        }

        val hardened = try {
            DesktopProfileProvisioner.provisionDirectory(profileDir).hardened
        } catch (failure: IOException) {
            return LockResult.IoFailure(failure)
        }

        val lockPath = profileDir.resolve(DesktopStorageLayout.LOCK_FILE_NAME)
        return when (val outcome = DesktopProfileLock.tryAcquire(lockPath)) {
            is DesktopProfileLock.Result.Acquired ->
                LockResult.Locked(profileDir, outcome.lock, hardened)
            DesktopProfileLock.Result.AlreadyHeld -> LockResult.LockUnavailable
            is DesktopProfileLock.Result.Unavailable -> LockResult.IoFailure(outcome.cause)
        }
    }

    /**
     * Opens the migrated database of an already-[lock]ed profile.
     *
     * Takes ownership of [locked]'s lock: the returned [Result.Opened] releases
     * it on close, and a failure here releases it before propagating, so the
     * caller never has to decide whether the lock survived.
     */
    suspend fun openLocked(
        locked: LockResult.Locked,
        migrationContext: MigrationContext = MigrationContext.system(),
    ): Result.Opened {
        val databasePath = locked.profileDir.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)
        return try {
            val opened = DesktopDatabaseFactory.open(databasePath.toString(), migrationContext)
            DesktopProfileProvisioner.hardenFile(databasePath)
            Result.Opened(opened.database, opened.transition, locked.lock, locked.hardened)
        } catch (failure: Throwable) {
            locked.lock.close()
            throw failure
        }
    }

    /** Gathers the [DesktopProfilePreflightPolicy.ProfileDirectoryFacts] for a path. */
    interface FilesystemProbe {
        fun factsFor(profileDir: Path): DesktopProfilePreflightPolicy.ProfileDirectoryFacts
    }

    /** Probes the real default filesystem. */
    object RealFilesystemProbe : FilesystemProbe {
        private val NETWORK_FS_TYPES = setOf(
            "nfs", "nfs4", "cifs", "smb", "smbfs", "smb2", "afpfs", "webdav", "fuse.sshfs",
        )

        override fun factsFor(profileDir: Path): DesktopProfilePreflightPolicy.ProfileDirectoryFacts {
            val exists = Files.exists(profileDir, LinkOption.NOFOLLOW_LINKS)
            val isSymlink = Files.isSymbolicLink(profileDir)
            val isDirectory = exists && !isSymlink &&
                Files.isDirectory(profileDir, LinkOption.NOFOLLOW_LINKS)
            val worldWritable = exists && isWorldWritable(profileDir)
            val store = nearestFileStore(profileDir)
            return DesktopProfilePreflightPolicy.ProfileDirectoryFacts(
                exists = exists,
                isDirectory = isDirectory,
                isSymlink = isSymlink,
                worldWritable = worldWritable,
                onNetworkShare = store != null && isNetworkShare(store),
                // Every local filesystem Kani targets supports atomic rename and
                // advisory locking; network shares (the case that does not) are
                // already rejected by the onNetworkShare check above. Tests that
                // exercise the unsupported-capability refusals inject a probe.
                supportsAtomicMove = true,
                supportsExclusiveLock = true,
            )
        }

        private fun isWorldWritable(path: Path): Boolean = try {
            val permissions = Files.getPosixFilePermissions(path)
            permissions.contains(PosixFilePermission.OTHERS_WRITE)
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX (Windows): world-writable is not expressible this way.
            false
        } catch (_: IOException) {
            false
        }

        private fun isNetworkShare(store: FileStore): Boolean =
            NETWORK_FS_TYPES.contains(store.type().lowercase())

        private fun nearestFileStore(path: Path): FileStore? {
            var current: Path? = path
            while (current != null) {
                if (Files.exists(current)) {
                    return try {
                        Files.getFileStore(current)
                    } catch (_: IOException) {
                        null
                    }
                }
                current = current.parent
            }
            return null
        }
    }

    /** The current host OS, used by callers to pick a [DesktopStorageLayout.Os]. */
    fun defaultProfilesRoot(os: DesktopStorageLayout.Os): Path {
        val directories = DesktopStorageLayout.directories(
            os = os,
            env = System::getenv,
            userHome = System.getProperty("user.home").orEmpty(),
        )
        return Paths.get(DesktopStorageLayout.profilesRoot(directories))
    }
}
