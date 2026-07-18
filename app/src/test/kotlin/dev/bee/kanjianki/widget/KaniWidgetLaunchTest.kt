package dev.bee.kanjianki.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.MainActivity
import dev.bee.kanjianki.MainActivityBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetLaunchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun dueWidgetReusesExistingTaskAndOpensStudy() {
        val intent = kaniWidgetLaunchIntent(
            context,
            KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 3),
        )

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_STUDY, false))
        assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STATS))
    }

    @Test
    fun idleWidgetReusesExistingTaskWithoutForcingStudy() {
        val intent = kaniWidgetLaunchIntent(
            context,
            KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE),
        )

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STUDY))
        assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STATS))
    }

    @Test
    fun everyUnavailableQuickStateOpensHomeWithoutStudyStatsOrSyncExtras() {
        listOf(
            KaniWidgetState.NOTHING_DUE,
            KaniWidgetState.NOT_SET_UP,
            KaniWidgetState.ERROR,
        ).forEach { state ->
            val intent = kaniWidgetLaunchIntent(context, KaniWidgetSnapshot(state))

            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STUDY))
            assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STATS))
            assertTrue(intent.extras?.keySet().orEmpty().isEmpty())
        }
    }

    @Test
    fun bodyTapOpensHomeWithTaskReuseFlagsAndNeverForcesStudy() {
        val intent = kaniWidgetHomeIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STUDY))
    }

    @Test
    fun bothTapTargetsShareTheDuplicateActivityFixFlags() {
        val homeFlags = kaniWidgetHomeIntent(context).flags
        val studyFlags = kaniWidgetLaunchIntent(
            context,
            KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 1),
        ).flags

        assertEquals(homeFlags, studyFlags)
        assertTrue(homeFlags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(homeFlags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun heatmapTapOpensStatsWithTaskReuseFlags() {
        val intent = kaniWidgetStatsIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_STATS, false))
        assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STUDY))
    }

    @Test
    fun activityHistoryAndEmptyHistoryOpenStatsButUnavailableStatesOpenHome() {
        listOf(ActivityWidgetState.HISTORY, ActivityWidgetState.NO_HISTORY).forEach { state ->
            val intent = kaniActivityLaunchIntent(context, ActivityWidgetSnapshot(state))
            assertTrue(intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_STATS, false))
            assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STUDY))
        }
        listOf(ActivityWidgetState.NOT_SET_UP, ActivityWidgetState.ERROR).forEach { state ->
            val intent = kaniActivityLaunchIntent(context, ActivityWidgetSnapshot(state))
            assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STATS))
            assertFalse(intent.hasExtra(MainActivityBase.EXTRA_OPEN_STUDY))
        }
    }
}
