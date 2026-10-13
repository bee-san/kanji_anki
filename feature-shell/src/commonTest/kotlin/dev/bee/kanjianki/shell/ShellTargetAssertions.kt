package dev.bee.kanjianki.shell

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.ui.KaniUiTokens
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That the shell's own controls are large enough to hit.
 *
 * The shell owns the controls a user reaches when something has gone wrong — retry,
 * dismiss, the two answers to a confirmation, and back — and those are the worst place
 * for an under-sized target: the user is already stuck, and a missed click on Retry
 * reads as the retry not working. All five are Material `TextButton`/`OutlinedButton`/
 * `IconButton`, whose defaults are 40dp, under [KaniUiTokens.MinTouchTarget].
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryShellControlIsBigEnoughToHit() {
    // A retryable failure, so both the retry and the dismiss are on screen at once.
    val retryable = PresentationFailure.Kind.entries.first { PresentationFailure(it).isRetryable }
    renderRoute(RouteState<String>(destination = KaniDestination.Home).withFailure(PresentationFailure(retryable))) {
        assertTargetIsBigEnough(SHELL_RETRY_TEST_TAG)
        assertTargetIsBigEnough(SHELL_DISMISS_FAILURE_TEST_TAG)
    }
    renderShell(state = ShellState().enqueueForTest(sampleConfirm())) {
        assertTargetIsBigEnough(SHELL_CONFIRM_ACCEPT_TEST_TAG)
        assertTargetIsBigEnough(SHELL_CONFIRM_DISMISS_TEST_TAG)
    }
    renderShell(
        state = ShellState(backStack = listOf(KaniDestination.Home, KaniDestination.Study)),
        backAffordance = ShellBackAffordanceMode.IN_SHELL,
    ) {
        assertTargetIsBigEnough(SHELL_BACK_TEST_TAG)
    }
}

/**
 * That a confirmation can be answered without a pointer, both ways.
 *
 * The keyboard-only half of Goal 203's dialog requirement. A confirmation is the one
 * surface in Kani that blocks everything behind it, so a keyboard user who cannot reach
 * its two buttons is not inconvenienced — they are stuck, with no way forward and no way
 * out. Both answers are asserted, because a dialog where only the accept is reachable is
 * worse than one where neither is: it turns "I cannot decide" into "I must agree".
 *
 * Driven through the buttons' own click semantics rather than by pressing Tab and reading
 * the focus owner: what matters is that each answer is independently invokable and
 * dispatches exactly its own action, and Compose's focus traversal order is the
 * framework's business, not a property of Kani that a test should pin.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertBothConfirmAnswersAreIndependentlyReachable() {
    val confirm = sampleConfirm()
    val queued = ShellState().enqueueForTest(confirm)
    val id = queued.effects.head!!.id

    // Accept alone: the confirmed action, and nothing the dismiss would have sent.
    val accepted = mutableListOf<KaniAction>()
    renderShell(state = queued, recorded = accepted) {
        onAllNodesWithTag(SHELL_CONFIRM_ACCEPT_TEST_TAG)[0].performClick()
    }
    assertEquals(listOf(KaniAction.Consume.Effect(id), confirm.confirm), accepted)

    // Dismiss alone: the effect consumed and the destructive action not taken. Asserted
    // separately rather than as a second click in the same render, because the point is
    // that neither answer needs the other to have been reachable first.
    val dismissed = mutableListOf<KaniAction>()
    renderShell(state = queued, recorded = dismissed) {
        onAllNodesWithTag(SHELL_CONFIRM_DISMISS_TEST_TAG)[0].performClick()
    }
    assertEquals(listOf<KaniAction>(KaniAction.Consume.Effect(id)), dismissed)
}

/** A destructive confirmation, which is the case whose dismiss path matters most. */
private fun sampleConfirm(): KaniEffect.Confirm = KaniEffect.Confirm(
    title = literal("Delete every backup?"),
    body = literal("This cannot be undone."),
    confirmLabel = literal("Delete"),
    dismissLabel = literal("Keep"),
    confirm = KaniAction.Retry,
    isDestructive = true,
)

/**
 * Asserts the node at [tag] is at least [KaniUiTokens.MinTouchTarget] tall.
 *
 * Measured from the laid-out node rather than read off the modifier chain: a `heightIn`
 * that is present but sits inside a `Row` whose own height is fixed produces no taller
 * node, and source-reading would not notice. The 1dp slack absorbs the density division
 * the fixed-window harness performs, so a logical 44dp measuring 43.999 does not fail.
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
