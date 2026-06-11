package dev.bee.kanjianki.core

import java.util.Locale

object AttributionCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun dictionaryFallback(): String {
        return localizedText(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            "KANJIDIC2の辞書データ（EDRDG、Jitenの順位データ、KanjiVGの画数データ）。",
        )
    }

    @JvmStatic
    fun kanjiVgFallback(): String {
        return localizedText(
            "KanjiVG stroke data, CC BY-SA 3.0.",
            "KanjiVGの画数データ、CC BY-SA 3.0。",
        )
    }

    @JvmStatic
    fun dictionarySources(generatedAt: String?, sources: List<Source?>?, notes: List<String?>?): String {
        if (sources.isNullOrEmpty()) {
            return localizedText("Dictionary manifest is empty.", "辞書マニフェストが空です。")
        }
        val lines = ArrayList<String>()
        val generated = safe(generatedAt)
        if (generated.isNotEmpty()) {
            lines.add("${localizedText("Generated", "生成日時")}: $generated")
        }
        for (source in sources) {
            appendSource(lines, source)
        }
        appendNotes(lines, notes)
        return lines.joinToString("\n").javaTrim()
    }

    @JvmStatic
    fun appendSource(lines: MutableList<String>, source: Source?) {
        if (source == null) {
            return
        }
        lines.add("")
        lines.add(firstNonEmpty(source.name, source.id))
        addSourceLine(lines, localizedText("License", "ライセンス"), source.license)
        addSourceLine(lines, localizedText("URL", "URL"), source.upstreamUrl)
        addSourceLine(lines, localizedText("Source", "ソース"), source.sourcePath)
        addSourceLine(lines, localizedText("Fetched", "取得日"), source.fetchDate)
        addSourceLine(
            lines,
            localizedText("Version", "バージョン"),
            firstNonEmpty(
                source.databaseVersion,
                source.version,
                source.dateOfCreation,
            ),
        )
        addSourceLine(lines, "SHA-256", source.sourceSha256)
    }

    @JvmStatic
    fun appendNotes(lines: MutableList<String>, notes: List<String?>?) {
        if (notes.isNullOrEmpty()) {
            return
        }
        lines.add("")
        for (note in notes) {
            lines.add(safe(note))
        }
    }

    private fun addSourceLine(lines: MutableList<String>, label: String, value: String?) {
        val safeValue = safe(value)
        if (safeValue.isNotEmpty()) {
            lines.add("$label: $safeValue")
        }
    }

    private fun firstNonEmpty(vararg values: String?): String {
        for (value in values) {
            val safeValue = safe(value)
            if (safeValue.isNotEmpty()) {
                return safeValue
            }
        }
        return ""
    }

    private fun safe(value: String?): String {
        return value?.javaTrim() ?: ""
    }

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (isJapaneseLocale()) japanese else english
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    class Source(
        @JvmField val id: String?,
        @JvmField val name: String?,
        @JvmField val license: String?,
        @JvmField val upstreamUrl: String?,
        @JvmField val sourcePath: String?,
        @JvmField val fetchDate: String?,
        @JvmField val databaseVersion: String?,
        @JvmField val version: String?,
        @JvmField val dateOfCreation: String?,
        @JvmField val sourceSha256: String?,
    )
}
