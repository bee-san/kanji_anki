package dev.bee.kanjianki.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the shell puts its navigation at a given window size.
 *
 * A value computed by a pure function rather than a `if (maxWidth >= 840.dp)`
 * inline in the layout, because the same decision now has to be right at eight
 * configurations (phone, tablet, 1280x800, 1440x900, high-DPI, and each of those
 * at a large font scale) and an expression buried in a `BoxWithConstraints` can
 * only be checked by rendering.
 */
enum class ShellNavigationPlacement {
    /** A bar across the bottom, within thumb reach. */
    BOTTOM_BAR,

    /** A vertical rail down the leading edge, for wide windows. */
    SIDE_RAIL,

    /** No navigation at all: an immersive route, or the keyboard is up. */
    HIDDEN,
}

/**
 * The resolved shell layout for one window.
 *
 * [contentMaxWidth] is the readable-measure cap. Kani's content is mostly single
 * column, and a 1440-point-wide window with no cap produces lines nobody can
 * follow — this is why the desktop window does not simply inherit the phone
 * layout stretched.
 */
data class ShellLayout(
    val placement: ShellNavigationPlacement,
    val contentMaxWidth: Dp,
    val contentPadding: Dp,
    /**
     * True when the tab labels must wrap onto a second row.
     *
     * At a large font scale four labelled tabs do not fit across a phone, and
     * the Android bar already handled this by chunking into two rows. Carried
     * over rather than reinvented, because the alternative — truncating the
     * labels — is what it was introduced to avoid.
     */
    val stackNavigationRows: Boolean,
) {
    val showsNavigation: Boolean
        get() = placement != ShellNavigationPlacement.HIDDEN
}

/**
 * Reasons the shell hides navigation entirely.
 *
 * Grouped into one parameter object because the Android shell computed the same
 * thing from four separate booleans (`navActions != null`, `!imeVisible`,
 * `!studyCardKeyboardResident`, `!activeStudySession`), and a caller that forgot
 * one got navigation over the keyboard.
 */
data class ShellImmersion(
    /** A soft keyboard or IME is covering the bottom of the window. */
    val keyboardVisible: Boolean = false,

    /**
     * The visible route asked for the whole window.
     *
     * An active Study session does: the bottom bar next to the rating buttons is
     * both a mis-tap risk and a distraction from the card.
     */
    val routeIsImmersive: Boolean = false,
)

/**
 * Resolves the layout for a window of [windowWidth] at [fontScale].
 *
 * [fontScale] is passed in rather than read from `LocalDensity` so the decision
 * stays pure and every configuration is reachable in a test without a device
 * whose accessibility settings have been changed.
 */
fun resolveShellLayout(
    windowWidth: Dp,
    fontScale: Float = 1f,
    immersion: ShellImmersion = ShellImmersion(),
): ShellLayout {
    val isExpanded = windowWidth >= EXPANDED_WIDTH_BREAKPOINT
    val placement = when {
        immersion.keyboardVisible || immersion.routeIsImmersive ->
            ShellNavigationPlacement.HIDDEN
        isExpanded -> ShellNavigationPlacement.SIDE_RAIL
        else -> ShellNavigationPlacement.BOTTOM_BAR
    }
    return ShellLayout(
        placement = placement,
        // Below the breakpoint the window is already narrower than the cap, so
        // applying it there would be a no-op that only risks under-filling a
        // window at some future size.
        contentMaxWidth = if (isExpanded) CONTENT_MAX_WIDTH else Dp.Unspecified,
        contentPadding = CONTENT_PADDING,
        // A rail lays its tabs out vertically and has room for each label, so the
        // two-row fallback is a bottom-bar concern only.
        stackNavigationRows = placement == ShellNavigationPlacement.BOTTOM_BAR &&
            fontScale >= LARGE_FONT_SCALE,
    )
}

/**
 * The window width at which navigation moves to a rail.
 *
 * 840dp is Material's expanded breakpoint and the value the Android shell already
 * used, so a tablet keeps the layout it has. Every desktop size Kani targets
 * (1280x800 and up) is above it, which is the intent: a desktop window gets the
 * rail, not a stretched phone layout.
 */
val EXPANDED_WIDTH_BREAKPOINT: Dp = 840.dp

/** The readable-measure cap for shell content, from the Android shell. */
val CONTENT_MAX_WIDTH: Dp = 640.dp

private val CONTENT_PADDING: Dp = 18.dp

/** `fontScale` at which four tab labels stop fitting on one row. */
const val LARGE_FONT_SCALE: Float = 1.5f

/**
 * The smallest window the desktop host may be resized to.
 *
 * Not a layout output — the host applies it to the window itself. It is here
 * because the number has to agree with [EXPANDED_WIDTH_BREAKPOINT]-driven layout:
 * the minimum is deliberately *below* the breakpoint, so a user who shrinks the
 * window gets the compact layout rather than a clipped rail. The height is set by
 * the tallest thing the shell must show without scrolling its own chrome away.
 */
val DESKTOP_MINIMUM_WINDOW_WIDTH: Dp = 480.dp

/** Companion to [DESKTOP_MINIMUM_WINDOW_WIDTH]. */
val DESKTOP_MINIMUM_WINDOW_HEIGHT: Dp = 600.dp
