package dev.bee.kanjianki.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.test.assertEquals

/**
 * The theme's rendering assertions, written once and run on both hosts.
 *
 * These are not `@Test` functions, because the two hosts need different JUnit
 * plumbing to compose at all: the desktop JVM renders into a Skia surface
 * directly, while the Android host target needs Robolectric to stand up an
 * Android environment first. Rather than let that plumbing difference turn into
 * two diverging copies of the assertions, each host contributes only a thin test
 * class that calls into these.
 */
internal fun assertMaterialSchemeIsDerivedFromThePaletteForEveryTheme() {
    // This is the mapping a new theme could silently outdate: add a palette,
    // forget the M3 wiring, and every Material component renders in stale
    // colors while KaniTheme.colors looks right. So render each one and read
    // the scheme back out of the composition.
    for (theme in KaniThemeId.entries) {
        for (hostIsDark in listOf(false, true)) {
            val palette = theme.resolvePalette(hostIsDark)
            var observed: ObservedScheme? = null
            renderOnce {
                KaniTheme(theme = theme, isSystemInDarkTheme = hostIsDark) {
                    observed = currentScheme()
                }
            }
            assertEquals(
                ObservedScheme(
                    primary = palette.primary,
                    onPrimary = palette.onPrimary,
                    background = palette.bg,
                    onBackground = palette.ink,
                    surface = palette.surface,
                    onSurface = palette.ink,
                    surfaceVariant = palette.panel,
                    onSurfaceVariant = palette.muted,
                    secondary = palette.teal,
                    tertiary = palette.blue,
                    outline = palette.border,
                    error = palette.coral,
                    local = palette,
                ),
                observed,
                "$theme (hostDark=$hostIsDark) scheme drifted from its palette",
            )
        }
    }
}

internal fun assertAnExplicitPaletteBypassesChoiceResolutionEntirely() {
    // The overload exists so a preview or a screenshot test can render a
    // palette no choice resolves to. If it silently re-resolved to a shipped
    // theme, that would be invisible from the call site.
    val invented = GirlypopKaniColors.copy(primary = Color(0xFF123456))
    var observed: ObservedScheme? = null
    renderOnce {
        KaniTheme(colors = invented) {
            observed = currentScheme()
        }
    }
    assertEquals(Color(0xFF123456), observed?.primary)
    assertEquals(invented, observed?.local)
}

internal fun assertTheDefaultThemeIsWhatAFreshInstallShows() {
    // The composable default and the storage fallback must agree, or a first
    // launch renders one theme and the first settings read reports another.
    var observed: KaniColors? = null
    renderOnce {
        KaniTheme {
            observed = KaniTheme.colors
        }
    }
    assertEquals(GirlypopKaniColors, observed)
    assertEquals(
        GirlypopKaniColors,
        KaniThemeId.fromStorageKey(null).resolvePalette(false),
    )
}

internal fun assertTypographyAndShapesAreAppliedRatherThanLeftAtMaterialDefaults() {
    var typography: Pair<TextStyle, TextStyle>? = null
    var shapes: Triple<CornerBasedShape, CornerBasedShape, CornerBasedShape>? = null
    renderOnce {
        KaniTheme {
            typography = MaterialTheme.typography.headlineLarge to
                MaterialTheme.typography.bodyMedium
            shapes = Triple(
                MaterialTheme.shapes.small,
                MaterialTheme.shapes.medium,
                MaterialTheme.shapes.large,
            )
        }
    }
    assertEquals(KaniTypography.headlineLarge to KaniTypography.bodyMedium, typography)
    assertEquals(
        Triple(
            KaniUiTokens.ButtonShape,
            KaniUiTokens.LeafShape,
            KaniUiTokens.PanelShape,
        ),
        shapes,
    )
}

internal fun assertThePaletteLocalHasAUsableValueWithNoThemeAboveIt() {
    // A component rendered outside KaniTheme — a stray preview, a host that
    // forgot the wrapper — should look plain rather than crash.
    var observed: KaniColors? = null
    renderOnce {
        observed = KaniTheme.colors
    }
    assertEquals(GirlypopKaniColors, observed)
}
