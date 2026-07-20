package dev.bee.kanjianki.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.KaniThemeChoice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetConfigActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var appWidgetManager: AppWidgetManager

    @Before
    fun setUp() {
        appWidgetManager = AppWidgetManager.getInstance(context)
    }

    @After
    fun tearDown() {
        ShadowAppWidgetManager.reset()
    }

    @Test
    fun missingAppWidgetIdFinishesWithCanceledResult() {
        val activity = Robolectric.buildActivity(KaniWidgetConfigActivity::class.java)
            .create()
            .get()

        assertCanceledAndFinishing(activity, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    @Test
    fun canonicalPlacedWidgetKeepsConfigScreenOpenWithCanceledDefault() {
        bindWidget(42, KaniWidgetReceiver::class.java)
        val activity = buildActivity(42)

        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.isFinishing)
        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
        assertEquals(42, shadow.resultIntent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ))
    }

    @Test
    fun foreignProviderAppWidgetIdIsRejected() {
        bindWidget(43, ActivityWidgetReceiver::class.java)
        val activity = buildActivity(43)

        shadowOf(Looper.getMainLooper()).idle()

        assertCanceledAndFinishing(activity, 43)
    }

    @Test
    fun deletedAppWidgetIdIsRejected() {
        val activity = buildActivity(44)

        shadowOf(Looper.getMainLooper()).idle()

        assertCanceledAndFinishing(activity, 44)
    }

    @Test
    fun reconfigureLoadsSavedLegacyValuesWithoutMigration() = runBlocking {
        bindWidget(45, KaniWidgetReceiver::class.java)
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(45)
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[stringPreferencesKey(KaniWidgetInstanceOptions.STYLE_PREF_KEY)] =
                KaniWidgetStyle.HEATMAP.storageKey
            prefs[stringPreferencesKey(KaniWidgetInstanceOptions.THEME_OVERRIDE_PREF_KEY)] =
                KaniThemeChoice.AUTUMN.storageKey
        }

        val loaded = loadKaniWidgetInstanceOptions(context, 45)

        assertEquals(
            KaniWidgetInstanceOptions(KaniWidgetStyle.HEATMAP, KaniThemeChoice.AUTUMN),
            loaded,
        )
    }

    @Test
    fun newProviderIdCannotReadOrCreateLegacyConfigState() = runBlocking {
        bindWidget(46, QuickStudyWidgetReceiver::class.java)

        assertEquals(null, loadKaniWidgetInstanceOptions(context, 46))
    }

    @Suppress("DEPRECATION")
    private fun bindWidget(id: Int, receiverClass: Class<*>) {
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, receiverClass)
        }
        shadowOf(appWidgetManager).putWidgetInfo(id, info)
    }

    private fun buildActivity(id: Int): KaniWidgetConfigActivity {
        val intent = Intent(context, KaniWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return Robolectric.buildActivity(KaniWidgetConfigActivity::class.java, intent)
            .create()
            .get()
    }

    private fun assertCanceledAndFinishing(activity: KaniWidgetConfigActivity, expectedId: Int) {
        assertTrue(activity.isFinishing)
        val shadow = shadowOf(activity)
        assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)
        assertEquals(expectedId, shadow.resultIntent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ))
    }
}
