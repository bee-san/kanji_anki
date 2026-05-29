package dev.bee.kanjianki

data class SettingsNewCardSortOptionModel(
    val label: String,
    val mode: String,
    val description: String,
)

data class SettingsNewCardSortPreviewRowModel(
    val kanji: String,
    val primaryMeaning: String,
    val scoreLabel: String,
)

fun interface SettingsNewCardSortSaver {
    fun save(mode: String)
}

data class SettingsNewCardSortPanelModel(
    val title: String,
    val body: String,
    val initialMode: String,
    val options: List<SettingsNewCardSortOptionModel>,
    val saveLabel: String,
    val previewRowsByMode: Map<String, List<SettingsNewCardSortPreviewRowModel>> = emptyMap(),
    val previewWarningsByMode: Map<String, String> = emptyMap(),
    val onSave: SettingsNewCardSortSaver,
) : SettingsPanelModel {
    fun hasPreviewRows(): Boolean = previewRowsByMode.values.any { it.isNotEmpty() }

    fun previewRows(mode: String): List<SettingsNewCardSortPreviewRowModel> {
        return previewRowsByMode[mode].orEmpty()
    }

    fun previewWarning(mode: String): String? {
        return previewWarningsByMode[mode]
    }
}

object SettingsNewCardSortPreviewWarnings {
    private const val MAX_DISTANCE = 2
    private const val MAX_EXAMPLES = 3

    fun nearbySimilarPairExamples(
        rows: List<SettingsNewCardSortPreviewRowModel>,
        areSimilar: (String, String) -> Boolean,
    ): List<String> {
        val examples = ArrayList<String>()
        val seen = LinkedHashSet<String>()
        for (leftIndex in rows.indices) {
            val rightLimit = minOf(rows.lastIndex, leftIndex + MAX_DISTANCE)
            for (rightIndex in (leftIndex + 1)..rightLimit) {
                val left = rows[leftIndex].kanji
                val right = rows[rightIndex].kanji
                if (areSimilar(left, right)) {
                    val example = "$left/$right"
                    if (seen.add(example)) {
                        examples.add(example)
                        if (examples.size >= MAX_EXAMPLES) {
                            return examples
                        }
                    }
                }
            }
        }
        return examples
    }
}
