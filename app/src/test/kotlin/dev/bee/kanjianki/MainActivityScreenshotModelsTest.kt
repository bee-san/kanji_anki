package dev.bee.kanjianki

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityScreenshotModelsTest {
    @Test
    fun screenshotSettingsScreenModelShowsAppearanceThemePanel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_SETTINGS_ROUTE)
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create()
            .start()
            .resume()
            .get()

        val model = screenshotSettingsScreenModel(activity)
        val appearance = model.categories.single { it.sectionKey == "settings-appearance" }
        val referenceData = model.categories.single { it.sectionKey == "settings-reference-data" }

        assertEquals(SettingsTextCopy.settingsAppearanceTitle(), appearance.title)
        assertTrue(appearance.expanded)
        assertTrue(appearance.panels.single() is SettingsThemePanelModel)
        assertFalse(referenceData.expanded)
    }
}
