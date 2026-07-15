package dev.bee.kanjianki.widget

import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KaniWidgetInstanceOptionsTest {

    @Test
    fun missingValuesFallBackToZeroConfigDefault() {
        val options = KaniWidgetInstanceOptions.fromStorageValues(null, null)

        assertEquals(KaniWidgetStyle.DUE_CARD, options.style)
        assertNull(options.themeOverride)
    }

    @Test
    fun unknownValuesFallBackToZeroConfigDefault() {
        val options = KaniWidgetInstanceOptions.fromStorageValues("no_such_style", "no_such_theme")

        assertEquals(KaniWidgetStyle.DUE_CARD, options.style)
        assertNull(options.themeOverride)
    }

    @Test
    fun followAppMarkerMeansNoThemeOverride() {
        val options = KaniWidgetInstanceOptions.fromStorageValues(
            KaniWidgetStyle.HEATMAP.storageKey,
            KaniWidgetInstanceOptions.THEME_FOLLOW_APP,
        )

        assertEquals(KaniWidgetStyle.HEATMAP, options.style)
        assertNull(options.themeOverride)
        assertEquals(KaniThemeChoice.DARK, options.resolveTheme(KaniThemeChoice.DARK))
    }

    @Test
    fun storedThemeOverrideWinsOverAppTheme() {
        val options = KaniWidgetInstanceOptions.fromStorageValues(
            KaniWidgetStyle.HEATMAP.storageKey,
            KaniThemeChoice.AUTUMN.storageKey,
        )

        assertEquals(KaniWidgetStyle.HEATMAP, options.style)
        assertEquals(KaniThemeChoice.AUTUMN, options.themeOverride)
        assertEquals(KaniThemeChoice.AUTUMN, options.resolveTheme(KaniThemeChoice.DARK))
    }

    @Test
    fun storageValuesRoundTripThroughParsing() {
        for (style in KaniWidgetStyle.entries) {
            for (theme in listOf<KaniThemeChoice?>(null) + KaniThemeChoice.entries) {
                val original = KaniWidgetInstanceOptions(style, theme)

                val reparsed = KaniWidgetInstanceOptions.fromStorageValues(
                    original.style.storageKey,
                    original.themeStorageValue(),
                )

                assertEquals(original, reparsed)
            }
        }
    }
}
