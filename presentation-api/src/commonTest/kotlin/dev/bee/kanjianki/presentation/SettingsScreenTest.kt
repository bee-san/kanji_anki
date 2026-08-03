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
    fun theSettingsActionsGuardTheirKeys() {
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetToggle(" ", enabled = true) }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetChoice(" ", "x") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetChoice("k", " ") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.SetNumber(" ", 1) }
        assertFailsWith<IllegalArgumentException> { KaniAction.Settings.Command(" ") }
    }
}
