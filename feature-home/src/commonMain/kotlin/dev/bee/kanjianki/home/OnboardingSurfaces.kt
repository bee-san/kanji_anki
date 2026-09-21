package dev.bee.kanjianki.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val ONBOARDING_TEST_TAG: String = "kani-onboarding"
const val ONBOARDING_BODY_TEST_TAG: String = "kani-onboarding-body"
const val ONBOARDING_PRIMARY_TEST_TAG: String = "kani-onboarding-primary"
const val PROVIDER_STATUS_TEST_TAG: String = "kani-provider-status"
const val SYNC_PROGRESS_TEST_TAG: String = "kani-sync-progress"
const val SYNC_CANCEL_TEST_TAG: String = "kani-sync-cancel"
const val REPAIRED_HANDOFF_TEST_TAG: String = "kani-repaired-handoff"
const val REPAIRED_HANDOFF_COPY_TEST_TAG: String = "kani-repaired-handoff-copy"

/**
 * The onboarding card: where the user is, why, and the one thing to do next.
 *
 * One primary button, never a menu of them. `HomeImportOnboardingPolicy` shipped
 * with exactly one `primaryActionLabel()` per state and that is the property worth
 * keeping: a user who cannot sync yet has one blocker, and offering three buttons
 * makes them guess which one addresses it.
 *
 * The action dispatched is [OnboardingPlan.primaryAction] — decided by the shared
 * policy, not by this composable — so the Android and desktop hosts cannot drift on
 * what the button does.
 */
@Composable
fun OnboardingCard(
    plan: OnboardingPlan,
    copy: HomeCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
    counted: HomeCountedCopy = HomeCountedCopy(),
    enabled: Boolean = true,
) {
    val body = copy.stepBody(plan, resolver, counted)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ONBOARDING_TEST_TAG),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = body,
                modifier = Modifier
                    .testTag(ONBOARDING_BODY_TEST_TAG)
                    // Polite rather than assertive: the step changing is progress
                    // the user caused, not an interruption. Announced at all because
                    // the body is the only thing that says why the button changed.
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
            )
            Button(
                onClick = { dispatch(plan.primaryAction) },
                modifier = Modifier.testTag(ONBOARDING_PRIMARY_TEST_TAG),
                // Disabled while a sync is running rather than hidden: a button that
                // vanishes mid-tap moves everything below it under the user's finger.
                enabled = enabled,
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(
                    text = copy.stepAction(plan, resolver),
                    fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Whether a collection is connected, stated rather than implied.
 *
 * Separate from the onboarding body because it stays true once onboarding is done:
 * a user who has synced for a month still benefits from seeing "Connected" when
 * something stops working, and the onboarding card by then says nothing about the
 * connection at all.
 */
@Composable
fun ProviderStatusRow(
    readiness: ProviderReadiness,
    copy: HomeCopy,
    modifier: Modifier = Modifier,
) {
    val label = copy.readinessLabel(readiness)
    val title = copy.providerStatusTitle
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PROVIDER_STATUS_TEST_TAG)
            // One description for the pair, so a screen reader says
            // "Collection, Connected" instead of reading two unrelated labels.
            .semantics { contentDescription = "$title, $label" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
        Text(
            text = label,
            color = if (readiness == ProviderReadiness.READY) {
                KaniTheme.colors.ink
            } else {
                KaniTheme.colors.coral
            },
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * The running-sync surface, with the one control that applies: stop.
 *
 * Cancellation is offered throughout rather than after a delay, because on a large
 * collection the sync is long enough that a user who started it by mistake should
 * not have to wait to say so. Stopping keeps whatever already committed — sync
 * writes in batches — so there is nothing to warn about and no confirmation.
 */
@Composable
fun SyncProgressCard(
    copy: HomeCopy,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = copy.syncInProgressTitle
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SYNC_PROGRESS_TEST_TAG)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = title
            },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            // Indeterminate on purpose: a sync's total is not known until the
            // provider has been queried, and a bar that jumps to 90% and waits is
            // worse than one that does not claim to know.
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = KaniTheme.colors.primary,
                trackColor = KaniTheme.colors.track,
            )
            TextButton(
                onClick = { dispatch(KaniAction.Provider.CancelSync) },
                modifier = Modifier
                    .heightIn(min = KaniUiTokens.MinTouchTarget)
                    .testTag(SYNC_CANCEL_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.syncCancelAction)
            }
        }
    }
}

/**
 * The repaired-card hand-off.
 *
 * Hands the user a search to paste into Anki rather than unsuspending anything.
 * Kani never writes card queue state, so the cards it has tagged still need
 * unsuspending by the user in Anki — and the search is the whole of what Kani can
 * usefully do about that.
 */
@Composable
fun RepairedHandoffCard(
    count: Int,
    copy: HomeCopy,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val body = rememberRepairedHandoffBody(count)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(REPAIRED_HANDOFF_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = copy.repairedHandoffTitle,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = body,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
            // Copies the search rather than performing it: Kani never writes card
            // queue state, so unsuspending is the user's move to make in Anki.
            TextButton(
                onClick = {
                    dispatch(
                        KaniAction.RequestCopy(
                            text = copy.repairedHandoffQuery,
                            confirmation = UiText.Literal(copy.repairedHandoffCopied),
                        ),
                    )
                },
                modifier = Modifier.testTag(REPAIRED_HANDOFF_COPY_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.repairedHandoffCopy)
            }
        }
    }
}
