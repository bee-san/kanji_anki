package dev.bee.kanjianki.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.ui.KaniColors
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniThemeId
import dev.bee.kanjianki.ui.resolvePalette
import java.io.File
import javax.imageio.ImageIO

/**
 * The pixel half of Goal 193's coverage, and the reason it lives in `desktopTest`.
 *
 * `ShellRenderAssertions` is deliberately host-shared, but rasterizing is not:
 * `captureToImage` on the Android host target needs a real window to force a
 * redraw, and under Robolectric it times out in `forceRedraw` before any pixel is
 * produced. Rather than pretend both hosts can do this, the raster assertions run
 * on the one that genuinely composes into a Skia surface, and the *shared*
 * assertions stay in `commonTest` where both hosts really do run them. Android's
 * own pixel coverage is the emulator capture path
 * (`ci/scripts/capture_kani_theme_screenshots.sh`), which renders the shipped app.
 *
 * What these assert is deliberately not a golden-image diff. No image-comparison
 * dependency exists in this build, and adding one would mean new
 * dependency-verification entries plus a reference-image set that has to be
 * regenerated on every font or Skia bump — a maintenance cost that buys very
 * little here, because a diff cannot say *why* it differs. Instead each assertion
 * names the property that would actually be a bug: the raster has the size the
 * window asked for, the palette reached the pixels, high-DPI changes pixel count
 * without changing layout, and the navigation surface is really drawn rather than
 * merely present in the semantics tree.
 *
 * Every capture is also written to `build/reports/shell-raster/` so a human can
 * look at the eight configurations Goal 193 lists. That is the part a screenshot
 * test is genuinely for: catching the thing no assertion was written for.
 */
private const val RASTER_OUTPUT_DIR = "build/reports/shell-raster"

/**
 * A rendered configuration: a window size, a scale factor, and a theme.
 *
 * Named rather than passed as loose numbers so a failure says
 * `DESKTOP_SMALL@2x/DARK` instead of three unlabelled values, and so the set Goal
 * 193 asks for is enumerable in one place.
 */
internal data class RasterConfig(
    val window: ShellWindow,
    val scale: Float = 1f,
    val theme: KaniThemeId = KaniThemeId.GIRLYPOP,
    val isSystemInDarkTheme: Boolean = false,
    val fontScale: Float = 1f,
) {
    /** Pixel width of the surface: dp width times the display scale. */
    val pixelWidth: Int get() = (window.width.value * scale).toInt()

    /** Pixel height of the surface: dp height times the display scale. */
    val pixelHeight: Int get() = (window.height.value * scale).toInt()

    val palette: KaniColors get() = theme.resolvePalette(isSystemInDarkTheme)

    /** A filename-safe label, also used in assertion messages. */
    val label: String
        get() = buildString {
            append(window.name.lowercase())
            if (scale != 1f) append("-${scale.toInt()}x")
            append("-${theme.storageKey}")
            if (isSystemInDarkTheme) append("-hostdark")
            if (fontScale != 1f) append("-font${fontScale}")
        }

    override fun toString(): String = label
}

/**
 * Composes the shell into a real Skia surface of exactly [config]'s pixel size.
 *
 * The density is the scale factor, so a 2x config renders the same dp layout into
 * four times the pixels — which is what a hidpi display does, and the only honest
 * way to cover "high-DPI" without a second machine. This is why the raster tests
 * use `runSkikoComposeUiTest` directly rather than `renderShell`: that harness
 * scales density *down* to fit a fixed 1024x768 root, which is right for semantics
 * (the dp layout decision is preserved) but wrong for pixels (the raster is never
 * the size the window asked for).
 */
@OptIn(ExperimentalTestApi::class)
internal fun rasterizeShell(
    config: RasterConfig,
    state: ShellState = ShellState(),
    backAffordance: ShellBackAffordanceMode = ShellBackAffordanceMode.IN_SHELL,
    routeBody: @Composable (KaniDestination) -> Unit = { },
    block: SkikoComposeUiTest.() -> Unit,
) {
    runSkikoComposeUiTest(
        size = Size(config.pixelWidth.toFloat(), config.pixelHeight.toFloat()),
        density = Density(density = config.scale, fontScale = config.fontScale),
    ) {
        setContent {
            KaniTheme(theme = config.theme, isSystemInDarkTheme = config.isSystemInDarkTheme) {
                KaniShell(
                    state = state,
                    resolver = LiteralUiTextResolver,
                    effectHandler = ShellEffectHandler.NoOp,
                    dispatch = { },
                    fontScale = config.fontScale,
                    backAffordance = backAffordance,
                    content = routeBody,
                )
            }
        }
        block()
    }
}

