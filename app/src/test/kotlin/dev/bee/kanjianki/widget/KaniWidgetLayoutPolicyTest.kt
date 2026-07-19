package dev.bee.kanjianki.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniWidgetLayoutPolicyTest {
    @Test
    fun focusUsesApprovedCompactRegularAndWideResponsiveBreakpoints() {
        assertEquals(
            FocusKanjiLayoutTier.COMPACT,
            focusKanjiLayout(120f, 120f, 1f).tier,
        )
        assertEquals(
            FocusKanjiLayoutTier.COMPACT,
            focusKanjiLayout(180f, 120f, 1f).tier,
        )
        assertEquals(
            FocusKanjiLayoutTier.WIDE,
            focusKanjiLayout(250f, 130f, 1f).tier,
        )
    }

    @Test
    fun accessibilityFontScaleDropsSecondaryContentAcrossTheNewFamily() {
        val quick = quickStudyLayout(180f, 72f, 2f)
        val activity = activityWidgetLayout(ActivityWidgetTier.WIDE, 2f)
        val focus = focusKanjiLayout(250f, 130f, 2f)

        assertFalse(quick.showBrand)
        assertFalse(quick.showSeparateAction)
        assertFalse(activity.showBestStreak)
        assertEquals(FocusKanjiLayoutTier.GLYPH_ONLY, focus.tier)
        assertTrue(focus.glyphFontSp >= 40)
    }

    @Test
    fun focusReadingsAreShownWholeOrOmittedForEachTier() {
        assertEquals("がく", focusVisibleReading("がく", FocusKanjiLayoutTier.COMPACT))
        assertNull(focusVisibleReading("abcdefghijklmnop", FocusKanjiLayoutTier.COMPACT))
        assertNull(focusVisibleReading("がく", FocusKanjiLayoutTier.GLYPH_ONLY))
    }

    @Test
    fun focusActionsMeetTheMinimumAccessibleTextSize() {
        assertTrue(FOCUS_ACTION_FONT_SP >= 13)
    }
}
