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

internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()
