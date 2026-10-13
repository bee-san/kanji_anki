package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.AttributionCopy
import dev.bee.kanjianki.core.BackupExportPolicy
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.DebugLogTextCopy
import dev.bee.kanjianki.core.DeckLimitsSettingsPolicy
import dev.bee.kanjianki.core.HowKaniWorksCopy
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import dev.bee.kanjianki.core.ReminderSettingsSavePolicy
import dev.bee.kanjianki.core.SettingsAutomationTextCopy
import dev.bee.kanjianki.core.SettingsReferenceDataTextCopy
import dev.bee.kanjianki.core.SettingsImportTextCopy
import dev.bee.kanjianki.core.SettingsKeybindingTextCopy
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsSectionTextCopy
import dev.bee.kanjianki.core.SettingsStudyBehaviorTextCopy
import dev.bee.kanjianki.core.SettingsThemeTextCopy
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy
import dev.bee.kanjianki.core.TimeOfDaySettingsPolicy
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy
import dev.bee.kanjianki.updatecore.BackgroundAutoUpdateOptionPolicy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.SettingsCategory
import dev.bee.kanjianki.presentation.SettingsChoiceOption
import dev.bee.kanjianki.presentation.SettingsCommands
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsKeybindingChoice
import dev.bee.kanjianki.presentation.SettingsKeybindingRow
import dev.bee.kanjianki.presentation.SettingsProseBlock
import dev.bee.kanjianki.presentation.SettingsRoot
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.presentation.StudyKeybindingCommands
import dev.bee.kanjianki.presentation.StudyKeybindingIssue
import dev.bee.kanjianki.presentation.StudyKeybindingScreen
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeybindingsCodec

/**
 * Maps a [SettingsSection] to the portable [SettingsScreen] the shared surface renders.
 *
 * Today this covers the root category menu, from `SettingsSectionTextCopy` — the same
 * titles and summaries the Android host shows. Every leaf section is
 * [SettingsScreen]'s honest placeholder: Settings is the app's largest surface (~40
 * Android panels), and it is ported section by section (later Goal 198 slices), so a
 * section not yet shared says so rather than rendering an empty panel.
 *
 * A capability [notice] rides on the category the platform limits — desktop reminders
 * fire only while the window is open — so the limitation is visible at the menu, not
 * discovered after the user opens a section that cannot do what Android's does.
 */
object DesktopSettingsModel {
    // Stable keys the controls dispatch and `settingsCommandFor` maps back. Kept here so
    // the round trip cannot drift: a control and its command read the same constant.
    private const val NEW_CARD_SORT_KEY = "new_card_sort_mode"
    private const val PROMOTION_INTERVAL_KEY = "ladder_promotion_interval_days"
    private const val DEMOTION_FAIL_STREAK_KEY = "ladder_demotion_fail_streak"
    private const val STUDY_AHEAD_KEY = "study_ahead_minutes"
    private const val NEW_PER_DAY_KEY = "deck_new_per_day"
    private const val ACTIVE_QUEUE_CAP_KEY = "deck_active_queue_cap"
    private const val IMPORT_ACTIVE_KEY = "import_active_cards"
    private const val IMPORT_SUSPENDED_KEY = "import_suspended_cards"
    private const val IMPORT_WEAK_KEY = "import_weak_cards"

    // The Automation keys. Namespaced apart from the portable settings keys above
    // because they persist somewhere else entirely — `DeviceSettingKeys`, never the
    // collection database — and `automationEditFor` is what maps them, not
    // `settingsCommandFor`. A key that ended up in both mappers would write twice.
    private const val REMINDER_ENABLED_KEY = "automation.reminder_enabled"
    private const val REMINDER_TIME_KEY = "automation.reminder_time"
    private const val REMINDER_MAX_PER_DAY_KEY = "automation.reminder_max_per_day"
    private const val REMINDER_QUIET_START_KEY = "automation.reminder_quiet_start"
    private const val REMINDER_QUIET_END_KEY = "automation.reminder_quiet_end"
    private const val AUTO_SYNC_ENABLED_KEY = "automation.auto_sync_enabled"
    private const val AUTO_SYNC_TIME_KEY = "automation.auto_sync_time"
    private const val DEBUG_LOG_ENABLED_KEY = "automation.debug_log_enabled"

    // The backup commands raise a host file picker rather than persisting anything, so
    // their ids live in `presentation-api` beside the reducer that turns them into a
    // `KaniEffect.PickFile`. Aliased here only so the section reads like the rest of it.
    private val BACKUP_EXPORT_COMMAND = SettingsCommands.BACKUP_EXPORT
    private val BACKUP_RESTORE_COMMAND = SettingsCommands.BACKUP_RESTORE

    private const val AUTO_UPDATE_ENABLED_KEY = "update.auto_update_enabled"
    private const val BETA_UPDATES_ENABLED_KEY = "update.beta_updates_enabled"

    // Host actions, not saved state, so their ids live beside the backup pair in
    // `presentation-api`. Aliased here only so the section reads like the rest of it.
    private val UPDATE_CHECK_COMMAND = SettingsCommands.UPDATE_CHECK
    private val UPDATE_INSTALL_COMMAND = SettingsCommands.UPDATE_INSTALL
    private val UPDATE_PERMISSION_COMMAND = SettingsCommands.UPDATE_PERMISSION
    private val UPDATE_BACKGROUND_SETUP_COMMAND = SettingsCommands.UPDATE_BACKGROUND_SETUP

    // A scheduled time is edited as one minute-of-day value rather than an hour control
    // and a minute control. Two steppers would need two distinct labels, and a label is
    // what a control's test tag is derived from, so the pair would either collide or need
    // copy that does not exist. The presets do the coarse jumping; the stepper's quarter
    // hour is the fine adjustment, which is the granularity the Android presets used.
    private const val TIME_STEP_MINUTES = 15
    private const val QUIET_STEP_MINUTES = 30
    private const val MINUTES_PER_DAY = 24 * 60

    // The new-card sort modes, in the order the section lists them.
    private val NEW_CARD_SORT_MODES: List<String> = listOf(
        RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY,
        RecordsBase.NEW_CARD_SORT_FREQUENCY,
        RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY,
        RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
        RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS,
    )

