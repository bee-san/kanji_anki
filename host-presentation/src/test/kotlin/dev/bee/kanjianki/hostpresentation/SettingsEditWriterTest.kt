package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.data.CommitFsrsFitCommand
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.SettingsCommands
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsKeybindingChoice
import dev.bee.kanjianki.presentation.SettingsKeybindingRow
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeybindingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a settings edit lands, and that the Automation round trip closes.
 *
 * The interesting failures here are silent ones: an edit that no mapper claims is dropped
 * with no error anywhere, which is exactly what the Android host did to every keybinding
 * and reminder edit before [SettingsEditWriter] existed. So these assert the *outcome* of
 * each path, not just that nothing threw.
 */
class SettingsEditWriterTest {
    @Test
    fun aReminderTimeSurvivesTheWriteAndTheNextRead() = runSync {
        val store = FakeDeviceSettingsStore()

        val outcome = write(store, KaniAction.Settings.SetNumber(REMINDER_TIME_KEY, 7 * 60 + 30))

        assertEquals(SettingsEditOutcome.Automation, outcome)
        // Read back through the store, not from the returned state: a write that used a
        // different key name than the read would pass any assertion made on the state.
        val reread = AutomationSettingsStore.read(store)
        assertEquals(7, reread.reminderHour)
        assertEquals(30, reread.reminderMinute)
    }

    @Test
    fun anUntouchedStoreReadsAsTheReviewedDefaults() {
        val read = AutomationSettingsStore.read(FakeDeviceSettingsStore())

        // Not zeros: a fresh install renders the 19:00 the schedulers already assume.
        assertEquals(DesktopSettingsModel.AutomationState(), read)
        assertEquals(TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR, read.reminderHour)
        assertEquals(ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY, read.reminderMaxPerDay)
    }

    @Test
    fun theWriterNeverWritesTheAutoSyncStateItsRunnerOwns() = runSync {
        val store = FakeDeviceSettingsStore()
        store.edit {
            put(DeviceSettingKeys.autoSyncConfigured, true)
            put(DeviceSettingKeys.autoSyncLastSuccessAt, 1_700_000_000_000L)
        }
        // Only the edit's own writes are the subject; the setup above is not.
        store.forgetWrites()

        write(store, KaniAction.Settings.SetNumber(AUTO_SYNC_TIME_KEY, 9 * 60 + 15))

        // The edit wrote the hour and minute; it must not have restated — or cleared —
        // the runner's own bookkeeping, or Settings would claim a sync that never ran.
        assertEquals(9, store.read(DeviceSettingKeys.autoSyncHour))
        assertEquals(15, store.read(DeviceSettingKeys.autoSyncMinute))
        assertEquals(1_700_000_000_000L, store.read(DeviceSettingKeys.autoSyncLastSuccessAt))
        assertTrue(DeviceSettingKeys.autoSyncHour.storageName in store.written)
        for (owned in RUNNER_OWNED_KEYS) {
            assertTrue("$owned was written by a settings edit", owned !in store.written)
        }
    }

    @Test
    fun aKeybindingEditTakesTheKeybindingKeyAndNothingElse() = runSync {
        val store = FakeDeviceSettingsStore()
        // The editor's own first offered candidate, rather than an id assembled here: the
        // id format is internal to `StudyKeybindingCommands`, and a test that rebuilt it
        // would keep passing after the format changed under the real editor.
        val keybindings = DesktopSettingsModel.screen(
            section = SettingsSection.KEYBINDINGS,
            snapshot = SettingsSnapshotFixtures.blank(),
        ).content as SettingsSectionContent.Keybindings
        // An available candidate the row does not already hold. "Available" alone is not
        // enough: a key already bound to *this* command has no conflict to report, so it
        // is offered, and binding it again is the no-op the mapper is right to refuse.
        val row = keybindings.rows.first { row -> row.freshCandidates().isNotEmpty() }
        val candidate = row.freshCandidates().first()

        val outcome = write(store, candidate.action as KaniAction.Settings)

        assertEquals(SettingsEditOutcome.Keybindings, outcome)
        assertEquals(setOf(DeviceSettingKeys.studyKeybindings.storageName), store.written)
        val stored = StudyKeybindingsCodec.decode(store.read(DeviceSettingKeys.studyKeybindings))
        assertTrue(stored.bindings != StudyKeybindings.DEFAULT.bindings)
    }

