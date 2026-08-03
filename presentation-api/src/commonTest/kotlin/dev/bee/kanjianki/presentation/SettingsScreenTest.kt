package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun theSettingsActionsGuardTheirKeys() {
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetToggle(" ", enabled = true) }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetChoice(" ", "x") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetChoice("k", " ") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.Command(" ") }
    }
}
