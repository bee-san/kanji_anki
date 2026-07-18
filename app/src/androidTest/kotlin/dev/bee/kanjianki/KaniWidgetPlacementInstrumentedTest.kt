package dev.bee.kanjianki

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bee.kanjianki.widget.ActivityWidgetReceiver
import dev.bee.kanjianki.widget.FocusKanjiWidgetReceiver
import dev.bee.kanjianki.widget.KaniWidgetReceiver
import dev.bee.kanjianki.widget.QuickStudyWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaniWidgetPlacementInstrumentedTest {
    @Test
    fun launcherCatalogDiscoversAllFourProviderEntriesWithUsableBounds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = AppWidgetManager.getInstance(context).installedProviders
            .filter { it.provider.packageName == context.packageName }
            .associateBy { it.provider.className }
        val expected = setOf(
            KaniWidgetReceiver::class.java.name,
            QuickStudyWidgetReceiver::class.java.name,
            ActivityWidgetReceiver::class.java.name,
            FocusKanjiWidgetReceiver::class.java.name,
        )

        assertEquals(expected, providers.keys)
        expected.forEach { className ->
            val provider = checkNotNull(providers[className])
            assertTrue("$className must have a launcher-visible minimum width", provider.minWidth > 0)
            assertTrue("$className must have a launcher-visible minimum height", provider.minHeight > 0)
            assertTrue("$className must provide preview content", provider.previewLayout != 0)
        }
    }
}
