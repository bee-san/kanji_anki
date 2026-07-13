package dev.bee.kanjianki.backup

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Persists directory-entry changes that must survive sudden power loss. */
internal fun interface DirectorySynchronizer {
    @Throws(IOException::class)
    fun sync(directory: File)
}

internal object SystemDirectorySynchronizer : DirectorySynchronizer {
    override fun sync(directory: File) {
        if (!directory.isDirectory) throw IOException("Directory sync target is not a directory")
        if (System.getProperty("java.runtime.name") == "Android Runtime") {
            syncAndroidDirectory(directory)
        } else {
            // Local JVM/Robolectric tests cannot call android.system.Os. FileChannel is
            // used only off-device; production always takes the Android branch above.
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        }
    }

    private fun syncAndroidDirectory(directory: File) {
        val descriptor = errnoAsIo("open directory") {
            Os.open(
                directory.absolutePath,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,
                0,
            )
        }
        var primaryFailure: Throwable? = null
        try {
            val stat = errnoAsIo("inspect directory") { Os.fstat(descriptor) }
            if (!OsConstants.S_ISDIR(stat.st_mode)) {
                throw IOException("Directory sync target is not a directory")
            }
            errnoAsIo("sync directory") { Os.fsync(descriptor) }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            try {
                Os.close(descriptor)
            } catch (error: ErrnoException) {
                val closeFailure = IOException("Unable to close directory sync handle", error)
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(closeFailure)
                } else {
                    throw closeFailure
                }
            }
        }
    }

    private inline fun <T> errnoAsIo(action: String, operation: () -> T): T {
        return try {
            operation()
        } catch (error: ErrnoException) {
            throw IOException("Unable to $action", error)
        }
    }
}