    @Test
    fun aBackupCommandPersistsNowhereBecauseTheShellRaisesAPicker() = runSync {
        val store = FakeDeviceSettingsStore()
        val settings = RecordingSettingsUseCases()

        for (id in listOf(SettingsCommands.BACKUP_EXPORT, SettingsCommands.BACKUP_RESTORE)) {
            val outcome = write(store, KaniAction.Settings.Command(id), settings)

            assertEquals(SettingsEditOutcome.Ignored, outcome)
        }
        assertEquals(emptySet<String>(), store.written)
        assertEquals(0, settings.saved)
    }

    @Test
    fun anAutomationEditNeverReachesTheCollectionDatabase() = runSync {
        val store = FakeDeviceSettingsStore()
        val settings = RecordingSettingsUseCases()

        write(store, KaniAction.Settings.SetToggle(REMINDER_ENABLED_KEY, true), settings)
        write(store, KaniAction.Settings.SetToggle(DEBUG_LOG_ENABLED_KEY, true), settings)

        // The device-local mappers run before the collection fallback, so an automation
        // key can never be mistaken for a portable setting. Zero saves is the assertion.
        assertEquals(0, settings.saved)
        assertEquals(true, store.read(DeviceSettingKeys.reminderEnabled))
        assertEquals(true, store.read(DeviceSettingKeys.debugLogEnabled))
    }

    @Test
    fun everyAutomationControlIsAnEditThisWriterAcceptsOrDeliberatelyRefuses() = runSync {
        // The section's own controls are the input, so a control added later without a
        // mapper case fails here rather than silently doing nothing when a user taps it.
        // Rendered from the seeded store's own state, not a separate fixture: a section
        // shown one state while the writer reads another would make every edit look like
        // a change and hide exactly the no-op case this asserts.
        val content = DesktopSettingsModel.screen(
            section = SettingsSection.AUTOMATION,
            snapshot = SettingsSnapshotFixtures.blank(),
            automation = AutomationSettingsStore.read(seededStore()),
            capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE),
        ).content as SettingsSectionContent.Controls

        val edits = content.controls.flatMap { control ->
            when (control) {
                is SettingsControl.Toggle -> listOf(control.onChange(!control.checked))
                is SettingsControl.Stepper -> listOf(control.onChange(control.incremented()))
                is SettingsControl.ActionButton -> listOf(control.action)
                else -> emptyList()
            }
        }.filterIsInstance<KaniAction.Settings>()

