package dev.bee.kanjianki.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetConfigActivityTest {

    @Test
    fun missingAppWidgetIdFinishesWithCanceledResult() {
        val activity = Robolectric.buildActivity(KaniWidgetConfigActivity::class.java)
            .create()
            .get()

        assertTrue(activity.isFinishing)
        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
        assertEquals(
            AppWidgetManager.INVALID_APPWIDGET_ID,
            shadow.resultIntent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
    }

    @Test
    fun validAppWidgetIdKeepsConfigScreenOpenWithCanceledDefault() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            KaniWidgetConfigActivity::class.java,
        ).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42)

        val activity = Robolectric.buildActivity(KaniWidgetConfigActivity::class.java, intent)
            .create()
            .get()

        assertEquals(false, activity.isFinishing)
        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
        assertEquals(
            42,
            shadow.resultIntent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ),
        )
    }
}
