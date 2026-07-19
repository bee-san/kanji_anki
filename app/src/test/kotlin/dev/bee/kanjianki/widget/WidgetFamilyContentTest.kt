package dev.bee.kanjianki.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetFamilyContentTest {
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
    fun wideQuickStudyCardUsesOneWholeSurfaceStudyTarget() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 12)
        val presentation = quickStudyPresentation(snapshot, quickStudyLayout(180f, 72f, 1f))
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 72.dp))
        provideComposable { QuickStudyWidgetContent(snapshot) }

        onNode(hasContentDescriptionEqualTo(presentation.contentDescription))
            .assertHasStartActivityClickAction(kaniWidgetLaunchIntent(context, snapshot))
        onNode(hasTextEqualTo(presentation.action)).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun tinyCaughtUpQuickStudyCardUsesOneWholeSurfaceHomeTarget() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE)
        val presentation = quickStudyPresentation(snapshot, quickStudyLayout(56f, 56f, 1f))
        setContext(context)
        setAppWidgetSize(DpSize(56.dp, 56.dp))
        provideComposable { QuickStudyWidgetContent(snapshot) }

        onNode(hasContentDescriptionEqualTo(presentation.contentDescription))
            .assertHasStartActivityClickAction(kaniWidgetHomeIntent(context))
        onNode(hasTextEqualTo(presentation.hero)).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun wideActivityCardRendersHistoryAndUsesOneStatsTarget() = runGlanceAppWidgetUnitTest(30.seconds) {
        val snapshot = ActivityWidgetSnapshot(
            state = ActivityWidgetState.HISTORY,
            last35DayCounts = (1..35).map { it % 5 },
            streakDays = 4,
            reviewsToday = 3,
            last7DayTotal = 17,
            last35DayTotal = 81,
            bestStreakDays = 12,
        )
        val presentation = activityWidgetPresentation(snapshot, ActivityWidgetTier.WIDE)
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 130.dp))
        provideComposable { ActivityWidgetContent(snapshot) }

        onNode(hasContentDescriptionEqualTo(presentation.contentDescription))
            .assertHasStartActivityClickAction(kaniWidgetStatsIntent(context))
        onNode(hasTextEqualTo(presentation.title)).assertExists()
        val visibleCopy = activityWidgetVisibleCopy(
            snapshot,
            presentation,
            activityWidgetLayout(ActivityWidgetTier.WIDE, 1f),
        )
        onNode(hasTextEqualTo(visibleCopy.action)).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun compactUnavailableActivityCardUsesOneHomeTargetWithoutHistory() =
        runGlanceAppWidgetUnitTest(30.seconds) {
            val snapshot = ActivityWidgetSnapshot(ActivityWidgetState.NOT_SET_UP)
            val presentation = activityWidgetPresentation(snapshot, ActivityWidgetTier.COMPACT)
            setContext(context)
            setAppWidgetSize(DpSize(120.dp, 72.dp))
            provideComposable { ActivityWidgetContent(snapshot) }

            onNode(hasContentDescriptionEqualTo(presentation.contentDescription))
                .assertHasStartActivityClickAction(kaniWidgetHomeIntent(context))
            onNode(hasTextEqualTo(presentation.title)).assertExists()
            onNode(hasTextEqualTo(presentation.action)).assertExists()
            onAllNodes(hasClickAction()).assertCountEquals(1)
        }
}
