package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsChromeLocaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsHeroFactoryUsesJapaneseTitle() {
        withLocale(Locale.JAPAN) {
            val model = settingsAutomationHeroModel(
                current = RecordsSyncModels.Settings.kikuDefaults(),
                reminder = LocalStoreBase.ReminderSettings(false, 8, 5),
                autoSync = LocalStoreBase.AutoSyncSettings(false, false, 7, 30, 0L, 0L, 0L),
                autoUpdate = LocalStoreBase.AutoUpdateStatus(false, 0L, "", "", "", ""),
                notificationsAllowed = true,
            )

            assertEquals("概要", model.cockpitLabel)
            assertEquals("設定", model.title)
        }
    }

    @Test
    fun studyTopBarUsesJapaneseSettingsDescription() {
        withLocale(Locale.JAPAN) {
            composeRule.setContent {
                StudyTopBar(
                    completed = 1,
                    target = 4,
                    fraction = 0.25f,
                    onClose = {},
                    onSettings = {},
                )
            }

            composeRule.onNodeWithContentDescription("設定").assertIsDisplayed()
        }
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
