package dev.bee.kanjianki.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.CapabilityGate
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.Loadable
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val SHELL_LOADING_TEST_TAG: String = "kani-shell-loading"
const val SHELL_REFRESHING_TEST_TAG: String = "kani-shell-refreshing"
const val SHELL_FAILURE_TEST_TAG: String = "kani-shell-failure"
const val SHELL_RETRY_TEST_TAG: String = "kani-shell-retry"
const val SHELL_DISMISS_FAILURE_TEST_TAG: String = "kani-shell-dismiss-failure"
const val SHELL_CAPABILITY_TEST_TAG: String = "kani-shell-capability"

/**
 * Renders one route's [Loadable] content, with the shell's own loading and error
 * surfaces around it.
 *
 * The point of putting this in the shell is that every route gets the same four
 * answers to "there is nothing to show yet". The Android screens each decided for
 * themselves, which is why some flashed empty on refresh and some did not.
 *
 * [content] is invoked with the loaded value. It is *also* invoked for
 * [Loadable.Refreshing], with the previous value — a refresh keeps what the user
 * was reading on screen and adds a progress hint above it.
 */
@Composable
fun <T> ShellRouteContent(
    state: RouteState<T>,
    copy: ShellCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // A failure alongside content is rendered as a banner above it rather
        // than replacing it: `RouteState.withFailure` deliberately keeps the last
        // good value, and blanking the screen would throw away information the
        // user already had.
        state.failure?.let { failure ->
            ShellFailureBanner(
                failure = failure,
                copy = copy,
                resolver = resolver,
                dispatch = dispatch,
            )
        }
        when (val loadable = state.content) {
            Loadable.Idle -> Unit

            Loadable.Loading -> ShellLoading(copy = copy)

            is Loadable.Refreshing -> {
                ShellRefreshingHint(copy = copy)
                content(loadable.previous)
            }

            is Loadable.Loaded -> content(loadable.value)

            is Loadable.Failed ->
                // `withFailure` sets `failure` and `content` together, so the
                // banner above has already rendered this one. Rendering it again
                // here would show the same error twice.
                if (state.failure == null) {
                    ShellFailureBanner(
                        failure = loadable.failure,
                        copy = copy,
                        resolver = resolver,
                        dispatch = dispatch,
                    )
                }
        }
    }
}

/** The blocking first-load surface. */
@Composable
fun ShellLoading(copy: ShellCopy, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(SHELL_LOADING_TEST_TAG)
            // Announced, because a spinner with no text tells a screen-reader
            // user nothing about why the screen is empty.
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = KaniTheme.colors.primary)
            Text(
                text = copy.loading,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
            )
        }
    }
}

/** The non-blocking refresh hint, shown above content the user is still reading. */
@Composable
fun ShellRefreshingHint(copy: ShellCopy, modifier: Modifier = Modifier) {
    val announcement = copy.loading
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SHELL_REFRESHING_TEST_TAG)
            // A bare progress bar in a live region announces nothing, which is the
            // same silence as having no live region at all. The description is what
            // makes the announcement say something.
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
        color = KaniTheme.colors.primary,
        trackColor = KaniTheme.colors.track,
    )
}

/**
 * The in-place error boundary.
 *
 * Retry is offered only when [PresentationFailure.isRetryable] — the kind's own
 * answer, not the caller's guess. Offering retry on a configuration error sends
 * the user in a loop; withholding it on a transient one strands them.
 */
@Composable
fun ShellFailureBanner(
    failure: PresentationFailure,
    copy: ShellCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .testTag(SHELL_FAILURE_TEST_TAG)
            // Assertive rather than polite: this interrupts, because the user is
            // looking at content that is now known to be wrong or incomplete.
            .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.secondaryFill,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = copy.failureMessage(failure, resolver),
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failure.isRetryable) {
                    OutlinedButton(
                        onClick = { dispatch(KaniAction.Retry) },
                        modifier = Modifier.testTag(SHELL_RETRY_TEST_TAG),
                        shape = KaniUiTokens.ButtonShape,
                    ) {
                        Text(text = copy.retry)
                    }
                }
                TextButton(
                    onClick = { dispatch(KaniAction.Consume.Failure) },
                    modifier = Modifier.testTag(SHELL_DISMISS_FAILURE_TEST_TAG),
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = copy.dismiss)
                }
            }
        }
    }
}

/**
 * Explains a capability the host does not have, in place of the affordance.
 *
 * This is the visible half of the contract `PlatformCapabilities.gate` enforces:
 * a screen either offers a working control or renders this. What it must never do
 * is offer a control that silently does nothing, which is the failure mode the
 * whole capability model exists to prevent.
 */
@Composable
fun ShellCapabilityExplanation(
    gate: CapabilityGate.Unavailable,
    copy: ShellCopy,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SHELL_CAPABILITY_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Text(
            text = copy.capabilityExplanation(gate.capability),
            modifier = Modifier.padding(16.dp),
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
    }
}
