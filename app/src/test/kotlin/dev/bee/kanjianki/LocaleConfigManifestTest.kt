package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocaleConfigManifestTest {
    @Test
    fun manifestDeclaresAndroidLocaleConfig() {
        val manifest = xmlFile("src/main/AndroidManifest.xml")
        val application = manifest.documentElement.getElementsByTagName("application").item(0) as Element

        assertEquals("@xml/locales_config", application.getAttributeNS(ANDROID_NS, "localeConfig"))
    }

    @Test
    fun localeConfigAdvertisesEnglishAndJapanese() {
        val config = xmlFile("src/main/res/xml/locales_config.xml")
        val locales = config.documentElement.getElementsByTagName("locale")
        val names = (0 until locales.length).map { index ->
            (locales.item(index) as Element).getAttributeNS(ANDROID_NS, "name")
        }

        assertEquals("locale-config", config.documentElement.tagName)
        assertEquals(listOf("en", "ja"), names)
    }

    private fun xmlFile(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
