package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.HowKaniWorksCopy
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.ReminderAntiSpamPolicy
import dev.bee.kanjianki.core.SettingsReferenceDataTextCopy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.StudyCommand
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeybindingsCodec
import dev.bee.kanjianki.presentation.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun everySectionRendersRealContentAndNoneIsStillAPlaceholder() {
        // This test used to assert the opposite — that an unported section says so rather
        // than rendering an empty panel — and inverted when the last one landed. Kept as
        // the standing invariant instead of deleted: `SettingsScreen.Placeholder` still
        // exists for a host on an older build to render, and a section added later without
        // a mapper case would reach a user as a blank screen.
        for (section in SettingsSection.entries) {
            val screen = DesktopSettingsModel.screen(section, snapshot())
            assertEquals(section, screen.section)
            if (section == SettingsSection.ROOT) {
                // The root is the category menu and carries no controls of its own, so its
                // `content` stays at the default. The menu itself is what must be there.
                assertNotNull("the root section is the category menu", screen.root)
            } else {
                assertNull("a leaf section has no root menu", screen.root)
                assertNotEquals(
                    "$section still renders the placeholder",
                    SettingsSectionContent.Placeholder,
                    screen.content,
                )
            }
        }
    }

    @Test
    fun displayDataOpensItsTwoChildPagesRatherThanDeadEnding() {
        val content = DesktopSettingsModel.screen(SettingsSection.DISPLAY_DATA, snapshot()).content
        val opened = (content as SettingsSectionContent.Controls).controls
            .filterIsInstance<SettingsControl.ActionButton>()
            .map { it.action }

        // Both prose pages hang off this section, so if it offered no way in they would be
        // reachable only by deep link -- which is how they would silently stop existing.
        assertTrue(
            opened.contains(
                KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.HOW_IT_WORKS)),
            ),
        )
        assertTrue(
            opened.contains(
                KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.LICENSES)),
            ),
        )
    }

    @Test
    fun howItWorksCarriesEveryExplainerSectionVerbatim() {
        val content = DesktopSettingsModel.screen(SettingsSection.HOW_IT_WORKS, snapshot()).content

        val prose = content as SettingsSectionContent.Prose
        assertEquals(HowKaniWorksCopy.pageTitle(), prose.title)
        // Verbatim, not paraphrased: the first section is Kani's stated promise about what
        // it reads from and writes to the user's collection. Reviewed as copy in `:core`,
        // so a mapping that trimmed or reworded it would be changing a promise.
        assertEquals(
            HowKaniWorksCopy.sections().map { it.title to it.body },
            prose.blocks.map { it.title to it.body },
        )
    }

    @Test
    fun licensesCreditEverySourceEvenWhenAHostCannotReadOne() {
        val content = DesktopSettingsModel.screen(
            SettingsSection.LICENSES,
            snapshot(),
            attribution = DesktopSettingsModel.AttributionTexts.UNAVAILABLE,
        ).content

        val prose = content as SettingsSectionContent.Prose
        // Three blocks whatever happened: attribution is a licence obligation, so a
        // section that disappeared when its file failed to load would hide an unmet one.
        assertEquals(
            listOf(
                SettingsReferenceDataTextCopy.dictionaryDataTitle(),
                SettingsReferenceDataTextCopy.strokeDataTitle(),
                SettingsReferenceDataTextCopy.fontsTitle(),
            ),
            prose.blocks.map { it.title },
        )
        assertTrue("an unreadable source still states something", prose.blocks.all { it.body.isNotBlank() })
    }

    @Test
    fun licensesShowTheTextsTheHostActuallyRead() {
        val content = DesktopSettingsModel.screen(
            SettingsSection.LICENSES,
            snapshot(),
            attribution = DesktopSettingsModel.AttributionTexts(
                dictionary = "JMdict, CC BY-SA 4.0",
                strokes = "KanjiVG, CC BY-SA 3.0",
                fonts = "Noto Sans JP, SIL OFL 1.1",
            ),
        ).content

        assertEquals(
            listOf("JMdict, CC BY-SA 4.0", "KanjiVG, CC BY-SA 3.0", "Noto Sans JP, SIL OFL 1.1"),
            (content as SettingsSectionContent.Prose).blocks.map { it.body },
        )
    }

    @Test
    fun aProsePageDispatchesNothing() {
        for (section in listOf(SettingsSection.HOW_IT_WORKS, SettingsSection.LICENSES)) {
            val screen = DesktopSettingsModel.screen(section, snapshot())
            assertNull("a prose page is a leaf, not a menu", screen.root)
            assertTrue(screen.content is SettingsSectionContent.Prose)
        }
    }

    @Test
    fun automationRendersEveryDeviceLocalValueItWasGiven() {
        val content = DesktopSettingsModel.screen(
            SettingsSection.AUTOMATION,
            snapshot(),
            automation = automation(),
            capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE),
        ).content as SettingsSectionContent.Controls

        val steppers = content.controls.filterIsInstance<SettingsControl.Stepper>()
        // A stored time reads as a clock time, not as the minute count it is: 07:30 rather
        // than "450", which is the whole reason `valueLabel` exists.
        assertTrue("the reminder time reads as a clock", steppers.any { it.displayValue == "07:30" })
        assertTrue("the quiet start reads as a clock", steppers.any { it.displayValue == "23:00" })
        assertTrue("the quiet end reads as a clock", steppers.any { it.displayValue == "06:00" })
        // Both status lines are present and truthful about the values given.
        val infoText = content.controls.filterIsInstance<SettingsControl.Info>()
            .joinToString(" ") { "${it.label} ${it.value}" }
        assertTrue("the reminder status names its time: $infoText", infoText.contains("07:30"))
        assertTrue("the sync status names its time: $infoText", infoText.contains("09:15"))
    }

    @Test
    fun anUnconfiguredDailySyncCannotBeTurnedOnFromTheControlOrTheMapper() {
        val unconfigured = automation(autoSyncConfigured = false)
        val content = DesktopSettingsModel.screen(
            SettingsSection.AUTOMATION,
            snapshot(),
            automation = unconfigured,
        ).content as SettingsSectionContent.Controls

        // The toggle is inoperable, and the status says why rather than just looking broken.
        val syncToggle = content.controls.filterIsInstance<SettingsControl.Toggle>()
            .first { it.label.contains("sync", ignoreCase = true) }
        assertFalse("an unconfigured daily sync is not operable", syncToggle.enabled)
        val statuses = content.controls.filterIsInstance<SettingsControl.Info>().map { it.label }
        assertTrue(
            "the status explains the disabled toggle: $statuses",
            statuses.any { it.contains("Sync once", ignoreCase = true) },
        )
        // And the mapper refuses it too: the disabled control is a courtesy, not the gate.
        // Otherwise a deep-linked or replayed action would write an enabled flag the
        // policy zeroes on read, so the setting would show off after saving on.
        assertNull(
            DesktopSettingsModel.automationEditFor(
                KaniAction.Settings.SetToggle("automation.auto_sync_enabled", enabled = true),
                unconfigured,
            ),
        )
    }

    @Test
    fun backupActionsAreOfferedOnlyWhereTheHostCanHonourThem() {
        val withBackup = DesktopSettingsModel.screen(
            SettingsSection.AUTOMATION,
            snapshot(),
            capabilities = PlatformCapabilities.of(PlatformCapability.BACKUP_RESTORE),
        ).content as SettingsSectionContent.Controls
        val withoutBackup = DesktopSettingsModel.screen(
            SettingsSection.AUTOMATION,
            snapshot(),
            capabilities = PlatformCapabilities.NONE,
        ).content as SettingsSectionContent.Controls

        fun backupButtons(content: SettingsSectionContent.Controls) =
            content.controls.filterIsInstance<SettingsControl.ActionButton>()
                .filter { it.action is KaniAction.Settings.Command }

        // Both listed either way — a host that cannot snapshot should say so, not hide the
        // feature — but only operable where the atomic-publication contract holds.
        assertEquals(2, backupButtons(withBackup).size)
        assertEquals(2, backupButtons(withoutBackup).size)
        assertTrue(backupButtons(withBackup).all { it.enabled })
        assertTrue(backupButtons(withoutBackup).none { it.enabled })
        // Restore replaces the whole database, so it is marked destructive and export is not.
        val restore = backupButtons(withBackup)
            .first { it.action == KaniAction.Settings.Command("automation.backup_restore") }
        val export = backupButtons(withBackup)
            .first { it.action == KaniAction.Settings.Command("automation.backup_export") }
        assertTrue("a whole-database restore is destructive", restore.destructive)
        assertFalse("an export changes nothing", export.destructive)
    }

    @Test
    fun aBlockedReminderSaysBlockedRatherThanOffOrOn() {
        fun status(state: DesktopSettingsModel.AutomationState): String =
            (
                DesktopSettingsModel.screen(SettingsSection.AUTOMATION, snapshot(), automation = state)
                    .content as SettingsSectionContent.Controls
                )
                .controls.filterIsInstance<SettingsControl.Info>().first().value

        // Blocked outranks both on and off: a reminder the OS is refusing is not "Daily
        // around 07:30", and telling the user it is armed is the one wrong answer here.
        assertTrue(status(automation(notificationsBlocked = true)).contains("Blocked"))
        assertTrue(status(automation(reminderEnabled = true)).contains("07:30"))
        assertFalse(status(automation(reminderEnabled = false)).contains("07:30"))
    }

    @Test
    fun aReminderPresetSetsTheTimeItsLabelAdvertises() {
        val content = DesktopSettingsModel.screen(
            SettingsSection.AUTOMATION,
            snapshot(),
            automation = automation(),
        ).content as SettingsSectionContent.Controls

        // The label and the action have to agree: a button reading "Evening 19:00" that
        // set some other minute would be the kind of bug nobody thinks to check for.
        val evening = content.controls.filterIsInstance<SettingsControl.ActionButton>()
            .first { it.label.contains("19:00") }
        assertEquals(
            KaniAction.Settings.SetNumber("automation.reminder_time", 19 * 60),
            evening.action,
        )
    }

    @Test
    fun anAutomationEditNormalizesThroughTheCorePolicyThatOwnsIt() {
        val current = automation()

        // A minute of day splits back into the hour/minute the schedulers read.
        val timed = DesktopSettingsModel.automationEditFor(
            KaniAction.Settings.SetNumber("automation.reminder_time", 21 * 60 + 45),
            current,
        )
        assertEquals(21, timed!!.reminderHour)
        assertEquals(45, timed.reminderMinute)

        // Out-of-range values are clamped by the policy, not stored raw: a max-per-day of 9
        // would otherwise be written and then read back as 3, so Settings would show a
        // number the user never chose.
        val clamped = DesktopSettingsModel.automationEditFor(
            KaniAction.Settings.SetNumber("automation.reminder_max_per_day", 9),
            current,
        )
        assertEquals(ReminderAntiSpamPolicy.MAX_MAX_PER_DAY, clamped!!.reminderMaxPerDay)
    }

    @Test
    fun anAutomationEditThatChangesNothingWritesNothing() {
        val current = automation()

        // Same value in, null out: the section re-renders on every reload, and a mapper
        // that returned an edit for an unchanged control would rewrite the device store
        // each time — churn on a file that also holds the restore-safety state.
        assertNull(
            DesktopSettingsModel.automationEditFor(
                KaniAction.Settings.SetNumber("automation.reminder_max_per_day", current.reminderMaxPerDay),
                current,
            ),
        )
        assertNull(
            DesktopSettingsModel.automationEditFor(
                KaniAction.Settings.SetToggle("automation.debug_log_enabled", current.debugLogEnabled),
                current,
            ),
        )
    }

    @Test
    fun theBackupCommandsPersistNothingBecauseTheyRaiseAPicker() {
        val current = automation()

        // Null from both mappers, which is what tells the host to raise
        // `KaniEffect.PickFile` instead. A command that produced an edit here would write a
        // device setting *and* open a dialog.
        for (id in listOf("automation.backup_export", "automation.backup_restore")) {
            val command = KaniAction.Settings.Command(id)
            assertNull(DesktopSettingsModel.automationEditFor(command, current))
            assertNull(DesktopSettingsModel.settingsCommandFor(command, snapshot()))
        }
    }

    @Test
    fun theTwoPersistencePathsShareNoKey() {
        // The device-local keys are namespaced apart from the portable collection keys, so
        // one edit can never take both paths and be written twice. Proven by asking the
        // portable mapper about every automation key the section dispatches.
        val content = DesktopSettingsModel.screen(
            SettingsSection.AUTOMATION,
            snapshot(),
            automation = automation(),
        ).content as SettingsSectionContent.Controls
        val dispatched = content.controls.flatMap { control ->
            when (control) {
                is SettingsControl.Toggle -> listOf(control.onChange(true))
                is SettingsControl.Stepper -> listOf(control.onChange(control.value))
                is SettingsControl.ActionButton -> listOf(control.action)
                else -> emptyList()
            }
        }.filterIsInstance<KaniAction.Settings>()
        assertTrue("the section dispatches something", dispatched.isNotEmpty())
        assertTrue(
            "no automation edit is also a portable settings command",
            dispatched.all { DesktopSettingsModel.settingsCommandFor(it, snapshot()) == null },
        )
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

    @Test
    fun theUpdateSectionReportsWhatWasCheckedBeforeOfferingToCheckAgain() {
        val content = updateContent(update())

        val infos = content.controls.filterIsInstance<SettingsControl.Info>()
        val text = infos.joinToString(" ") { "${it.label} ${it.value}" }
        // Both versions, so "did the update work" is answerable without leaving the screen.
        assertTrue("the installed version is reported: $text", text.contains("0.3.6"))
        assertTrue("the found version is reported: $text", text.contains("0.4.0"))
        assertTrue("the last result is reported: $text", text.contains("Downloaded"))
        // Reported before offered: the notification deep-links here, and the question it
        // raises is what happened, not what to do next.
        val firstAction = content.controls.indexOfFirst { it is SettingsControl.ActionButton }
        val lastInfo = content.controls.indexOfLast { it is SettingsControl.Info }
        assertTrue("the status lines come before the actions", firstAction < lastInfo)
    }

    @Test
    fun aStagedUpdateIsOfferedForInstallOnlyWhenTheHostMayInstallIt() {
        val allowed = updateContent(update(canInstall = true))
        val refused = updateContent(update(canInstall = false))

        // Offered and operable when the OS will honour it.
        val install = allowed.controls.filterIsInstance<SettingsControl.ActionButton>()
            .first { it.action == KaniAction.Settings.Command("update.install_pending") }
        assertTrue("a staged update the host can install is operable", install.enabled)
        // Replaces the running application, so it reads as destructive.
        assertTrue("replacing the running app is destructive", install.destructive)

        // Without the permission the panel offers the permission page instead of an
        // install that would bounce straight off it.
        val refusedCommands = refused.controls.filterIsInstance<SettingsControl.ActionButton>()
            .map { (it.action as KaniAction.Settings.Command).id }
        assertTrue(
            "the permission page is offered: $refusedCommands",
            "update.open_install_permission" in refusedCommands,
        )
        assertFalse(
            "the permission page is not offered once granted",
            allowed.controls.filterIsInstance<SettingsControl.ActionButton>()
                .any { (it.action as KaniAction.Settings.Command).id == "update.open_install_permission" },
        )
    }

    @Test
    fun nothingStagedMeansNoInstallButtonAtAll() {
        val content = updateContent(update(canInstall = true, pendingPackage = ""))

        // An install button with nothing staged does nothing when pressed, which is worse
        // than not offering it: the user reads it as "an update is ready".
        assertFalse(
            "no install button without a staged artifact",
            content.controls.filterIsInstance<SettingsControl.ActionButton>()
                .any { (it.action as KaniAction.Settings.Command).id == "update.install_pending" },
        )
    }

    @Test
    fun anInstallKaniDidNotPlaceIsReportedAndOfferedNothing() {
        val unmanaged = DesktopSettingsModel.screen(
            SettingsSection.UPDATE,
            snapshot(),
            update = update(canInstall = true),
            capabilities = PlatformCapabilities.NONE,
        ).content as SettingsSectionContent.Controls

        // A source build, a Flatpak, a distro package: Kani did not place it and must not
        // replace it. Every action is listed but inoperable, so the screen explains itself
        // rather than looking broken or empty.
        val actions = unmanaged.controls.filterIsInstance<SettingsControl.ActionButton>()
        val toggles = unmanaged.controls.filterIsInstance<SettingsControl.Toggle>()
        assertTrue("the actions are still listed", actions.isNotEmpty())
        assertTrue("no action is operable", actions.none { it.enabled })
        assertTrue("no toggle is operable", toggles.none { it.enabled })
        // And the versions are still reported: reading them never needed the capability.
        val text = unmanaged.controls.filterIsInstance<SettingsControl.Info>()
            .joinToString(" ") { "${it.label} ${it.value}" }
        assertTrue("the installed version is still reported: $text", text.contains("0.3.6"))
    }

    @Test
    fun onlyTheTwoUpdateTogglesArePersistedHere() {
        val current = update()

        // The two user choices map to writes.
        assertEquals(
            current.copy(autoUpdateEnabled = true),
            DesktopSettingsModel.updateEditFor(
                KaniAction.Settings.SetToggle("update.auto_update_enabled", enabled = true),
                current,
            ),
        )
        assertEquals(
            current.copy(betaUpdatesEnabled = true),
            DesktopSettingsModel.updateEditFor(
                KaniAction.Settings.SetToggle("update.beta_updates_enabled", enabled = true),
                current,
            ),
        )
        // Every command is the host's to perform, not this mapper's to persist. A mapper
        // that claimed one would write a settings key on "check for updates".
        for (id in listOf(
            "update.check_now",
            "update.install_pending",
            "update.open_install_permission",
            "update.background_setup",
        )) {
            assertNull(id, DesktopSettingsModel.updateEditFor(KaniAction.Settings.Command(id), current))
        }
        // And a toggle already in that state writes nothing, so re-rendering the section
        // does not churn the store.
        assertNull(
            DesktopSettingsModel.updateEditFor(
                KaniAction.Settings.SetToggle("update.auto_update_enabled", enabled = false),
                current,
            ),
        )
    }

    @Test
    fun theBackgroundSetupPathIsOfferedUntilBackgroundUpdatingActuallyWorks() {
        fun offers(state: DesktopSettingsModel.UpdateState): Boolean =
            updateContent(state).controls.filterIsInstance<SettingsControl.ActionButton>()
                .any { (it.action as KaniAction.Settings.Command).id == "update.background_setup" }

        // Background updating needs both halves, so the one-tap path stays visible until
        // both are true — offering it after that would be a button that does nothing.
        assertTrue(offers(update(autoUpdateEnabled = false, canInstall = false)))
        assertTrue(offers(update(autoUpdateEnabled = true, canInstall = false)))
        assertTrue(offers(update(autoUpdateEnabled = false, canInstall = true)))
        assertFalse(offers(update(autoUpdateEnabled = true, canInstall = true)))
    }

    private fun updateContent(
        state: DesktopSettingsModel.UpdateState,
    ): SettingsSectionContent.Controls = DesktopSettingsModel.screen(
        SettingsSection.UPDATE,
        snapshot(),
        update = state,
        capabilities = PlatformCapabilities.of(PlatformCapability.UPDATE_DELIVERY),
    ).content as SettingsSectionContent.Controls

    private fun snapshot(theme: KaniThemeChoice = KaniThemeChoice.GIRLYPOP): SettingsSnapshot =
        SettingsSnapshotFixtures.blank(theme)

    /**
     * Automation state with every value deliberately off its default.
     *
     * 07:30/09:15/23:00–06:00 rather than the reviewed 19:00/22:00–08:00 defaults, so a
     * mapping that ignored the state it was handed and rendered the default would fail
     * instead of passing by coincidence.
     */
    private fun automation(
        reminderEnabled: Boolean = true,
        notificationsBlocked: Boolean = false,
        autoSyncConfigured: Boolean = true,
    ): DesktopSettingsModel.AutomationState = DesktopSettingsModel.AutomationState(
        reminderEnabled = reminderEnabled,
        reminderHour = 7,
        reminderMinute = 30,
        reminderMaxPerDay = 1,
        reminderQuietStartMinute = 23 * 60,
        reminderQuietEndMinute = 6 * 60,
        notificationsBlocked = notificationsBlocked,
        autoSyncConfigured = autoSyncConfigured,
        autoSyncEnabled = autoSyncConfigured,
        autoSyncHour = 9,
        autoSyncMinute = 15,
        debugLogEnabled = false,
    )

    /**
     * Update state as it reads after a successful background check found a newer release.
     *
     * The interesting state rather than the empty one: a staged artifact, two different
     * versions, and a result line. A fixture with nothing in it would let a section that
     * rendered blanks pass every one of these.
     */
    private fun update(
        autoUpdateEnabled: Boolean = false,
        betaUpdatesEnabled: Boolean = false,
        canInstall: Boolean = true,
        pendingPackage: String = "kani-0.4.0.apk",
    ): DesktopSettingsModel.UpdateState = DesktopSettingsModel.UpdateState(
        autoUpdateEnabled = autoUpdateEnabled,
        betaUpdatesEnabled = betaUpdatesEnabled,
        installedVersion = "0.3.6",
        lastCheckAtMillis = 1_700_000_000_000L,
        lastResult = "Downloaded and verified 0.4.0",
        lastVersion = "0.4.0",
        pendingPackage = pendingPackage,
        pendingMessage = "",
        canInstall = canInstall,
    )
}