    fun screen(
        section: SettingsSection,
        snapshot: SettingsSnapshot,
        bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
        platform: KeyboardPlatform = KeyboardPlatform.LINUX,
        attribution: AttributionTexts = AttributionTexts.UNAVAILABLE,
        automation: AutomationState = AutomationState(),
        update: UpdateState = UpdateState(),
        capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    ): SettingsScreen = when (section) {
        SettingsSection.ROOT -> SettingsScreen(section = section, root = root())
        SettingsSection.APPEARANCE ->
            SettingsScreen(section = section, content = appearance(snapshot.themeChoice))
        SettingsSection.STUDY_BEHAVIOR ->
            SettingsScreen(section = section, content = studyBehavior(snapshot))
        SettingsSection.IMPORT_SYNC ->
            SettingsScreen(section = section, content = importSync(snapshot))
        SettingsSection.KEYBINDINGS ->
            SettingsScreen(section = section, content = keybindings(bindings, platform))
        SettingsSection.AUTOMATION ->
            SettingsScreen(section = section, content = automation(automation, capabilities))
        SettingsSection.UPDATE ->
            SettingsScreen(section = section, content = update(update, capabilities))
        SettingsSection.DISPLAY_DATA ->
            SettingsScreen(section = section, content = displayData())
        SettingsSection.HOW_IT_WORKS ->
            SettingsScreen(section = section, content = howItWorks())
        SettingsSection.LICENSES ->
            SettingsScreen(section = section, content = licenses(attribution))
        // Deliberately no `else`: every section is mapped now, and an exhaustive `when` is
        // what makes a section added later a compile error here rather than a screen that
        // renders `SettingsScreen`'s placeholder in a shipped build.
    }

    /**
     * The device-local automation state a host has read, for [SettingsSection.AUTOMATION].
     *
     * Its own holder rather than fields on [SettingsSnapshot] because every value here is
     * device-local (`DeviceSettingKeys`) and deliberately excluded from portable backups:
     * restoring a phone's profile onto a laptop must not tell the laptop it has a
     * pending update or a 19:00 alarm the OS never armed. Read by the host from its
     * device store, mapped here, and written back through [automationEditFor].
     *
     * The defaults are the reviewed ones from `:core` rather than zeros, so an install
     * that has never touched Automation renders the same 19:00 the schedulers assume
     * instead of a midnight the user did not choose.
     */
    data class AutomationState(
        val reminderEnabled: Boolean = false,
        val reminderHour: Int = TimeOfDaySettingsPolicy.DEFAULT_REMINDER_HOUR,
        val reminderMinute: Int = TimeOfDaySettingsPolicy.DEFAULT_REMINDER_MINUTE,
        val reminderMaxPerDay: Int = ReminderAntiSpamPolicy.DEFAULT_MAX_PER_DAY,
        val reminderQuietStartMinute: Int = ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE,
        val reminderQuietEndMinute: Int = ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE,
        /**
         * Whether the OS is currently refusing Kani's notifications.
         *
         * Runtime state the host asks its notifier for, not a [PlatformCapabilities] entry:
         * "the user revoked the permission" and "this host has no OS-scheduled notifier"
         * are different facts with different remedies, and only the first is something the
         * user can fix. Desktop leaves this false — its reminders do fire, in-window.
         */
        val notificationsBlocked: Boolean = false,
        val autoSyncConfigured: Boolean = false,
        val autoSyncEnabled: Boolean = false,
        val autoSyncHour: Int = TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_HOUR,
        val autoSyncMinute: Int = TimeOfDaySettingsPolicy.DEFAULT_AUTO_SYNC_MINUTE,
        val autoSyncLastSuccessAtMillis: Long = 0L,
        val autoSyncLastAttemptAtMillis: Long = 0L,
        val autoSyncNextRunAtMillis: Long = 0L,
        val debugLogEnabled: Boolean = false,
        val lastAutomaticBackupAtMillis: Long? = null,
        val automaticBackupCount: Int = 0,
    )

    /**
     * The update state a host has read, for [SettingsSection.UPDATE].
     *
     * Two device-local user choices — [autoUpdateEnabled] and [betaUpdatesEnabled] — and
     * everything else the updater's own record of what it last did. Separate from
     * [AutomationState] despite both being device-local, because this section is a live
     * deep-link target: the update notification opens it directly, so it has to render
     * from whatever the background checker last wrote without the Automation section
     * having been loaded.
     *
     * [canInstall] is the host's answer, not a stored value. On Android it is
     * `REQUEST_INSTALL_PACKAGES`, which the user can revoke between two renders; on
     * desktop it is whether the resolved installation channel is one Kani may replace.
     * Reading it per load rather than persisting it is what stops the panel offering an
     * install button that would immediately bounce off a permission.
     */
    data class UpdateState(
        val autoUpdateEnabled: Boolean = false,
        val betaUpdatesEnabled: Boolean = false,
        val installedVersion: String = "",
        val lastCheckAtMillis: Long = 0L,
        val lastResult: String = "",
        val lastVersion: String = "",
        val pendingPackage: String = "",
        val pendingMessage: String = "",
        val canInstall: Boolean = false,
    ) {
        /** Whether a verified artifact is staged and waiting to be installed. */
        val hasPendingUpdate: Boolean
            get() = AutoUpdateStatusPolicy.hasPendingUpdate(pendingPackage)
    }

    /**
     * One device-local automation write, or null when the action is not one.
     *
     * The [AutomationState] the store should hold after [action], already normalized: the
     * reminder through `ReminderSettingsSavePolicy`, the schedule through
     * `TimeOfDaySettingsPolicy.normalizeAutoSync`, the anti-spam knobs through
     * `ReminderAntiSpamPolicy`. Normalizing here rather than at the store means a control
     * cannot persist a value the policy would clamp on the way back out, which is how a
     * setting comes to read differently from what was saved.
     *
     * Its own mapper rather than a [SettingsSaveCommand] branch, mirroring
     * [keybindingEditFor], because these keys live in `DeviceSettingKeys` and are on
     * `portableExclusionStorageNames` — restoring a phone's backup onto a laptop must not
     * hand the laptop a 19:00 alarm no OS armed. A key in both mappers would be written
     * twice, so the automation keys are namespaced apart to make that impossible.
     *
     * Null covers "not an automation edit" *and* "nothing would change": rewriting the
     * store on every re-render of an untouched section is how a settings file churns.
     * The backup commands deliberately map to null — they persist nothing and are the
     * host's cue to raise `KaniEffect.PickFile`.
     */
    fun automationEditFor(
        action: KaniAction.Settings,
        current: AutomationState,
    ): AutomationState? {
        val next = when (action) {
            is KaniAction.Settings.SetToggle -> when (action.key) {
                REMINDER_ENABLED_KEY -> current.copy(reminderEnabled = action.enabled)
                AUTO_SYNC_ENABLED_KEY ->
                    // Refused outright while unconfigured rather than normalized to false:
                    // the control is disabled there, so an arriving edit is not a user
                    // choice, and writing the zeroed value would still churn the store.
                    if (current.autoSyncConfigured) {
                        current.copy(autoSyncEnabled = action.enabled)
                    } else {
                        return null
                    }
                DEBUG_LOG_ENABLED_KEY -> current.copy(debugLogEnabled = action.enabled)
                else -> return null
            }
            is KaniAction.Settings.SetNumber -> when (action.key) {
                REMINDER_TIME_KEY -> current.copy(
                    reminderHour = action.value.floorDiv(60),
                    reminderMinute = action.value.mod(60),
                )
                AUTO_SYNC_TIME_KEY -> current.copy(
                    autoSyncHour = action.value.floorDiv(60),
                    autoSyncMinute = action.value.mod(60),
                )
                REMINDER_MAX_PER_DAY_KEY -> current.copy(reminderMaxPerDay = action.value)
                REMINDER_QUIET_START_KEY -> current.copy(reminderQuietStartMinute = action.value)
                REMINDER_QUIET_END_KEY -> current.copy(reminderQuietEndMinute = action.value)
                else -> return null
            }
            is KaniAction.Settings.SetChoice -> return null
            is KaniAction.Settings.Command -> return null
        }
        val normalized = normalizeAutomation(next)
        return if (normalized == current) null else normalized
    }

