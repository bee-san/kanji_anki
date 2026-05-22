package dev.bee.kanjianki.core

object AttributionCopy {
    const val DICTIONARY_FALLBACK: String =
        "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data."
    const val KANJIVG_FALLBACK: String = "KanjiVG stroke data, CC BY-SA 3.0."

    @JvmStatic
    fun dictionarySources(generatedAt: String?, sources: List<Source?>?, notes: List<String?>?): String {
        if (sources.isNullOrEmpty()) {
            return "Dictionary manifest is empty."
        }
        val lines = ArrayList<String>()
        if (safe(generatedAt).isNotEmpty()) {
            lines.add("Generated: " + safe(generatedAt))
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
        addSourceLine(lines, "License", source.license)
        addSourceLine(lines, "URL", source.upstreamUrl)
        addSourceLine(lines, "Source", source.sourcePath)
        addSourceLine(lines, "Fetched", source.fetchDate)
        addSourceLine(
            lines,
            "Version",
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
