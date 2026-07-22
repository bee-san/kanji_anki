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
    fun everyProviderHasUniquePickerMetadataAndUsesOnlyEventDrivenRefresh() {
        val info = widgetReceivers().values.map { providerInfo(it.providerInfo) }

        assertEquals(4, info.map { it.getAttributeNS(ANDROID_NS, "previewLayout") }.toSet().size)
        assertEquals(4, info.map { it.getAttributeNS(ANDROID_NS, "previewImage") }.toSet().size)
        assertEquals(4, info.map { it.getAttributeNS(ANDROID_NS, "description") }.toSet().size)
        info.forEach { provider ->
            assertEquals("appwidget-provider", provider.tagName)
            assertEquals("0", provider.androidAttribute("updatePeriodMillis"))
        }
    }

    @Test
    fun canonicalProviderKeepsComponentXmlConfigurationAndSizeContract() {
        val provider = providerInfo("@xml/kani_widget_info")
        val preview = File("src/main/res/layout/kani_widget_preview.xml").readText()

        assertEquals("dev.bee.kanjianki.widget.KaniWidgetConfigActivity", provider.androidAttribute("configure"))
        assertEquals("reconfigurable|configuration_optional", provider.androidAttribute("widgetFeatures"))
        assertEquals("250dp", provider.androidAttribute("minWidth"))
        assertEquals("72dp", provider.androidAttribute("minHeight"))
        assertEquals("180dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("72dp", provider.androidAttribute("minResizeHeight"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals("4", provider.androidAttribute("targetCellWidth"))
        assertEquals("1", provider.androidAttribute("targetCellHeight"))
        assertTrue(preview.contains("android:minWidth=\"80dp\""))
        assertTrue(preview.contains("android:layout_height=\"56dp\""))
    }

    @Test
    fun quickStudyProviderUsesTheApprovedTinyCompactAndWideSizeContract() {
        val provider = providerInfo("@xml/quick_study_widget_info")

        assertEquals("56dp", provider.androidAttribute("minWidth"))
        assertEquals("56dp", provider.androidAttribute("minHeight"))
        assertEquals("56dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("56dp", provider.androidAttribute("minResizeHeight"))
        assertEquals("180dp", provider.androidAttribute("maxResizeWidth"))
        assertEquals("72dp", provider.androidAttribute("maxResizeHeight"))
        assertEquals("horizontal", provider.androidAttribute("resizeMode"))
        assertEquals("2", provider.androidAttribute("targetCellWidth"))
        assertEquals("1", provider.androidAttribute("targetCellHeight"))
        assertEquals("@layout/quick_study_widget_preview", provider.androidAttribute("previewLayout"))
        assertEquals("@layout/quick_study_widget_loading", provider.androidAttribute("initialLayout"))
    }

    @Test
    fun quickStudyPreviewIsStaticLocalizedDemoAndLoadingLayoutHasNoDemoFactsOrActions() {
        val preview = File("src/main/res/layout/quick_study_widget_preview.xml").readText()
        val loading = File("src/main/res/layout/quick_study_widget_loading.xml").readText()
        val legacyPreview = File("src/main/res/drawable/quick_study_widget_preview_image.xml").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val japanese = File("src/main/res/values-ja/strings.xml").readText()

        assertTrue(preview.contains("@string/quick_study_widget_preview_count"))
        assertTrue(preview.contains("@string/quick_study_widget_preview_status"))
        assertTrue(preview.contains("@string/quick_study_widget_preview_action"))
        assertTrue(preview.contains("@drawable/widget_preview_primary_action"))
        assertTrue(preview.contains("@color/widget_preview_on_primary"))
        assertTrue(english.contains("name=\"quick_study_widget_preview_count\">12</string>"))
        assertTrue(english.contains("name=\"quick_study_widget_preview_status\">Due</string>"))
        assertTrue(english.contains("name=\"quick_study_widget_preview_action\">Study</string>"))
        assertTrue(japanese.contains("name=\"quick_study_widget_preview_status\">期限</string>"))
        assertTrue(japanese.contains("name=\"quick_study_widget_preview_action\">学習</string>"))
        assertFalse(loading.contains("quick_study_widget_preview_count"))
        assertFalse(loading.contains("quick_study_widget_preview_action"))
        assertFalse(loading.contains("widget_shortcut_study_now"))
        assertTrue("API 26 preview must show a study/play mark", legacyPreview.contains("M44,30 L68,42 L44,54 Z"))
    }

    @Test
    fun activityProviderUsesTheApprovedCompactRegularAndWideSizeContract() {
        val provider = providerInfo("@xml/activity_widget_info")

        assertEquals("120dp", provider.androidAttribute("minWidth"))
        assertEquals("72dp", provider.androidAttribute("minHeight"))
        assertEquals("120dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("72dp", provider.androidAttribute("minResizeHeight"))
        assertEquals("340dp", provider.androidAttribute("maxResizeWidth"))
        assertEquals("180dp", provider.androidAttribute("maxResizeHeight"))
        assertEquals("horizontal|vertical", provider.androidAttribute("resizeMode"))
        assertEquals("4", provider.androidAttribute("targetCellWidth"))
        assertEquals("2", provider.androidAttribute("targetCellHeight"))
        assertEquals("@layout/activity_widget_preview", provider.androidAttribute("previewLayout"))
        assertEquals("@layout/activity_widget_loading", provider.androidAttribute("initialLayout"))
    }

    @Test
    fun activityPreviewIsStaticLocalizedHistoryAndLoadingLayoutHasNoDemoFactsOrActions() {
        val preview = File("src/main/res/layout/activity_widget_preview.xml").readText()
        val loading = File("src/main/res/layout/activity_widget_loading.xml").readText()
        val legacyPreview = File("src/main/res/drawable/activity_widget_preview_image.xml").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val japanese = File("src/main/res/values-ja/strings.xml").readText()

        assertTrue(preview.contains("@string/activity_widget_preview_total"))
        assertTrue(preview.contains("@string/activity_widget_preview_streak"))
        assertTrue(preview.contains("@string/activity_widget_preview_best"))
        assertFalse(preview.contains("@string/activity_widget_preview_stats"))
        assertTrue(english.contains("name=\"activity_widget_preview_total\">87 reviews in 35 days</string>"))
        assertTrue(english.contains("name=\"activity_widget_preview_streak\">5-day streak</string>"))
        assertTrue(english.contains("name=\"activity_widget_preview_best\">Best: 21 days</string>"))
        assertFalse(english.contains("name=\"activity_widget_preview_stats\""))
        assertTrue(japanese.contains("name=\"activity_widget_preview_total\">35日間で復習87件</string>"))
        assertTrue(japanese.contains("name=\"activity_widget_preview_streak\">5日連続</string>"))
        assertTrue(japanese.contains("name=\"activity_widget_preview_best\">最長21日</string>"))
        assertFalse(japanese.contains("name=\"activity_widget_preview_stats\""))
        assertFalse(loading.contains("activity_widget_preview_total"))
        assertFalse(loading.contains("activity_widget_preview_stats"))
        assertFalse(loading.contains("preview_activity_bars"))
        assertTrue(Regex("M\\d+,\\d+h10v10").findAll(legacyPreview).count() >= 35)
    }

    @Test
    fun pickerCopyMatchesApprovedEnglishAndJapaneseText() {
        val english = File("src/main/res/values/strings.xml").readText()
        val japanese = File("src/main/res/values-ja/strings.xml").readText()

        assertTrue(english.contains("name=\"study_overview_widget_label\">Study overview</string>"))
        assertTrue(english.contains("name=\"kani_widget_description\">See today’s study status at a glance.</string>"))
        assertTrue(english.contains("name=\"quick_study_widget_label\">Quick study</string>"))
        assertTrue(english.contains("name=\"quick_study_widget_description\">One-tap access to your next study session.</string>"))
        assertTrue(english.contains("name=\"activity_widget_label\">Activity</string>"))
        assertTrue(english.contains("name=\"activity_widget_description\">See your 35-day study streak and review history.</string>"))
        assertTrue(english.contains("name=\"focus_kanji_widget_description\">Review one kanji with its meaning and reading.</string>"))
        assertTrue(japanese.contains("name=\"study_overview_widget_label\">学習概要</string>"))
        assertTrue(japanese.contains("name=\"quick_study_widget_label\">クイック学習</string>"))
        assertTrue(japanese.contains("name=\"quick_study_widget_description\">次の学習セッションをワンタップで開始します。</string>"))
        assertTrue(japanese.contains("name=\"activity_widget_label\">学習履歴</string>"))
        assertTrue(japanese.contains("name=\"activity_widget_description\">35日間の連続学習と復習履歴を確認します。</string>"))
    }

    @Test
    fun allStaticWidgetSurfacesUseSharedDayNightSemanticColors() {
        val resourcePaths = listOf(
            "src/main/res/layout/kani_widget_loading.xml",
            "src/main/res/layout/kani_widget_preview.xml",
            "src/main/res/layout/quick_study_widget_loading.xml",
            "src/main/res/layout/quick_study_widget_preview.xml",
            "src/main/res/layout/activity_widget_loading.xml",
            "src/main/res/layout/activity_widget_preview.xml",
            "src/main/res/layout/focus_kanji_widget_loading.xml",
            "src/main/res/layout/focus_kanji_widget_preview.xml",
            "src/main/res/drawable/kani_widget_preview_image.xml",
            "src/main/res/drawable/quick_study_widget_preview_image.xml",
            "src/main/res/drawable/activity_widget_preview_image.xml",
            "src/main/res/drawable/focus_kanji_widget_preview_image.xml",
            "src/main/res/drawable/widget_preview_primary_action.xml",
        )
        resourcePaths.forEach { path ->
            val resource = File(path).readText()
            assertFalse("$path must not hard-code colors", Regex("#[0-9A-Fa-f]{6,8}").containsMatchIn(resource))
            assertTrue("$path must use semantic preview colors", resource.contains("@color/widget_preview_"))
        }
        val day = File("src/main/res/values/widget_preview_colors.xml").readText()
        val night = File("src/main/res/values-night/widget_preview_colors.xml").readText()
        listOf("background", "ink", "muted", "primary", "on_primary", "track", "heat_low", "heat_medium").forEach { role ->
            assertTrue(day.contains("name=\"widget_preview_$role\""))
            assertTrue(night.contains("name=\"widget_preview_$role\""))
        }
    }

    @Test
    fun focusProviderUsesTheApprovedCompactAndWideSizeContract() {
        val provider = providerInfo("@xml/focus_kanji_widget_info")

        assertEquals("120dp", provider.androidAttribute("minWidth"))
        assertEquals("120dp", provider.androidAttribute("minHeight"))
        assertEquals("120dp", provider.androidAttribute("minResizeWidth"))
        assertEquals("120dp", provider.androidAttribute("minResizeHeight"))
        assertEquals("250dp", provider.androidAttribute("maxResizeWidth"))
        assertEquals("130dp", provider.androidAttribute("maxResizeHeight"))
        assertEquals("horizontal", provider.androidAttribute("resizeMode"))
        assertEquals("2", provider.androidAttribute("targetCellWidth"))
        assertEquals("2", provider.androidAttribute("targetCellHeight"))
        assertEquals("@layout/focus_kanji_widget_preview", provider.androidAttribute("previewLayout"))
        assertEquals("@layout/focus_kanji_widget_loading", provider.androidAttribute("initialLayout"))
    }

    @Test
    fun focusPreviewUsesOnlyLocalizedDemoFactsAndLoadingHasNoDemoFactsOrActions() {
        val preview = File("src/main/res/layout/focus_kanji_widget_preview.xml").readText()
        val loading = File("src/main/res/layout/focus_kanji_widget_loading.xml").readText()
        val legacyPreview = File("src/main/res/drawable/focus_kanji_widget_preview_image.xml").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val japanese = File("src/main/res/values-ja/strings.xml").readText()

        assertTrue(preview.contains("@string/focus_kanji_widget_preview_kanji"))
        assertTrue(preview.contains("@string/focus_kanji_widget_preview_meaning"))
        assertTrue(preview.contains("@string/focus_kanji_widget_preview_reading"))
        assertTrue(preview.contains("@string/focus_kanji_widget_preview_status"))
        assertTrue(preview.contains("@string/focus_kanji_widget_preview_details"))
        assertTrue(english.contains("name=\"focus_kanji_widget_preview_kanji\">学</string>"))
        assertTrue(english.contains("name=\"focus_kanji_widget_preview_meaning\">learn</string>"))
        assertTrue(english.contains("name=\"focus_kanji_widget_preview_reading\">がく</string>"))
        assertTrue(english.contains("name=\"focus_kanji_widget_preview_status\">Due</string>"))
        assertTrue(english.contains("name=\"focus_kanji_widget_preview_details\">Details</string>"))
        assertTrue(japanese.contains("name=\"focus_kanji_widget_preview_meaning\">学ぶ</string>"))
        assertFalse(loading.contains("focus_kanji_widget_preview_kanji"))
        assertFalse(loading.contains("focus_kanji_widget_preview_meaning"))
        assertFalse(loading.contains("focus_kanji_widget_preview_reading"))
        assertFalse(loading.contains("widget_shortcut_study_now"))
        assertTrue("API 26 preview must draw a legible glyph from open strokes", legacyPreview.contains("android:strokeColor=\"@color/widget_preview_ink\""))
        assertTrue(Regex("M\\d+,\\d+").findAll(legacyPreview).count() >= 8)
        assertFalse(
            "Dense cubic glyph silhouettes render as a square in older pickers",
            Regex("\\dC\\d").containsMatchIn(legacyPreview),
        )
    }

    @Test
    fun focusPickerCopyMatchesApprovedEnglishAndJapaneseText() {
        val english = File("src/main/res/values/strings.xml").readText()
        val japanese = File("src/main/res/values-ja/strings.xml").readText()

        assertTrue(english.contains("name=\"focus_kanji_widget_label\">Focus kanji</string>"))
        assertTrue(english.contains("name=\"focus_kanji_widget_description\">Review one kanji with its meaning and reading.</string>"))
        assertTrue(japanese.contains("name=\"focus_kanji_widget_label\">注目漢字</string>"))
        assertTrue(japanese.contains("name=\"focus_kanji_widget_description\">漢字1字を意味と読みと一緒に復習します。</string>"))
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