    /**
     * [state] with every field put through the `:core` policy that owns it.
     *
     * One place, used by both the mapper and any host reading a store that predates a
     * bound: a reminder hour of 31 or a quiet boundary of -1 comes back as the reviewed
     * default rather than being rendered as though the user chose it.
     */
    fun normalizeAutomation(state: AutomationState): AutomationState {
        val reminder = ReminderSettingsSavePolicy.fields(
            state.reminderEnabled,
            state.reminderHour,
            state.reminderMinute,
        )
        val autoSync = TimeOfDaySettingsPolicy.normalizeAutoSync(
            state.autoSyncConfigured,
            state.autoSyncEnabled,
            state.autoSyncHour,
            state.autoSyncMinute,
            state.autoSyncLastAttemptAtMillis,
            state.autoSyncLastSuccessAtMillis,
            state.autoSyncNextRunAtMillis,
        )
        return state.copy(
            reminderEnabled = reminder.enabled,
            reminderHour = reminder.hour,
            reminderMinute = reminder.minute,
            reminderMaxPerDay = ReminderAntiSpamPolicy.normalizeMaxPerDay(state.reminderMaxPerDay),
            reminderQuietStartMinute = ReminderAntiSpamPolicy.normalizeMinuteOfDay(
                state.reminderQuietStartMinute,
                ReminderAntiSpamPolicy.DEFAULT_QUIET_START_MINUTE,
            ),
            reminderQuietEndMinute = ReminderAntiSpamPolicy.normalizeMinuteOfDay(
                state.reminderQuietEndMinute,
                ReminderAntiSpamPolicy.DEFAULT_QUIET_END_MINUTE,
            ),
            autoSyncConfigured = autoSync.configured,
            autoSyncEnabled = autoSync.enabled,
            autoSyncHour = autoSync.hour,
            autoSyncMinute = autoSync.minute,
            autoSyncLastAttemptAtMillis = autoSync.lastAttemptAtMillis,
            autoSyncLastSuccessAtMillis = autoSync.lastSuccessAtMillis,
            autoSyncNextRunAtMillis = autoSync.nextRunAtMillis,
        )
    }

    /**
     * The attribution bodies a host has already read, for the licences page.
     *
     * Passed in rather than read here because each host keeps them somewhere different —
     * Android in `res/raw`, desktop on the classpath — while the *formatting* is
     * [AttributionCopy]'s in `:core` and shared. A host reads bytes; this decides nothing
     * about wording.
     *
     * Each field falls back to [AttributionCopy]'s own fallback text when a host cannot
     * read its source, so a missing file yields a named credit rather than a blank
     * section. Dropping a credit silently is the one outcome this must not have: the
     * attributions are a licence obligation, not a nicety.
     */
    data class AttributionTexts(
        val dictionary: String,
        val strokes: String,
        val fonts: String,
    ) {
        companion object {
            /**
             * The stated-fallback texts, for a host that has not wired its sources yet.
             *
             * Not blank strings: an unwired host shows the same "credits unavailable"
             * wording a read failure produces, which is honest, rather than a page that
             * looks as though nothing needs crediting.
             */
            val UNAVAILABLE: AttributionTexts = AttributionTexts(
                dictionary = AttributionCopy.dictionaryFallback(),
                strokes = AttributionCopy.kanjiVgFallback(),
                fonts = AttributionCopy.kanjiVgFallback(),
            )
        }
    }

