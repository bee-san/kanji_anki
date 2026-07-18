package dev.bee.kanjianki.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import androidx.annotation.XmlRes
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import dev.bee.kanjianki.R

internal data class KaniWidgetDescriptor(
    val receiverClass: Class<out BroadcastReceiver>,
    val widgetFactory: () -> GlanceAppWidget,
    @param:XmlRes val providerInfoRes: Int,
)

internal class KaniWidgetRegistry(
    val descriptors: List<KaniWidgetDescriptor> = DESCRIPTORS,
    private val appWidgetIds: (Context, Class<out BroadcastReceiver>) -> IntArray = { context, receiver ->
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver))
    },
    private val providerForAppWidgetId: (Context, Int) -> ComponentName? = { context, id ->
        AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
    },
    private val widgetUpdater: suspend (GlanceAppWidget, Context) -> Unit = { widget, context ->
        widget.updateAll(context)
    },
) {
    fun installedDescriptors(context: Context): List<KaniWidgetDescriptor> = descriptors.filter { descriptor ->
        appWidgetIds(context, descriptor.receiverClass).isNotEmpty()
    }

    suspend fun refreshInstalled(context: Context) {
        installedDescriptors(context).forEach { descriptor ->
            widgetUpdater(descriptor.widgetFactory(), context)
        }
    }

    fun descriptorForAppWidgetId(context: Context, appWidgetId: Int): KaniWidgetDescriptor? {
        val provider = providerForAppWidgetId(context, appWidgetId) ?: return null
        return descriptors.firstOrNull { descriptor ->
            provider == ComponentName(context, descriptor.receiverClass)
        }
    }

    fun hasInstalledWidgets(context: Context): Boolean = installedDescriptors(context).isNotEmpty()

    companion object {
        val DESCRIPTORS: List<KaniWidgetDescriptor> = listOf(
            KaniWidgetDescriptor(KaniWidgetReceiver::class.java, ::KaniWidget, R.xml.kani_widget_info),
            KaniWidgetDescriptor(
                QuickStudyWidgetReceiver::class.java,
                ::QuickStudyWidget,
                R.xml.quick_study_widget_info,
            ),
            KaniWidgetDescriptor(
                ActivityWidgetReceiver::class.java,
                ::ActivityWidget,
                R.xml.activity_widget_info,
            ),
            KaniWidgetDescriptor(
                FocusKanjiWidgetReceiver::class.java,
                ::FocusKanjiWidget,
                R.xml.focus_kanji_widget_info,
            ),
        )

        val DEFAULT = KaniWidgetRegistry()
    }
}
