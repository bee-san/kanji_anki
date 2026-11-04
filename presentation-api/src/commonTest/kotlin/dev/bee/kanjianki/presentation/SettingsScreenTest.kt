package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsScreenTest {
    @Test
    fun aCategoryOpensItsOwnSection() {
        val category = SettingsCategory(
            section = SettingsSection.AUTOMATION,
            title = "Automation",
            summary = "Reminders and backups",
        )
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.AUTOMATION)),
            category.action,
        )
        assertTrue(category.notices.isEmpty())
    }

    @Test
    fun aScreenDefaultsToTheHonestPlaceholder() {
        val screen = SettingsScreen(section = SettingsSection.APPEARANCE)
        assertNull(screen.root)
        assertEquals(SettingsSectionContent.Placeholder, screen.content)
    }

    @Test
    fun theRootScreenCarriesItsMenu() {
        val root = SettingsRoot(
            title = "Settings",
            categories = listOf(
                SettingsCategory(SettingsSection.STUDY_BEHAVIOR, "Study", "ladder"),
            ),
        )
        val screen = SettingsScreen(section = SettingsSection.ROOT, root = root)
        assertEquals(1, screen.root?.categories?.size)
    }

    @Test
    fun aToggleReportsTheValueItWasFlippedTo() {
        val toggle = SettingsControl.Toggle(
            label = "Import weak cards",
            checked = true,
            onChange = { KaniAction.Settings.SetToggle("import_weak_cards", it) },
        )
        assertEquals("Import weak cards", toggle.label)
        assertTrue(toggle.enabled)
        assertEquals(KaniAction.Settings.SetToggle("import_weak_cards", enabled = false), toggle.onChange(false))
        assertEquals(KaniAction.Settings.SetToggle("import_weak_cards", enabled = true), toggle.onChange(true))
    }

    @Test
    fun aChoiceOptionCarriesTheActionItsButtonDispatches() {
        val option = SettingsChoiceOption(
            id = "frequency",
            label = "Frequency",
            action = KaniAction.Settings.SetChoice("new_card_sort", "frequency"),
        )
        val choice = SettingsControl.Choice(
            label = "New card order",
            options = listOf(option),
            selectedId = "frequency",
        )
        assertEquals("New card order", choice.label)
        assertEquals(option.action, choice.options.single().action)
        assertEquals("frequency", choice.selectedId)
    }

    @Test
    fun aChoiceOptionNeedsAnId() {
        assertFailsWith<IllegalArgumentException> {
            SettingsChoiceOption(id = " ", label = "x", action = KaniAction.Retry)
        }
    }

    @Test
    fun anActionButtonCarriesItsCommandAndItsFlags() {
        val destructive = SettingsControl.ActionButton(
            label = "Reset ladder",
            action = KaniAction.Settings.Command("reset_ladder"),
            destructive = true,
        )
        assertEquals("Reset ladder", destructive.label)
        assertTrue(destructive.destructive)
        assertTrue(destructive.enabled)

        val plain = SettingsControl.ActionButton(
            label = "Recompute",
            action = KaniAction.Settings.Command("recompute_stats"),
        )
        assertEquals(false, plain.destructive)
    }

    @Test
    fun aStepperClampsToItsBoundsAndReportsWhereItCanGo() {
        val mid = SettingsControl.Stepper(
            label = "Promotion interval",
            value = 21,
            min = 1,
            max = 365,
            step = 7,
            unit = "days",
            onChange = { KaniAction.Settings.SetNumber("promotion_interval_days", it) },
        )
        assertEquals(28, mid.incremented())
        assertEquals(14, mid.decremented())
        assertTrue(mid.canIncrement)
        assertTrue(mid.canDecrement)
        assertEquals(KaniAction.Settings.SetNumber("promotion_interval_days", 28), mid.onChange(mid.incremented()))

        val atMax = mid.copy(value = 364)
        assertEquals(365, atMax.incremented())
        assertTrue(atMax.canIncrement)
        val pastMax = mid.copy(value = 365)
        assertEquals(365, pastMax.incremented())
        assertEquals(false, pastMax.canIncrement)

        val atMin = mid.copy(value = 1)
        assertEquals(1, atMin.decremented())
        assertEquals(false, atMin.canDecrement)
    }

    @Test
    fun aStepperRejectsAnInvalidRangeOrStep() {
        assertFailsWith<IllegalArgumentException> {
            SettingsControl.Stepper(label = "x", value = 0, min = 5, max = 1, onChange = { KaniAction.Retry })
        }
        assertFailsWith<IllegalArgumentException> {
            SettingsControl.Stepper(label = "x", value = 0, min = 0, max = 1, step = 0, onChange = { KaniAction.Retry })
        }
    }

    @Test
    fun anInfoRowIsLabelAndValueOnly() {
        val info = SettingsControl.Info(label = "Database version", value = "31")
        assertEquals("Database version", info.label)
        assertEquals("31", info.value)
    }

    @Test
    fun aControlsSectionListsItsControls() {
        val controls = SettingsSectionContent.Controls(
            title = "Study",
            controls = listOf(SettingsControl.Info("Database version", "31")),
        )
        assertEquals("Study", controls.title)
        assertEquals(1, controls.controls.size)
    }

    @Test
    fun aKeybindingRowShowsWhatItHoldsAndWhatCanReplaceIt() {
        val section = SettingsSectionContent.Keybindings(
            title = "Keyboard shortcuts",
            rows = listOf(
                SettingsKeybindingRow(
                    label = "Pass",
                    accelerator = "3, Numpad 3, P",
                    unbind = listOf(
                        SettingsKeybindingChoice("Remove P", KaniAction.Settings.Command("study_keybindings.unbind:P")),
                    ),
                    candidates = listOf(
                        SettingsKeybindingChoice("G", KaniAction.Settings.Command("study_keybindings.bind:grade_pass:G")),
                        SettingsKeybindingChoice(
                            label = "1",
                            action = KaniAction.Settings.Command("study_keybindings.bind:grade_pass:1"),
                            unavailableReason = "Already Fail",
                        ),
                    ),
                ),
                // A command may hold nothing; the row still exists, so the editor never
                // hides an action the user could bind.
                SettingsKeybindingRow(label = "Undo", accelerator = "No key"),
            ),
            reset = SettingsControl.ActionButton(
                label = "Reset to defaults",
                action = KaniAction.Settings.Command("study_keybindings.reset"),
            ),
        )
        assertEquals("Keyboard shortcuts", section.title)
        assertEquals(listOf("Pass", "Undo"), section.rows.map { it.label })
        assertEquals("study_keybindings.reset", (section.reset.action as KaniAction.Settings.Command).id)

        val pass = section.rows.first()
        assertEquals("3, Numpad 3, P", pass.accelerator)
        assertEquals("Remove P", pass.unbind.single().label)
        assertTrue(pass.unbind.single().enabled)
        // A refused candidate stays listed and carries its reason, rather than vanishing:
        // a hidden key leaves the user hunting for why it will not take.
        val (offered, refused) = pass.candidates.partition { it.enabled }
        assertEquals(listOf("G"), offered.map { it.label })
        assertEquals(listOf("Already Fail"), refused.map { it.unavailableReason })

        val undo = section.rows.last()
        assertTrue(undo.unbind.isEmpty())
        assertTrue(undo.candidates.isEmpty())
    }

    @Test
    fun aProsePageCarriesTitledBlocksAndAnUntitledOneIsBodyAlone() {
        val page = SettingsSectionContent.Prose(
            title = "How Kani works",
            blocks = listOf(
                SettingsProseBlock(title = "The ladder", body = "Ten rungs, one at a time."),
                // An untitled block is the attribution case: one already-formatted body
                // with no heading of its own, which must render as body rather than as an
                // empty heading followed by text.
                SettingsProseBlock(body = "Licensed under Apache 2.0."),
            ),
        )

        assertEquals("How Kani works", page.title)
        assertEquals(listOf("The ladder", ""), page.blocks.map { it.title })
        assertEquals("Licensed under Apache 2.0.", page.blocks.last().body)
        // Blocks rather than one string so a screen reader can skip between headings;
        // a page that collapsed to a single body would lose that and still look right.
        assertEquals(2, page.blocks.size)
    }

    @Test
    fun theHostCommandsAreTheOnesNoMapperMayPersist() {
        // Both file commands resolve to their purpose, and are host commands too: the
        // narrower question is "does this raise a picker", the wider one is "is this
        // anybody's to persist", and the writer asks the wider one.
        assertEquals(
            KaniEffect.FilePurpose.BACKUP_EXPORT,
            SettingsCommands.filePurposeFor(SettingsCommands.BACKUP_EXPORT),
        )
        assertEquals(
            KaniEffect.FilePurpose.BACKUP_RESTORE,
            SettingsCommands.filePurposeFor(SettingsCommands.BACKUP_RESTORE),
        )
        for (id in listOf(SettingsCommands.BACKUP_EXPORT, SettingsCommands.BACKUP_RESTORE)) {
            assertTrue(SettingsCommands.isPickerCommand(id))
            assertTrue(SettingsCommands.isHostCommand(id))
        }
        // The update commands are host work but not file dialogs, so they are host
        // commands and not pickers. A writer that only asked `isPickerCommand` would fall
        // through to the collection mapper on these.
        for (id in listOf(
            SettingsCommands.UPDATE_CHECK,
            SettingsCommands.UPDATE_INSTALL,
            SettingsCommands.UPDATE_PERMISSION,
            SettingsCommands.UPDATE_BACKGROUND_SETUP,
        )) {
            assertNull(SettingsCommands.filePurposeFor(id))
            assertTrue(SettingsCommands.isHostCommand(id), id)
        }
        // Fail-closed both ways: an id from a build that knows a flow this one does not is
        // neither performed nor persisted-as-unknown.
        assertFalse(SettingsCommands.isHostCommand("update.teleport"))
        assertFalse(SettingsCommands.isHostCommand("study_keybindings.reset"))
        // Whitespace is tolerated, because these ids cross a wire format.
        assertTrue(SettingsCommands.isHostCommand("  ${SettingsCommands.UPDATE_CHECK}  "))
    }

    @Test
    fun theSettingsActionsGuardTheirKeys() {
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetToggle(" ", enabled = true) }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetChoice(" ", "x") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetChoice("k", " ") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetNumber(" ", 1) }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.Command(" ") }
    }
}