    /**
     * The Automation section: reminders, daily sync, backups, and the debug log.
     *
     * Every value is device-local, and that shapes the whole section. The times are
     * steppers rather than a native time picker because a picker is a per-host dialog
     * and the shared surface has no such control; a 15-minute stepper reaches every
     * time the presets offered and is keyboard-operable, which the Android dialog was
     * not.
     *
     * Three truths are stated rather than hidden. The reminder reports blocked when the
     * host says its notifications are off ([AutomationState.notificationsBlocked]) —
     * runtime state, not a missing capability, because a host without
     * [PlatformCapability.NOTIFICATIONS] still evaluates reminders in-window and saying
     * "blocked" there would be wrong. Daily sync stays disabled until a sync has actually
     * run ([AutomationState.autoSyncConfigured]) — `TimeOfDaySettingsPolicy` zeroes
     * `enabled` when unconfigured anyway, so a toggle that appeared to take would be
     * lying — and its status line says why. Backup export/restore is gated on
     * [PlatformCapability.BACKUP_RESTORE], because a host that cannot take a live
     * snapshot cannot honour the atomic-publication contract and must not offer a button
     * that implies it can.
     *
     * The reminder's own scheduling limit is not restated here: the root menu already
     * carries [REMINDER_NOTICE] on the Automation card, which is where the user meets it.
     */
    private fun automation(
        state: AutomationState,
        capabilities: PlatformCapabilities,
    ): SettingsSectionContent.Controls = SettingsSectionContent.Controls(
        title = SettingsSectionTextCopy.settingsAutomationTitle(),
        controls = buildList {
            add(
                SettingsControl.Toggle(
                    label = SettingsAutomationTextCopy.dailyReminderTitle(),
                    checked = state.reminderEnabled,
                    onChange = { KaniAction.Settings.SetToggle(REMINDER_ENABLED_KEY, it) },
                ),
            )
            add(
                SettingsControl.Info(
                    label = SettingsAutomationTextCopy.dailyReminderTitle(),
                    value = SettingsAutomationTextCopy.reminderStatus(
                        enabled = state.reminderEnabled,
                        blocked = state.notificationsBlocked,
                        displayTime = SettingsAutomationTextCopy.reminderTime(
                            state.reminderHour,
                            state.reminderMinute,
                        ),
                    ),
                ),
            )
            add(
                timeStepper(
                    label = SettingsAutomationTextCopy.reminderTimeButtonLabel(
                        state.reminderHour,
                        state.reminderMinute,
                    ),
                    minuteOfDay = state.reminderHour * 60 + state.reminderMinute,
                    key = REMINDER_TIME_KEY,
                ),
            )
            addAll(reminderPresets(REMINDER_TIME_KEY))
            add(
                SettingsControl.Stepper(
                    label = SettingsAutomationTextCopy.reminderMaxPerDayLabel(state.reminderMaxPerDay),
                    value = state.reminderMaxPerDay,
                    min = ReminderAntiSpamPolicy.MIN_MAX_PER_DAY,
                    max = ReminderAntiSpamPolicy.MAX_MAX_PER_DAY,
                    onChange = { KaniAction.Settings.SetNumber(REMINDER_MAX_PER_DAY_KEY, it) },
                ),
            )
            add(
                SettingsControl.Info(
                    label = SettingsAutomationTextCopy.reminderQuietHoursLabel(
                        state.reminderQuietStartMinute,
                        state.reminderQuietEndMinute,
                    ),
                    value = SettingsAutomationTextCopy.reminderQuietHoursBody(),
                ),
            )
            add(
                quietStepper(
                    label = SettingsAutomationTextCopy.reminderQuietStartButtonLabel(
                        state.reminderQuietStartMinute,
                    ),
                    minuteOfDay = state.reminderQuietStartMinute,
                    key = REMINDER_QUIET_START_KEY,
                ),
            )
            add(
                quietStepper(
                    label = SettingsAutomationTextCopy.reminderQuietEndButtonLabel(
                        state.reminderQuietEndMinute,
                    ),
                    minuteOfDay = state.reminderQuietEndMinute,
                    key = REMINDER_QUIET_END_KEY,
                ),
            )
            add(
                SettingsControl.Toggle(
                    label = SettingsAutomationTextCopy.dailyAnkiSyncTitle(),
                    checked = state.autoSyncEnabled,
                    // Never operable before the first real sync: the policy zeroes
                    // `enabled` while unconfigured, so a toggle that appeared to take
                    // would silently revert on the reload.
                    enabled = state.autoSyncConfigured,
                    onChange = { KaniAction.Settings.SetToggle(AUTO_SYNC_ENABLED_KEY, it) },
                ),
            )
            add(
                SettingsControl.Info(
                    label = SettingsAutomationTextCopy.autoSyncStatus(
                        configured = state.autoSyncConfigured,
                        enabled = state.autoSyncEnabled,
                        displayTime = SettingsAutomationTextCopy.reminderTime(
                            state.autoSyncHour,
                            state.autoSyncMinute,
                        ),
                    ),
                    value = SettingsAutomationTextCopy.autoSyncDetail(
                        configured = state.autoSyncConfigured,
                        enabled = state.autoSyncEnabled,
                        lastSuccessText = shortDateTimeOrBlank(state.autoSyncLastSuccessAtMillis),
                        // Only when it differs from the last success, matching the Android
                        // panel: repeating one timestamp under two labels reads as two
                        // separate events.
                        lastAttemptText = if (
                            state.autoSyncLastAttemptAtMillis != state.autoSyncLastSuccessAtMillis
                        ) {
                            shortDateTimeOrBlank(state.autoSyncLastAttemptAtMillis)
                        } else {
                            ""
                        },
                        nextRunText = shortDateTimeOrBlank(state.autoSyncNextRunAtMillis),
                    ),
                ),
            )
            add(
                timeStepper(
                    label = SettingsAutomationTextCopy.dailyAnkiSyncTitle(),
                    minuteOfDay = state.autoSyncHour * 60 + state.autoSyncMinute,
                    key = AUTO_SYNC_TIME_KEY,
                ),
            )
            add(
                SettingsControl.Info(
                    label = BackupExportPolicy.panelTitle(),
                    value = BackupExportPolicy.lastBackupLine(state.lastAutomaticBackupAtMillis),
                ),
            )
            add(
                SettingsControl.Info(
                    label = BackupExportPolicy.panelBody(),
                    value = BackupExportPolicy.archiveCountLine(state.automaticBackupCount),
                ),
            )
            val backupSupported = capabilities.supports(PlatformCapability.BACKUP_RESTORE)
            add(
                SettingsControl.ActionButton(
                    label = BackupExportPolicy.exportNowLabel(),
                    action = KaniAction.Settings.Command(BACKUP_EXPORT_COMMAND),
                    enabled = backupSupported,
                ),
            )
            add(
                SettingsControl.ActionButton(
                    label = BackupExportPolicy.restoreFromBackupLabel(),
                    action = KaniAction.Settings.Command(BACKUP_RESTORE_COMMAND),
                    // Destructive: a restore replaces this device's whole database. The
                    // confirmation and validation are the restore flow's own, raised by
                    // the host effect; this only makes the button look like what it does.
                    destructive = true,
                    enabled = backupSupported,
                ),
            )
            add(
                SettingsControl.Toggle(
                    label = DebugLogTextCopy.debugLogToggleLabel(state.debugLogEnabled),
                    checked = state.debugLogEnabled,
                    onChange = { KaniAction.Settings.SetToggle(DEBUG_LOG_ENABLED_KEY, it) },
                ),
            )
            add(
                SettingsControl.Info(
                    label = DebugLogTextCopy.debugLogStatus(state.debugLogEnabled),
                    value = DebugLogTextCopy.debugLogDetail(state.debugLogEnabled),
                ),
            )
            // The way in to the Update section, whose parent this is. Without it the
            // section is reachable only from the update notification's deep link, which
            // is how a screen comes to exist that a user cannot navigate to.
            add(
                SettingsControl.ActionButton(
                    label = SettingsAutomationTextCopy.updatePageTitle(),
                    action = KaniAction.Navigation.Open(
                        KaniDestination.Settings(SettingsSection.UPDATE),
                    ),
                ),
            )
        },
    )

