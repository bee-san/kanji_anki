package dev.bee.kanjianki.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class KaniWidgetProviderInfoTest {
    @Test
    fun providerRequestsHourlyFallbackUpdates() {
        val provider = providerInfo()

        assertEquals("appwidget-provider", provider.tagName)
        assertEquals(
            HOURLY_UPDATE_MILLIS.toString(),
            provider.getAttributeNS(ANDROID_NS, "updatePeriodMillis"),
        )
    }

    @Test
    fun providerDeclaresPickerPreviews() {
        val provider = providerInfo()

        assertEquals(
            "@layout/kani_widget_preview",
            provider.getAttributeNS(ANDROID_NS, "previewLayout"),
        )
        assertEquals(
            "@drawable/kani_widget_preview_image",
            provider.getAttributeNS(ANDROID_NS, "previewImage"),
        )
    }

    @Test
    fun providerDeclaresModernResizeMetadata() {
        val provider = providerInfo()

        assertEquals("horizontal|vertical", provider.getAttributeNS(ANDROID_NS, "resizeMode"))
        assertEquals("180dp", provider.getAttributeNS(ANDROID_NS, "minResizeWidth"))
        assertEquals("72dp", provider.getAttributeNS(ANDROID_NS, "minResizeHeight"))
        assertEquals("4", provider.getAttributeNS(ANDROID_NS, "targetCellWidth"))
        assertEquals("1", provider.getAttributeNS(ANDROID_NS, "targetCellHeight"))
    }

    @Test
    fun providerConfigurationIsOptionalAndReconfigurable() {
        val provider = providerInfo()

        assertEquals(
            "dev.bee.kanjianki.widget.KaniWidgetConfigActivity",
            provider.getAttributeNS(ANDROID_NS, "configure"),
        )
        assertEquals(
            "reconfigurable|configuration_optional",
            provider.getAttributeNS(ANDROID_NS, "widgetFeatures"),
        )
    }

    @Test
    fun widgetReceiverListensForDayBoundaryBroadcasts() {
        val manifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
            .documentElement
        val receivers = manifest.getElementsByTagName("receiver")
        var widgetReceiver: Element? = null
        for (index in 0 until receivers.length) {
            val receiver = receivers.item(index) as Element
            if (receiver.getAttributeNS(ANDROID_NS, "name") == ".widget.KaniWidgetReceiver") {
                widgetReceiver = receiver
            }
        }
        requireNotNull(widgetReceiver)

        val actions = mutableSetOf<String>()
        val actionNodes = widgetReceiver.getElementsByTagName("action")
        for (index in 0 until actionNodes.length) {
            actions += (actionNodes.item(index) as Element).getAttributeNS(ANDROID_NS, "name")
        }

        assertTrue(actions.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(actions.contains("android.intent.action.TIME_SET"))
        assertTrue(actions.contains("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(actions.contains("android.intent.action.DATE_CHANGED"))
        assertTrue(actions.contains("dev.bee.kanjianki.widget.action.REFRESH"))
    }

    private fun providerInfo(): Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/res/xml/kani_widget_info.xml"))
        .documentElement

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val HOURLY_UPDATE_MILLIS = 60L * 60L * 1_000L
    }
}
