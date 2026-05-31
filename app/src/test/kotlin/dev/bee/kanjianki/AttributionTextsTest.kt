package dev.bee.kanjianki

import dev.bee.kanjianki.core.AttributionCopy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AttributionTextsTest {
    @Test
    fun dictionarySourcesUsesSafeFallbackWithoutAndroidResources() {
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySources(null)
        )
        assertEquals("KanjiVG stroke data, CC BY-SA 3.0.", AttributionTexts.kanjiVg(null))
        assertEquals("", AttributionTexts.rawResourceText(null, 0))
    }

    @Test
    fun dictionaryManifestTextFallsBackForInvalidJsonInJvmUnitTests() {
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifestText("not json")
        )
    }

    @Test
    fun parsedDictionaryManifestDistinguishesMissingSourcesFromEmptySources() {
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", null, null))
        )
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifest(FakeNonArraySourcesManifest("2026-05-15"))
        )
        assertEquals(
            "Dictionary manifest is empty.",
            AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", sourceArray(), null))
        )
    }

    @Test
    fun parsedDictionaryManifestDelegatesSourcesAndNotesToCoreFormatter() {
        val sources = array(jsonObject(
            "name", "KANJIDIC2",
            "license", "CC BY-SA",
            "source_path", "kanjidic2.xml",
            "database_version", "2026-05-01"
        ))
        val notes = array("note one", "note two")

        assertEquals(
            "Generated: 2026-05-15\n\nKANJIDIC2\nLicense: CC BY-SA\nSource: kanjidic2.xml\nVersion: 2026-05-01\n\nnote one\nnote two",
            AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", sources, notes))
        )
    }

    @Test
    fun sourceAndNoteAdaptersDelegateToCoreFormatter() {
        val lines = arrayListOf<String>()

        AttributionCopy.appendSource(
            lines,
            AttributionCopy.Source(
                null,
                "KANJIDIC2",
                "CC BY-SA",
                null,
                "kanjidic2.xml",
                null,
                "2026-05-01",
                null,
                null,
                null
            )
        )
        AttributionCopy.appendNotes(lines, listOf("note one", "note two"))

        assertEquals(
            "\nKANJIDIC2\nLicense: CC BY-SA\nSource: kanjidic2.xml\nVersion: 2026-05-01\n\nnote one\nnote two",
            lines.joinToString("\n")
        )
    }

    private fun jsonObject(vararg entries: String): JSONObject {
        val values = linkedMapOf<String, String>()
        var i = 0
        while (i < entries.size) {
            values[entries[i]] = entries[i + 1]
            i += 2
        }
        return FakeJsonObject(values)
    }

    private fun array(vararg values: String): JSONArray = FakeStringArray(values.toList())
    private fun array(vararg values: JSONObject): JSONArray = FakeObjectArray(values.toList())
    private fun sourceArray(): JSONArray = FakeObjectArray(emptyList())

    private class FakeJsonObject(private val values: Map<String, String>) : JSONObject() {
        override fun optString(name: String?): String = optString(name, "")
        override fun optString(name: String?, fallback: String): String = values[name] ?: fallback
    }

    private class FakeManifest(
        private val generatedAt: String,
        private val sources: JSONArray?,
        private val notes: JSONArray?
    ) : JSONObject() {
        override fun optString(name: String?): String = when (name) {
            "generated_at" -> generatedAt
            else -> ""
        }

        override fun optJSONArray(name: String?): JSONArray? = when (name) {
            "sources" -> sources
            "notes" -> notes
            else -> null
        }
    }

    private class FakeStringArray(private val values: List<String>) : JSONArray() {
        override fun length(): Int = values.size
        override fun optString(index: Int): String = values[index]
    }

    private class FakeNonArraySourcesManifest(private val generatedAt: String) : JSONObject() {
        override fun optString(name: String): String = when (name) {
            "generated_at" -> generatedAt
            "sources" -> "not an array"
            else -> ""
        }

        override fun optJSONArray(name: String): JSONArray? = null
    }

    private class FakeObjectArray(private val values: List<JSONObject>) : JSONArray() {
        override fun length(): Int = values.size
        override fun getJSONObject(index: Int): JSONObject = values[index]
    }
}
