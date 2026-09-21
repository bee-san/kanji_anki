package dev.bee.kanjianki.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.WidgetTextCopy
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStudyWidgetTest {
    @Test
    fun responsiveSizesCoverTinyCompactAndWideLauncherCells() {
        assertEquals(
            setOf(
                DpSize(56.dp, 56.dp),
                DpSize(120.dp, 56.dp),
                DpSize(180.dp, 72.dp),
            ),
            QuickStudyWidget.RESPONSIVE_SIZES,
        )
        assertEquals(QuickStudyTier.TINY, quickStudyLayout(56f, 56f, 1f).tier)
        assertEquals(QuickStudyTier.COMPACT, quickStudyLayout(120f, 56f, 1f).tier)
        assertEquals(QuickStudyTier.WIDE, quickStudyLayout(180f, 72f, 1f).tier)
        assertEquals(56, quickStudyLayout(120f, 56f, 1f).actionWidthDp)
        assertEquals(72, quickStudyLayout(180f, 72f, 1f).actionWidthDp)
    }

    @Test
    fun compactActionWrapsWhileTinyWideAndAccessibilityLayoutsKeepTheirExistingShape() {
        val tiny = quickStudyLayout(56f, 56f, 1f)
        val compact = quickStudyLayout(120f, 56f, 1f)
        val wide = quickStudyLayout(180f, 72f, 1f)
        val accessibility = quickStudyLayout(120f, 56f, 2f)

        assertEquals(1, tiny.actionMaxLines)
        assertEquals(2, compact.actionMaxLines)
        assertEquals(1, wide.actionMaxLines)
        assertFalse(accessibility.showSeparateAction)
    }

    @Test
    fun duePresentationCapsOnlyTheVisualCountAndKeepsExactAccessibleCount() {
        withLocale(Locale.ENGLISH) {
            val presentation = quickStudyPresentation(
                KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 1_204),
                quickStudyLayout(180f, 72f, 1f),
            )

            assertEquals("999+", presentation.hero)
            assertEquals("Due", presentation.status)
            assertEquals(WidgetTextCopy.studyNowLabel(), presentation.action)
            assertEquals(KaniWidgetDestination.STUDY, presentation.destination)
            assertTrue(presentation.showSeparateAction)
            assertTrue(presentation.contentDescription.contains("1,204 reviews ready"))
            assertFalse(presentation.contentDescription.contains("999+"))
        }
    }

    @Test
    fun tinyDueSurfaceIsOneStudyTargetWithoutASecondNestedAction() {
        val presentation = quickStudyPresentation(
            KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 12),
            quickStudyLayout(56f, 56f, 1f),
        )

        assertEquals(KaniWidgetDestination.STUDY, presentation.destination)
        assertFalse(presentation.showSeparateAction)
        assertEquals("12", presentation.hero)
        assertEquals(WidgetTextCopy.studyLabel(), presentation.status)
    }

    @Test
    fun caughtUpSetupAndErrorAreHonestHomeStatesWithoutStudyAction() {
        withLocale(Locale.ENGLISH) {
            val layout = quickStudyLayout(120f, 56f, 1f)
            val caughtUp = quickStudyPresentation(
                KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE),
                layout,
            )
            val setup = quickStudyPresentation(KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP), layout)
            val error = quickStudyPresentation(KaniWidgetSnapshot(KaniWidgetState.ERROR), layout)

            listOf(caughtUp, setup, error).forEach { presentation ->
                assertEquals(KaniWidgetDestination.HOME, presentation.destination)
                assertNotEquals(WidgetTextCopy.studyNowLabel(), presentation.action)
                assertFalse(presentation.contentDescription.contains(WidgetTextCopy.studyNowLabel()))
            }
            assertEquals("0", caughtUp.hero)
            assertEquals("Caught up", caughtUp.status)
            assertEquals("Set up", setup.status)
            assertEquals("Unavailable", error.status)
            assertNotEquals(setup.contentDescription, error.contentDescription)
        }
    }

    @Test
    fun japaneseQuickStatesAndActionAreLocalized() {
        withLocale(Locale.JAPANESE) {
            val due = quickStudyPresentation(
                KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 12),
                quickStudyLayout(120f, 56f, 1f),
            )
            val caughtUp = quickStudyPresentation(
                KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE),
                quickStudyLayout(120f, 56f, 1f),
            )

            assertEquals("期限", due.status)
            assertEquals("学習", due.action)
            assertTrue(due.contentDescription.contains("復習できるカード12件"))
            assertEquals("復習完了", caughtUp.status)
        }
    }

    @Test
    fun largeFontLayoutDropsBrandBeforeStateOrActionAndUsesFittingHeroType() {
        val normal = quickStudyLayout(180f, 72f, 1f)
        val scaled = quickStudyLayout(180f, 72f, 2f)

        assertTrue(normal.showBrand)
        assertFalse(scaled.showBrand)
        assertTrue(scaled.showStatus)
        assertTrue(scaled.showAction)
        assertTrue(scaled.heroFontSp <= normal.heroFontSp)
        assertTrue(scaled.statusFontSp >= 12)
        assertTrue(scaled.actionFontSp >= 13)
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
