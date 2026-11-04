package dev.bee.kanjianki.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniThemeId

/**
 * A window size the shell is rendered at, named so failures read as devices.
 *
 * Goal 193 asks for coverage at phone, tablet, and the two desktop window sizes.
 * Naming them here rather than passing raw `Dp` pairs means an assertion message
 * says "PHONE" instead of "411.0.dp x 891.0.dp", and adding a size is one entry
 * rather than a new call at every use site.
 */
internal enum class ShellWindow(val width: Dp, val height: Dp) {
    PHONE(411.dp, 891.dp),
    TABLET(1024.dp, 768.dp),
    DESKTOP_SMALL(1280.dp, 800.dp),
    DESKTOP_LARGE(1440.dp, 900.dp),

    /** The floor the desktop window enforces; must still be usable. */
    DESKTOP_MINIMUM(DESKTOP_MINIMUM_WINDOW_WIDTH, DESKTOP_MINIMUM_WINDOW_HEIGHT),
    ;

    val expectedPlacement: ShellNavigationPlacement
        get() = resolveShellLayout(windowWidth = width).placement
}

/** The tag the test content renders under, to prove the route body was invoked. */
internal const val TEST_ROUTE_BODY_TAG: String = "test-route-body"

/**
 * Composes [content] as though the window were exactly [width] by [height].
 *
 * Sizing a child `Box` cannot do this. Each host gives the test root a fixed size
 * it will not exceed — the desktop root is 1024x768 and Robolectric's is its
 * configured display — so `size` silently coerced every window down to the host's
 * own bounds, and `requiredSize` overflowed the root instead, putting nodes outside
 * the viewport where `assertIsDisplayed` and click injection both fail.
 *
 * Scaling the density is what actually works, and it is not a trick: `Dp` is
 * defined relative to density, so a root of `w` pixels at density `d` *is* a
 * `w / d` dp window. Dividing the density until the logical size fits reproduces
 * exactly what a hidpi display does. `BoxWithConstraints` then reports the intended
 * dp width, the layout decision under test is the real one, and every node stays
 * inside the root and stays clickable.
 *
 * Under plain `size` this was invisible on desktop: 1440.dp coerced to the root's
 * 1024.dp, which is still past `EXPANDED_WIDTH_BREAKPOINT`, so the rail assertions
 * passed at a width they never reached. The Android host, whose display is narrower
 * than the breakpoint, is what exposed it.
 */
