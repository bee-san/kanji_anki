package dev.bee.kanjianki

data class BrowseScreenModel(
    val initialQuery: String,
    val resultHeading: String,
    val rows: List<BrowseKanjiRowModel>,
    val similarFilterActive: Boolean = false,
    val studySelectionSummary: String = "",
    val onToggleSimilarFilter: (String) -> Unit = {},
    val onSelectAllStudied: () -> Unit = {},
    val onDeselectAllStudied: () -> Unit = {},
    val onHome: () -> Unit,
    val onSearch: (String) -> Unit,
)

data class BrowseKanjiRowModel(
    val kanji: String,
    val meaning: String,
    val readings: String,
    val summary: String,
    val contentDescription: String,
    val suspended: Boolean,
    val studied: Boolean = !suspended,
    val onStudiedChange: (Boolean) -> Unit = {},
    val onClick: () -> Unit,
)
