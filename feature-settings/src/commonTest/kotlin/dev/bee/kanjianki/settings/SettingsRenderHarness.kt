package dev.bee.kanjianki.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.SettingsCategory
import dev.bee.kanjianki.presentation.SettingsChoiceOption
import dev.bee.kanjianki.presentation.SettingsControl
import dev.bee.kanjianki.presentation.SettingsKeybindingChoice
import dev.bee.kanjianki.presentation.SettingsKeybindingRow
import dev.bee.kanjianki.presentation.SettingsProseBlock
import dev.bee.kanjianki.presentation.SettingsRoot
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.ui.KaniTheme

internal fun settingsCopy(): SettingsCopy = SettingsCopy(
    placeholder = "Coming to desktop",
    placeholderHint = "Available on Android today",
)

/** The root menu, with one category carrying a capability notice. */
internal fun rootScreen(): SettingsScreen = SettingsScreen(
    section = SettingsSection.ROOT,
    root = SettingsRoot(
        title = "Settings",
        categories = listOf(
            SettingsCategory(
                section = SettingsSection.STUDY_BEHAVIOR,
                title = "Study behaviour",
                summary = "Ladder, repair, and thresholds",
            ),
            SettingsCategory(
                section = SettingsSection.AUTOMATION,
                title = "Automation",
                summary = "Reminders and backups",
                notices = listOf("Reminders only fire while the app is open"),
            ),
        ),
    ),
)

/** A section screen exercising every control variant. */
internal fun controlsScreen(): SettingsScreen = SettingsScreen(
    section = SettingsSection.STUDY_BEHAVIOR,
    content = SettingsSectionContent.Controls(
        title = "Study behaviour",
        controls = listOf(
            SettingsControl.Toggle(
                label = "Import weak cards",
                checked = true,
                onChange = { KaniAction.Settings.SetToggle("import_weak_cards", it) },
            ),
            SettingsControl.Toggle(
                label = "Personalise weights",
                checked = false,
                enabled = false,
                onChange = { KaniAction.Settings.SetToggle("personalize_weights", it) },
            ),
            SettingsControl.Choice(
                label = "New card order",
                selectedId = "balanced_priority",
                options = listOf(
                    SettingsChoiceOption("balanced_priority", "Balanced", KaniAction.Settings.SetChoice("new_card_sort", "balanced_priority")),
                    SettingsChoiceOption("frequency", "Frequency", KaniAction.Settings.SetChoice("new_card_sort", "frequency")),
                ),
            ),
            SettingsControl.Stepper(
                label = "Promotion interval",
                value = 21,
                min = 1,
                max = 365,
                step = 7,
                unit = "days",
                onChange = { KaniAction.Settings.SetNumber("promotion_interval_days", it) },
            ),
            SettingsControl.ActionButton(
                label = "Reset ladder",
                action = KaniAction.Settings.Command("reset_ladder"),
                destructive = true,
            ),
            SettingsControl.ActionButton(
                label = "Recompute now",
                action = KaniAction.Settings.Command("recompute_stats"),
            ),
            SettingsControl.Info(
                label = "Database version",
                value = "31",
            ),
        ),
    ),
)

/**
 * The keybinding editor, with a bound command, an unbound one, and both refusal reasons.
 *
 * Small on purpose: the real screen offers ~50 candidates per command, and the point
 * under test is that a row shows what it holds, offers what can be chosen, and states
 * why the rest cannot — not that fifty chips lay out.
 */
internal fun keybindingsScreen(): SettingsScreen = SettingsScreen(
    section = SettingsSection.KEYBINDINGS,
    content = SettingsSectionContent.Keybindings(
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
                    SettingsKeybindingChoice(
                        label = "Ctrl+Z",
                        action = KaniAction.Settings.Command("study_keybindings.bind:grade_pass:Ctrl+Z"),
                        unavailableReason = "Used by the system: Undo",
                    ),
                ),
            ),
            SettingsKeybindingRow(label = "Undo", accelerator = "No key"),
        ),
        reset = SettingsControl.ActionButton(
            label = "Reset to defaults",
            action = KaniAction.Settings.Command("study_keybindings.reset"),
        ),
    ),
)

/**
 * A prose page: one titled explainer block, and one untitled attribution body.
 *
 * Both shapes in one fixture because they are the two real callers — the explainer's
 * sections each have a heading, while an attribution arrives as a single already-formatted
 * body with none, and the untitled case is the one that could silently render an empty
 * heading.
 */
internal fun proseScreen(): SettingsScreen = SettingsScreen(
    section = SettingsSection.HOW_IT_WORKS,
    content = SettingsSectionContent.Prose(
        title = "How Kani works",
        blocks = listOf(
            SettingsProseBlock(
                title = "What Kani reads",
                body = "Kani reads your notes and cards through AnkiDroid's provider.",
            ),
            SettingsProseBlock(body = "JMdict, CC BY-SA 4.0."),
        ),
    ),
)

private val WINDOW_WIDTH: Dp = 411.dp
private val WINDOW_HEIGHT: Dp = 891.dp

@Composable
private fun FixedWindow(width: Dp, height: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = maxOf(width / maxWidth, height / maxHeight, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density / scale, fontScale = density.fontScale),
        ) {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) { content() }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun renderSettings(content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = WINDOW_WIDTH, height = WINDOW_HEIGHT) {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
                }
            }
        }
        block()
    }
}

internal fun SemanticsNodeInteraction.subtreeTextOrEmpty(): String {
    fun collect(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::collect)
    return collect(fetchSemanticsNode()).joinToString(" ")
}

/**
 * The text of every node in the subtree marked as a heading.
 *
 * Assistive technology navigates a long page by its headings, so what counts is not that
 * a title is on screen but that it is *announced as* a heading. This is also the only way
 * to catch the opposite failure — a heading emitted for a block that has no title, which
 * a screen reader reads out as an empty stop.
 */
internal fun SemanticsNodeInteraction.subtreeHeadingTexts(): List<String> {
    fun collect(node: SemanticsNode): List<String> {
        val own = if (node.config.getOrNull(SemanticsProperties.Heading) != null) {
            listOf(node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text })
        } else {
            emptyList()
        }
        return own + node.children.flatMap(::collect)
    }
    return collect(fetchSemanticsNode())
}

internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()
