package dev.bee.kanjianki.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniWidgetReceiverTest {
    @Test
    fun providerReceiversUseNormalGlanceLifecycleAndDedicatedWidgets() {
        val receivers = listOf(
            KaniWidgetReceiver() to KaniWidget::class.java,
            QuickStudyWidgetReceiver() to QuickStudyWidget::class.java,
            ActivityWidgetReceiver() to ActivityWidget::class.java,
            FocusKanjiWidgetReceiver() to FocusKanjiWidget::class.java,
        )

        receivers.forEach { (receiver, widgetClass) ->
            assertTrue(GlanceAppWidgetReceiver::class.java.isAssignableFrom(receiver::class.java))
            assertEquals(widgetClass, receiver.glanceAppWidget::class.java)
            assertFalse(receiver::class.java.declaredMethods.any { it.name == "onReceive" })
        }
    }

    @Test
    fun heatCellRoleUsesTrackForEmptyDaysAndScaledPrimaryOtherwise() {
        val palette = KaniWidgetPalette.forChoice(KaniThemeChoice.LIGHT)

        assertEquals(palette.track, heatCellRole(0, 10, palette))
        assertEquals(palette.primary.withAlpha(1.0f), heatCellRole(10, 10, palette))
        assertEquals(palette.primary.withAlpha(0.5f), heatCellRole(5, 10, palette))
        assertEquals(palette.primary.withAlpha(0.15f), heatCellRole(1, 100, palette))
        assertEquals(palette.primary.withAlpha(0.15f), heatCellRole(3, 0, palette))
    }
}
