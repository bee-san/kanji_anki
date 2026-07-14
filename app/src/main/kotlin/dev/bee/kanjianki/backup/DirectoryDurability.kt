package dev.bee.kanjianki.backup

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Persists directory-entry changes that must survive sudden power loss. */
internal fun interface DirectorySynchronizer {
    @Throws(IOException::class)
    fun sync(directory: File)
}

internal interface AndroidDirectoryOperations {
    @Throws(IOException::class)
    fun open(directory: File): FileDescriptor

    @Throws(IOException::class)
    fun isDirectory(descriptor: FileDescriptor): Boolean

    @Throws(IOException::class)
    fun sync(descriptor: FileDescriptor)

    @Throws(IOException::class)
    fun close(descriptor: FileDescriptor)
}

internal class AndroidDirectorySynchronizer(
    private val operations: AndroidDirectoryOperations = SystemAndroidDirectoryOperations,
) : DirectorySynchronizer {
    override fun sync(directory: File) {
        val descriptor = operations.open(directory)
        DirectoryHandle(descriptor, operations).use { handle ->
            if (!operations.isDirectory(handle.descriptor)) {
                throw IOException("Directory sync target is not a directory")
            }
            operations.sync(handle.descriptor)
        }
    }

    private class DirectoryHandle(
        val descriptor: FileDescriptor,
        private val operations: AndroidDirectoryOperations,
    ) : AutoCloseable {
        override fun close() = operations.close(descriptor)
    }
}

internal object SystemDirectorySynchronizer : DirectorySynchronizer {
    private val androidSynchronizer = AndroidDirectorySynchronizer()

    override fun sync(directory: File) {
        if (!directory.isDirectory) throw IOException("Directory sync target is not a directory")
        if (System.getProperty("java.runtime.name") == "Android Runtime") {
            androidSynchronizer.sync(directory)
        } else {
            // Local JVM/Robolectric tests cannot call android.system.Os. FileChannel is
            // used only off-device; production always takes the Android branch above.
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        }
    }
}

private object SystemAndroidDirectoryOperations : AndroidDirectoryOperations {
    override fun open(directory: File): FileDescriptor {
        return errnoAsIo("open directory") {
            Os.open(
                directory.absolutePath,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,
                0,
            )
        }
    }

    override fun isDirectory(descriptor: FileDescriptor): Boolean {
        return errnoAsIo("inspect directory") { OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode) }
    }

    override fun sync(descriptor: FileDescriptor) {
        errnoAsIo("sync directory") { Os.fsync(descriptor) }
    }

    override fun close(descriptor: FileDescriptor) {
        errnoAsIo("close directory sync handle") { Os.close(descriptor) }
    }

    private inline fun <T> errnoAsIo(action: String, operation: () -> T): T {
        return try {
            operation()
        } catch (error: ErrnoException) {
            throw IOException("Unable to $action", error)
        }
    }
}
