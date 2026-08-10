package dev.bee.kanjianki.data.desktop

/**
 * Decides where the desktop window opens, and whether the geometry a session
 * ended with is safe to remember. Pure policy: the caller supplies the stored
 * geometry and the set of screens currently attached (both gathered by platform
 * I/O — `GraphicsEnvironment` on the desktop), and this returns a placement to
 * open at or a decision that geometry must not be persisted.
 *
 * Two failures are the whole reason this is not inline in the window:
 *
 *  - **A monitor went away.** Bounds saved on a second monitor that is now
 *    unplugged land the window entirely off every screen, where it cannot be
 *    dragged back. [restore] detects that the stored rectangle no longer
 *    overlaps any screen and re-centres on the primary one instead.
 *  - **A degenerate size was saved.** A zero/negative or sub-minimum size — from
 *    a crash mid-resize, or a placement the toolkit reported oddly — must never
 *    be written back, or the next launch opens an unusable sliver. [capture]
 *    refuses to persist anything that would not itself pass [restore]'s
 *    reachability and minimum-size checks. That is the "save only after bounds
 *    validation" rule: validation happens here, before the store is touched.
 *
 * The minimum size mirrors the shared shell's `DESKTOP_MINIMUM_WINDOW_WIDTH` /
 * `_HEIGHT` (480×600) — below that the shell's navigation is not shown to be
 * usable. It is duplicated as a plain `Int` rather than referenced because this
 * module has no Compose dependency and so cannot name a `Dp`.
 * `DesktopWindowBoundsPolicyMinimumTest` in `:desktop-app` — the only module that
 * depends on both — pins the two together so they cannot drift.
 */
object DesktopWindowBoundsPolicy {
    /** Below this the shared shell's navigation has no usable-layout coverage. */
    const val MIN_WIDTH = 480
    const val MIN_HEIGHT = 600

    /** First-run size when nothing is stored; matches the window's initial state. */
    const val DEFAULT_WIDTH = 1280
    const val DEFAULT_HEIGHT = 800

    /**
     * A window counts as reachable only if at least this much of it overlaps some
     * screen, so the title bar can still be grabbed. Wide enough to expose the
     * window controls, short enough to accept a window nudged mostly off the
     * bottom edge.
     */
    const val MIN_VISIBLE_WIDTH = 240
    const val MIN_VISIBLE_HEIGHT = 48

    /** One attached screen's usable area, in the toolkit's virtual coordinates. */
    data class ScreenRect(val x: Int, val y: Int, val width: Int, val height: Int) {
        init {
            require(width > 0 && height > 0) {
                "A screen must have a positive size, was ${width}x$height"
            }
        }

        val right: Int get() = x + width
        val bottom: Int get() = y + height
    }

    /** A concrete window rectangle to open at. */
    data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Geometry as it was last persisted. Every field is nullable because a fresh
     * profile has none of it, and a partially-written store (older Kani, manual
     * edit) must still resolve to something sane.
     */
    data class StoredWindow(
        val x: Int?,
        val y: Int?,
        val width: Int?,
        val height: Int?,
        val maximized: Boolean,
    )

    /** Where and how to open: the floating bounds plus whether to maximise. */
    data class Placement(val bounds: WindowBounds, val maximized: Boolean)

    /**
     * Resolves the placement to open at.
     *
     * With no screens reported the host is in a state this policy cannot reason
     * about (headless, or between display changes); it returns the default size at
     * the origin and leaves maximise off, which the window can still open with.
     *
     * Otherwise the size is taken from [stored] (falling back to the default),
     * raised to the minimum, and capped to the largest single screen so it fits
     * somewhere. The position is honoured only if the resulting rectangle is
     * [reachable]; if not — the common "unplugged monitor" case, or no stored
     * position at all — the window is centred on the primary (first) screen.
     */
    fun restore(stored: StoredWindow, screens: List<ScreenRect>): Placement {
        val primary = screens.firstOrNull()
            ?: return Placement(
                WindowBounds(0, 0, DEFAULT_WIDTH, DEFAULT_HEIGHT),
                maximized = false,
            )

        val widthCap = maxOf(MIN_WIDTH, screens.maxOf { it.width })
        val heightCap = maxOf(MIN_HEIGHT, screens.maxOf { it.height })
        val width = (stored.width ?: DEFAULT_WIDTH).coerceIn(MIN_WIDTH, widthCap)
        val height = (stored.height ?: DEFAULT_HEIGHT).coerceIn(MIN_HEIGHT, heightCap)

        if (stored.x != null && stored.y != null) {
            val candidate = WindowBounds(stored.x, stored.y, width, height)
            if (reachable(candidate, screens)) {
                return Placement(candidate, stored.maximized)
            }
        }
        return Placement(centeredOn(primary, width, height), stored.maximized)
    }

    /**
     * Validates [current] and returns what to persist, or `null` to persist
     * nothing and keep the previous value.
     *
     * Nothing below the minimum size or unreachable is ever written back, so a
     * bad frame at shutdown cannot poison the next launch. [maximized] is carried
     * through unchanged: the caller passes the *restore* bounds (the size to
     * return to when un-maximised) alongside the maximised flag, so a maximised
     * session still remembers a usable floating size.
     */
    fun capture(
        current: WindowBounds,
        maximized: Boolean,
        screens: List<ScreenRect>,
    ): StoredWindow? {
        if (current.width < MIN_WIDTH || current.height < MIN_HEIGHT) return null
        if (!reachable(current, screens)) return null
        return StoredWindow(
            x = current.x,
            y = current.y,
            width = current.width,
            height = current.height,
            maximized = maximized,
        )
    }

    /** True when some screen shows at least the minimum grabbable slice of [bounds]. */
    private fun reachable(bounds: WindowBounds, screens: List<ScreenRect>): Boolean =
        screens.any { screen ->
            val overlapWidth = minOf(bounds.x + bounds.width, screen.right) - maxOf(bounds.x, screen.x)
            val overlapHeight = minOf(bounds.y + bounds.height, screen.bottom) - maxOf(bounds.y, screen.y)
            overlapWidth >= MIN_VISIBLE_WIDTH && overlapHeight >= MIN_VISIBLE_HEIGHT
        }

    private fun centeredOn(screen: ScreenRect, width: Int, height: Int): WindowBounds {
        val fittedWidth = width.coerceAtMost(screen.width)
        val fittedHeight = height.coerceAtMost(screen.height)
        return WindowBounds(
            x = screen.x + (screen.width - fittedWidth) / 2,
            y = screen.y + (screen.height - fittedHeight) / 2,
            width = fittedWidth,
            height = fittedHeight,
        )
    }
}