    /**
     * The update panel: what is installed, what the last check found, and what may be done.
     *
     * Reports before it offers. The three "what happened" lines come first because the
     * commonest reason a user opens this section is the update notification, and the
     * question it raises is "what version, and did it work" rather than "check again".
     *
     * The install action is offered only when there is something staged *and* the host may
     * install it. Both halves matter: an install button with nothing staged does nothing,
     * and one the OS will refuse sends the user into a permission screen they cannot get
     * back from. When the permission is what is missing, the panel says so and offers the
     * settings page instead — [SettingsCommands.UPDATE_PERMISSION] rather than an install
     * that would fail.
     *
     * [PlatformCapability.UPDATE_DELIVERY] gates the whole action set rather than any one
     * button. A source build, a Flatpak, a distro package — anything
     * `DesktopInstallationChannelPolicy` resolves to UNKNOWN — is a install Kani did not
     * place and must not replace, so it reads its versions and is offered no action at all.
     */
    private fun update(
        state: UpdateState,
        capabilities: PlatformCapabilities,
    ): SettingsSectionContent.Controls {
        val status = AutoUpdateStatusPolicy.normalize(
            state.autoUpdateEnabled,
            state.lastCheckAtMillis,
            state.lastResult,
            state.lastVersion,
            state.pendingPackage,
            state.pendingMessage,
        )
        val deliverable = capabilities.supports(PlatformCapability.UPDATE_DELIVERY)
        return SettingsSectionContent.Controls(
            title = SettingsAutomationTextCopy.updatePageTitle(),
            controls = buildList {
                add(
                    SettingsControl.Info(
                        label = SettingsAutomationTextCopy.installedVersionLine(state.installedVersion),
                        value = SettingsAutomationTextCopy.latestVersionLine(status.lastVersion()),
                    ),
                )
                add(
                    SettingsControl.Info(
                        label = SettingsAutomationTextCopy.autoUpdateLastCheckLine(
                            DateTextPolicy.autoUpdateLastCheckText(status.lastCheckAtMillis()),
                        ),
                        value = SettingsAutomationTextCopy.autoUpdateLastResultLine(status.lastResult()),
                    ),
                )
                add(
                    SettingsControl.Info(
                        label = SettingsAutomationTextCopy.autoUpdatePanelStatus(status.enabled()),
                        value = SettingsAutomationTextCopy.installPermissionLine(state.canInstall),
                    ),
                )
                if (state.hasPendingUpdate) {
                    add(
                        SettingsControl.Info(
                            label = SettingsAutomationTextCopy.verifiedApkReadyLine(status.lastVersion()),
                            value = status.pendingMessage().ifEmpty {
                                SettingsAutomationTextCopy.pendingUpdateFallback(state.canInstall)
                            },
                        ),
                    )
                }
                add(
                    SettingsControl.ActionButton(
                        label = SettingsAutomationTextCopy.checkForUpdateLabel(),
                        action = KaniAction.Settings.Command(UPDATE_CHECK_COMMAND),
                        enabled = deliverable,
                    ),
                )
                if (state.hasPendingUpdate) {
                    add(
                        SettingsControl.ActionButton(
                            label = SettingsAutomationTextCopy.installVerifiedUpdateLabel(),
                            action = KaniAction.Settings.Command(UPDATE_INSTALL_COMMAND),
                            // Replaces the running application, so the host raises its own
                            // per-version confirmation (`DesktopUpdateHandoffPolicy`); this
                            // only makes the button look like what it does.
                            destructive = true,
                            enabled = deliverable && state.canInstall,
                        ),
                    )
                }
                if (!state.canInstall) {
                    add(
                        SettingsControl.ActionButton(
                            label = SettingsAutomationTextCopy.setupAppInstallsLabel(),
                            action = KaniAction.Settings.Command(UPDATE_PERMISSION_COMMAND),
                            enabled = deliverable,
                        ),
                    )
                }
                add(
                    SettingsControl.Toggle(
                        label = SettingsAutomationTextCopy.automaticUpdatesToggleLabel(status.enabled()),
                        checked = status.enabled(),
                        enabled = deliverable,
                        onChange = { KaniAction.Settings.SetToggle(AUTO_UPDATE_ENABLED_KEY, it) },
                    ),
                )
                if (BackgroundAutoUpdateOptionPolicy.optionVisible(status.enabled(), state.canInstall)) {
                    add(
                        SettingsControl.ActionButton(
                            label = SettingsAutomationTextCopy.autoUpdateInBackgroundLabel(),
                            action = KaniAction.Settings.Command(UPDATE_BACKGROUND_SETUP_COMMAND),
                            enabled = deliverable,
                        ),
                    )
                }
                add(
                    SettingsControl.Toggle(
                        label = SettingsAutomationTextCopy.betaUpdatesToggleLabel(state.betaUpdatesEnabled),
                        checked = state.betaUpdatesEnabled,
                        enabled = deliverable,
                        onChange = { KaniAction.Settings.SetToggle(BETA_UPDATES_ENABLED_KEY, it) },
                    ),
                )
                add(
                    SettingsControl.Info(
                        label = SettingsAutomationTextCopy.betaUpdatesToggleLabel(state.betaUpdatesEnabled),
                        value = SettingsAutomationTextCopy.betaUpdatesDescription(),
                    ),
                )
            },
        )
    }

    /**
     * One device-local update write, or null when the action is not one.
     *
     * Only the two toggles: everything else the section dispatches is an action the host
     * performs — check now, install, open the OS permission page — and those are
     * [SettingsCommands] the shell turns into effects, exactly like the backup pair. The
     * updater's own record (last check, last result, pending artifact) is written by the
     * checker and only read here, for the same reason the auto-sync timestamps are.
     *
     * Turning automatic updates on or off also has to schedule or cancel the background
     * check, which is host work; the host does that after this returns a state whose
     * [UpdateState.autoUpdateEnabled] changed.
     */
    fun updateEditFor(
        action: KaniAction.Settings,
        current: UpdateState,
    ): UpdateState? {
        val next = when {
            action !is KaniAction.Settings.SetToggle -> return null
            action.key == AUTO_UPDATE_ENABLED_KEY -> current.copy(autoUpdateEnabled = action.enabled)
            action.key == BETA_UPDATES_ENABLED_KEY -> current.copy(betaUpdatesEnabled = action.enabled)
            else -> return null
        }
        return if (next == current) null else next
    }

