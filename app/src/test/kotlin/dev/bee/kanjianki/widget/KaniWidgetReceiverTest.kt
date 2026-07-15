package dev.bee.kanjianki.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun boundaryBroadcastsAreHandledWithoutTouchingTheGlanceUpdatePath() {
        val receiver = KaniWidgetReceiver()

        // No widget instances are installed, so the updater no-ops; the key
        // assertion is that these actions route to the refresh branch and
        // never reach AppWidgetProvider.onReceive, which would throw for
        // non-appwidget actions lacking extras.
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        receiver.onReceive(context, Intent(KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH))
    }

    @Test
    fun heatCellRoleUsesTrackForEmptyDaysAndScaledPrimaryOtherwise() {
        val palette = KaniWidgetPalette.forChoice(KaniThemeChoice.LIGHT)

        assertEquals(palette.track, heatCellRole(0, 10, palette))
        assertEquals(palette.primary.withAlpha(1.0f), heatCellRole(10, 10, palette))
        assertEquals(palette.primary.withAlpha(0.5f), heatCellRole(5, 10, palette))
        // Below the visibility floor the alpha clamps at 0.15.
        assertEquals(palette.primary.withAlpha(0.15f), heatCellRole(1, 100, palette))
        // Defensive zero max: still renders at the floor rather than dividing by zero.
        assertEquals(palette.primary.withAlpha(0.15f), heatCellRole(3, 0, palette))
    }
}
