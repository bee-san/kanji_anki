package dev.bee.kanjianki.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.WidgetTextCopy
import dev.bee.kanjianki.theme.KaniThemeChoice
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
    fun defaultSaveKeepsZeroConfigDueCardFollowingAppTheme() {
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(onSave = { saved = it })
        }

        composeRule.onNodeWithText(WidgetTextCopy.widgetConfigTitle()).assertIsDisplayed()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(KaniWidgetStyle.DUE_CARD, saved?.style)
        assertNull(saved?.themeOverride)
    }

    @Test
    fun selectingHeatmapAndThemeOverrideSavesThoseOptions() {
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(onSave = { saved = it })
        }

        composeRule.onNodeWithTag("widget_style_heatmap").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget_theme_${KaniThemeChoice.DARK.storageKey}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(KaniWidgetStyle.HEATMAP, saved?.style)
        assertEquals(KaniThemeChoice.DARK, saved?.themeOverride)
    }

    @Test
    fun returningToFollowAppClearsAThemeOverride() {
        var saved: KaniWidgetInstanceOptions? = null
        composeRule.setContent {
            KaniWidgetConfigScreen(onSave = { saved = it })
        }

        composeRule.onNodeWithTag("widget_theme_${KaniThemeChoice.AUTUMN.storageKey}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("widget_theme_follow_app").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget_config_save").performScrollTo().performClick()

        assertEquals(KaniWidgetStyle.DUE_CARD, saved?.style)
        assertNull(saved?.themeOverride)
    }

    @Test
    fun everyThemeChoiceRendersASelectableRow() {
        composeRule.setContent {
            KaniWidgetConfigScreen(onSave = {})
        }

        for (choice in KaniThemeChoice.entries) {
            composeRule.onNodeWithTag("widget_theme_${choice.storageKey}")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
