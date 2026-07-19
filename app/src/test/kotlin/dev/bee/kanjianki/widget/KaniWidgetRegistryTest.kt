package dev.bee.kanjianki.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
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
    fun refreshFailureInOneInstalledFamilyDoesNotBlockTheOthers() = runTest {
        val attempted = mutableListOf<Class<out GlanceAppWidget>>()
        val registry = registry(
            installedIds = { intArrayOf(1) },
            updateWidget = { widget ->
                attempted += widget::class.java
                if (widget is QuickStudyWidget) error("quick refresh failed")
            },
        )

        registry.refreshInstalled(context)

        assertEquals(
            listOf(
                KaniWidget::class.java,
                QuickStudyWidget::class.java,
                ActivityWidget::class.java,
                FocusKanjiWidget::class.java,
            ),
            attempted,
        )
    }

    @Test
    fun refreshCancellationStopsWithoutAttemptingLaterFamilies() = runTest {
        val attempted = mutableListOf<Class<out GlanceAppWidget>>()
        val cancellation = CancellationException("refresh cancelled")
        val registry = registry(
            installedIds = { intArrayOf(1) },
            updateWidget = { widget ->
                attempted += widget::class.java
                if (widget is QuickStudyWidget) throw cancellation
            },
        )

        var thrown: CancellationException? = null
        try {
            registry.refreshInstalled(context)
        } catch (failure: CancellationException) {
            thrown = failure
        }

        assertSame(cancellation, thrown)
        assertEquals(
            listOf(KaniWidget::class.java, QuickStudyWidget::class.java),
            attempted,
        )
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
    @Config(sdk = [26])
    fun api26WidgetIdLookupUsesProviderComponentIdsWithoutProviderInfoApi() {
        val registry = registry(
            installedIds = { receiver ->
                if (receiver == KaniWidgetReceiver::class.java) intArrayOf(41) else intArrayOf()
            },
            providerForId = { error("provider-info lookup is not available on API 26") },
        )

        assertSame(KaniWidgetReceiver::class.java, registry.descriptorForAppWidgetId(context, 41)?.receiverClass)
        assertNull(registry.descriptorForAppWidgetId(context, 42))
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
