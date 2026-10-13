package dev.bee.kanjianki.data.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProfileLockTest {
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
    fun acquiresAndReleasesAnExclusiveLock() {
        val lockPath = profileDirectory().resolve("kanji_anki.lock")
        val result = DesktopProfileLock.tryAcquire(lockPath)
        val acquired = assertAcquired(result)
        assertTrue(acquired.isHeld)
        acquired.close()
        assertFalse(acquired.isHeld)
    }

    @Test
    fun rejectsASecondHolderWhileTheFirstIsHeld() {
        val lockPath = profileDirectory().resolve("kanji_anki.lock")
        val first = assertAcquired(DesktopProfileLock.tryAcquire(lockPath))
        try {
            assertTrue(
                DesktopProfileLock.tryAcquire(lockPath) is DesktopProfileLock.Result.AlreadyHeld,
            )
        } finally {
            first.close()
        }
        // Once released, the lock is available again.
        val reacquired = assertAcquired(DesktopProfileLock.tryAcquire(lockPath))
        reacquired.close()
    }

    @Test
    fun reportsUnavailableWhenTheLockFileCannotBeOpened() {
        // Parent directory does not exist, so the lock file cannot be created.
        val missing = profileDirectory().resolve("nested/does-not-exist/kanji_anki.lock")
        val result = DesktopProfileLock.tryAcquire(missing)
        assertTrue(result is DesktopProfileLock.Result.Unavailable)
    }

    private fun assertAcquired(result: DesktopProfileLock.Result): DesktopProfileLock {
        assertTrue(
            "expected Acquired but was $result",
            result is DesktopProfileLock.Result.Acquired,
        )
        return (result as DesktopProfileLock.Result.Acquired).lock
    }

    private fun profileDirectory(): Path {
        val directory = Files.createTempDirectory("kani-desktop-lock-")
        temporaryDirectories.add(directory)
        return directory
    }
}
