package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.BackupRestorePolicy
import dev.bee.kanjianki.data.desktop.DesktopBackupRestoreValidator
import dev.bee.kanjianki.platform.FilePicker
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The restore pick-validate-stage flow, with the picker, validator, and stager as
 * seams. An accepted validation stages and asks for a restart; a rejected one leaves
 * the profile untouched; a cancel does nothing.
 */
class DesktopBackupRestoreTest {
    private val roots = ArrayList<Path>()

    @After
    fun tearDown() {
        roots.asReversed().forEach { root ->
            if (!Files.exists(root)) return@forEach
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun anAcceptedBackupIsStagedPendingRestart() {
        val staged = root().resolve("staged.db")
        var stagedProfile: Path? = null
        var stagedDb: Path? = null
        val restore = restore(
            picked = root().resolve("backup.db.gz"),
            validation = accepted(staged),
            stage = { profileDir, validatedDatabase ->
                stagedProfile = profileDir
                stagedDb = validatedDatabase
            },
        )

        var result: DesktopBackupRestore.Result? = null
        restore.run { result = it }

        assertEquals(DesktopBackupRestore.Result.StagedPendingRestart, result)
        assertEquals(PROFILE, stagedProfile)
        assertEquals(staged, stagedDb)
    }

    @Test
    fun aRejectedBackupIsNotStagedAndNamesWhy() {
        var stageCalled = false
        val restore = restore(
            picked = root().resolve("bad.gz"),
            validation = DesktopBackupRestoreValidator.Validation(
                BackupRestorePolicy.ValidationResult(BackupRestorePolicy.CopyId.BAD_SQLITE_MAGIC, accepted = false, message = ""),
            ),
            stage = { _, _ -> stageCalled = true },
        )

        var result: DesktopBackupRestore.Result? = null
        restore.run { result = it }

        assertEquals(DesktopBackupRestore.Result.Rejected("BAD_SQLITE_MAGIC"), result)
        assertTrue("a rejected file is never staged", !stageCalled)
    }

    @Test
    fun aCancelledDialogValidatesNothing() {
        var validated = false
        val restore = DesktopBackupRestore(
            picker = pickerReturning(null),
            restoreDir = root(),
            profileDir = PROFILE,
            openInput = { ByteArrayInputStream(ByteArray(0)) },
            validate = { _, _, _ -> validated = true; error("must not validate on cancel") },
            stage = { _, _ -> },
        )

        var result: DesktopBackupRestore.Result? = null
        restore.run { result = it }

        assertEquals(DesktopBackupRestore.Result.Cancelled, result)
        assertTrue(!validated)
    }

    @Test
    fun aValidatorFailureIsReportedRatherThanThrown() {
        val restore = restore(
            picked = root().resolve("backup.db.gz"),
            validation = null,
            stage = { _, _ -> },
            validateThrows = true,
        )

        var result: DesktopBackupRestore.Result? = null
        restore.run { result = it }

        assertTrue(result is DesktopBackupRestore.Result.Failed)
    }

    private fun restore(
        picked: Path,
        validation: DesktopBackupRestoreValidator.Validation?,
        stage: (Path, Path) -> Unit,
        validateThrows: Boolean = false,
    ) = DesktopBackupRestore(
        picker = pickerReturning(picked),
        restoreDir = root(),
        profileDir = PROFILE,
        openInput = { ByteArrayInputStream(ByteArray(1)) },
        validate = { _, _, input ->
            input()?.close()
            if (validateThrows) throw java.io.IOException("validator blew up")
            validation!!
        },
        stage = stage,
    )

    private fun accepted(staged: Path) = DesktopBackupRestoreValidator.Validation(
        result = BackupRestorePolicy.ValidationResult(BackupRestorePolicy.CopyId.READY, accepted = true, message = ""),
        stagedDatabase = staged,
        sourceName = "backup.db.gz",
    )

    private fun pickerReturning(path: Path?): FilePicker = FilePicker { _, onResult ->
        onResult(path?.let { PlatformFileReference.create(it.toString(), it.fileName.toString()) })
    }

    private fun root(): Path = Files.createTempDirectory("kani-restore").also(roots::add)

    private companion object {
        val PROFILE: Path = Path.of("/tmp/kani-profile")
    }
}