        assertTrue("the section dispatches nothing", edits.isNotEmpty())
        for (edit in edits) {
            val store = seededStore()
            val settings = RecordingSettingsUseCases()
            val outcome = write(store, edit, settings)
            val isPicker = (edit as? KaniAction.Settings.Command)
                ?.let { SettingsCommands.isPickerCommand(it.id) } == true
            if (isPicker) {
                assertEquals("$edit", SettingsEditOutcome.Ignored, outcome)
            } else {
                assertEquals("$edit", SettingsEditOutcome.Automation, outcome)
            }
            assertEquals("$edit reached the collection", 0, settings.saved)
        }
    }

    /**
     * A store holding automation state deliberately away from every default and bound.
     *
     * Away from the bounds because a stepper at its `max` clamps, so its "increment" is
     * the value already stored and the mapper is right to refuse it — which would read
     * here as a missing mapper case. Away from the defaults so the assertion is about the
     * mapper rather than about the seed happening to differ.
     */
    private fun seededStore(): FakeDeviceSettingsStore {
        val store = FakeDeviceSettingsStore()
        store.edit {
            AutomationSettingsStore.write(
                this,
                DesktopSettingsModel.AutomationState(
                    reminderEnabled = true,
                    reminderHour = 7,
                    reminderMinute = 30,
                    reminderMaxPerDay = ReminderAntiSpamPolicy.MIN_MAX_PER_DAY,
                    reminderQuietStartMinute = 21 * 60,
                    reminderQuietEndMinute = 6 * 60,
                    autoSyncConfigured = true,
                    autoSyncEnabled = true,
                    autoSyncHour = 9,
                    autoSyncMinute = 15,
                    debugLogEnabled = false,
                ),
            )
            // The runner's, not the section's — but the auto-sync toggle is inoperable
            // until it is set, so a section rendered without it offers a disabled control.
            put(DeviceSettingKeys.autoSyncConfigured, true)
        }
        store.forgetWrites()
        return store
    }

    @Test
    fun anUpdateToggleSurvivesTheWriteAndTheNextRead() = runSync {
        val store = FakeDeviceSettingsStore()
        val settings = RecordingSettingsUseCases()

        val outcome = write(store, KaniAction.Settings.SetToggle(BETA_UPDATES_KEY, true), settings)

        assertEquals(SettingsEditOutcome.Update, outcome)
        // Re-read through the store for the same reason the reminder test does: a write
        // under a different key name would satisfy any assertion made on the return value.
        assertEquals(true, UpdateSettingsStore.read(store).betaUpdatesEnabled)
        assertEquals("an update toggle is device-local", 0, settings.saved)
    }

    @Test
    fun theWriterNeverWritesTheUpdateRecordItsCheckerOwns() = runSync {
        val store = FakeDeviceSettingsStore()
        store.edit {
            put(DeviceSettingKeys.autoUpdateLastVersion, "0.4.0")
            put(DeviceSettingKeys.autoUpdatePendingPackage, "kani-0.4.0.apk")
        }
        store.forgetWrites()

        write(store, KaniAction.Settings.SetToggle(AUTO_UPDATE_KEY, true))

        // The staged artifact and the last found version are the checker's record. A
        // settings edit that restated them would let Settings claim a check it never ran.
        assertEquals(true, store.read(DeviceSettingKeys.autoUpdateEnabled))
        assertEquals("kani-0.4.0.apk", store.read(DeviceSettingKeys.autoUpdatePendingPackage))
        for (owned in CHECKER_OWNED_KEYS) {
            assertTrue("$owned was written by a settings edit", owned !in store.written)
        }
    }

    @Test
    fun anUpdateCommandPersistsNowhereBecauseTheHostPerformsIt() = runSync {
        val store = FakeDeviceSettingsStore()
        val settings = RecordingSettingsUseCases()

        // Checking, installing, and opening the OS permission page are host work. The
        // failure this guards is the writer falling through to the collection mapper on an
        // id it does not recognize, which would write an `update.*` key to the database.
        for (id in listOf(
            SettingsCommands.UPDATE_CHECK,
            SettingsCommands.UPDATE_INSTALL,
            SettingsCommands.UPDATE_PERMISSION,
            SettingsCommands.UPDATE_BACKGROUND_SETUP,
        )) {
            assertEquals(id, SettingsEditOutcome.Ignored, write(store, KaniAction.Settings.Command(id), settings))
        }
        assertEquals(emptySet<String>(), store.written)
        assertEquals(0, settings.saved)
    }

    @Test
    fun everyUpdateControlIsAnEditThisWriterAcceptsOrDeliberatelyRefuses() = runSync {
        val content = DesktopSettingsModel.screen(
            section = SettingsSection.UPDATE,
            snapshot = SettingsSnapshotFixtures.blank(),
            update = DesktopSettingsModel.UpdateState(
                installedVersion = "0.3.6",
                lastVersion = "0.4.0",
                pendingPackage = "kani-0.4.0.apk",
                canInstall = true,
            ),
            capabilities = PlatformCapabilities.of(PlatformCapability.UPDATE_DELIVERY),
        ).content as SettingsSectionContent.Controls

        val edits = content.controls.flatMap { control ->
            when (control) {
                is SettingsControl.Toggle -> listOf(control.onChange(!control.checked))
                is SettingsControl.ActionButton -> listOf(control.action)
                else -> emptyList()
            }
        }.filterIsInstance<KaniAction.Settings>()

        assertTrue("the section dispatches nothing", edits.isNotEmpty())
        for (edit in edits) {
            val store = FakeDeviceSettingsStore()
            val settings = RecordingSettingsUseCases()
            val outcome = write(store, edit, settings)
            val expected = if (edit is KaniAction.Settings.Command) {
                SettingsEditOutcome.Ignored
            } else {
                SettingsEditOutcome.Update
            }
            assertEquals("$edit", expected, outcome)
            assertEquals("$edit reached the collection", 0, settings.saved)
        }
    }

    @Test
    fun theSharedPickerIdsAreTheOnesTheSectionDispatches() {
        assertNotNull(SettingsCommands.filePurposeFor(SettingsCommands.BACKUP_EXPORT))
        assertNotNull(SettingsCommands.filePurposeFor(SettingsCommands.BACKUP_RESTORE))
        // Fail-closed: an id from a build that knows a fourth flow raises no effect here.
        assertNull(SettingsCommands.filePurposeFor("automation.backup_teleport"))
        assertNull(SettingsCommands.filePurposeFor("study_keybindings.reset"))
    }

    /**
     * The row's offered candidates it does not already hold.
     *
     * By label, because the rendered row carries labels rather than keystrokes — which is
     * the point of the surface — and [SettingsKeybindingRow.accelerator] is the row's own
     * bound labels joined, so the two are comparable by construction.
     */
    private fun SettingsKeybindingRow.freshCandidates(): List<SettingsKeybindingChoice> {
        val bound = accelerator.split(", ").map { it.trim() }.toSet()
        return candidates.filter { it.enabled && it.label !in bound }
    }

    private suspend fun write(
        store: DeviceSettingsStore,
        action: KaniAction.Settings,
        settings: RecordingSettingsUseCases = RecordingSettingsUseCases(),
    ): SettingsEditOutcome = SettingsEditWriter.write(
        action = action,
        deviceSettings = store,
        settingsUseCases = settings.useCases,
        keyboardPlatform = KeyboardPlatform.LINUX,
    )

    private companion object {
        // The section's own key strings. Duplicated rather than exposed, because making
        // them public would invite a host to dispatch one without the section rendering it.
        const val REMINDER_ENABLED_KEY = "automation.reminder_enabled"
        const val REMINDER_TIME_KEY = "automation.reminder_time"
        const val AUTO_SYNC_TIME_KEY = "automation.auto_sync_time"
        const val DEBUG_LOG_ENABLED_KEY = "automation.debug_log_enabled"
        const val AUTO_UPDATE_KEY = "update.auto_update_enabled"
        const val BETA_UPDATES_KEY = "update.beta_updates_enabled"

        /** State the sync runner reports and the settings screen may only render. */
        val RUNNER_OWNED_KEYS: Set<String> = setOf(
            DeviceSettingKeys.autoSyncConfigured.storageName,
            DeviceSettingKeys.autoSyncLastSuccessAt.storageName,
            DeviceSettingKeys.autoSyncLastAttemptAt.storageName,
            DeviceSettingKeys.autoSyncNextRunAt.storageName,
        )

        /** State the update checker reports and the settings screen may only render. */
        val CHECKER_OWNED_KEYS: Set<String> = setOf(
            DeviceSettingKeys.autoUpdateLastCheckAt.storageName,
            DeviceSettingKeys.autoUpdateLastResult.storageName,
            DeviceSettingKeys.autoUpdateLastVersion.storageName,
            DeviceSettingKeys.autoUpdatePendingPackage.storageName,
            DeviceSettingKeys.autoUpdatePendingMessage.storageName,
        )
    }

    /**
     * A [SettingsUseCases] over a repository that refuses to save.
     *
     * Counting saves is the point, and throwing on one is deliberate: the collection path
     * must not be reached at all by a device-local edit, so a test that took that path by
     * mistake fails loudly here instead of quietly counting to one.
     */
    private class RecordingSettingsUseCases {
        var saved: Int = 0
            private set

        val useCases: SettingsUseCases = SettingsUseCases(
            object : SettingsRepository {
                override suspend fun load(): StoreResult<SettingsSnapshot> =
                    StoreResult.ok(SettingsSnapshotFixtures.blank())

                override suspend fun save(command: SettingsSaveCommand): StoreResult<Unit> {
                    saved++
                    return StoreResult.ok(Unit)
                }

                override suspend fun commitFsrsFit(
                    command: CommitFsrsFitCommand,
                ): StoreResult<Boolean> = StoreResult.ok(false)
            },
        )
    }

    /** An in-memory device store that also records which keys were written. */
    private class FakeDeviceSettingsStore : DeviceSettingsStore {
        private val values = linkedMapOf<String, Any>()

        /** The storage names any [edit] has put, for asserting what a path did *not* touch. */
        val written: MutableSet<String> = linkedSetOf()

        /** Drops the record so far, so a test's setup writes are not part of its subject. */
        fun forgetWrites() {
            written.clear()
        }

        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> read(key: DeviceSettingKey<T>): T? = values[key.storageName] as T?

        override fun snapshot(): DeviceSettingsReader {
            val captured = LinkedHashMap(values)
            return object : DeviceSettingsReader {
                override fun contains(key: DeviceSettingKey<*>): Boolean =
                    captured.containsKey(key.storageName)

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
                    captured[key.storageName] as T?
            }
        }

        override fun edit(block: DeviceSettingsEditor.() -> Unit) {
            val staged = LinkedHashMap(values)
            val touched = linkedSetOf<String>()
            object : DeviceSettingsEditor {
                override fun contains(key: DeviceSettingKey<*>): Boolean =
                    staged.containsKey(key.storageName)

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
                    staged[key.storageName] as T?

                override fun <T : Any> put(key: DeviceSettingKey<T>, value: T) {
                    staged[key.storageName] = value
                    touched += key.storageName
                }

                override fun remove(key: DeviceSettingKey<*>) {
                    staged.remove(key.storageName)
                    touched += key.storageName
                }
            }.block()
            values.clear()
            values.putAll(staged)
            written += touched
        }
    }
}
