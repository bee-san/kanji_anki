package dev.bee.kanjianki

import dev.bee.kanjianki.core.AttributionCopy
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AttributionTextsTest {
    @Test
    fun dictionarySourcesUsesSafeFallbackWithoutAndroidResources() {
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySources(null),
        )
        assertEquals("KanjiVG stroke data, CC BY-SA 3.0.", AttributionTexts.kanjiVg(null))
        assertEquals("", AttributionTexts.rawResourceText(null, 0))
    }

    @Test
    fun dictionarySourcesUsesJapaneseFallbacksWithoutAndroidResources() {
        withJapaneseLocale {
            assertEquals(
                "EDRDGのKANJIDIC2辞書データ、Jiten順位データ、KanjiVG筆順データ。",
                AttributionTexts.dictionarySources(null),
            )
            assertEquals("KanjiVG筆順データ、CC BY-SA 3.0。", AttributionTexts.kanjiVg(null))
            assertEquals(
                "EDRDGのKANJIDIC2辞書データ、Jiten順位データ、KanjiVG筆順データ。",
                AttributionTexts.dictionarySourcesFromManifestText("not json"),
            )
            assertEquals(
                "EDRDGのKANJIDIC2辞書データ、Jiten順位データ、KanjiVG筆順データ。",
                AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", null, null)),
            )
        }
    }

    @Test
    fun dictionaryManifestTextFallsBackForInvalidJsonInJvmUnitTests() {
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifestText("not json"),
        )
    }

    @Test
    fun parsedDictionaryManifestDistinguishesMissingSourcesFromEmptySources() {
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", null, null)),
        )
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifest(FakeNonArraySourcesManifest("2026-05-15")),
        )
        assertEquals(
            "Dictionary manifest is empty.",
            AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", sourceArray(), null)),
        )
    }

    @Test
    fun parsedDictionaryManifestDelegatesSourcesAndNotesToCoreFormatter() {
        val sources = objectArray(
            jsonObject(
                "name", "KANJIDIC2",
                "license", "CC BY-SA",
                "source_path", "kanjidic2.xml",
                "database_version", "2026-05-01",
            ),
        )
        val notes = stringArray("note one", "note two")

        assertEquals(
            "Generated: 2026-05-15\n\nKANJIDIC2\nLicense: CC BY-SA\nSource: kanjidic2.xml\nVersion: 2026-05-01\n\nnote one\nnote two",
            AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", sources, notes)),
        )
    }

    @Test
    fun sourceAndNoteAdaptersDelegateToCoreFormatter() {
        val lines = mutableListOf<String>()

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
                null,
            ),
        )
        AttributionCopy.appendNotes(lines, listOf("note one", "note two"))

        assertEquals(
            "\nKANJIDIC2\nLicense: CC BY-SA\nSource: kanjidic2.xml\nVersion: 2026-05-01\n\nnote one\nnote two",
            lines.joinToString("\n"),
        )
    }

    @Test
    fun parsedDictionaryManifestUsesJapaneseLabelsInJapaneseLocale() {
        withJapaneseLocale {
            assertEquals(
                "辞書マニフェストが空です。",
                AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", sourceArray(), null)),
            )
            val sources = objectArray(
                jsonObject(
                    "name", "KANJIDIC2",
                    "license", "CC BY-SA",
                    "source_path", "kanjidic2.xml",
                    "database_version", "2026-05-01",
                ),
            )
            assertEquals(
                "生成: 2026-05-15\n\nKANJIDIC2\nライセンス: CC BY-SA\n出典: kanjidic2.xml\nバージョン: 2026-05-01",
                AttributionTexts.dictionarySourcesFromManifest(FakeManifest("2026-05-15", sources, null)),
            )
        }
    }

    private fun withJapaneseLocale(block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
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

    private fun stringArray(vararg values: String): JSONArray {
        return FakeStringArray(values.toList())
    }

    private fun objectArray(vararg values: JSONObject): JSONArray {
        return FakeObjectArray(values.toList())
    }

    private fun sourceArray(): JSONArray {
        return FakeObjectArray(emptyList())
    }

    private class FakeJsonObject(private val values: Map<String, String>) : JSONObject() {
        override fun optString(name: String?): String {
            return optString(name, "")
        }

        override fun optString(name: String?, fallback: String): String {
            val value = name?.let(values::get)
            return value ?: fallback
        }
    }

    private class FakeManifest(
        private val generatedAt: String,
        private val sources: JSONArray?,
        private val notes: JSONArray?,
    ) : JSONObject() {
        override fun optString(name: String?): String {
            return if (name == "generated_at") generatedAt else ""
        }

        override fun optJSONArray(name: String?): JSONArray? {
            return when (name) {
                "sources" -> sources
                "notes" -> notes
                else -> null
            }
        }
    }

    private class FakeStringArray(private val values: List<String>) : JSONArray() {
        override fun length(): Int {
            return values.size
        }

        override fun optString(index: Int): String {
            return values[index]
        }
    }

    private class FakeNonArraySourcesManifest(private val generatedAt: String) : JSONObject() {
        override fun optString(name: String?): String {
            return when (name) {
                "generated_at" -> generatedAt
                "sources" -> "not an array"
                else -> ""
            }
        }

        override fun optJSONArray(name: String?): JSONArray? {
            return null
        }
    }

    private class FakeObjectArray(private val values: List<JSONObject>) : JSONArray() {
        override fun length(): Int {
            return values.size
        }

        override fun getJSONObject(index: Int): JSONObject {
            return values[index]
        }
    }
}
