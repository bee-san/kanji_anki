package dev.bee.kanjianki.host

import android.content.Context
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.BackupExportPolicy
import dev.bee.kanjianki.presentation.KaniEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the host's Activity Result launchers must not get wrong.
 *
 * The registration order is the load-bearing part and the reason this test exists:
 * `ComponentActivity`'s automatic registry derives its keys positionally, so a result
 * Android restored for a process it already killed is routed by *position*. Reordering the
 * registrations can therefore deliver a pending document pick to the permission callback,
 * and nothing at the call site shows that. A real activity's registry hides the ordering,
 * so the caller is faked here instead: what is asserted is the sequence of contracts the
 * constructor registers, in order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidHostLaunchersTest {
    @Test
    fun theLaunchersRegisterInTheOrderMainActivityAlwaysUsed() {
        val caller = RecordingCaller()

        AndroidHostLaunchers(
            context = ApplicationProvider.getApplicationContext(),
            caller = caller,
            onAnkiPermissionResult = {},
            onNotificationPermissionResult = {},
            onFilePicked = { _, _ -> },
        )

        // Save document, open document, then the two permissions -- the order
        // `MainActivityBase` has always registered them in.
        assertEquals(
            listOf(
                ActivityResultContracts.CreateDocument::class.java,
                ActivityResultContracts.OpenDocument::class.java,
                ActivityResultContracts.RequestPermission::class.java,
                ActivityResultContracts.RequestPermission::class.java,
            ),
            caller.registered.map { it.javaClass },
        )
        // And the documented order names the same four positions, so the constant a reader
        // is pointed at cannot drift from the code it describes.
        assertEquals(caller.registered.size, AndroidHostLaunchers.REGISTRATION_ORDER.size)
    }

    @Test
    fun aBackupExportLaunchesTheSaveDocumentPicker() {
        val caller = RecordingCaller()
        val launchers = launchers(caller)

        val launched = launchers.pickFile(
            KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_EXPORT, suggestedName = "kani-backup.gz"),
        )

        assertTrue("the save picker was launched", launched)
        assertEquals("kani-backup.gz", caller.launchesFor(0).single())
    }

    @Test
    fun aBackupExportWithNoSuggestedNameStillOpensADialog() {
        // The defect this pins: `suggestedName` defaults to blank, and `AndroidFilePicker`
        // *requires* a name for CreateDocument -- reporting a missing one as a declined
        // launch. Passing the blank through as absent therefore opened no dialog at all,
        // which from the user's side is a dead "Export backup" button.
        val caller = RecordingCaller()
        val launchers = launchers(caller, clock = { FIXED_NOW })

        assertTrue(launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_EXPORT)))
        // The same timestamped name the old backup flow suggested, so an exported file is
        // still recognizable to the restore side's own name parsing.
        assertEquals(
            BackupExportPolicy.suggestedFileName(FIXED_NOW),
            caller.launchesFor(0).single(),
        )
    }

    @Test
    fun aBackupRestoreLaunchesTheOpenDocumentPicker() {
        val caller = RecordingCaller()
        val launchers = launchers(caller)

        val launched = launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_RESTORE))

        assertTrue("the open picker was launched", launched)
        // The picker's own accepted types, which include `*/*`: a backup chosen from a
        // provider that reports no MIME type still has to be selectable, and the restore
        // path validates the file's contents rather than trusting its declared type.
        assertEquals(
            listOf("application/gzip", "application/octet-stream", "*/*"),
            (caller.launchesFor(1).single() as Array<*>).toList(),
        )
    }

    @Test
    fun aSecondPickWhileOneIsPendingIsDeclinedRatherThanQueued() {
        // The picker refuses to overwrite a waiting callback, so the second request must
        // report that it did not launch -- the caller keeps its own state unchanged rather
        // than believing a dialog is on screen.
        val caller = RecordingCaller()
        val launchers = launchers(caller)

        assertTrue(launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_RESTORE)))
        assertFalse(launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_RESTORE)))
        assertEquals(1, caller.launchesFor(1).size)
    }

    @Test
    fun theMissingKanjiCsvExportOpensNoDialog() {
        // Deliberately unmapped, matching the desktop handler: its report needs the Goal 183
        // dictionary assets, so a dialog now would save an empty file. Asserted rather than
        // left implicit, because "no consumer" and "silently broken" look identical at run
        // time.
        val caller = RecordingCaller()
        val launchers = launchers(caller)

        assertFalse(
            launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.MISSING_KANJI_CSV_EXPORT)),
        )
        assertTrue(caller.launchesFor(0).isEmpty())
        assertTrue(caller.launchesFor(1).isEmpty())
    }

    @Test
    fun aRefusedPreparationOpensNoDialogAndReportsItDidNot() {
        // Android snapshots the database *before* the save dialog opens, so a snapshot that
        // failed -- or a host that cannot take one safely -- must not be answered with a
        // picker. The user would choose a destination for a file that does not exist.
        val caller = RecordingCaller()
        val refusedPurposes = mutableListOf<KaniEffect.FilePurpose>()
        val launchers = launchers(caller, beforePick = { purpose ->
            refusedPurposes += purpose
            false
        })

        assertFalse(launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_EXPORT)))
        assertTrue("no dialog opened", caller.launchesFor(0).isEmpty())
        assertEquals(listOf(KaniEffect.FilePurpose.BACKUP_EXPORT), refusedPurposes)
    }

    @Test
    fun thePreparationRunsBeforeTheDialogRatherThanAfterIt() {
        val caller = RecordingCaller()
        val order = mutableListOf<String>()
        val launchers = launchers(caller, beforePick = {
            order += "prepare"
            true
        })

        launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_EXPORT))
        if (caller.launchesFor(0).isNotEmpty()) {
            order += "dialog"
        }

        // The order is the invariant, not just that both happened: a snapshot taken after
        // the dialog would write whatever the database looked like once the user finished
        // browsing, which is not the state they asked to export.
        assertEquals(listOf("prepare", "dialog"), order)
    }

    @Test
    fun aRestoreDoesNotWaitOnTheExportPreparation() {
        // `beforePick` is consulted for every purpose, so a restore has to be allowed
        // through by the host's own predicate rather than gated on an export snapshot it
        // does not need. Pinned here because the alternative -- one flag for both -- would
        // silently make restore unavailable whenever export was.
        val caller = RecordingCaller()
        val launchers = launchers(
            caller,
            beforePick = { purpose -> purpose != KaniEffect.FilePurpose.BACKUP_EXPORT },
        )

        assertFalse(launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_EXPORT)))
        assertTrue(launchers.pickFile(KaniEffect.PickFile(KaniEffect.FilePurpose.BACKUP_RESTORE)))
        assertTrue(caller.launchesFor(0).isEmpty())
        assertEquals(1, caller.launchesFor(1).size)
    }

    @Test
    fun eachPermissionRequestGoesToItsOwnLauncher() {
        val caller = RecordingCaller()
        val launchers = launchers(caller)

        launchers.requestAnkiDatabasePermission("com.ichi2.anki.permission.READ_WRITE_DATABASE")
        launchers.requestNotificationPermission()

        // Position 2 is AnkiDroid's, position 3 is notifications'. Crossing them would ask
        // the user for the wrong permission with the right dialog.
        assertEquals(
            listOf("com.ichi2.anki.permission.READ_WRITE_DATABASE"),
            caller.launchesFor(2),
        )
        assertEquals(
            listOf(AndroidHostLaunchers.PERMISSION_POST_NOTIFICATIONS),
            caller.launchesFor(3),
        )
    }

    private fun launchers(
        caller: RecordingCaller,
        clock: () -> Long = { FIXED_NOW },
        beforePick: (KaniEffect.FilePurpose) -> Boolean = { true },
    ): AndroidHostLaunchers = AndroidHostLaunchers(
        context = ApplicationProvider.getApplicationContext<Context>(),
        caller = caller,
        onAnkiPermissionResult = {},
        onNotificationPermissionResult = {},
        onFilePicked = { _, _ -> },
        beforePick = beforePick,
        clock = clock,
    )

    /**
     * An [ActivityResultCaller] that records registrations and launches by position.
     *
     * By position because that is exactly what the real registry keys on, and what this
     * test exists to pin. Nothing is delivered back: a result needs the framework, and the
     * callback paths belong to the instrumented gate.
     */
    private class RecordingCaller : ActivityResultCaller {
        val registered = mutableListOf<ActivityResultContract<*, *>>()
        private val launches = mutableMapOf<Int, MutableList<Any?>>()

        fun launchesFor(position: Int): List<Any?> = launches[position].orEmpty()

        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>,
            callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> {
            val position = registered.size
            val registeredContract = contract
            registered += contract
            return object : ActivityResultLauncher<I>() {
                override val contract: ActivityResultContract<I, *> = registeredContract

                override fun launch(input: I, options: ActivityOptionsCompat?) {
                    launches.getOrPut(position) { mutableListOf() } += input
                }

                override fun unregister() = Unit
            }
        }

        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>,
            registry: ActivityResultRegistry,
            callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> = registerForActivityResult(contract, callback)
    }

    private companion object {
        /** 2026-07-04T12:00:00Z, so the suggested backup name is a fixed string. */
        const val FIXED_NOW = 1_783_252_800_000L
    }
}