/** Captures the shell root and writes it out under [config]'s label. */
@OptIn(ExperimentalTestApi::class)
internal fun SkikoComposeUiTest.captureShellRoot(config: RasterConfig): ImageBitmap =
    onNodeWithTag(SHELL_ROOT_TEST_TAG).captureToImage().also { image ->
        writeRaster(image, "${config.label}.png")
    }

/**
 * Writes [image] into the report directory, for human review.
 *
 * Failures are swallowed: these are review artifacts, not assertions, so a
 * read-only or full build directory must not turn a passing render into a red
 * test. The assertions in this file all read the in-memory bitmap.
 */
private fun writeRaster(image: ImageBitmap, fileName: String) {
    runCatching {
        val target = File(RASTER_OUTPUT_DIR, fileName)
        target.parentFile?.mkdirs()
        ImageIO.write(image.toAwtImage(), "png", target)
    }
}

/**
 * How many distinct colors [this] contains.
 *
 * Keyed on the raw `ULong`, not `Color.toArgb()` or `value.toInt()`: the low 32
 * bits of a packed `Color` are its color space, so truncating to `Int` collapses
 * every pixel to one key and the count comes back as 1 no matter what was drawn.
 * That mistake makes this exact assertion pass on a blank surface, which is worse
 * than not having it.
 */
internal fun ImageBitmap.distinctColorCount(): Int {
    val map = toPixelMap()
    val seen = HashSet<ULong>()
    for (y in 0 until map.height) {
        for (x in 0 until map.width) {
            seen += map[x, y].value
        }
    }
    return seen.size
}

/** The color at [x], [y] in this bitmap. */
internal fun ImageBitmap.colorAt(x: Int, y: Int): Color = toPixelMap()[x, y]

/**
 * The color of the shell's own background, sampled where nothing overlays it.
 *
 * The top-*right* pixel, not the top-left, and that is the whole point of naming
 * this rather than inlining a corner: at a rail width the leading edge is the
 * navigation surface, so a top-left sample reads `surface` (or its border) and a
 * background assertion written against it fails on a perfectly correct render. The
 * trailing edge is inside the content region in both placements — right of the rail
 * when there is one, above the bar when there is not — and the content region draws
 * nothing of its own, so what is there is the root background.
 */
internal fun ImageBitmap.shellBackgroundColor(): Color = colorAt(width - 1, 0)

/**
 * True when [this] and [other] are the same color to within 8-bit rounding.
 *
 * An exact comparison is wrong here: the surface is 8-bit sRGB, so a palette color
 * that survives the round trip still differs from the source `Color` in the low
 * bits of each float channel. A tolerance of half a channel step keeps the
 * assertion honest — a genuinely wrong color is off by far more than one step.
 */
internal fun Color.matchesWithin8Bit(other: Color): Boolean {
    val tolerance = 1f / 255f
    return kotlin.math.abs(red - other.red) <= tolerance &&
        kotlin.math.abs(green - other.green) <= tolerance &&
        kotlin.math.abs(blue - other.blue) <= tolerance &&
        kotlin.math.abs(alpha - other.alpha) <= tolerance
}

/** Captures [this] node, writing it out under [name] for review. */
@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.captureAndWrite(name: String): ImageBitmap =
    captureToImage().also { writeRaster(it, "$name.png") }

/**
 * The state most raster configs render: a nested route, with a Study badge.
 *
 * Nested so the back button is in frame, and badged so the badge is. Deliberately
 * *not* on the Study tab: Study is the one destination that paints [KaniColors.bg]'s
 * alternative, so a Study-rooted default would make every background assertion in
 * this file expect `studyBg` and the ordinary background would go uncovered.
 * `studyPaintsItsOwnBackgroundAndEveryOtherTabPaintsTheOrdinaryOne` covers that side
 * explicitly.
 */
internal fun rasterState(badge: Int = 7): ShellState = ShellState(
    backStack = listOf(KaniDestination.Home, KaniDestination.Stats),
    studyBadgeCount = badge,
)
