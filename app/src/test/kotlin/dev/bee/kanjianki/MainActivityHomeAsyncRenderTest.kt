package dev.bee.kanjianki

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityHomeAsyncRenderTest {
    @Test
    fun renderHomeShowsLoadingScreenBeforeBackgroundLoadCompletes() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val mainTasks = ArrayDeque<Runnable>()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "async-home-loading")
        )
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            }
            val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .get()

            activity.cancelPendingHomeRouteLoads()
            activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            replaceLazyDelegate(
                activity,
                "statsPrecomputeScheduler",
                StatsPrecomputeScheduler(
                    background = Executor { },
                    isFresh = { true },
                    refresh = { },
                ),
            )
            replaceLazyDelegate(
                activity,
                "asyncHomeRouteLoader",
                AsyncHomeRouteLoader(
                    background = Executor { backgroundTasks.addLast(it) },
                    postToMain = { mainTasks.addLast(it) },
                ),
            )

            activity.renderHome()
            shadowOf(Looper.getMainLooper()).idle()

            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
            assertEquals(1, backgroundTasks.size)
            assertTrue(mainTasks.isEmpty())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun renderHomeQueuesHomeLoadAheadOfStatsPrecompute() {
        val backgroundTasks = ArrayDeque<Runnable>()
        val precomputeTasks = ArrayDeque<Runnable>()
        val scheduledOrder = mutableListOf<String>()
        val mainTasks = ArrayDeque<Runnable>()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(
            AnkiDroidGateway.testProvider(context, "async-home-order")
        )
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            }
            val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .get()

            activity.cancelPendingHomeRouteLoads()
            activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            replaceLazyDelegate(
                activity,
                "statsPrecomputeScheduler",
                StatsPrecomputeScheduler(
                    background = Executor {
                        scheduledOrder.add("stats-precompute")
                        precomputeTasks.addLast(it)
                    },
                    isFresh = { false },
                    refresh = { },
                ),
            )
            replaceLazyDelegate(
                activity,
                "asyncHomeRouteLoader",
                AsyncHomeRouteLoader(
                    background = Executor {
                        scheduledOrder.add("home-load")
                        backgroundTasks.addLast(it)
                    },
                    postToMain = { mainTasks.addLast(it) },
                ),
            )

            activity.renderHome()
            shadowOf(Looper.getMainLooper()).idle()

            val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(contentRoot.childCount > 0)
            assertEquals(listOf("home-load", "stats-precompute"), scheduledOrder)
            assertEquals(1, backgroundTasks.size)
            assertEquals(1, precomputeTasks.size)
            assertTrue(mainTasks.isEmpty())
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun replaceLazyDelegate(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityHome::class.java.getDeclaredField("$propertyName\$delegate")
        field.isAccessible = true
        field.set(activity, lazyOf(value))
    }

    private fun containsText(view: View?, expected: String): Boolean {
        if (view == null || view.visibility != View.VISIBLE) {
            return false
        }
        if (view is TextView && view.text?.toString() == expected) {
            return true
        }
        if (containsAccessibilityText(view.createAccessibilityNodeInfo(), expected)) {
            return true
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (containsText(view.getChildAt(index), expected)) {
                    return true
                }
            }
        }
        return false
    }

    private fun containsAccessibilityText(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) {
            return false
        }
        val text = node.text?.toString()
        if (text == expected) {
            return true
        }
        val description = node.contentDescription?.toString()
        if (description == expected) {
            return true
        }
        for (index in 0 until node.childCount) {
            if (containsAccessibilityText(node.getChild(index), expected)) {
                return true
            }
        }
        return false
    }
}
