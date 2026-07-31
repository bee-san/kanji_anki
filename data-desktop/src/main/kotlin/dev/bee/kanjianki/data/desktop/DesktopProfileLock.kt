package dev.bee.kanjianki.data.desktop

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * An exclusive, process-held lock on a single desktop profile. Only one Kani
 * process may open a given profile at a time; the lock guards the profile's
 * `kanji_anki_simple.db` and its backups against concurrent writers from a
 * second launched instance.
 *
 * The lock is an OS advisory lock on the profile's `.lock` file
 * ([DesktopStorageLayout.profileLockPath]). It is held for the lifetime of the
 * open profile and released on [close]; the JVM releases it on process exit as
 * a backstop. Acquisition is non-blocking: a second holder is rejected rather
 * than queued, so a second launch reports "profile in use" immediately.
 */
class DesktopProfileLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    /** Whether the underlying OS lock is still held. */
    val isHeld: Boolean
        get() = lock.isValid

    override fun close() {
        try {
            if (lock.isValid) lock.release()
        } finally {
            channel.close()
        }
    }

    /** The outcome of attempting to acquire a profile lock. */
    sealed interface Result {
        data class Acquired(val lock: DesktopProfileLock) : Result

        /** Another process (or thread in this process) already holds the lock. */
        data object AlreadyHeld : Result

        /** The lock file could not be opened or locked (I/O failure). */
        data class Unavailable(val cause: IOException) : Result
    }

    companion object {
        /**
         * Attempts to acquire the exclusive lock for the profile whose lock file
         * is [lockPath]. Non-blocking: returns [Result.AlreadyHeld] immediately if
         * another holder exists rather than waiting. The parent directory must
         * already exist (created by the profile preflight/creation step).
         */
        fun tryAcquire(lockPath: Path): Result {
            val channel = try {
                FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            } catch (failure: IOException) {
                return Result.Unavailable(failure)
            }
            return try {
                val lock = channel.tryLock()
                if (lock == null) {
                    channel.close()
                    Result.AlreadyHeld
                } else {
                    Result.Acquired(DesktopProfileLock(channel, lock))
                }
            } catch (_: OverlappingFileLockException) {
                channel.close()
                Result.AlreadyHeld
            } catch (failure: IOException) {
                channel.close()
                Result.Unavailable(failure)
            }
        }
    }
}
