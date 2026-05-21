package dev.bee.kanjianki

data class BrowseScreenModel(
    val initialQuery: String,
    val resultHeading: String,
    val rows: List<BrowseKanjiRowModel>,
    val onHome: () -> Unit,
    val onSearch: (String) -> Unit,
)

data class BrowseKanjiRowModel(
    val kanji: String,
    val meaning: String,
    val readings: String,
    val summary: String,
    val suspended: Boolean,
    val onClick: () -> Unit,
)
