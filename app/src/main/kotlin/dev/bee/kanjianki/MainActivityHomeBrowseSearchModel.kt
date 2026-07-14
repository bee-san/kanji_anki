package dev.bee.kanjianki

data class BrowseScreenModel(
    val initialQuery: String,
    val resultHeading: String,
    val rows: List<BrowseKanjiRowModel>,
    val similarFilterActive: Boolean = false,
    val allKanjiScope: Boolean = false,
    val studySelectionSummary: String = "",
    val onToggleSimilarFilter: (String) -> Unit = {},
    val onToggleAllKanjiScope: (String) -> Unit = {},
    val onSelectAllStudied: () -> Unit = {},
    val onDeselectAllStudied: () -> Unit = {},
    val onHome: () -> Unit,
    val onSearch: (String) -> Unit,
)

internal data class BrowseScreenData(
    val rows: List<BrowseKanjiRowModel>,
    val kanjiList: List<String>,
    val studiedCount: Int,
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