    /**
     * One scheduled time, edited as a minute of day and read as a clock time.
     *
     * A stepper rather than a native time picker: a picker is a per-host dialog the shared
     * surface has no control for, and the Android one was not keyboard-reachable. The
     * quarter-hour step is the fine adjustment; [reminderPresets] does the coarse jumping,
     * so nobody presses `+` ninety times.
     *
     * [SettingsControl.Stepper.valueLabel] is what makes this legible — the stored value
     * is 1,140, and "1140 minutes" is not a time anyone set.
     */
    private fun timeStepper(label: String, minuteOfDay: Int, key: String): SettingsControl =
        SettingsControl.Stepper(
            label = label,
            value = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - TIME_STEP_MINUTES),
            min = 0,
            max = MINUTES_PER_DAY - TIME_STEP_MINUTES,
            step = TIME_STEP_MINUTES,
            valueLabel = minuteOfDayLabel(minuteOfDay),
            onChange = { KaniAction.Settings.SetNumber(key, it) },
        )

    /**
     * The four one-tap reminder times, carried over from the Android panel unchanged.
     *
     * The same hours a user already knows (Morning 8:00, Lunch 12:30, Evening 19:00,
     * Night 21:00), so an install that syncs its device settings across hosts finds the
     * times where it left them.
     */
    private fun reminderPresets(key: String): List<SettingsControl> = listOf(
        Triple(SettingsAutomationTextCopy.morningReminderPresetLabel(), 8, 0),
        Triple(SettingsAutomationTextCopy.lunchReminderPresetLabel(), 12, 30),
        Triple(SettingsAutomationTextCopy.eveningReminderPresetLabel(), 19, 0),
        Triple(SettingsAutomationTextCopy.nightReminderPresetLabel(), 21, 0),
    ).map { (label, hour, minute) ->
        SettingsControl.ActionButton(
            label = SettingsAutomationTextCopy.reminderPresetButtonLabel(label, hour, minute),
            action = KaniAction.Settings.SetNumber(key, hour * 60 + minute),
        )
    }

    /** One quiet-hour boundary, stepped by a half hour and read as a clock time. */
    private fun quietStepper(label: String, minuteOfDay: Int, key: String): SettingsControl =
        SettingsControl.Stepper(
            label = label,
            value = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - QUIET_STEP_MINUTES),
            min = 0,
            max = MINUTES_PER_DAY - QUIET_STEP_MINUTES,
            step = QUIET_STEP_MINUTES,
            valueLabel = minuteOfDayLabel(minuteOfDay),
            onChange = { KaniAction.Settings.SetNumber(key, it) },
        )

    /** A minute of day as `HH:mm`, clamped so a stored out-of-range value still reads. */
    private fun minuteOfDayLabel(minuteOfDay: Int): String {
        val safe = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return TimeOfDaySettingsPolicy.displayTime(safe / 60, safe % 60)
    }

    /**
     * A timestamp as a short date-time, or blank when it never happened.
     *
     * Blank rather than an epoch date: `autoSyncDetail` drops an empty part entirely, so
     * a sync that has never run shows no "Last sync" line instead of 1970.
     */
    private fun shortDateTimeOrBlank(millis: Long): String =
        if (millis > 0L) DateTextPolicy.shortDateTime(millis) else ""

    /**
     * Display & data: the two pages this section is a doorway to.
     *
     * Buttons rather than category cards because those belong to
     * [SettingsSection.ROOT]'s menu, and these are one level down. Both children are
     * prose, so this section carries no state of its own and takes no snapshot: the
     * frequency-rank editor that also lives under this heading on Android stays
     * unported for now, which the missing control says plainly rather than a disabled
     * one implying it is coming.
     */
    private fun displayData(): SettingsSectionContent.Controls = SettingsSectionContent.Controls(
        title = SettingsSectionTextCopy.settingsReferenceDataTitle(),
        controls = listOf(
            SettingsControl.ActionButton(
                label = HowKaniWorksCopy.pageTitle(),
                action = KaniAction.Navigation.Open(
                    KaniDestination.Settings(SettingsSection.HOW_IT_WORKS),
                ),
            ),
            SettingsControl.ActionButton(
                label = SettingsReferenceDataTextCopy.openDataLicensesLabel(),
                action = KaniAction.Navigation.Open(
                    KaniDestination.Settings(SettingsSection.LICENSES),
                ),
            ),
        ),
    )

    /**
     * The "How Kani works" explainer: [HowKaniWorksCopy]'s sections, verbatim.
     *
     * Verbatim matters here. The first section states what Kani reads from AnkiDroid and
     * that the only writes are manual-confirm-only note tags — a promise about the
     * user's collection, reviewed as copy in `:core`. This maps it to prose blocks and
     * changes not a word of it.
     */
    private fun howItWorks(): SettingsSectionContent.Prose = SettingsSectionContent.Prose(
        title = HowKaniWorksCopy.pageTitle(),
        blocks = HowKaniWorksCopy.sections().map { SettingsProseBlock(title = it.title, body = it.body) },
    )

    /**
     * The offline-data credits: dictionary, stroke, and font attributions.
     *
     * Every source gets a block whether or not its text was readable, because the
     * headings themselves record what Kani ships. A section that vanished when its file
     * failed to load would make an unmet licence obligation invisible.
     */
    private fun licenses(attribution: AttributionTexts): SettingsSectionContent.Prose =
        SettingsSectionContent.Prose(
            title = SettingsReferenceDataTextCopy.dataLicensesTitle(),
            blocks = listOf(
                SettingsProseBlock(
                    title = SettingsReferenceDataTextCopy.dictionaryDataTitle(),
                    body = attribution.dictionary,
                ),
                SettingsProseBlock(
                    title = SettingsReferenceDataTextCopy.strokeDataTitle(),
                    body = attribution.strokes,
                ),
                SettingsProseBlock(
                    title = SettingsReferenceDataTextCopy.fontsTitle(),
                    body = attribution.fonts,
                ),
            ),
        )

    /**
     * The Study keybinding editor for [bindings], as read on [platform].
     *
     * The rules — what conflicts, what the platform reserves, how a chord is written —
     * are [StudyKeybindingScreen]'s, in `:presentation-api`, so both hosts get the same
     * editor and the validation is assertable without a screen. This is only the copy:
     * command names from [SettingsKeybindingTextCopy], and each refusal turned into the
     * sentence the row shows.
     *
     * Every candidate is listed even when it cannot be chosen, with its reason. Hiding
     * an unavailable key leaves the user hunting for a row that is not there; "Already
     * Fail" answers the question instead.
     */
    private fun keybindings(
        bindings: StudyKeybindings,
        platform: KeyboardPlatform,
    ): SettingsSectionContent.Keybindings {
        val screen = StudyKeybindingScreen.of(bindings, platform)
        return SettingsSectionContent.Keybindings(
            title = SettingsKeybindingTextCopy.keybindingsTitle(),
            rows = screen.rows.map { row ->
                SettingsKeybindingRow(
                    label = SettingsKeybindingTextCopy.commandLabel(row.command.id),
                    accelerator = row.acceleratorLabel.ifBlank {
                        SettingsKeybindingTextCopy.unboundLabel()
                    },
                    unbind = row.bound.map { bound ->
                        SettingsKeybindingChoice(
                            label = SettingsKeybindingTextCopy.removeKeyLabel(bound.label),
                            action = bound.unbindAction,
                        )
                    },
                    candidates = row.candidates.map { candidate ->
                        SettingsKeybindingChoice(
                            label = candidate.label,
                            action = candidate.bindAction(row.command),
                            unavailableReason = candidate.issue?.let(::issueReason),
                        )
                    },
                )
            },
            reset = SettingsControl.ActionButton(
                label = SettingsKeybindingTextCopy.resetLabel(),
                action = KaniAction.Settings.Command(StudyKeybindingCommands.RESET),
            ),
        )
    }

    /** The sentence a refusal reads as on a row. */
    private fun issueReason(issue: StudyKeybindingIssue): String = when (issue) {
        is StudyKeybindingIssue.Conflict -> SettingsKeybindingTextCopy.conflictReason(
            SettingsKeybindingTextCopy.commandLabel(issue.command.id),
        )
        is StudyKeybindingIssue.Reserved ->
            SettingsKeybindingTextCopy.reservedReason(issue.reservedFor)
    }

    /**
     * The Import & sync section: the self-contained source toggles.
     *
     * Active, suspended, and weak-card import are plain booleans and port cleanly. The
     * tagged-cards and browser-query sources depend on their tag-list/query text (the
     * settings model zeroes tagged import when the tag list is empty), and a text-field
     * control is not in the shared vocabulary yet — so those, and the weak-card
     * thresholds, stay unported. This is the honest subset, not a claim the section is
     * complete.
     */
    private fun importSync(snapshot: SettingsSnapshot): SettingsSectionContent.Controls {
        val sync = snapshot.sync
        return SettingsSectionContent.Controls(
            title = SettingsSectionTextCopy.settingsAnkiSourceTitle(),
            controls = listOf(
                SettingsControl.Toggle(
                    label = SettingsImportTextCopy.activeCardsLabel(),
                    checked = sync.importActiveCards,
                    onChange = { KaniAction.Settings.SetToggle(IMPORT_ACTIVE_KEY, it) },
                ),
                SettingsControl.Toggle(
                    label = SettingsImportTextCopy.suspendedCardsLabel(),
                    checked = sync.importSuspendedCards,
                    onChange = { KaniAction.Settings.SetToggle(IMPORT_SUSPENDED_KEY, it) },
                ),
                SettingsControl.Toggle(
                    label = SettingsImportTextCopy.weakCardsLabel(),
                    checked = sync.importWeakCards,
                    onChange = { KaniAction.Settings.SetToggle(IMPORT_WEAK_KEY, it) },
                ),
            ),
        )
    }

    /**
     * The Study-behaviour section: the tractable subset that is pure device state.
     *
     * New-card order (a choice), promotion interval / demotion fail streak / study-ahead
     * (bounded steppers). Bounds come from the settings model itself
     * ([StudyLadderThresholdPolicy], [SettingsInputRules]) rather than being restated
     * here, so a control can never offer a value the store would clamp. The FSRS
     * personalisation and learning-step editors stay unported for now (their Android
     * panels are larger); this is the honest subset, not a claim the whole section is
     * shared.
     */
    private fun studyBehavior(snapshot: SettingsSnapshot): SettingsSectionContent.Controls {
        val sync = snapshot.sync
        return SettingsSectionContent.Controls(
            title = SettingsSectionTextCopy.settingsStudyBehaviorTitle(),
            controls = listOf(
                SettingsControl.Choice(
                    label = SettingsStudyBehaviorTextCopy.newCardSortLabel(),
                    selectedId = sync.newCardSortMode,
                    options = NEW_CARD_SORT_MODES.map { mode ->
                        SettingsChoiceOption(
                            id = mode,
                            label = SettingsStudyBehaviorTextCopy.newCardSortModeLabel(mode),
                            action = KaniAction.Settings.SetChoice(NEW_CARD_SORT_KEY, mode),
                        )
                    },
                ),
                SettingsControl.Stepper(
                    label = SettingsStudyBehaviorTextCopy.promotionIntervalLabel(),
                    value = sync.ladderPromotionIntervalDays,
                    min = 1,
                    max = StudyLadderThresholdPolicy.MAX_PROMOTION_INTERVAL_DAYS,
                    step = 7,
                    unit = SettingsStudyBehaviorTextCopy.daysUnit(),
                    onChange = { KaniAction.Settings.SetNumber(PROMOTION_INTERVAL_KEY, it) },
                ),
                SettingsControl.Stepper(
                    label = SettingsStudyBehaviorTextCopy.demotionFailStreakLabel(),
                    value = sync.ladderDemotionFailStreak,
                    min = 1,
                    max = StudyLadderThresholdPolicy.MAX_DEMOTION_FAIL_STREAK,
                    unit = SettingsStudyBehaviorTextCopy.failsUnit(),
                    onChange = { KaniAction.Settings.SetNumber(DEMOTION_FAIL_STREAK_KEY, it) },
                ),
                SettingsControl.Stepper(
                    label = SettingsStudyBehaviorTextCopy.studyAheadLabel(),
                    value = snapshot.studyAheadMinutes,
                    min = 0,
                    max = SettingsInputRules.MAX_STUDY_AHEAD_MINUTES,
                    step = 15,
                    unit = SettingsStudyBehaviorTextCopy.minutesUnit(),
                    onChange = { KaniAction.Settings.SetNumber(STUDY_AHEAD_KEY, it) },
                ),
                SettingsControl.Stepper(
                    label = SettingsStudyBehaviorTextCopy.newPerDayLabel(),
                    value = sync.newPerDay,
                    min = 0,
                    max = DeckLimitsSettingsPolicy.MAX_NEW_PER_DAY,
                    step = 5,
                    unit = SettingsStudyBehaviorTextCopy.cardsUnit(),
                    onChange = { KaniAction.Settings.SetNumber(NEW_PER_DAY_KEY, it) },
                ),
                SettingsControl.Stepper(
                    label = SettingsStudyBehaviorTextCopy.activeQueueCapLabel(),
                    value = sync.activeQueueCap,
                    min = DeckLimitsSettingsPolicy.MIN_ACTIVE_QUEUE_CAP,
                    max = DeckLimitsSettingsPolicy.MAX_ACTIVE_QUEUE_CAP,
                    step = 8,
                    unit = SettingsStudyBehaviorTextCopy.cardsUnit(),
                    onChange = { KaniAction.Settings.SetNumber(ACTIVE_QUEUE_CAP_KEY, it) },
                ),
            ),
        )
    }

    /**
     * The Appearance section: one theme choice over every [KaniThemeChoice].
     *
     * Fully shareable and unblocked — the palette resolution and the write command
     * (`SettingsSaveCommand.Theme`) are already in shared modules, so this is the first
     * real ported section rather than a placeholder. Each option dispatches a
     * [KaniAction.Settings.SetChoice] keyed by [KaniThemeChoice.SETTING_KEY]; the host
     * maps that back to the theme command and the window re-themes on the reload.
     */
    private fun appearance(theme: KaniThemeChoice): SettingsSectionContent.Controls =
        SettingsSectionContent.Controls(
            title = SettingsSectionTextCopy.settingsAppearanceTitle(),
            controls = listOf(
                SettingsControl.Choice(
                    label = SettingsSectionTextCopy.settingsAppearanceBody(),
                    selectedId = theme.storageKey,
                    options = KaniThemeChoice.entries.map { choice ->
                        SettingsChoiceOption(
                            id = choice.storageKey,
                            label = SettingsThemeTextCopy.themeTitle(choice),
                            action = KaniAction.Settings.SetChoice(
                                key = KaniThemeChoice.SETTING_KEY,
                                optionId = choice.storageKey,
                            ),
                        )
                    },
                ),
            ),
        )

    /**
     * The persistence command a settings [action] means against [current], or null if it
     * is not one the desktop app currently persists.
     *
     * The inverse of the [SettingsControl] actions [screen] builds: a control dispatches
     * a keyed [KaniAction.Settings], and this turns it back into the concrete
     * `SettingsSaveCommand`. [current] is needed because some commands are paired —
     * `LadderThresholds` carries both thresholds, so setting one reads the other from the
     * current snapshot rather than clobbering it. Kept a pure function so the round trip
     * is unit-testable without a store. Only the ported edits map; the `null` branches
     * are reached only by an edit whose control is not yet rendered.
     */
    fun settingsCommandFor(action: KaniAction.Settings, current: SettingsSnapshot): SettingsSaveCommand? =
        when (action) {
            is KaniAction.Settings.SetChoice -> when (action.key) {
                KaniThemeChoice.SETTING_KEY ->
                    SettingsSaveCommand.Theme(KaniThemeChoice.fromStorageKey(action.optionId))
                NEW_CARD_SORT_KEY -> SettingsSaveCommand.NewCardSort(action.optionId)
                else -> null
            }
            is KaniAction.Settings.SetNumber -> when (action.key) {
                PROMOTION_INTERVAL_KEY -> SettingsSaveCommand.LadderThresholds(
                    promotionIntervalDays = action.value,
                    demotionFailStreak = current.sync.ladderDemotionFailStreak,
                )
                DEMOTION_FAIL_STREAK_KEY -> SettingsSaveCommand.LadderThresholds(
                    promotionIntervalDays = current.sync.ladderPromotionIntervalDays,
                    demotionFailStreak = action.value,
                )
                STUDY_AHEAD_KEY -> SettingsSaveCommand.StudyAhead(minutes = action.value)
                NEW_PER_DAY_KEY -> SettingsSaveCommand.DeckLimits(
                    newPerDay = action.value,
                    activeQueueCap = current.sync.activeQueueCap,
                )
                ACTIVE_QUEUE_CAP_KEY -> SettingsSaveCommand.DeckLimits(
                    newPerDay = current.sync.newPerDay,
                    activeQueueCap = action.value,
                )
                else -> null
            }
            is KaniAction.Settings.SetToggle -> when (action.key) {
                IMPORT_ACTIVE_KEY -> importFilters(current) { it.copy(activeCards = action.enabled) }
                IMPORT_SUSPENDED_KEY -> importFilters(current) { it.copy(suspendedCards = action.enabled) }
                IMPORT_WEAK_KEY -> importFilters(current) { it.copy(weakCards = action.enabled) }
                else -> null
            }
            is KaniAction.Settings.Command -> null
        }

    /**
     * The encoded keybinding string a settings [action] should store, or null to store
     * nothing.
     *
     * The keybinding editor's own persistence path, separate from [settingsCommandFor]
     * because bindings are device-local (`DeviceSettingKeys.studyKeybindings`) rather
     * than portable collection settings — a Mac user's `⌘Z` must not restore onto their
     * Windows install. Kept a pure String-in/String-out function so the host's only job
     * is one `edit { put(…) }`, and every rule is testable without a store.
     *
     * Null means "do not write": the action was not a keybinding edit, this build cannot
     * read the id, the platform or another command holds the key, or it was already the
     * state. Writing on a no-op would rewrite the settings file every time the editor
     * re-renders a row the user did not change.
     */
    fun keybindingEditFor(
        action: KaniAction.Settings,
        stored: String?,
        platform: KeyboardPlatform,
    ): String? {
        val command = action as? KaniAction.Settings.Command ?: return null
        val edit = StudyKeybindingCommands.parse(command.id) ?: return null
        val current = StudyKeybindingsCodec.decode(stored)
        val next = StudyKeybindingCommands.apply(edit, current, platform) ?: return null
        return StudyKeybindingsCodec.encode(next)
    }

    /**
     * The current import filters as a full [SettingsSaveCommand.ImportFilters], with one
     * field changed by [mutate].
     *
     * `ImportFilters` carries every filter at once, so flipping one source toggle has to
     * resend the rest from the current snapshot rather than defaulting them — otherwise
     * ticking "import suspended" would silently reset the weak-card thresholds and the
     * tag list. This reads the untouched fields from [current] and lets [mutate] change
     * only the one the toggle owns.
     */
    private fun importFilters(
        current: SettingsSnapshot,
        mutate: (SettingsSaveCommand.ImportFilters) -> SettingsSaveCommand.ImportFilters,
    ): SettingsSaveCommand.ImportFilters {
        val sync = current.sync
        return mutate(
            SettingsSaveCommand.ImportFilters(
                activeCards = sync.importActiveCards,
                suspendedCards = sync.importSuspendedCards,
                taggedCards = sync.importTaggedCards,
                tags = sync.importTagsText(),
                weakCards = sync.importWeakCards,
                weakDifficulty = sync.importWeakFsrsDifficultyThreshold,
                weakLapses = sync.importWeakLapsesThreshold,
                minMatchingCards = sync.importMinMatchingCardsPerKanji,
                browserQueryCards = sync.importBrowserQueryCards,
                browserQuery = sync.importBrowserQuery,
                tagRepairedCards = current.tagRepairedCards,
            ),
        )
    }

    private fun root(): SettingsRoot = SettingsRoot(
        title = SettingsSectionTextCopy.settingsTitle(),
        categories = listOf(
            SettingsCategory(
                section = SettingsSection.IMPORT_SYNC,
                title = SettingsSectionTextCopy.settingsAnkiSourceTitle(),
                summary = SettingsSectionTextCopy.settingsAnkiSourceBody(),
            ),
            SettingsCategory(
                section = SettingsSection.STUDY_BEHAVIOR,
                title = SettingsSectionTextCopy.settingsStudyBehaviorTitle(),
                summary = SettingsSectionTextCopy.settingsStudyBehaviorBody(),
            ),
            SettingsCategory(
                section = SettingsSection.KEYBINDINGS,
                title = SettingsKeybindingTextCopy.keybindingsTitle(),
                summary = SettingsKeybindingTextCopy.keybindingsSummary(),
            ),
            SettingsCategory(
                section = SettingsSection.AUTOMATION,
                title = SettingsSectionTextCopy.settingsAutomationTitle(),
                summary = SettingsSectionTextCopy.settingsAutomationBody(),
                notices = listOf(REMINDER_NOTICE),
            ),
            SettingsCategory(
                section = SettingsSection.APPEARANCE,
                title = SettingsSectionTextCopy.settingsAppearanceTitle(),
                summary = SettingsSectionTextCopy.settingsAppearanceBody(),
            ),
            SettingsCategory(
                section = SettingsSection.DISPLAY_DATA,
                title = SettingsSectionTextCopy.settingsReferenceDataTitle(),
                summary = SettingsSectionTextCopy.settingsReferenceDataBody(),
            ),
        ),
    )

    // Threaded as a truthful capability line rather than hidden: the desktop
    // reminder/notification surface (Goal 203) has no OS-scheduled worker, so a
    // reminder only evaluates while the window is open.
    private const val REMINDER_NOTICE =
        "On desktop, reminders are evaluated while Kani is open."
}
