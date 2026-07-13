package dev.bee.kanjianki.backup

import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryDurabilityTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun androidSynchronizerInspectsSyncsAndClosesDirectory() {
        val operations = FakeAndroidDirectoryOperations()

        AndroidDirectorySynchronizer(operations).sync(temp.newFolder("android-sync"))

        assertEquals(listOf("open", "inspect", "sync", "close"), operations.events)
    }

    @Test
    fun androidSynchronizerClosesDescriptorWhenTargetIsNotDirectory() {
        val operations = FakeAndroidDirectoryOperations(directory = false)

        val error = assertThrows(IOException::class.java) {
            AndroidDirectorySynchronizer(operations).sync(temp.newFolder("not-directory"))
        }

        assertEquals("Directory sync target is not a directory", error.message)
        assertEquals(listOf("open", "inspect", "close"), operations.events)
    }

    @Test
    fun androidSynchronizerPreservesPrimaryFailureAndSuppressesCloseFailure() {
        val syncFailure = IOException("sync failed")
        val closeFailure = IOException("close failed")
        val operations = FakeAndroidDirectoryOperations(
            syncFailure = syncFailure,
            closeFailure = closeFailure,
        )

        val error = assertThrows(IOException::class.java) {
            AndroidDirectorySynchronizer(operations).sync(temp.newFolder("two-failures"))
        }

        assertSame(syncFailure, error)
        assertEquals(listOf(closeFailure), error.suppressed.toList())
        assertEquals(listOf("open", "inspect", "sync", "close"), operations.events)
    }

    @Test
    fun androidSynchronizerReportsCloseFailureAfterSuccessfulSync() {
        val closeFailure = IOException("close failed")
        val operations = FakeAndroidDirectoryOperations(closeFailure = closeFailure)

        val error = assertThrows(IOException::class.java) {
            AndroidDirectorySynchronizer(operations).sync(temp.newFolder("close-failure"))
        }

        assertSame(closeFailure, error)
        assertEquals(listOf("open", "inspect", "sync", "close"), operations.events)
    }

    @Test
    fun androidSynchronizerClosesAndSuppressesCloseFailureForErrors() {
        val syncFailure = AssertionError("fatal sync failure")
        val closeFailure = IOException("close failed")
        val operations = FakeAndroidDirectoryOperations(
            syncFailure = syncFailure,
            closeFailure = closeFailure,
        )

        val error = assertThrows(AssertionError::class.java) {
            AndroidDirectorySynchronizer(operations).sync(temp.newFolder("error-failure"))
        }

        assertSame(syncFailure, error)
        assertEquals(listOf(closeFailure), error.suppressed.toList())
        assertEquals(listOf("open", "inspect", "sync", "close"), operations.events)
    }

    @Test
    fun androidSynchronizerDoesNotCloseWhenOpenFails() {
        val openFailure = IOException("open failed")
        val operations = FakeAndroidDirectoryOperations(openFailure = openFailure)

        val error = assertThrows(IOException::class.java) {
            AndroidDirectorySynchronizer(operations).sync(temp.newFolder("open-failure"))
        }

        assertSame(openFailure, error)
        assertEquals(listOf("open"), operations.events)
    }

    @Test
    fun offDeviceSystemSynchronizerAcceptsDirectoryAndRejectsFile() {
        SystemDirectorySynchronizer.sync(temp.newFolder("jvm-directory"))

        val error = assertThrows(IOException::class.java) {
            SystemDirectorySynchronizer.sync(temp.newFile("jvm-file"))
        }

        assertEquals("Directory sync target is not a directory", error.message)
    }

    private class FakeAndroidDirectoryOperations(
        private val directory: Boolean = true,
        private val openFailure: Throwable? = null,
        private val syncFailure: Throwable? = null,
        private val closeFailure: Throwable? = null,
    ) : AndroidDirectoryOperations {
        val events = ArrayList<String>()
        private val descriptor = FileDescriptor()

        override fun open(directory: File): FileDescriptor {
            events.add("open")
            openFailure?.let { throw it }
            return descriptor
        }

        override fun isDirectory(descriptor: FileDescriptor): Boolean {
            events.add("inspect")
            return directory
        }

        override fun sync(descriptor: FileDescriptor) {
            events.add("sync")
            syncFailure?.let { throw it }
        }

        override fun close(descriptor: FileDescriptor) {
            events.add("close")
            closeFailure?.let { throw it }
        }
    }
}
