package dev.bee.kanjianki.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class KaniWidgetProviderInfoTest {
    @Test
    fun providerRequestsHourlyFallbackUpdates() {
        val provider = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/kani_widget_info.xml"))
            .documentElement

        assertEquals("appwidget-provider", provider.tagName)
        assertEquals(
            HOURLY_UPDATE_MILLIS.toString(),
            provider.getAttributeNS(ANDROID_NS, "updatePeriodMillis"),
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val HOURLY_UPDATE_MILLIS = 60L * 60L * 1_000L
    }
}
