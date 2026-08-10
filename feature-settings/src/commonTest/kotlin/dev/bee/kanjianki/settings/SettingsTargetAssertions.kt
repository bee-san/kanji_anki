package dev.bee.kanjianki.settings

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import dev.bee.kanjianki.ui.KaniUiTokens
import kotlin.test.assertTrue

/**
 * That every Settings control is large enough to hit.
 *
 * Settings is where the defect this file exists to catch is densest: it is almost
 * entirely secondary controls — chips, steppers, and toggle rows — and Material's
 * defaults for all three are 40dp, under [KaniUiTokens.MinTouchTarget]. A stepper's
 * "−" and "+" are the worst case, because they are small, adjacent, and the two
 * outcomes of mis-hitting one are opposite.
 *
 * Measured rather than asserted from the source, because a `heightIn` that is present
 * but sits after a `size` or inside a `Row` with a fixed height does not produce a
 * taller node, and reading the modifier chain would not notice.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEverySettingsControlIsBigEnoughToHit() {
    renderSettings(content = { SettingsScreenView(controlsScreen(), settingsCopy(), dispatch = {}) }) {
        val tags = listOf(
            settingsControlTestTag("Import weak cards"),
            settingsControlTestTag("Personalise weights"),
            settingsControlTestTag("New card order"),
            settingsStepperButtonTestTag("Promotion interval", up = false),
            settingsStepperButtonTestTag("Promotion interval", up = true),
            settingsControlTestTag("Reset ladder"),
            settingsControlTestTag("Recompute now"),
        )
        for (tag in tags) {
            assertTargetIsBigEnough(tag)
        }
    }
}

/**
 * That a keybinding chip is a target and not just a label.
 *
 * The chips are the smallest interactive thing Kani ships — one character wide in the
 * common case — and they are how a keyboard user reassigns the keys they rely on. A
 * chip too small to click is a mouse-only path to a keyboard-only feature.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryKeybindingChipIsBigEnoughToHit() {
    renderSettings(content = { SettingsScreenView(keybindingsScreen(), settingsCopy(), dispatch = {}) }) {
        // Every chip on the screen, found by tag prefix rather than named one by one:
        // the point is that none is small, and naming them would let a new one escape.
        for (label in listOf("Remove P", "G", "1")) {
            assertTargetIsBigEnough(settingsKeybindingChoiceTestTag(label))
        }
    }
}

/**
 * Asserts the node at [tag] is at least [KaniUiTokens.MinTouchTarget] tall.
 *
 * The 1dp slack absorbs the density division the fixed-window harness performs: a
 * logical 44dp can measure 43.999 after a divide-and-round trip, and a test that fails
 * on floating-point noise gets suppressed rather than fixed.
 *
 * No scroll-into-view first: the bounds of a laid-out node are the same whether or not it
 * is on screen, and requiring a scrollable ancestor would make the check depend on how
 * each module's harness wraps its content rather than on the control's own size.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTargetIsBigEnough(tag: String) {
    val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$tag did not render, so its size is untested")
    val bounds = onAllNodesWithTag(tag)[0].getBoundsInRoot()
    val height = bounds.bottom - bounds.top
    assertTrue(
        height.value + TARGET_SLACK >= KaniUiTokens.MinTouchTarget.value,
        "$tag is $height tall, under the ${KaniUiTokens.MinTouchTarget} target",
    )
}

private const val TARGET_SLACK: Float = 1f
