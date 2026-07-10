package dev.bee.kanjianki.syncdomain

import java.util.LinkedHashMap

class ProviderNotePolicy private constructor() {
    companion object {
        const val ARCHIVED_TAG: String = "kani_archived"
        const val REPAIRED_TAG: String = "kani_repaired"
        private const val LEGACY_ARCHIVED_TAG = "kanji_anki_archived"
        private const val NOTE_MODEL_QUERY_PREFIX = "note:\""

        @JvmStatic
        fun isArchivedTagPresent(tags: List<String>?): Boolean {
            return tags != null && (tags.contains(ARCHIVED_TAG) || tags.contains(LEGACY_ARCHIVED_TAG))
        }

        @JvmStatic
        fun isRepairedTagPresent(tags: List<String>?): Boolean = tags?.contains(REPAIRED_TAG) == true

        @JvmStatic
        fun selectRequiredFields(
            modelFields: List<String>,
            values: List<String>,
            requiredFields: List<String>,
        ): Map<String, String> {
            val fieldMap = LinkedHashMap<String, String>()
            for (field in requiredFields) {
                val index = modelFields.indexOf(field)
                fieldMap[field] = if (index >= 0 && index < values.size) values[index] else ""
            }
            return fieldMap
        }

        @JvmStatic
        fun browserQuerySearch(normalizedBrowserQuery: String): String = normalizedBrowserQuery

        @JvmStatic
        fun modelSearch(modelName: String): String = "$NOTE_MODEL_QUERY_PREFIX${escapeQuotedSearchValue(modelName)}\""

        private fun escapeQuotedSearchValue(value: String): String {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
        }
    }
}
