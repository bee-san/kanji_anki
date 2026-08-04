package dev.bee.kanjianki.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.SettingsSection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Settings surface's render assertions, run on both hosts.
 *
 * Structure, the action each control dispatches, and — the point of the placeholder —
 * that an un-ported section names itself rather than showing a blank panel. Settings
 * validation and persistence are `:core`/`:application`'s and are tested there.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEachCategoryOpensItsSection() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(rootScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsCategoryTestTag(SettingsSection.STUDY_BEHAVIOR.route)).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.STUDY_BEHAVIOR))),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertACategoryCarriesItsCapabilityNotice() {
    renderSettings(
        content = { SettingsScreenView(rootScreen(), settingsCopy(), dispatch = {}) },
    ) {
        val text = onNodeWithTag(settingsCategoryTestTag(SettingsSection.AUTOMATION.route)).subtreeTextOrEmpty()
        assertTrue(text.contains("Reminders only fire while the app is open"), "notice must be on the card: $text")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertATogglePassesItsNewValue() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsControlTestTag("Import weak cards")).performScrollTo().assertIsOn().performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Settings.SetToggle("import_weak_cards", enabled = false)),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertADisabledToggleStaysVisibleAndDispatchesNothing() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsControlTestTag("Personalise weights")).performScrollTo().assertIsOff().assertIsNotEnabled()
        onNodeWithTag(settingsControlTestTag("Personalise weights")).performClick()
        assertEquals(emptyList<KaniAction>(), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAChoiceDispatchesTheOptionItsButtonCarries() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsControlTestTag("New card order")).performScrollTo()
        onNodeWithText("Frequency").performClick()
        assertEquals(
            listOf<KaniAction>(KaniAction.Settings.SetChoice("new_card_sort", "frequency")),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAStepperStepsWithinItsBounds() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        // Value 21, step 7: up commits 28, down commits 14 — both clamped by the model.
        onNodeWithTag(settingsStepperButtonTestTag("Promotion interval", up = true)).performScrollTo().performClick()
        onNodeWithTag(settingsStepperButtonTestTag("Promotion interval", up = false)).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.Settings.SetNumber("promotion_interval_days", 28),
                KaniAction.Settings.SetNumber("promotion_interval_days", 14),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertADestructiveButtonDispatchesItsCommand() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsControlTestTag("Reset ladder")).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Settings.Command("reset_ladder")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAPlainButtonDispatchesItsCommand() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsControlTestTag("Recompute now")).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Settings.Command("recompute_stats")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnInfoRowShowsItsLabelAndValueWithNoAction() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        val text = onNodeWithTag(settingsControlTestTag("Database version")).performScrollTo().subtreeTextOrEmpty()
        assertTrue(text.contains("Database version"), "label must show: $text")
        assertTrue(text.contains("31"), "value must show: $text")
        onNodeWithTag(settingsControlTestTag("Database version")).performClick()
        assertEquals(emptyList<KaniAction>(), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAKeybindingRowReadsOutTheKeysItsCommandHolds() {
    renderSettings(
        content = { SettingsScreenView(keybindingsScreen(), settingsCopy(), dispatch = {}) },
    ) {
        onNodeWithTag(SETTINGS_KEYBINDINGS_TEST_TAG).performScrollTo()
        // The accelerator line is the answer to "what is this bound to now", so a screen
        // reader must get it with the command name rather than having to walk the chips.
        assertEquals(
            "Pass: 3, Numpad 3, P",
            onNodeWithTag(settingsKeybindingAcceleratorTestTag("Pass")).performScrollTo().contentDescriptionOrEmpty(),
        )
        // A command holding nothing still shows a row and says so.
        assertEquals(
            "Undo: No key",
            onNodeWithTag(settingsKeybindingAcceleratorTestTag("Undo")).performScrollTo().contentDescriptionOrEmpty(),
        )
        val row = onNodeWithTag(settingsKeybindingRowTestTag("Pass")).subtreeTextOrEmpty()
        assertTrue(row.contains("Pass"), "command must name itself: $row")
        assertTrue(row.contains("3, Numpad 3, P"), "bound keys must show: $row")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertBindingAndUnbindingDispatchTheKeyTheyWereShownOn() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(keybindingsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(settingsKeybindingChoiceTestTag("G")).performScrollTo().performClick()
        onNodeWithTag(settingsKeybindingChoiceTestTag("Remove P")).performScrollTo().performClick()
        onNodeWithTag(settingsControlTestTag("Reset to defaults")).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.Settings.Command("study_keybindings.bind:grade_pass:G"),
                KaniAction.Settings.Command("study_keybindings.unbind:P"),
                KaniAction.Settings.Command("study_keybindings.reset"),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnUnavailableKeyStatesItsReasonAndDispatchesNothing() {
    val recorded = mutableListOf<KaniAction>()
    renderSettings(
        content = { SettingsScreenView(keybindingsScreen(), settingsCopy(), dispatch = { recorded += it }) },
    ) {
        // Refused keys stay on screen with their reason rather than vanishing: a hidden
        // key leaves the user hunting for why the remap will not take.
        onNodeWithTag(settingsKeybindingChoiceTestTag("1")).performScrollTo().assertIsNotEnabled()
        assertEquals(
            "1. Already Fail",
            onNodeWithTag(settingsKeybindingChoiceTestTag("1")).contentDescriptionOrEmpty(),
        )
        assertEquals(
            "Ctrl+Z. Used by the system: Undo",
            onNodeWithTag(settingsKeybindingChoiceTestTag("Ctrl+Z")).performScrollTo().contentDescriptionOrEmpty(),
        )
        onNodeWithTag(settingsKeybindingChoiceTestTag("1")).performClick()
        onNodeWithTag(settingsKeybindingChoiceTestTag("Ctrl+Z")).performClick()
        assertEquals(emptyList<KaniAction>(), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnUnportedSectionNamesItselfRatherThanBlank() {
    val copy = settingsCopy()
    renderSettings(
        content = { SettingsScreenView(SettingsScreen(section = SettingsSection.APPEARANCE), copy, dispatch = {}) },
    ) {
        val description = onNodeWithTag(SETTINGS_PLACEHOLDER_TEST_TAG).performScrollTo().contentDescriptionOrEmpty()
        assertEquals("${copy.placeholder} ${copy.placeholderHint}", description)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedSettingsResourcesResolveOnThisHost() {
    var text = ""
    renderSettings(
        content = {
            val copy = rememberSettingsCopy()
            text = copy.placeholder + copy.placeholderHint
        },
    ) {
        assertTrue(text.isNotBlank(), "shipped resources must resolve")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheSettingsTestTagsAreDistinct() {
    val tags = listOf(
        SETTINGS_SCREEN_TEST_TAG,
        SETTINGS_ROOT_TEST_TAG,
        SETTINGS_CONTROLS_TEST_TAG,
        SETTINGS_PLACEHOLDER_TEST_TAG,
        SETTINGS_KEYBINDINGS_TEST_TAG,
    ) + SettingsSection.entries.map { settingsCategoryTestTag(it.route) } +
        listOf("Import weak cards", "New card order").map(::settingsControlTestTag) +
        listOf(
            settingsStepperButtonTestTag("Promotion interval", up = true),
            settingsStepperButtonTestTag("Promotion interval", up = false),
        ) +
        listOf("Pass", "Undo").flatMap {
            listOf(settingsKeybindingRowTestTag(it), settingsKeybindingAcceleratorTestTag(it))
        } +
        listOf("G", "Ctrl+Z", "Remove P").map(::settingsKeybindingChoiceTestTag)
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-settings-category-settings/appearance", settingsCategoryTestTag(SettingsSection.APPEARANCE.route))
}