@Composable
internal fun FixedWindow(
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // The larger shrink factor wins, so both dimensions fit; never magnify,
        // because a window smaller than the root needs no adjustment.
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
 * Renders [KaniShell] at a fixed logical window size and runs [block] against it.
 *
 * The size comes from [FixedWindow] rather than from configuring the test display,
 * because `BoxWithConstraints` reads its incoming constraints — which makes one set
 * of assertions cover a Robolectric Android host and a Skiko desktop one.
 *
 * [recorded] collects every dispatched action in order. Assertions about
 * navigation and effect acknowledgement read it rather than mutating state,
 * because what the shell owes its host is the action, not the next state — the
 * reducer's job is tested in `:presentation-api`.
 *
 * [recorded] collects every dispatched action in order. Assertions about
 * navigation and effect acknowledgement read it rather than mutating state,
 * because what the shell owes its host is the action, not the next state — the
 * reducer's job is tested in `:presentation-api`.
 */
@OptIn(ExperimentalTestApi::class)
internal fun renderShell(
    state: ShellState,
    window: ShellWindow = ShellWindow.PHONE,
    fontScale: Float = 1f,
    immersion: ShellImmersion = ShellImmersion(),
    backAffordance: ShellBackAffordanceMode = ShellBackAffordanceMode.SYSTEM,
    effectHandler: ShellEffectHandler = ShellEffectHandler.NoOp,
    recorded: MutableList<KaniAction> = mutableListOf(),
    theme: KaniThemeId = KaniThemeId.GIRLYPOP,
    isSystemInDarkTheme: Boolean = false,
    routeBody: @Composable (KaniDestination) -> Unit = { destination ->
        Text(text = destination.route, modifier = Modifier.testTag(TEST_ROUTE_BODY_TAG))
    },
    block: ComposeUiTest.() -> Unit,
) {
    runComposeUiTest {
        setContent {
            KaniTheme(theme = theme, isSystemInDarkTheme = isSystemInDarkTheme) {
                FixedWindow(width = window.width, height = window.height) {
                    KaniShell(
                        state = state,
                        resolver = LiteralUiTextResolver,
                        effectHandler = effectHandler,
                        dispatch = { recorded += it },
                        fontScale = fontScale,
                        immersion = immersion,
                        backAffordance = backAffordance,
                        content = routeBody,
                    )
                }
            }
        }
        block()
    }
}

/** The tag the route-surface tests render their loaded content under. */
internal const val ROUTE_CONTENT_TAG: String = "test-loadable-content"

/**
 * Renders [ShellRouteContent] for one [RouteState] and runs [block] against it.
 *
 * Separate from [renderShell] because the state surfaces are the part a feature
 * composes directly — a route calls `ShellRouteContent` itself and does not go
 * through `KaniShell` to get loading and error handling. Testing it through the
 * whole shell would make a failure here look like a shell failure.
 */
@OptIn(ExperimentalTestApi::class)
internal fun <T> renderRoute(
    state: RouteState<T>,
    recorded: MutableList<KaniAction> = mutableListOf(),
    content: @Composable (T) -> Unit = { value ->
        Text(text = value.toString(), modifier = Modifier.testTag(ROUTE_CONTENT_TAG))
    },
    block: ComposeUiTest.() -> Unit,
) {
    renderComposable(
        content = {
            ShellRouteContent(
                state = state,
                copy = rememberShellCopy(),
                resolver = LiteralUiTextResolver,
                dispatch = { recorded += it },
                content = content,
            )
        },
        block = block,
    )
}

/**
 * Composes [content] inside the theme and runs [block] against it.
 *
 * The theme wrapper is not optional: every shell surface reads `KaniTheme.colors`,
 * so a composable rendered without it fails on the composition local rather than
 * on whatever the test was about.
 */
@OptIn(ExperimentalTestApi::class)
internal fun renderComposable(
    content: @Composable () -> Unit,
    block: ComposeUiTest.() -> Unit,
) {
    runComposeUiTest {
        setContent {
            KaniTheme {
                FixedWindow(
                    width = ShellWindow.PHONE.width,
                    height = ShellWindow.PHONE.height,
                ) {
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
 * A tagged container usually does not carry the text itself: `Surface` puts the
 * tag on the container and the `Text` lands on a child node. Asserting on the
 * container's own `Text` property therefore reads empty even when the panel is
 * visibly full of words, so these assertions walk the subtree instead.
 */
internal fun SemanticsNodeInteraction.subtreeTextOrEmpty(): String {
    fun collect(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::collect)
    return collect(fetchSemanticsNode()).joinToString(" ")
}

/**
 * Queues [effect] on a [ShellState] for a test.
 *
 * `ShellState` has no `enqueue` of its own — effects reach it through the reducer,
 * which is the only thing that should raise one in production. A test that needs a
 * queued effect as a *precondition* would otherwise have to run a whole reducer
 * action to get there, so this reaches into the queue directly and stays in test
 * code.
 */
internal fun ShellState.enqueueForTest(effect: KaniEffect): ShellState =
    copy(effects = effects.enqueue(effect))

/** Shorthand for the resolver-independent text these tests use. */
internal fun literal(text: String): UiText = UiText.Literal(text)

/**
 * Asserts a node's selected state matches [selected].
 *
 * Compose ships `assertIsSelected`/`assertIsNotSelected` as separate calls, and
 * these tests loop over tabs where selection is a computed boolean. Branching at
 * every call site obscured what was being checked.
 */
internal fun SemanticsNodeInteraction.assertIsSelected(
    selected: Boolean,
): SemanticsNodeInteraction = if (selected) assertIsSelected() else assertIsNotSelected()

/**
 * The node's content description, joined, or the empty string when it has none.
 *
 * Reads the semantics directly rather than matching on an expected string, because
 * the assertions are about *whether* a tab is described and whether the
 * description reflects selection — not about the shipped wording, which a
 * translator may change.
 */
internal fun SemanticsNodeInteraction.contentDescriptionOrEmpty(): String =
    fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
        .orEmpty()

/** A recording [ShellEffectHandler], for asserting which platform call an effect reached. */
internal class RecordingEffectHandler : ShellEffectHandler {
    val openedUrls: MutableList<String> = mutableListOf()
    val clipboardWrites: MutableList<String> = mutableListOf()
    val filePickers: MutableList<KaniEffect.PickFile> = mutableListOf()
    val focusRequests: MutableList<String> = mutableListOf()

    override fun openUrl(url: String) {
        openedUrls += url
    }

    override fun copyToClipboard(text: String) {
        clipboardWrites += text
    }

    override fun pickFile(purpose: KaniEffect.PickFile) {
        filePickers += purpose
    }

    override fun requestFocus(target: String) {
        focusRequests += target
    }
}
