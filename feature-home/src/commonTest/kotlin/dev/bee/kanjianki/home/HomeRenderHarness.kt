package dev.bee.kanjianki.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.FieldMapping
import dev.bee.kanjianki.presentation.FieldRole
import dev.bee.kanjianki.presentation.FocusEmptyReason
import dev.bee.kanjianki.presentation.HomeMetricKind
import dev.bee.kanjianki.presentation.HomeNotice
import dev.bee.kanjianki.presentation.ImportSource
import dev.bee.kanjianki.presentation.NoteTypeOption
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme

/**
 * A [HomeCopy] built from marker strings rather than the shipped wording.
 *
 * Every template keeps its `%n$s` placeholders so a substitution that silently
 * failed shows up as a literal `%1$s` in the assertion, and every map is built from
 * `.entries` so a new enum entry is a missing-key failure here rather than a
 * `NoSuchElementException` the first time a user reaches that state.
 *
 * The bodies map deliberately covers only the four steps with a fixed sentence,
 * matching what [rememberHomeCopy] puts there: the other three assemble their body
 * from the note type, the provider's error, and a plural count.
 */
internal fun homeCopy(): HomeCopy = HomeCopy(
    providerStatusTitle = "Collection",
    noteTypeTitle = "Note type",
    noteTypeTooFewFields = "too few fields",
    fieldUnmapped = "not set",
    fieldRequired = "required",
    fieldOptional = "optional",
    syncConfirmTitle = "Sync collection",
    syncConfirmAction = "Sync cards",
    syncCancel = "Cancel",
    syncInProgressTitle = "Syncing collection",
    syncCancelAction = "Stop sync",
    syncAlreadyRunning = "already running",
    syncFailureFallback = "no reason given",
    repairedHandoffTitle = "Repaired kanji",
    repairedHandoffQuery = "tag:kani_repaired is:suspended",
    repairedHandoffCopy = "Copy search",
    repairedHandoffCopied = "Search copied.",
    sourceNone = "none",
    sourceAndModel = "model=%1\$s sources=%2\$s",
    sourceQuery = "query=%1\$s",
    syncConfirmBodyTemplate = "will sync %1\$s",
    recoverSyncBodyTemplate = "last sync failed: %1\$s",
    noteTypeLabelTemplate = "%1\$s (%2\$s)",
    fieldMissingRequiredTemplate = "choose a field for %1\$s",
    fieldStaleTemplate = "%1\$s no longer exists",
    stepBodies = FIXED_BODY_STEPS.associateWith { "body-$it" },
    stepActions = OnboardingStep.entries.associateWith { "action-$it" },
    readinessLabels = ProviderReadiness.entries.associateWith { "readiness-$it" },
    roleLabels = FieldRole.entries.associateWith { "role-$it" },
    sourceLabels = ImportSource.entries.associateWith { "source-$it" },
)

/**
 * The steps whose body is a fixed sentence rather than an assembled one.
 *
 * Named here so the copy factory and the tests that walk every step agree on which
 * bodies come from the map — a test that expected `body-SYNCED` would be asserting
 * against a key [rememberHomeCopy] never writes.
 */
internal val FIXED_BODY_STEPS: List<OnboardingStep> = listOf(
    OnboardingStep.CONNECT_PROVIDER,
    OnboardingStep.AUTHORIZE_PROVIDER,
    OnboardingStep.CHOOSE_SOURCE,
    OnboardingStep.RECOVER_AUTHORIZATION,
)

/**
 * A [DashboardCopy] built from marker strings rather than the shipped wording.
 *
 * Same discipline as [homeCopy]: templates keep their `%n$s` so a failed substitution
 * shows up as a literal placeholder, and every map comes from `.entries` so a new enum
 * entry fails here rather than at the first user who reaches that state.
 */
internal fun dashboardCopy(): DashboardCopy = DashboardCopy(
    title = "Kani",
    syncAction = "Sync collection",
    studyAction = "Study now",
    todayTitle = "Today",
    deckOverviewTitle = "Deck overview",
    metricCardDescription = "metric",
    focusQueueTitle = "Focus queue",
    focusQueueViewAll = "View all",
    metricLabels = HomeMetricKind.entries.associateWith { "metric-$it" },
    emptyTitles = FocusEmptyReason.entries.associateWith { "empty-title-$it" },
    emptyBodies = FocusEmptyReason.entries.associateWith { "empty-body-$it" },
    noticeTitles = HomeNotice.entries.associateWith { "notice-title-$it" },
    noticeBodies = HomeNotice.entries.associateWith { "notice-body-$it" },
    focusCardDescriptionTemplate = "card %1\$s %2\$s",
)

/** A [BrowseCopy] built from marker strings, with every template placeholder kept. */
internal fun browseCopy(): BrowseCopy = BrowseCopy(
    title = "Browse Kanji",
    searchHint = "Search",
    searchAction = "Search now",
    similarFilter = "Similar kanji only",
    showSuspended = "Show suspended",
    resultNone = "No matches",
    selectionNone = "None selected",
    selectionAll = "All selected",
    selectAll = "Select all",
    deselectAll = "Clear all",
    suspendedChip = "SUSPENDED",
    emptyTitle = "empty-title",
    emptyBody = "empty-body",
    studiedToggleTemplate = "study %1\$s",
    rowDescriptionTemplate = "open %1\$s",
    selectionPartialTemplate = "%1\$s of %2\$s selected",
    resultTruncatedTemplate = "first %1\$s matches",
    rowSelected = "selected",
    rowNotSelected = "not selected",
)

