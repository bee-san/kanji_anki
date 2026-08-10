package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetEventHooksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun completedThemeWriteRequestsOneRefresh() {
        var refreshes = 0
        val hooks = KaniWidgetEventHooks { refreshes += 1 }

        hooks.themeWriteCompleted(context)

        assertEquals(1, refreshes)
    }

    @Test
    fun onlySuccessfullyAppliedRestoreRequestsRefresh() {
        var refreshes = 0
        val hooks = KaniWidgetEventHooks { refreshes += 1 }

        // Every outcome the applier can report, now as the Boolean the hook takes: only an
        // applied restore should refresh, because the others left the database untouched and
        // a refresh would redraw identical content.
        for (applied in listOf(false, true, false)) {
            hooks.restoreCompleted(context, applied = applied)
        }

        assertEquals(1, refreshes)
    }
}
