package dev.bee.kanjianki.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.WidgetTextCopy
import dev.bee.kanjianki.core.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetConfigScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freshScreenHasNoLegacyHeatmapChoiceAndSavesDueFollowingAppTheme() {
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(
                initialOptions = KaniWidgetInstanceOptions(),
                onSave = { saved = it },
            )
        }

        composeRule.onNodeWithText(WidgetTextCopy.widgetConfigTitle()).assertIsDisplayed()
        composeRule.onNodeWithTag("widget_style_heatmap").assertDoesNotExist()
        composeRule.onNodeWithTag("widget_style_due_card").assertDoesNotExist()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(KaniWidgetStyle.DUE_CARD, saved?.style)
        assertNull(saved?.themeOverride)
    }

    @Test
    fun legacyHeatmapDefaultsToKeepAndRoundTripsTheme() {
        val legacy = KaniWidgetInstanceOptions(KaniWidgetStyle.HEATMAP, KaniThemeChoice.AUTUMN)
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(initialOptions = legacy, onSave = { saved = it })
        }

        composeRule.onNodeWithTag("widget_style_heatmap").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(legacy, saved)
    }

    @Test
    fun explicitLegacySwitchChangesOnlyStyle() {
        val legacy = KaniWidgetInstanceOptions(KaniWidgetStyle.HEATMAP, KaniThemeChoice.DARK)
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(initialOptions = legacy, onSave = { saved = it })
        }

        composeRule.onNodeWithTag("widget_style_due_card").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(KaniWidgetInstanceOptions(KaniWidgetStyle.DUE_CARD, KaniThemeChoice.DARK), saved)
    }

    @Test
    fun reconfigureDueLoadsAndPreservesSavedTheme() {
        val stored = KaniWidgetInstanceOptions(KaniWidgetStyle.DUE_CARD, KaniThemeChoice.MIDNIGHT_ARCADE)
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(initialOptions = stored, onSave = { saved = it })
        }

        composeRule.onNodeWithTag("widget_theme_${KaniThemeChoice.MIDNIGHT_ARCADE.storageKey}")
            .performScrollTo()
            .assertIsSelected()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(stored, saved)
    }

    @Test
    fun returningToFollowAppClearsAThemeOverride() {
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(
                initialOptions = KaniWidgetInstanceOptions(
                    themeOverride = KaniThemeChoice.AUTUMN,
                ),
                onSave = { saved = it },
            )
        }

        composeRule.onNodeWithTag("widget_theme_follow_app").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(KaniWidgetStyle.DUE_CARD, saved?.style)
        assertNull(saved?.themeOverride)
    }

    @Test
    fun everyThemeChoiceRendersASelectableRow() {
        composeRule.setContent {
            KaniWidgetConfigScreen(initialOptions = KaniWidgetInstanceOptions(), onSave = {})
        }

        for (choice in KaniThemeChoice.entries) {
            composeRule.onNodeWithTag("widget_theme_${choice.storageKey}")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