/**
 * A resolver that passes literals through and blanks what only a host could know.
 *
 * `:feature-shell` ships one of these for production use, and this module cannot
 * depend on it: `test_leaf_feature_modules_cannot_depend_on_each_other` forbids one
 * leaf feature reaching into another. Blank rather than a visible marker because
 * every caller treats blank as "fall back to shared copy", so a placeholder like
 * `???` would defeat the fallback these tests are checking.
 */
internal val TestUiTextResolver: UiTextResolver = UiTextResolver { text ->
    when (text) {
        is UiText.Literal -> text.text
        is UiText.Key, is UiText.Quantity -> ""
    }
}

/** The Kiku note type, matching the field names `Settings.kikuDefaults()` maps. */
internal fun kikuOption(): NoteTypeOption = NoteTypeOption(
    name = "Kiku",
    fields = listOf(
        "Expression",
        "ExpressionReading",
        "MainDefinition",
        "Sentence",
        "Frequency",
        "FreqSort",
    ),
)

/** A note type with too few fields for Kani to import from. */
internal fun thinOption(): NoteTypeOption =
    NoteTypeOption(name = "Vocab front only", fields = listOf("Front"))

/** The Kiku default mapping, complete and consistent with [kikuOption]. */
internal fun kikuMapping(): FieldMapping = FieldMapping(
    assignments = mapOf(
        FieldRole.EXPRESSION to "Expression",
        FieldRole.READING to "ExpressionReading",
        FieldRole.MEANING to "MainDefinition",
        FieldRole.SENTENCE to "Sentence",
        FieldRole.FREQUENCY to "Frequency",
        FieldRole.FREQUENCY_SORT to "FreqSort",
    ),
)

/** A binding whose sources are chosen, so onboarding is past `CHOOSE_SOURCE`. */
internal fun configuredBinding(): CollectionBinding = CollectionBinding(
    noteType = "Kiku",
    sources = setOf(ImportSource.ACTIVE_CARDS, ImportSource.SUSPENDED_CARDS),
)

/**
 * Composes [content] as though the window were exactly [width] by [height].
 *
 * The same density-scaling technique `:feature-shell`'s harness uses, and for the
 * same reason: each host clamps the test root to a size it will not exceed, so
 * sizing a child `Box` either coerces the window down silently or overflows the
 * root and puts nodes where clicks cannot reach them. Scaling `LocalDensity` is
 * what a hidpi display does, and it leaves `BoxWithConstraints` reporting the
 * intended dp width with every node inside the root.
 */
@Composable
internal fun FixedWindow(
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scale = maxOf(width / maxWidth, height / maxHeight, 1f)
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = density.density / scale,
                fontScale = density.fontScale,
            ),
        ) {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) {
                content()
            }
        }
    }
}

/**
 * Composes [content] at an accessibility font scale.
 *
 * Only `fontScale` changes; `density` is left alone so [FixedWindow]'s dp width still
 * means what it says. This is how a user's system font-size setting reaches a
 * composable, and the layouts that respond to it have no other way to be tested.
 */
@Composable
internal fun ScaledFont(fontScale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density = density.density, fontScale = fontScale),
    ) {
        content()
    }
}

/** The phone window these surfaces are asserted at; the narrowest they must fit. */
internal val HOME_WINDOW_WIDTH: Dp = 411.dp
internal val HOME_WINDOW_HEIGHT: Dp = 891.dp

/**
 * Composes [content] inside the theme at a fixed window size and runs [block].
 *
 * The theme wrapper is not optional: every surface here reads `KaniTheme.colors`,
 * so a composable rendered without it fails on the composition local rather than on
 * whatever the test was about.
 */
@OptIn(ExperimentalTestApi::class)
internal fun renderHome(
    content: @Composable () -> Unit,
    block: ComposeUiTest.() -> Unit,
) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(width = HOME_WINDOW_WIDTH, height = HOME_WINDOW_HEIGHT) {
                    content()
                }
            }
        }
        block()
    }
}

/** The node's visible text, joined, or the empty string when it has none. */
internal fun SemanticsNodeInteraction.textOrEmpty(): String =
    fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }
        .orEmpty()

/**
 * All text in this node's semantics subtree, including its own.
 *
 * A tagged container does not carry the text itself — `Surface` takes the tag and
 * the `Text` lands on a child — so asserting on the container's own `Text` property
 * reads empty even when the card is visibly full of words.
 */
internal fun SemanticsNodeInteraction.subtreeTextOrEmpty(): String {
    fun collect(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::collect)
    return collect(fetchSemanticsNode()).joinToString(" ")
}

/** The node's content description, joined, or the empty string when it has none. */
internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
        .orEmpty()

/**
 * Asserts a node's selected state matches [selected].
 *
 * Compose ships `assertIsSelected`/`assertIsNotSelected` as separate calls, and these
 * assertions loop over rows where selection is a computed boolean. Branching at every
 * call site obscured what was being checked.
 */
internal fun SemanticsNodeInteraction.assertIsSelectedForTest(
    selected: Boolean,
): SemanticsNodeInteraction = if (selected) assertIsSelected() else assertIsNotSelected()

/**
 * Asserts a toggleable node's checked state matches [checked].
 *
 * Distinct from [assertIsSelectedForTest] because the two are different semantics
 * properties, and a filter checkbox carries `ToggleableState` rather than `Selected` —
 * asserting the wrong one fails on a control that is behaving correctly. Selection is
 * "this is the one of several you picked"; toggling is "this is on or off".
 */
internal fun SemanticsNodeInteraction.assertIsToggledForTest(
    checked: Boolean,
): SemanticsNodeInteraction = if (checked) assertIsOn() else assertIsOff()
