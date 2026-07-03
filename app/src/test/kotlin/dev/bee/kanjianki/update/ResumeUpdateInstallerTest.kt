package dev.bee.kanjianki.update

import dev.bee.kanjianki.QueueingExecutor
import dev.bee.kanjianki.data.LocalStoreBase
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeUpdateInstallerTest {
    @Test
    fun pendingVerifiedUpdateInstallsOnResume() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = installer(
            executor,
            installs,
            canInstall = true,
            status = status(enabled = true, pendingApkName = "kani-update.apk"),
        )

        installer.onResume()
        executor.runNext()

        assertEquals(1, installs.get())
        assertTrue(executor.isEmpty())
    }

    @Test
    fun missingInstallPermissionDoesNothingOnResume() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = installer(
            executor,
            installs,
            canInstall = false,
            status = status(enabled = true, pendingApkName = "kani-update.apk"),
        )

        installer.onResume()

        assertEquals(0, installs.get())
        assertTrue(executor.isEmpty())
    }

    @Test
    fun noPendingUpdateDoesNothingOnResume() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = installer(
            executor,
            installs,
            canInstall = true,
            status = status(enabled = true, pendingApkName = ""),
        )

        installer.onResume()

        assertEquals(0, installs.get())
        assertTrue(executor.isEmpty())
    }

    @Test
    fun disabledAutomaticUpdatesDoNothingOnResume() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = installer(
            executor,
            installs,
            canInstall = true,
            status = status(enabled = false, pendingApkName = "kani-update.apk"),
        )

        installer.onResume()

        assertEquals(0, installs.get())
        assertTrue(executor.isEmpty())
    }

    @Test
    fun resumeWhileInstallAttemptQueuedDoesNotDoubleInstall() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = installer(
            executor,
            installs,
            canInstall = true,
            status = status(enabled = true, pendingApkName = "kani-update.apk"),
        )

        installer.onResume()
        installer.onResume()
        executor.runNext()

        assertEquals(1, installs.get())
        assertTrue(executor.isEmpty())
    }

    @Test
    fun installAttemptFlagResetsAfterCompletion() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = installer(
            executor,
            installs,
            canInstall = true,
            status = status(enabled = true, pendingApkName = "kani-update.apk"),
        )

        installer.onResume()
        executor.runNext()
        installer.onResume()
        executor.runNext()

        assertEquals(2, installs.get())
    }

    @Test
    fun installAttemptFlagResetsWhenInstallThrows() {
        val executor = QueueingExecutor()
        val installs = AtomicInteger()
        val installer = ResumeUpdateInstaller(
            { true },
            { status(enabled = true, pendingApkName = "kani-update.apk") },
            executor,
        ) {
            if (installs.incrementAndGet() == 1) {
                throw IllegalStateException("install broke")
            }
        }

        installer.onResume()
        try {
            executor.runNext()
        } catch (_: IllegalStateException) {
        }
        installer.onResume()
        executor.runNext()

        assertEquals(2, installs.get())
    }

    private fun installer(
        executor: QueueingExecutor,
        installs: AtomicInteger,
        canInstall: Boolean,
        status: LocalStoreBase.AutoUpdateStatus,
    ): ResumeUpdateInstaller {
        return ResumeUpdateInstaller(
            { canInstall },
            { status },
            executor,
        ) {
            installs.incrementAndGet()
        }
    }

    private fun status(enabled: Boolean, pendingApkName: String): LocalStoreBase.AutoUpdateStatus {
        return LocalStoreBase.AutoUpdateStatus(
            enabled,
            0L,
            "last result",
            "v9.9.9",
            pendingApkName,
            "pending message",
        )
    }
}
