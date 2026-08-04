package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.DeckLimitsSettingsPolicy
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsImportTextCopy
import dev.bee.kanjianki.core.SettingsKeybindingTextCopy
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsSectionTextCopy
import dev.bee.kanjianki.core.SettingsStudyBehaviorTextCopy
import dev.bee.kanjianki.core.SettingsThemeTextCopy
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.SettingsCategory
import dev.bee.kanjianki.presentation.SettingsChoiceOption
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsKeybindingChoice
import dev.bee.kanjianki.presentation.SettingsKeybindingRow
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
        else -> SettingsScreen(section = section)
    }

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
