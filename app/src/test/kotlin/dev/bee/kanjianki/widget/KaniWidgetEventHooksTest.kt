package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.backup.StagedRestoreApplier
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

        StagedRestoreApplier.Result.entries.forEach { result ->
            hooks.restoreCompleted(context, result)
        }

        assertEquals(1, refreshes)
    }
}
