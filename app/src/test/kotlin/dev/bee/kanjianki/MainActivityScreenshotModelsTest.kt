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
        val appearance = model.cards.single { it.routeKey == MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE }
        val referenceData = model.cards.single { it.routeKey == MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE }
        val studyBehavior = model.cards.single { it.routeKey == MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE }
        val automation = model.cards.single { it.routeKey == MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE }

        assertEquals(SettingsTextCopy.settingsAppearanceTitle(), appearance.title)
        assertEquals("1 card", appearance.panelCount)
        assertEquals(SettingsTextCopy.sectionOpenDescription(SettingsTextCopy.settingsAppearanceTitle()), appearance.contentDescription)
        assertEquals(SettingsTextCopy.settingsReferenceDataTitle(), referenceData.title)
        assertEquals("1 card", referenceData.panelCount)
        assertFalse(referenceData.contentDescription.isBlank())
        assertEquals("10 cards", studyBehavior.panelCount)
        assertEquals("4 cards", automation.panelCount)
    }
}
