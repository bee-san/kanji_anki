package dev.bee.kanjianki.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class KaniWidgetProviderInfoTest {
    @Test
    fun manifestRegistersExactlyFourDistinctWidgetProviders() {
        val providers = widgetReceivers()

        assertEquals(
            setOf(
                ".widget.KaniWidgetReceiver",
                ".widget.QuickStudyWidgetReceiver",
                ".widget.ActivityWidgetReceiver",
                ".widget.FocusKanjiWidgetReceiver",
            ),
            providers.keys,
        )
        assertEquals(4, providers.values.map { it.providerInfo }.toSet().size)
        assertEquals(4, providers.values.map { it.label }.toSet().size)
    }

    @Test
    fun everyProviderHasUniquePickerMetadataAndHourlyFallback() {
        val info = widgetReceivers().values.map { providerInfo(it.providerInfo) }

        assertEquals(4, info.map { it.getAttributeNS(ANDROID_NS, "previewLayout") }.toSet().size)
        assertEquals(4, info.map { it.getAttributeNS(ANDROID_NS, "previewImage") }.toSet().size)
        assertEquals(4, info.map { it.getAttributeNS(ANDROID_NS, "description") }.toSet().size)
        info.forEach { provider ->
            assertEquals("appwidget-provider", provider.tagName)
            assertEquals(HOURLY_UPDATE_MILLIS.toString(), provider.androidAttribute("updatePeriodMillis"))
        }
    }

    @Test
    fun canonicalProviderKeepsComponentXmlConfigurationAndSizeContract() {
        val provider = providerInfo("@xml/kani_widget_info")

        assertEquals("dev.bee.kanjianki.widget.KaniWidgetConfigActivity", provider.androidAttribute("configure"))
        assertEquals("reconfigurable|configuration_optional", provider.androidAttribute("widgetFeatures"))
        assertEquals("250dp", provider.androidAttribute("minWidth"))
        assertEquals("72dp", provider.androidAttribute("minHeight"))
        assertEquals("180dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("72dp", provider.androidAttribute("minResizeHeight"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals("4", provider.androidAttribute("targetCellWidth"))
        assertEquals("1", provider.androidAttribute("targetCellHeight"))
    }

    @Test
    fun quickStudyProviderUsesTheApprovedTinyCompactAndWideSizeContract() {
        val provider = providerInfo("@xml/quick_study_widget_info")

        assertEquals("56dp", provider.androidAttribute("minWidth"))
        assertEquals("56dp", provider.androidAttribute("minHeight"))
        assertEquals("56dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("56dp", provider.androidAttribute("minResizeHeight"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals("1", provider.androidAttribute("targetCellWidth"))
        assertEquals("1", provider.androidAttribute("targetCellHeight"))
        assertEquals("@layout/quick_study_widget_preview", provider.androidAttribute("previewLayout"))
        assertEquals("@layout/quick_study_widget_loading", provider.androidAttribute("initialLayout"))
    }

    @Test
    fun quickStudyPreviewIsStaticLocalizedDemoAndLoadingLayoutHasNoDemoFactsOrActions() {
        val preview = File("src/main/res/layout/quick_study_widget_preview.xml").readText()
        val loading = File("src/main/res/layout/quick_study_widget_loading.xml").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val japanese = File("src/main/res/values-ja/strings.xml").readText()

        assertTrue(preview.contains("@string/quick_study_widget_preview_count"))
        assertTrue(preview.contains("@string/quick_study_widget_preview_status"))
        assertTrue(preview.contains("@string/quick_study_widget_preview_action"))
        assertTrue(english.contains("name=\"quick_study_widget_preview_count\">12</string>"))
        assertTrue(english.contains("name=\"quick_study_widget_preview_status\">Due</string>"))
        assertTrue(english.contains("name=\"quick_study_widget_preview_action\">Study now</string>"))
        assertTrue(japanese.contains("name=\"quick_study_widget_preview_status\">期限</string>"))
        assertTrue(japanese.contains("name=\"quick_study_widget_preview_action\">今すぐ学習</string>"))
        assertFalse(loading.contains("quick_study_widget_preview_count"))
        assertFalse(loading.contains("quick_study_widget_preview_action"))
        assertFalse(loading.contains("widget_shortcut_study_now"))
    }

    @Test
    fun newProvidersNeverDeclareConfigurationState() {
        val newInfo = widgetReceivers()
            .filterKeys { it != ".widget.KaniWidgetReceiver" }
            .values
            .map { providerInfo(it.providerInfo) }

        newInfo.forEach { provider ->
            assertFalse(provider.hasAttributeNS(ANDROID_NS, "configure"))
            assertFalse(provider.hasAttributeNS(ANDROID_NS, "widgetFeatures"))
        }
    }

    @Test
    fun onlyPlainRefreshReceiverOwnsSystemRefreshEvents() {
        val manifestReceivers = manifestReceivers()
        val refresh = manifestReceivers.single { it.androidAttribute("name") == ".widget.KaniWidgetRefreshReceiver" }
        val refreshActions = actions(refresh)

        assertEquals(
            setOf(
                KaniWidgetRefreshPolicy.ACTION_WIDGET_REFRESH,
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.LOCALE_CHANGED",
            ),
            refreshActions,
        )
        assertFalse(refreshActions.contains("android.intent.action.DATE_CHANGED"))
        widgetReceivers().values.forEach { assertEquals(setOf(APPWIDGET_UPDATE), it.actions) }
    }

    private fun widgetReceivers(): Map<String, ReceiverMetadata> = manifestReceivers()
        .mapNotNull { receiver ->
            val receiverActions = actions(receiver)
            if (!receiverActions.contains(APPWIDGET_UPDATE)) return@mapNotNull null
            val metadata = receiver.getElementsByTagName("meta-data")
            val providerInfo = (0 until metadata.length)
                .map { metadata.item(it) as Element }
                .single { it.androidAttribute("name") == "android.appwidget.provider" }
                .androidAttribute("resource")
            receiver.androidAttribute("name") to ReceiverMetadata(
                label = receiver.androidAttribute("label"),
                providerInfo = providerInfo,
                actions = receiverActions,
            )
        }
        .toMap()

    private fun manifestReceivers(): List<Element> {
        val manifest = parse(File("src/main/AndroidManifest.xml"))
        val nodes = manifest.getElementsByTagName("receiver")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun providerInfo(resource: String): Element {
        val name = resource.removePrefix("@xml/")
        return parse(File("src/main/res/xml/$name.xml"))
    }

    private fun parse(file: File): Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)
        .documentElement

    private fun actions(receiver: Element): Set<String> {
        val nodes = receiver.getElementsByTagName("action")
        return (0 until nodes.length)
            .map { (nodes.item(it) as Element).androidAttribute("name") }
            .toSet()
    }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)

    private data class ReceiverMetadata(
        val label: String,
        val providerInfo: String,
        val actions: Set<String>,
    )

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val APPWIDGET_UPDATE = "android.appwidget.action.APPWIDGET_UPDATE"
        const val HOURLY_UPDATE_MILLIS = 60L * 60L * 1_000L
    }
}
