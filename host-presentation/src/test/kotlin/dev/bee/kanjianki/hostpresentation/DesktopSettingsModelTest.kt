package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.StudyCommand
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeybindingsCodec
import dev.bee.kanjianki.presentation.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of the Settings parity: the mapping turns a [SettingsSection] into
 * the portable screen the shared surface renders. The surface's layout is proven by
 * `:feature-settings`'s render tests on both hosts; this checks the root menu, the
 * ported sections' controls, the control-to-command round trip, and that an un-ported
 * section is the honest placeholder.
 */
class DesktopSettingsModelTest {
    @Test
    fun theRootBuildsTheSixCategoryMenu() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT, snapshot())

        assertEquals(SettingsSection.ROOT, screen.section)
        val root = screen.root
        assertNotNull("root screen carries a menu", root)
        assertEquals(
            listOf(
                SettingsSection.IMPORT_SYNC,
                SettingsSection.STUDY_BEHAVIOR,
                // The keybinding editor is a root category, not a child of study
                // behaviour: the shared Settings surface has no sub-category control
                // inside a section's controls panel, so a card on the root menu is the
                // only way a user reaches it.
                SettingsSection.KEYBINDINGS,
                SettingsSection.AUTOMATION,
                SettingsSection.APPEARANCE,
                SettingsSection.DISPLAY_DATA,
            ),
            root!!.categories.map { it.section },
        )
        // Every category names itself; none is blank.
        assertTrue(root.categories.all { it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test
    fun automationCarriesTheReminderCapabilityNotice() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT, snapshot())
        val automation = screen.root!!.categories.first { it.section == SettingsSection.AUTOMATION }

        assertEquals(1, automation.notices.size)
        assertTrue(automation.notices.single().contains("while Kani is open"))
    }

    @Test
    fun otherCategoriesCarryNoNotice() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT, snapshot())
        val nonAutomation = screen.root!!.categories.filter { it.section != SettingsSection.AUTOMATION }

        assertTrue(nonAutomation.all { it.notices.isEmpty() })
    }

    @Test
    fun anUnportedSectionIsTheHonestPlaceholder() {
        val ported = setOf(
            SettingsSection.ROOT,
            SettingsSection.APPEARANCE,
            SettingsSection.STUDY_BEHAVIOR,
            SettingsSection.IMPORT_SYNC,
            SettingsSection.KEYBINDINGS,
        )
        for (section in SettingsSection.entries.filter { it !in ported }) {
            val screen = DesktopSettingsModel.screen(section, snapshot())
            assertEquals(section, screen.section)
            assertNull("a leaf section has no root menu", screen.root)
            assertEquals(SettingsSectionContent.Placeholder, screen.content)
        }
    }

    @Test
    fun appearanceOffersEveryThemeAndMarksTheCurrentOne() {
        val screen = DesktopSettingsModel.screen(
            SettingsSection.APPEARANCE,
            snapshot(theme = KaniThemeChoice.OCEAN_STUDY),
        )

        val content = screen.content as SettingsSectionContent.Controls
        val choice = content.controls.single() as SettingsControl.Choice
        assertEquals(
            KaniThemeChoice.entries.map { it.storageKey },
            choice.options.map { it.id },
        )
        assertEquals(KaniThemeChoice.OCEAN_STUDY.storageKey, choice.selectedId)
        // Every option dispatches a theme choice keyed by the shared setting key.
        val dark = choice.options.first { it.id == KaniThemeChoice.DARK.storageKey }
        assertEquals(
            KaniAction.Settings.SetChoice(KaniThemeChoice.SETTING_KEY, KaniThemeChoice.DARK.storageKey),
            dark.action,
        )
    }

    @Test
    fun studyBehaviorReflectsTheCurrentValuesAndBounds() {
        val screen = DesktopSettingsModel.screen(SettingsSection.STUDY_BEHAVIOR, snapshot())
        val controls = (screen.content as SettingsSectionContent.Controls).controls

        val sort = controls.filterIsInstance<SettingsControl.Choice>().single()
        assertEquals(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY, sort.selectedId)
        assertTrue(sort.options.any { it.id == RecordsBase.NEW_CARD_SORT_FREQUENCY })

        val steppers = controls.filterIsInstance<SettingsControl.Stepper>()
        val promotion = steppers.first { it.value == RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS }
        assertEquals(1, promotion.min)
        assertEquals(365, promotion.max)
        val demotion = steppers.first { it.max == 30 }
        assertEquals(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK, demotion.value)
    }

    @Test
    fun importSyncReflectsTheCurrentSourceToggles() {
        val screen = DesktopSettingsModel.screen(SettingsSection.IMPORT_SYNC, snapshot())
        val toggles = (screen.content as SettingsSectionContent.Controls).controls
            .filterIsInstance<SettingsControl.Toggle>()
        assertEquals(3, toggles.size)
        // Each toggle carries the current value and dispatches its own keyed action.
        val weak = toggles.first { it.label.contains("weak") }
        assertEquals(
            KaniAction.Settings.SetToggle("import_weak_cards", enabled = false),
            weak.onChange(false),
        )
    }

    @Test
    fun anImportToggleResendsEveryOtherFilterUnchanged() {
        // Flipping suspended-card import keeps active, weak, and the thresholds as they
        // were — ImportFilters carries them all, so a partial command would reset them.
        val current = snapshot()
        val command = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetToggle("import_suspended_cards", enabled = true),
            current,
        ) as SettingsSaveCommand.ImportFilters

        assertEquals(true, command.suspendedCards)
        assertEquals(current.sync.importActiveCards, command.activeCards)
        assertEquals(current.sync.importWeakCards, command.weakCards)
        assertEquals(current.sync.importWeakLapsesThreshold, command.weakLapses)
        assertEquals(current.sync.importMinMatchingCardsPerKanji, command.minMatchingCards)
        assertEquals(current.tagRepairedCards, command.tagRepairedCards)
    }

    @Test
    fun aThemeChoiceMapsBackToTheThemeSaveCommand() {
        val command = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetChoice(KaniThemeChoice.SETTING_KEY, KaniThemeChoice.MATCHA_MILK.storageKey),
            snapshot(),
        )
        assertEquals(SettingsSaveCommand.Theme(KaniThemeChoice.MATCHA_MILK), command)
    }

    @Test
    fun aLadderStepperMapsToTheThresholdCommandKeepingTheOtherValue() {
        // Promotion set to 28 keeps the current fail streak; only the touched value moves.
        val current = snapshot()
        val command = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetNumber("ladder_promotion_interval_days", 28),
            current,
        )
        assertEquals(
            SettingsSaveCommand.LadderThresholds(
                promotionIntervalDays = 28,
                demotionFailStreak = current.sync.ladderDemotionFailStreak,
            ),
            command,
        )

        val demotion = DesktopSettingsModel.settingsCommandFor(
            KaniAction.Settings.SetNumber("ladder_demotion_fail_streak", 5),
            current,
        )
        assertEquals(
            SettingsSaveCommand.LadderThresholds(
                promotionIntervalDays = current.sync.ladderPromotionIntervalDays,
                demotionFailStreak = 5,
            ),
            demotion,
        )
    }

    @Test
    fun aDeckLimitStepperMapsToTheDeckLimitsCommandKeepingTheOtherValue() {
        val current = snapshot()
        assertEquals(
            SettingsSaveCommand.DeckLimits(newPerDay = 25, activeQueueCap = current.sync.activeQueueCap),
            DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetNumber("deck_new_per_day", 25), current),
        )
        assertEquals(
            SettingsSaveCommand.DeckLimits(newPerDay = current.sync.newPerDay, activeQueueCap = 64),
            DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetNumber("deck_active_queue_cap", 64), current),
        )
    }

    @Test
    fun theActiveQueueCapStepperStaysWithinItsBounds() {
        val screen = DesktopSettingsModel.screen(SettingsSection.STUDY_BEHAVIOR, snapshot())
        val steppers = (screen.content as SettingsSectionContent.Controls).controls
            .filterIsInstance<SettingsControl.Stepper>()
        val cap = steppers.first { it.min == 8 && it.max == 200 }
        assertTrue(cap.value in 8..200)
    }

    @Test
    fun sortAndStudyAheadMapToTheirCommands() {
        assertEquals(
            SettingsSaveCommand.NewCardSort(RecordsBase.NEW_CARD_SORT_FREQUENCY),
            DesktopSettingsModel.settingsCommandFor(
                KaniAction.Settings.SetChoice("new_card_sort_mode", RecordsBase.NEW_CARD_SORT_FREQUENCY),
                snapshot(),
            ),
        )
        assertEquals(
            SettingsSaveCommand.StudyAhead(minutes = 30),
            DesktopSettingsModel.settingsCommandFor(
                KaniAction.Settings.SetNumber("study_ahead_minutes", 30),
                snapshot(),
            ),
        )
    }

    @Test
    fun anUnmappedSettingsEditProducesNoCommand() {
        val current = snapshot()
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetChoice("unknown_key", "x"), current))
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetNumber("unknown_key", 1), current))
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.SetToggle("unknown_key", enabled = true), current))
        assertNull(DesktopSettingsModel.settingsCommandFor(KaniAction.Settings.Command("reset_ladder"), current))
    }

    @Test
    fun theKeybindingEditorNamesEveryCommandAndTheKeysItHolds() {
        val screen = DesktopSettingsModel.screen(
            section = SettingsSection.KEYBINDINGS,
            snapshot = snapshot(),
            bindings = StudyKeybindings.DEFAULT,
            platform = KeyboardPlatform.LINUX,
        )

        val content = screen.content as SettingsSectionContent.Keybindings
        assertEquals(StudyCommand.entries.size, content.rows.size)
        // Named the way the Study buttons name them, not by wire id.
        assertEquals(
            listOf("Show answer / continue", "Pass", "Fail", "Undo"),
            content.rows.map { it.label },
        )
        assertEquals("3, Numpad 3, P", content.rows.first { it.label == "Pass" }.accelerator)
        assertEquals(
            KaniAction.Settings.Command("study_keybindings.reset"),
            content.reset.action,
        )
    }

    @Test
    fun anUnavailableKeyStaysListedWithTheReasonItCannotBeChosen() {
        val screen = DesktopSettingsModel.screen(
            section = SettingsSection.KEYBINDINGS,
            snapshot = snapshot(),
            bindings = StudyKeybindings.DEFAULT,
            platform = KeyboardPlatform.WINDOWS,
        )

        val content = screen.content as SettingsSectionContent.Keybindings
        val pass = content.rows.first { it.label == "Pass" }
        // `1` belongs to Fail, and the reason names the command that holds it, so the
        // user is told where the key went rather than finding the row inert.
        assertEquals("Already Fail", pass.candidates.first { it.label == "1" }.unavailableReason)
        // An OS chord is refused with the OS action, and both refusals disable the chip.
        val ctrlC = pass.candidates.first { it.label == "Ctrl+C" }
        assertEquals("Used by the system: Copy", ctrlC.unavailableReason)
        assertFalse(ctrlC.enabled)
        // Kani's own undo chord reports the command that holds it, not an OS reservation.
        assertEquals("Already Undo", pass.candidates.first { it.label == "Ctrl+Z" }.unavailableReason)
        // A free key and a free chord are both offered.
        assertTrue(pass.candidates.first { it.label == "G" }.enabled)
        assertTrue(pass.candidates.first { it.label == "Ctrl+G" }.enabled)
    }

    @Test
    fun aKeybindingEditRoundTripsThroughStorageAndRefusesWhatWouldNotStick() {
        val stored = StudyKeybindingsCodec.encode(StudyKeybindings.DEFAULT)
        val bindPassG = (
            DesktopSettingsModel.screen(
                section = SettingsSection.KEYBINDINGS,
                snapshot = snapshot(),
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.LINUX,
            ).content as SettingsSectionContent.Keybindings
            )
            .rows.first { it.label == "Pass" }
            .candidates.first { it.label == "G" }
            .action as KaniAction.Settings

        val next = DesktopSettingsModel.keybindingEditFor(bindPassG, stored, KeyboardPlatform.LINUX)
        assertNotNull("binding a free key must produce storable state", next)
        val decoded = StudyKeybindingsCodec.decode(next)
        assertEquals("3, Numpad 3, P, G", decoded.strokesFor(StudyCommand.GRADE_PASS).joinToString(", ") { it.label(KeyboardPlatform.LINUX) })

        // Re-applying the same bind changes nothing, and null is how "do not write" is
        // said — otherwise a repeated click would churn the settings file.
        assertNull(DesktopSettingsModel.keybindingEditFor(bindPassG, next, KeyboardPlatform.LINUX))
        // Not a keybinding edit at all, and an id this build cannot read: both ignored
        // rather than guessed at.
        assertNull(DesktopSettingsModel.keybindingEditFor(KaniAction.Settings.SetNumber("k", 1), stored, KeyboardPlatform.LINUX))
        assertNull(DesktopSettingsModel.keybindingEditFor(KaniAction.Settings.Command("reset_ladder"), stored, KeyboardPlatform.LINUX))
        assertNull(
            DesktopSettingsModel.keybindingEditFor(
                KaniAction.Settings.Command("study_keybindings.bind:grade_pass:Nonsense"),
                stored,
                KeyboardPlatform.LINUX,
            ),
        )
        // A host that dispatched a reserved chord anyway still cannot store a dead
        // binding: apply re-gates it rather than trusting the greyed-out chip.
        assertNull(
            DesktopSettingsModel.keybindingEditFor(
                KaniAction.Settings.Command("study_keybindings.bind:grade_pass:ctrl+c"),
                stored,
                KeyboardPlatform.WINDOWS,
            ),
        )
    }

    @Test
    fun unbindingAndResettingAreEditsLikeAnyOther() {
        val stored = StudyKeybindingsCodec.encode(StudyKeybindings.DEFAULT)
        val pass = (
            DesktopSettingsModel.screen(
                section = SettingsSection.KEYBINDINGS,
                snapshot = snapshot(),
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.LINUX,
            ).content as SettingsSectionContent.Keybindings
            ).rows.first { it.label == "Pass" }

        val removeP = pass.unbind.first { it.label == "Remove P" }.action as KaniAction.Settings
        val withoutP = DesktopSettingsModel.keybindingEditFor(removeP, stored, KeyboardPlatform.LINUX)
        assertEquals(
            "3, Numpad 3",
            StudyKeybindingsCodec.decode(withoutP).strokesFor(StudyCommand.GRADE_PASS)
                .joinToString(", ") { it.label(KeyboardPlatform.LINUX) },
        )

        // Reset from an edited set restores the reviewed defaults; reset from the
        // defaults is a no-op and writes nothing.
        val reset = KaniAction.Settings.Command("study_keybindings.reset")
        assertEquals(stored, DesktopSettingsModel.keybindingEditFor(reset, withoutP, KeyboardPlatform.LINUX))
        assertNull(DesktopSettingsModel.keybindingEditFor(reset, stored, KeyboardPlatform.LINUX))
        // Unreadable stored state is read as the defaults, so reset from garbage is also
        // a no-op rather than a write of the same value.
        assertNull(DesktopSettingsModel.keybindingEditFor(reset, "not a binding set", KeyboardPlatform.LINUX))
    }

    private fun snapshot(theme: KaniThemeChoice = KaniThemeChoice.GIRLYPOP): SettingsSnapshot = SettingsSnapshot(
        sync = RecordsSyncModels.Settings.kikuDefaults(),
        tagRepairedCards = false,
        adaptiveWorkload = AdaptiveWorkloadSnapshot(workPercent = 100, maxItems = 40, mode = "balanced"),
        studyAheadMinutes = 0,
        studyLadder = RecordsBase.StudyLadderSettings.defaults(),
        schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
        schedulerFsrsWeights = null,
        learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
        themeChoice = theme,
        fsrsPersonalizationEnabled = false,
        fsrsFitSummaryJson = "",
    )
}
