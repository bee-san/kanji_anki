package dev.bee.kanjianki.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.Dp
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetContentTest {
    private lateinit var context: Context
    private lateinit var originalLocale: Locale
    private var originalFontScale: Float = 1f

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalLocale = Locale.getDefault()
        originalFontScale = context.resources.configuration.fontScale
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        setFontScale(originalFontScale)
    }

    @Test
    fun compactDueOverviewUsesSiblingHomeAndStudyTargets() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 3)
        val copy = widgetCopy(snapshot, isExpanded = false)
        val description = WidgetTextCopy.widgetDescription(copy.title, copy.body)
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 72.dp))
        provideComposable { KaniWidgetContent(snapshot) }

        onNode(hasContentDescriptionEqualTo(description))
            .assertHasStartActivityClickAction(kaniWidgetHomeIntent(context))
        onNode(hasAnyDescendant(hasTextEqualTo(copy.title)) and hasClickAction())
            .assertHasStartActivityClickAction(kaniWidgetHomeIntent(context))
        onNode(
            hasAnyDescendant(hasTextEqualTo(copy.action)) and
                hasClickAction() and
                hasMinimumTapTarget(48.dp),
        ).assertHasStartActivityClickAction(kaniWidgetLaunchIntent(context, snapshot))
        onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun expandedOverviewDropsSecondaryAndTertiaryLinesAtTwoXFontScale() =
        runGlanceAppWidgetUnitTest(30.seconds) {
            setFontScale(2f)
            val snapshot = KaniWidgetSnapshot(
                state = KaniWidgetState.DUE_NOW,
                dueCount = 3,
                streakDays = 2,
                dueLaterCount = 4,
                dueLaterByMillis = 1_800_000L,
            )
            val copy = widgetCopy(snapshot, isExpanded = true)
            setContext(context)
            setAppWidgetSize(DpSize(250.dp, 130.dp))
            provideComposable { KaniWidgetContent(snapshot) }

            onNode(hasTextEqualTo(WidgetTextCopy.visualCountLabel(snapshot.dueCount))).assertExists()
            onNode(hasTextEqualTo(copy.title)).assertDoesNotExist()
            onNode(hasTextEqualTo(WidgetTextCopy.studyLabel())).assertExists()
            onNode(hasTextEqualTo(copy.action)).assertDoesNotExist()
            onNode(hasTextEqualTo(WidgetTextCopy.appName())).assertDoesNotExist()
            onNode(hasTextEqualTo(copy.body)).assertDoesNotExist()
            onNode(hasTextEqualTo(copy.extraLine)).assertDoesNotExist()
        }

    @Test
    fun sevenDayActivityStripFitsTheNarrowExpandedOverviewColumn() {
        val metrics = overviewActivityStripMetrics()

        assertEquals(9, metrics.cellSizeDp)
        assertEquals(2, metrics.gapDp)
        assertEquals(75, metrics.widthDp(dayCount = 7))
        assertTrue(metrics.widthDp(dayCount = 7) <= 80)
    }

    @Test
    fun overviewStudyActionHasRoomForItsCompleteLabel() {
        val metrics = overviewActionMetrics()

        assertTrue(metrics.widthDp >= 80)
        assertTrue(metrics.heightDp >= 56)
        assertTrue(metrics.fontSp >= 13)
    }

    @Test
    fun widenedOverviewKeepsItsTextAndActivityContent() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = KaniWidgetSnapshot(
            state = KaniWidgetState.DUE_NOW,
            dueCount = 3,
            streakDays = 2,
            dueLaterCount = 4,
            dueLaterByMillis = 1_800_000L,
            last7DayCounts = listOf(1, 0, 2, 0, 3, 1, 4),
        )
        val copy = widgetCopy(snapshot, isExpanded = true)
        setContext(context)
        setAppWidgetSize(KaniWidget.WIDE_SIZE)
        provideComposable { KaniWidgetContent(snapshot) }

        onNode(hasTextEqualTo(copy.title)).assertExists()
        onNode(hasTextEqualTo(copy.body)).assertExists()
        onNode(hasTextEqualTo(copy.extraLine)).assertExists()
        onNode(hasTextEqualTo(WidgetTextCopy.appName())).assertExists()
        onNode(hasTextEqualTo(copy.action)).assertExists()
        val stripMetrics = overviewActivityStripMetrics()
        onAllNodes(hasExactSize(stripMetrics.cellSizeDp.dp)).assertCountEquals(7)
    }

    @Suppress("DEPRECATION")
    private fun setFontScale(fontScale: Float) {
        val configuration = Configuration(context.resources.configuration).apply {
            this.fontScale = fontScale
        }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    private fun hasExactSize(size: Dp) =
        GlanceNodeMatcher<MappedNode>("has exact ${size.value}dp size") { node ->
            var width: Dp? = null
            var height: Dp? = null
            node.value.emittable.modifier.foldIn(Unit) { _, element ->
                when (element) {
                    is WidthModifier -> width = (element.width as? Dimension.Dp)?.dp
                    is HeightModifier -> height = (element.height as? Dimension.Dp)?.dp
                }
            }
            width == size && height == size
        }

    private fun hasMinimumTapTarget(minimum: Dp) =
        GlanceNodeMatcher<MappedNode>("has minimum ${minimum.value}dp tap target") { node ->
            var width: Dp? = null
            var height: Dp? = null
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
