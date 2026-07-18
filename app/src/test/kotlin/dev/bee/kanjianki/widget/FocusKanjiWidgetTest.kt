package dev.bee.kanjianki.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.layout.HeightModifier
import androidx.glance.layout.WidthModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.unit.Dimension
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.WidgetTextCopy
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusKanjiWidgetTest {
    private lateinit var context: Context
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun visibleMeaningTruncatesAtWordAndGraphemeBoundaries() {
        assertEquals(
            "learn safely",
            focusVisibleMeaning("learn safely always", FocusKanjiLayoutTier.WIDE),
        )
        assertEquals(
            "123456",
            focusVisibleMeaning("123456👩‍🔬suffix", FocusKanjiLayoutTier.COMPACT),
        )
    }

    @Test
    fun readingIsShownWholeOrOmittedAtMinimumTierWidths() {
        assertEquals("がく", focusVisibleReading("がく", FocusKanjiLayoutTier.COMPACT))
        assertNull(focusVisibleReading("123456789", FocusKanjiLayoutTier.COMPACT))
        assertNull(focusVisibleReading("123456789", FocusKanjiLayoutTier.WIDE))
    }

    @Test
    fun narrowReadyFocusCentersGlyphAndKeepsHiddenFactsAccessible() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = readySnapshot(isDueNow = true)
        val description = WidgetTextCopy.focusKanjiDescription("学", "learn", "がく", true)
        setContext(context)
        setAppWidgetSize(DpSize(110.dp, 120.dp))
        provideComposable { FocusKanjiWidgetContent(snapshot) }

        onNode(hasTextEqualTo("学")).assertExists()
        onNode(hasTextEqualTo("learn")).assertDoesNotExist()
        onNode(hasTextEqualTo("がく")).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetTextCopy.focusDetailsLabel())).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetTextCopy.studyNowLabel())).assertDoesNotExist()
        onNode(hasContentDescriptionEqualTo(description))
            .assertExists()
            .assertHasStartActivityClickAction(kaniFocusDetailIntent(context, "学"))
    }

    @Test
    fun compactFocusShowsExactFactsAndUsesOneWholeCardDetailsTarget() = runGlanceAppWidgetUnitTest(30.seconds) {
        setContext(context)
        setAppWidgetSize(DpSize(120.dp, 120.dp))
        provideComposable {
            FocusKanjiWidgetContent(readySnapshot(isDueNow = true))
        }

        onNode(hasTextEqualTo("学")).assertExists()
        onNode(hasTextEqualTo("learn")).assertExists()
        onNode(hasTextEqualTo("がく")).assertExists()
        onNode(hasTextEqualTo("Due now")).assertExists()
        onNode(hasTextEqualTo("Details")).assertDoesNotExist()
        onNode(hasTextEqualTo("Study now")).assertDoesNotExist()
        onNode(hasContentDescriptionEqualTo(WidgetTextCopy.focusKanjiDescription("学", "learn", "がく", true)))
            .assertHasStartActivityClickAction(kaniFocusDetailIntent(context, "学"))
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun wideDueFocusShowsEvidenceBackedFactsAndSeparateActions() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = readySnapshot(isDueNow = true)
        val description = WidgetTextCopy.focusKanjiDescription("学", "learn", "がく", true)
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 130.dp))
        provideComposable { FocusKanjiWidgetContent(snapshot) }

        onNode(hasTextEqualTo("学")).assertExists()
        onNode(hasTextEqualTo("learn")).assertExists()
        onNode(hasTextEqualTo("がく")).assertExists()
        onNode(hasTextEqualTo(WidgetTextCopy.focusDueStatus())).assertExists()
        onNode(
            hasAnyDescendant(hasTextEqualTo(WidgetTextCopy.focusDetailsLabel())) and
                hasClickAction() and
                hasMinimumTapTarget(48.dp),
        )
            .assertExists()
            .assertHasStartActivityClickAction(kaniFocusDetailIntent(context, "学"))
        onNode(
            hasAnyDescendant(hasTextEqualTo(WidgetTextCopy.studyNowLabel())) and
                hasClickAction() and
                hasMinimumTapTarget(48.dp),
        )
            .assertExists()
            .assertHasStartActivityClickAction(
                kaniWidgetLaunchIntent(
                    context,
                    KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 1),
                ),
            )
        onNode(hasContentDescriptionEqualTo(description)).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun wideNotDueFocusOmitsDueStatusAndStudyAction() = runGlanceAppWidgetUnitTest(30.seconds) {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 130.dp))
        provideComposable { FocusKanjiWidgetContent(readySnapshot(isDueNow = false)) }

        onNode(hasTextEqualTo(WidgetTextCopy.focusDueStatus())).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetTextCopy.studyNowLabel())).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetTextCopy.focusDetailsLabel())).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun emptyFocusUsesFocusSpecificFallbackCopy() = runGlanceAppWidgetUnitTest(30.seconds) {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 130.dp))
        provideComposable {
            FocusKanjiWidgetContent(
                FocusKanjiWidgetSnapshot(state = FocusKanjiWidgetState.EMPTY),
            )
        }

        onNode(hasTextEqualTo(WidgetTextCopy.focusEmptyTitle())).assertExists()
        onNode(hasTextEqualTo(WidgetTextCopy.focusEmptyBody())).assertExists()
        onNode(hasTextEqualTo(WidgetTextCopy.openKaniLabel())).assertExists()
    }

    @Test
    fun largeFontScaleCompressesFocusInsteadOfClippingWideFacts() {
        val normal = focusKanjiLayout(widthDp = 250f, heightDp = 130f, fontScale = 1f)
        val moderate = focusKanjiLayout(widthDp = 250f, heightDp = 130f, fontScale = 1.3f)
        val largeFont = focusKanjiLayout(widthDp = 250f, heightDp = 130f, fontScale = 2f)

        assertTrue(normal.isWide)
        assertTrue(moderate.isWide)
        assertFalse(largeFont.isWide)
        assertTrue(largeFont.glyphFontSp < normal.glyphFontSp)
    }

    private fun readySnapshot(isDueNow: Boolean) = FocusKanjiWidgetSnapshot(
        state = FocusKanjiWidgetState.READY,
        kanji = "学",
        primaryMeaning = "learn",
        readings = "がく",
        isDueNow = isDueNow,
    )

    private fun hasMinimumTapTarget(minimum: androidx.compose.ui.unit.Dp) =
        GlanceNodeMatcher<MappedNode>("has minimum ${minimum.value}dp tap target") { node ->
            var width: androidx.compose.ui.unit.Dp? = null
            var height: androidx.compose.ui.unit.Dp? = null
            node.value.emittable.modifier.foldIn(Unit) { _, element ->
                when (element) {
                    is WidthModifier -> width = (element.width as? Dimension.Dp)?.dp
                    is HeightModifier -> height = (element.height as? Dimension.Dp)?.dp
                }
            }
            val actualWidth = width
            val actualHeight = height
            actualWidth != null && actualHeight != null &&
                actualWidth >= minimum && actualHeight >= minimum
        }
}
