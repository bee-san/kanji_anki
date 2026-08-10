package dev.bee.kanjianki

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.backup.BackupRestoreStager
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartRestoreBoundaryInstrumentedTest {
    @Test
    fun blockedRestoreStopsColdProcessReceiverBeforeDatabaseAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = BackupRestoreStager.markerFile(context.filesDir)
        val staged = BackupRestoreStager.stagedFile(context.filesDir)
        val signal = File(context.filesDir, StartupBoundaryProbeReceiver.SIGNAL_FILE_NAME)
        require(!marker.exists() && !staged.exists()) {
            "Cold-start probe requires no pending restore state"
        }
        signal.delete()
        val databaseFiles = databaseFiles(context)
        val before = databaseFiles.associateWith(::fingerprint)
        marker.parentFile?.mkdirs()
        marker.writeText("invalid-marker")

        try {
            sendProbe(context)
            SystemClock.sleep(PROCESS_FAILURE_SETTLE_MILLIS)

            assertFalse(signal.exists())
            assertEquals(before, databaseFiles.associateWith(::fingerprint))
        } finally {
            marker.delete()
            signal.delete()
        }

        sendProbe(context)
        assertTrue(waitForFile(signal))
        signal.delete()
    }

    private fun sendProbe(context: Context) {
        context.sendBroadcast(
            Intent(StartupBoundaryProbeReceiver.ACTION_PROBE).apply {
                component = ComponentName(context, StartupBoundaryProbeReceiver::class.java)
            },
        )
    }

    private fun databaseFiles(context: Context): List<File> {
        val database = context.getDatabasePath(DatabaseBackupPolicy.DB_NAME)
        return listOf(
            database,
            File(database.path + "-wal"),
            File(database.path + "-shm"),
            File(database.path + "-journal"),
        )
    }

    private fun fingerprint(file: File): FileFingerprint {
        if (!file.isFile) return FileFingerprint(false, 0L, "")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return FileFingerprint(true, file.length(), sha256)
    }

    private fun waitForFile(file: File): Boolean {
        val deadline = SystemClock.elapsedRealtime() + SIGNAL_TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (file.isFile) return true
            SystemClock.sleep(POLL_MILLIS)
        }
        return file.isFile
    }

    private data class FileFingerprint(
        val exists: Boolean,
        val length: Long,
        val sha256: String,
    )

    private companion object {
        const val PROCESS_FAILURE_SETTLE_MILLIS = 1_000L
        const val SIGNAL_TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
