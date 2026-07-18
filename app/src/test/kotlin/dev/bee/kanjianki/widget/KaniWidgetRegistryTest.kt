package dev.bee.kanjianki.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetRegistryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun descriptorsContainExactlyTheFourStableProviderFamilies() {
        assertEquals(
            listOf(
                KaniWidgetReceiver::class.java,
                QuickStudyWidgetReceiver::class.java,
                ActivityWidgetReceiver::class.java,
                FocusKanjiWidgetReceiver::class.java,
            ),
            KaniWidgetRegistry.DESCRIPTORS.map { it.receiverClass },
        )
        assertEquals(4, KaniWidgetRegistry.DESCRIPTORS.map { it.providerInfoRes }.toSet().size)
        assertEquals(4, KaniWidgetRegistry.DESCRIPTORS.map { it.widgetFactory()::class.java }.toSet().size)
    }

    @Test
    fun refreshInstalledUpdatesAllAndOnlyInstalledFamiliesOnce() = runTest {
        val updated = mutableListOf<Class<out GlanceAppWidget>>()
        val installed = setOf(
            QuickStudyWidgetReceiver::class.java,
            FocusKanjiWidgetReceiver::class.java,
        )
        val registry = registry(
            installedIds = { receiver -> if (receiver in installed) intArrayOf(1) else intArrayOf() },
            updateWidget = { widget -> updated += widget::class.java },
        )

        registry.refreshInstalled(context)

        assertEquals(listOf(QuickStudyWidget::class.java, FocusKanjiWidget::class.java), updated)
    }

    @Test
    fun zeroInstalledWidgetsIsARefreshNoOp() = runTest {
        var updates = 0
        val registry = registry(updateWidget = { updates++ })

        registry.refreshInstalled(context)

        assertEquals(0, updates)
        assertFalse(registry.hasInstalledWidgets(context))
    }

    @Test
    fun knownWidgetIdResolvesButForeignAndDeletedIdsDoNot() {
        val activityComponent = ComponentName(context, ActivityWidgetReceiver::class.java)
        val foreignComponent = ComponentName(context.packageName, "other.WidgetReceiver")
        val registry = registry(
            providerForId = { id ->
                when (id) {
                    41 -> activityComponent
                    42 -> foreignComponent
                    else -> null
                }
            },
        )

        val known = registry.descriptorForAppWidgetId(context, 41)

        assertSame(ActivityWidgetReceiver::class.java, known?.receiverClass)
        assertNull(registry.descriptorForAppWidgetId(context, 42))
        assertNull(registry.descriptorForAppWidgetId(context, 43))
    }

    @Test
    fun installedDescriptorLookupUsesReceiverComponentIds() {
        val registry = registry(
            installedIds = { receiver ->
                if (receiver == KaniWidgetReceiver::class.java) intArrayOf(7, 8) else intArrayOf()
            },
        )

        assertEquals(listOf(KaniWidgetReceiver::class.java), registry.installedDescriptors(context).map { it.receiverClass })
        assertTrue(registry.hasInstalledWidgets(context))
    }

    private fun registry(
        installedIds: (Class<out BroadcastReceiver>) -> IntArray = { intArrayOf() },
        providerForId: (Int) -> ComponentName? = { null },
        updateWidget: suspend (GlanceAppWidget) -> Unit = {},
    ): KaniWidgetRegistry = KaniWidgetRegistry(
        descriptors = KaniWidgetRegistry.DESCRIPTORS,
        appWidgetIds = { _, receiver -> installedIds(receiver) },
        providerForAppWidgetId = { _, id -> providerForId(id) },
        widgetUpdater = { widget, _ -> updateWidget(widget) },
    )
}
