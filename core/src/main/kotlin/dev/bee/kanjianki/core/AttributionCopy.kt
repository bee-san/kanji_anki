package dev.bee.kanjianki.core

import java.util.Locale

object AttributionCopy {
    const val DICTIONARY_FALLBACK: String =
        "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data."
    const val KANJIVG_FALLBACK: String = "KanjiVG stroke data, CC BY-SA 3.0."

    private const val DICTIONARY_FALLBACK_JAPANESE: String =
        "EDRDGのKANJIDIC2辞書データ、Jiten順位データ、KanjiVG筆順データ。"
    private const val KANJIVG_FALLBACK_JAPANESE: String = "KanjiVG筆順データ、CC BY-SA 3.0。"
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun dictionaryFallback(): String = localizedText(DICTIONARY_FALLBACK, DICTIONARY_FALLBACK_JAPANESE)

    @JvmStatic
    fun kanjiVgFallback(): String = localizedText(KANJIVG_FALLBACK, KANJIVG_FALLBACK_JAPANESE)

    @JvmStatic
    fun dictionarySources(generatedAt: String?, sources: List<Source?>?, notes: List<String?>?): String {
        if (sources.isNullOrEmpty()) {
            return localizedText("Dictionary manifest is empty.", "辞書マニフェストが空です。")
        }
        val lines = ArrayList<String>()
        if (safe(generatedAt).isNotEmpty()) {
            lines.add(localizedLabel("Generated") + ": " + safe(generatedAt))
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
        addSourceLine(lines, localizedLabel("License"), source.license)
        addSourceLine(lines, localizedLabel("URL"), source.upstreamUrl)
        addSourceLine(lines, localizedLabel("Source"), source.sourcePath)
        addSourceLine(lines, localizedLabel("Fetched"), source.fetchDate)
        addSourceLine(
            lines,
            localizedLabel("Version"),
            firstNonEmpty(
                source.databaseVersion,
                source.version,
                source.dateOfCreation,
            ),
        )
        addSourceLine(lines, localizedLabel("SHA-256"), source.sourceSha256)
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

    private fun localizedLabel(english: String): String {
        if (!isJapaneseLocale()) {
            return english
        }
        return when (english) {
            "Generated" -> "生成"
            "License" -> "ライセンス"
            "Source" -> "出典"
            "Fetched" -> "取得日"
            "Version" -> "バージョン"
            else -> english
        }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

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
