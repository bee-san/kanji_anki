package dev.bee.kanjianki.backup

import android.content.Context
import android.os.Build
import dev.bee.kanjianki.FreshKaniProfileSnapshot
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.util.UUID

internal class SourceBindingRecoveryStorage private constructor(
    private val databaseFile: File,
    private val filesDir: File,
    private val cacheDir: File,
    private val apiLevel: Int,
    private val currentSnapshotter: (File) -> Unit,
    private val freshSnapshotter: (File) -> Unit,
) {
    constructor(
        context: Context,
        currentSnapshotter: (File) -> Unit,
    ) : this(
        databaseFile = context.getDatabasePath(DatabaseBackupPolicy.DB_NAME),
        filesDir = context.filesDir,
        cacheDir = context.cacheDir,
        apiLevel = Build.VERSION.SDK_INT,
        currentSnapshotter = currentSnapshotter,
        freshSnapshotter = { destination ->
            FreshKaniProfileSnapshot.create(context.applicationContext, destination)
        },
    )

    fun operationsAllowed(): Boolean =
        DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel).operationsAllowed

    fun createSafetyBackup(nowMillis: Long): Boolean {
        if (!operationsAllowed()) return false
        val backupTime = availableBackupTime(nowMillis) ?: return false
        DatabaseBackupWorker.backupDatabase(
            databaseFile,
            filesDir,
            backupTime,
            DatabaseBackupWorker.Snapshotter { _, destination ->
                currentSnapshotter(destination)
            },
        )
        return DatabaseBackupPolicy.backupFile(filesDir, backupTime).isFile
    }

    fun prepareFreshProfile(nowMillis: Long): FreshProfilePreparationResult {
        if (!createSafetyBackup(nowMillis)) {
            return FreshProfilePreparationResult.BACKUP_FAILED
        }
        return if (stageFreshProfile()) {
            FreshProfilePreparationResult.STAGED
        } else {
            FreshProfilePreparationResult.STAGING_FAILED
        }
    }

    fun stageFreshProfile(): Boolean {
        if (!operationsAllowed()) return false
        val candidate = File(
            cacheDir,
            "kani-new-profile-${UUID.randomUUID()}.db",
        )
        return try {
            freshSnapshotter(candidate)
            BackupRestoreStager.stage(
                ValidatedBackup(candidate, "new-local-kani-profile"),
                filesDir,
                apiLevel,
            )
        } catch (_: Exception) {
            false
        } finally {
            BackupRestoreStager.deleteBestEffort(candidate)
        }
    }

    private fun availableBackupTime(nowMillis: Long): Long? {
        for (offsetSeconds in 0L..MAX_BACKUP_TIME_OFFSETS) {
            val candidateTime = nowMillis + offsetSeconds * 1_000L
            if (!DatabaseBackupPolicy.backupFile(filesDir, candidateTime).exists()) {
                return candidateTime
            }
        }
        return null
    }

    internal companion object {
        private const val MAX_BACKUP_TIME_OFFSETS = 60L

        fun testing(
            databaseFile: File,
            filesDir: File,
            cacheDir: File,
            apiLevel: Int,
            currentSnapshotter: (File) -> Unit,
            freshSnapshotter: (File) -> Unit,
        ): SourceBindingRecoveryStorage =
            SourceBindingRecoveryStorage(
                databaseFile,
                filesDir,
                cacheDir,
                apiLevel,
                currentSnapshotter,
                freshSnapshotter,
            )
    }
}

internal enum class FreshProfilePreparationResult {
    STAGED,
    BACKUP_FAILED,
    STAGING_FAILED,
}
